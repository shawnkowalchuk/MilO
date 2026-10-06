package com.shawnkowalchuk.milo.platform.trip

import com.shawnkowalchuk.milo.core.trip.TripEvent
import com.shawnkowalchuk.milo.core.trip.TripRules
import com.shawnkowalchuk.milo.core.trip.TripState
import com.shawnkowalchuk.milo.core.trip.TripStateMachine
import com.shawnkowalchuk.milo.core.trip.TripTransition
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.point.RawPoint
import com.shawnkowalchuk.milo.data.settings.MiloSettings
import com.shawnkowalchuk.milo.data.settings.SettingsStore
import com.shawnkowalchuk.milo.platform.bluetooth.TruckConnectionSource
import com.shawnkowalchuk.milo.platform.bluetooth.TruckReading
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.max
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * How long after a deadline a GPS fix stands in for the timer. A timer in the service can stall
 * while the phone sleeps between fixes; the fixes themselves keep arriving. Two fixes of margin,
 * so the timer gets to go first when it is working.
 */
private const val TIMER_BACKSTOP_MS = 10_000L

/** What the event log calls the look at storage that follows a failure. */
private const val AFTER_FAILURE = "after a failure in the trip controller"

/**
 * The half of [TripController] that works through the inbox: one piece of work at a time, on
 * one coroutine. For each event it asks the trip rules what should happen, writes the outcome to
 * storage and the event log, and only then tells the service and the screens.
 *
 * Only [handle] may be called, and only by the controller's inbox loop. [known] and [rules] are
 * also read from other threads, so they are volatile.
 *
 * @param startService asks Android to start the trip service with a trigger in its intent.
 */
internal class TripWorker(
    private val ledger: TripLedger,
    private val eventLog: EventLogRepository,
    settings: SettingsStore,
    truck: TruckConnectionSource,
    private val clock: () -> Long,
    private val startService: (StartRequest) -> Unit,
) {
    /** What the trip rules know, or null until the stored state has been picked up. */
    @Volatile
    var known: TripState? = null
        private set

    private val ruleSettings = TripRuleSettings(settings, eventLog, clock)
    val rules: TripRules get() = ruleSettings.rules

    /** The hold on the trip service. The controller attaches the service; this class lets go. */
    val service = TripServiceLink()

    private val mutableActivity = MutableStateFlow(TripActivity())
    val activity: StateFlow<TripActivity> = mutableActivity.asStateFlow()

    private var startFailure: StartFailure? = null

    private val evidence = TripEvidence(truck)

    suspend fun handle(work: TripWork) {
        try {
            carryOut(work)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            // Every kind of failure, on purpose: in practice storage failed part-way through,
            // and what is in memory may no longer match it.
            forget(failure, "handling ${work::class.simpleName}")
            readStorageAgain()
        }
        // After a failure too: the screens must not go on showing what memory held before it.
        publish()
    }

    private suspend fun carryOut(work: TripWork) {
        when (work) {
            is TripWork.Trigger -> onTrigger(work.request)
            is TripWork.AndroidAuto -> onAndroidAuto(work)
            is TripWork.Fix -> onFix(work.fix)
            is TripWork.StartFailed -> onStartFailed(work)
            is TripWork.ServiceStopped -> onServiceStopped(work)
            is TripWork.Note -> eventLog.add(work.atMs, work.category, work.message, work.detail)
            is TripWork.CaughtUp -> work.done()
        }
        service.sync(known, rules)
    }

    /**
     * Drops what is held in memory, which may no longer match storage, and logs why. If the
     * event log is what failed, this write throws too and the process crashes, which leaves a
     * crash file: a failure here must not vanish.
     */
    private suspend fun forget(failure: Exception, doing: String) {
        known = null
        ledger.forget()
        val what = "The trip controller failed while $doing"
        eventLog.add(clock(), EventCategory.ERROR, what, failure.stackTraceToString())
    }

    /**
     * After a failure, storage is read again at once, so that an attached service is not left
     * with what was true before it: a service started for a trip that could not be stored is
     * stopped, and one whose trip is still open in storage is given it back. Once only, so
     * that storage which keeps failing cannot loop: the next trigger then tries again.
     */
    private suspend fun readStorageAgain() {
        try {
            onTrigger(StartRequest(TripTrigger.RECONCILE, AFTER_FAILURE, clock()))
            service.sync(known, rules)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (again: Exception) {
            forget(again, "reading storage again after a failure")
        }
    }

    private suspend fun onTrigger(request: StartRequest) {
        val settingsNow = ruleSettings.read()
        evidence.arrived(request)
        if (known == null) {
            if (!restore(request, settingsNow)) return
            // Picking up the stored state already took a fresh reading of the truck.
            if (request.trigger == TripTrigger.RECONCILE) return
        }
        val state = checkNotNull(known) { "The state was restored a moment ago" }
        val found = evidence.evidenceFor(request, state)
        val event = found.event
        if (event == null) {
            eventLog.add(clock(), EventCategory.TRIGGER, noNewsText(request, found))
        } else {
            advance(request.source, event, request, quiet = found.routine, reading = found.reading)
        }
        if (request.trigger == TripTrigger.POLL) recheckAndroidAuto(request)
    }

    /** After each poll: may Android Auto's word still be believed? */
    private suspend fun recheckAndroidAuto(request: StartRequest) {
        val event = evidence.androidAutoAfterPoll(known, request.atMs) ?: return
        val source = androidAutoRecheckText(event.connected)
        advance(source, event, request, EventCategory.ANDROID_AUTO)
    }

    /**
     * Picks up what storage holds after a process start (ADR-002, "State survives the process").
     *
     * @return false if the stored trip carries on and the service has to be started first. The
     * service then hands [request] back, and this runs again.
     */
    private suspend fun restore(request: StartRequest, settingsNow: MiloSettings): Boolean {
        val stored = ledger.load(rules)
        val reading = evidence.readTruck()
        // A reading of "unknown" counts as "not connected" here: nothing starts on it, and a
        // trip that was recording waits out a grace period, in which a later reading can
        // still show the truck. The price: it also releases a hold-off, as "not connected" does.
        val truckConnected = reading.connected
        val transition =
            TripStateMachine.restore(
                storedTrip = stored?.trip,
                lastRecordedAtMs = stored?.lastRecordedAtMs,
                autoStartHeldOffSinceMs = settingsNow.autoStartHeldOffSinceMs,
                truckConnected = truckConnected,
                // Android Auto can only be watched from the running service. If it is connected
                // the service says so a moment after it starts, in time to cancel a grace period
                // that was begun here.
                androidAutoConnected = false,
                atMs = request.atMs,
                rules = rules,
            )
        val found =
            TripState(stored?.trip, truckConnected, false, settingsNow.autoStartHeldOffSinceMs)
        val what = "${request.source}: picked up the stored state; ${reading.describe()}"
        val done = commit(what, found.describe(), transition, request)
        if (!done) ledger.forget()
        return done
    }

    private suspend fun onAndroidAuto(work: TripWork.AndroidAuto) {
        // Android Auto is reported only by the running service, whose own trigger comes first
        // and picks the state up. Without a state there is nothing to tell yet; the report is
        // remembered and told after the next poll.
        val event = evidence.androidAutoReported(work.connected, known, work.atMs) ?: return
        val stuck = work.connected && !event.connected
        val distrusted = if (stuck) " (taken for a stuck value)" else ""
        val restart = StartRequest(TripTrigger.RECONCILE, work.source, work.atMs)
        advance(work.source + distrusted, event, restart, EventCategory.ANDROID_AUTO)
    }

    private suspend fun onFix(fix: RawPoint) {
        // A deadline the service's timer missed comes first, judged before this fix is
        // counted. Counted first, a fix that shows movement would move the no-movement limit
        // of a forgotten manual trip on, and the trip would swallow the next drive.
        val due = known?.let { TripStateMachine.nextCheckAtMs(it, rules) }
        if (due != null && fix.wallClockMs >= due + TIMER_BACKSTOP_MS) {
            val source = "a GPS fix arrived after a deadline the timer missed"
            onTrigger(StartRequest(TripTrigger.CHECK_DUE, source, max(clock(), due)))
        }
        // A fix that arrives after the trip closed belongs to no trip.
        if (known?.trip == null || ledger.open == null) return
        val moved = ledger.addFix(fix)
        val restart = StartRequest(TripTrigger.RECONCILE, "GPS", fix.wallClockMs)
        if (moved) advance("GPS", TripEvent.Moved(fix.wallClockMs), restart, quiet = true)
    }

    private suspend fun onStartFailed(work: TripWork.StartFailed) {
        startFailure = work.failure
        val what = couldNotStartText(work.request, work.failure)
        eventLog.add(work.atMs, EventCategory.SERVICE, what, work.failure.detail)
        // A trip is open in memory, its service is gone, and Android will not bring it back:
        // nothing is recording that trip. Memory is dropped, so the screens stop showing a
        // trip in progress, and the next trigger picks the stored trip up through the restart
        // rules, which close it if it has gone stale (ADR-002, "State survives the process").
        if (known?.trip == null || service.recorder != null) return
        known = null
        ledger.forget()
        val dropped = "The open trip is not being recorded. The next trigger picks it up"
        eventLog.add(work.atMs, EventCategory.SERVICE, dropped)
    }

    private suspend fun onServiceStopped(work: TripWork.ServiceStopped) {
        // The usual case is not this one: the worker told the service to stop, and had let go
        // of it by then.
        if (!service.lostUnexpectedly(work.recorder) || known?.trip == null) return
        val what = "The trip service stopped while a trip is open. Asking Android to start it again"
        eventLog.add(work.atMs, EventCategory.SERVICE, what)
        startService(StartRequest(TripTrigger.RECONCILE, "trip service stopped", work.atMs))
    }

    private suspend fun advance(
        source: String,
        event: TripEvent,
        restart: StartRequest,
        category: EventCategory = EventCategory.TRIGGER,
        quiet: Boolean = false,
        reading: TruckReading? = null,
    ): Boolean {
        val before = checkNotNull(known) { "An event arrived before the state was picked up" }
        val transition = TripStateMachine.step(before, event, rules)
        val what = "$source: ${event.describe()}${reading.inWords()}"
        return commit(what, before.describe(), transition, restart, category, quiet)
    }

    /**
     * Makes the outcome of one step real: the event log and storage first, memory after.
     *
     * The lines are dated when they are written, not when the trigger fired. A trigger that had
     * to start the service is handled a moment after it fired, and the log must read in the
     * order things happened: service up, then trip started. The trip row keeps the trigger's
     * own time as its start.
     *
     * @param restart the trigger the service hands back if it has to be started first.
     * @param quiet no line for the event itself unless it changed something. For the two things
     * that arrive all day and usually change nothing: a GPS fix and a routine poll.
     * @return false if nothing was changed because the trip service is not running. ADR-002: a
     * trip is opened or carried on only with the service in the foreground.
     */
    private suspend fun commit(
        what: String,
        before: String,
        transition: TripTransition,
        restart: StartRequest,
        category: EventCategory = EventCategory.TRIGGER,
        quiet: Boolean = false,
    ): Boolean {
        val after = transition.state
        if (after.trip != null && service.recorder == null) {
            val waiting = "$what. Recording needs the trip service, which is not running"
            eventLog.add(clock(), category, waiting, "State: $before")
            startService(restart)
            return false
        }
        if (!quiet || transition.effects.isNotEmpty()) {
            eventLog.add(clock(), category, what, "Before: $before\nAfter: ${after.describe()}")
        }
        for (effect in transition.effects) {
            val line = ledger.carryOut(effect, ruleSettings.minimumTripDistanceMetres)
            eventLog.add(clock(), line.category, line.message)
        }
        if (transition.tripReallyBegan(before = known)) service.tripJustStarted = true
        known = after
        // A trip is open and the service is up for it: whatever failed before is history.
        if (after.trip != null) startFailure = null
        return true
    }

    private fun publish() {
        mutableActivity.value = tripActivityOf(ledger.open, known, startFailure)
    }
}

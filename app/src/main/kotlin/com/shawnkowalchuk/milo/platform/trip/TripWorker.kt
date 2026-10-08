package com.shawnkowalchuk.milo.platform.trip

import com.shawnkowalchuk.milo.core.trip.GPS_AFTER_VEHICLE_REPORT_MS
import com.shawnkowalchuk.milo.core.trip.ParkedGps
import com.shawnkowalchuk.milo.core.trip.TripEffect
import com.shawnkowalchuk.milo.core.trip.TripEvent
import com.shawnkowalchuk.milo.core.trip.TripRules
import com.shawnkowalchuk.milo.core.trip.TripStartCause
import com.shawnkowalchuk.milo.core.trip.TripState
import com.shawnkowalchuk.milo.core.trip.TripStateMachine
import com.shawnkowalchuk.milo.core.trip.TripTransition
import com.shawnkowalchuk.milo.core.trip.parkedGps
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.point.RawPoint
import com.shawnkowalchuk.milo.data.settings.MiloSettings
import com.shawnkowalchuk.milo.data.settings.SettingsStore
import com.shawnkowalchuk.milo.platform.bluetooth.TruckConnectionSource
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
 * @param motionSensorWatching see [TripController].
 * @param startService asks Android to start the trip service with a trigger in its intent.
 */
internal class TripWorker(
    private val ledger: TripLedger,
    private val eventLog: EventLogRepository,
    settings: SettingsStore,
    truck: TruckConnectionSource,
    private val motionSensorWatching: () -> Boolean,
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

    /**
     * When the phone last reported getting into a vehicle, or null if it has not since this
     * process started. Kept whatever MilO is doing: a report that starts the process arrives
     * before the stored wait has been picked up, and must still turn GPS on for it.
     */
    private var vehicleEnteredAtMs: Long? = null

    /**
     * The open trip whose driving off the service has been told of, so that the trip-start
     * sound plays once per trip. A trip picked up from storage that had driven off already is
     * entered here as it is picked up, without being told: its sound was played before.
     */
    private var drivingOffToldFor: Long? = null

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
            is TripWork.VehicleEntered -> onVehicleEntered(work.atMs)
            is TripWork.StartFailed -> onStartFailed(work)
            is TripWork.ServiceStopped -> onServiceStopped(work)
            is TripWork.Note -> eventLog.add(work.atMs, work.category, work.message, work.detail)
            is TripWork.CaughtUp -> work.done()
        }
        noticeDrivingOff()
        noticeVehicle()
        service.sync(known, rules, parkedGpsNow() ?: ParkedGps())
    }

    /**
     * The open trip is given the paired vehicle last seen connected (since 2026-10-08, when
     * MilO learned several), once it is a trip in a paired vehicle: the truck started it, or
     * was seen during it. A trip started with the button that no vehicle joined has none.
     * Looked at after every piece of work, like the driving off, because the reading that
     * names the vehicle can come after the trigger that opened the trip.
     */
    private suspend fun noticeVehicle() {
        val trip = known?.trip ?: return
        if (!trip.truckSeen && trip.startedBy != TripStartCause.TRUCK) return
        val vehicle = evidence.vehicleNow ?: return
        val line = ledger.noteVehicle(vehicle) ?: return
        eventLog.add(clock(), line.category, line.message)
    }

    /**
     * The open trip has been seen driving for the first time (`TripProgress.drivenAtMs`): the
     * moment for the trip-start sound. Looked at after every piece of work, because the fix
     * that shows it often changes nothing else, and a trip that a parked truck's moving starts
     * shows it from its first points. A trip opened by the companion callback alone waits
     * until the truck is confirmed, like its connect sound, and a false start has none.
     */
    private fun noticeDrivingOff() {
        val open = ledger.open ?: return
        val unconfirmed = known?.trip?.confirmByMs != null
        if (open.id == drivingOffToldFor || unconfirmed || open.progress.drivenAtMs == null) return
        drivingOffToldFor = open.id
        service.drivingOff = true
    }

    /**
     * How GPS is read beside the parked truck ([parkedGps]), or null while MilO is not
     * watching a parked truck.
     */
    private fun parkedGpsNow(): ParkedGps? {
        val parked = known?.takeIf { it.waitingToMove }?.parked ?: return null
        return parkedGps(parked.sinceMs, vehicleEnteredAtMs, motionSensorWatching())
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
            service.sync(known, rules, parkedGpsNow() ?: ParkedGps())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (again: Exception) {
            forget(again, "reading storage again after a failure")
        }
    }

    private suspend fun onTrigger(request: StartRequest) {
        // A timer of the service that watched the parked truck, arriving after MilO has stopped
        // watching. It must not count as a reconcile: there a reconcile starts a trip, and the
        // minute check still under way would start one in the moment watching stopped.
        if (request.trigger.isServiceTimer && known?.parked?.watching == false) return
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
            val read = found.readingInWords()
            advance(request.source, event, request, quiet = found.routine, reading = read)
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
        val stored = pickUpStored(ledger, evidence, rules, settingsNow, request.atMs)
        // A stored trip that had driven off before the restart has had its trip-start sound.
        ledger.open?.takeIf { it.progress.drivenAtMs != null }?.let { drivingOffToldFor = it.id }
        val what = "${request.source}: picked up the stored state; ${stored.readingText}"
        val done = commit(what, stored.found.describe(), stored.transition, request)
        if (!done) ledger.forget()
        // A wait that carries on watches from the place that was stored with it. One that
        // began in this very step (a stored trip had stood too long) has its place already.
        val carriesOn = stored.transition.effects.none { it is TripEffect.StartWaiting }
        if (done && carriesOn && stored.transition.state.waitingToMove) {
            ledger.resumeWaiting(settingsNow.parkedTruck)
        }
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
        val restart = StartRequest(TripTrigger.RECONCILE, "GPS", fix.wallClockMs)
        // Beside a parked truck the fix is looked at first: if it shows the truck driving off,
        // the trip starts, however long the wait has lasted. A missed trip is worse.
        if (known?.waitingToMove == true) {
            val movedAtMs = ledger.watchFix(fix)
            if (movedAtMs != null) {
                advance("GPS", TripEvent.Moved(movedAtMs), restart, quiet = true)
                // The trip's first points, this fix among them, were stored when it was opened.
                // It last moved at the newest of them that counted, not at the first.
                val last = ledger.lastMovementAtMs ?: return
                advance("GPS", TripEvent.Moved(last), restart, quiet = true)
                return
            }
        }
        // During a trip a deadline the service's timer missed comes first, judged before this
        // fix is counted. Counted first, a fix that shows movement would move the parked limit
        // of a trip that stood for hours on, and the trip would swallow the next drive.
        val due = known?.let { TripStateMachine.nextCheckAtMs(it, rules) }
        if (due != null && fix.wallClockMs >= due + TIMER_BACKSTOP_MS) {
            val source = "a GPS fix arrived after a deadline the timer missed"
            onTrigger(StartRequest(TripTrigger.CHECK_DUE, source, max(clock(), due)))
        }
        // A fix that arrives after the trip closed belongs to no trip.
        if (known?.trip == null || ledger.open == null) return
        val movement = ledger.addFix(fix) ?: return
        advance("GPS", movement, restart, quiet = true)
    }

    /**
     * The phone reported getting into a vehicle. Beside the parked truck GPS is read every 5
     * seconds for a while ([GPS_AFTER_VEHICLE_REPORT_MS]): the next [service] sync passes the
     * new times on. The driving alert writes a line for every report; this one is written only
     * for a report that changes how GPS is read beside the parked truck.
     */
    private suspend fun onVehicleEntered(atMs: Long) {
        vehicleEnteredAtMs = max(atMs, vehicleEnteredAtMs ?: atMs)
        val fastUntilMs = parkedGpsNow()?.fastUntilMs ?: return
        val nowMs = clock()
        if (fastUntilMs <= nowMs) return
        val line = gpsForDrivingText(ageMs = nowMs - atMs, forMs = fastUntilMs - nowMs)
        eventLog.add(nowMs, EventCategory.LOCATION, line)
    }

    private suspend fun onStartFailed(work: TripWork.StartFailed) {
        startFailure = work.failure
        val what = couldNotStartText(work.request, work.failure)
        eventLog.add(work.atMs, EventCategory.SERVICE, what, work.failure.detail)
        // A trip is open in memory, its service is gone, and Android will not bring it back:
        // nothing is recording that trip. Memory is dropped, so the screens stop showing a
        // trip in progress, and the next trigger picks the stored trip up through the restart
        // rules, which close it if it has gone stale (ADR-002, "State survives the process").
        // The same goes for a wait beside the parked truck that nothing is watching.
        val wasRecording = known?.trip != null
        if (known?.wantsService != true || service.recorder != null) return
        known = null
        ledger.forget()
        eventLog.add(work.atMs, EventCategory.SERVICE, droppedText(wasRecording))
    }

    private suspend fun onServiceStopped(work: TripWork.ServiceStopped) {
        // The usual case is not this one: the worker told the service to stop, and had let go
        // of it by then.
        if (!service.lostUnexpectedly(work.recorder) || known?.wantsService != true) return
        eventLog.add(work.atMs, EventCategory.SERVICE, serviceLostText(known?.trip != null))
        startService(StartRequest(TripTrigger.RECONCILE, "trip service stopped", work.atMs))
    }

    private suspend fun advance(
        source: String,
        event: TripEvent,
        restart: StartRequest,
        category: EventCategory = EventCategory.TRIGGER,
        quiet: Boolean = false,
        reading: String = "",
    ): Boolean {
        val before = checkNotNull(known) { "An event arrived before the state was picked up" }
        val transition = TripStateMachine.step(before, event, rules)
        val what = "$source: ${event.describe()}$reading"
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
     * trip is opened or carried on only with the service in the foreground, and so is a wait
     * beside the parked truck.
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
        if (after.wantsService && service.recorder == null) {
            val waiting = "$what. ${serviceNeededText(after.trip != null)}"
            eventLog.add(clock(), category, waiting, "State: $before")
            startService(restart)
            return false
        }
        if (!quiet || transition.effects.isNotEmpty()) {
            eventLog.add(clock(), category, what, "Before: $before\nAfter: ${after.describe()}")
        }
        for (effect in transition.effects) {
            val line = ledger.carryOut(effect, ruleSettings)
            eventLog.add(clock(), line.category, line.message)
        }
        if (transition.tripReallyBegan(before = known)) service.tripJustStarted = true
        known = after
        // A trip is open and the service is up for it: whatever failed before is history.
        if (after.trip != null) startFailure = null
        return true
    }

    private fun publish() {
        mutableActivity.value =
            tripActivityOf(ledger.open, known, startFailure, evidence.vehicleNow)
    }
}

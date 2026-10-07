package com.shawnkowalchuk.milo.platform.trip

import com.shawnkowalchuk.milo.core.trip.TripEvent
import com.shawnkowalchuk.milo.core.trip.TripStateMachine
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.point.RawPoint
import com.shawnkowalchuk.milo.data.point.RawPointRepository
import com.shawnkowalchuk.milo.data.settings.SettingsStore
import com.shawnkowalchuk.milo.data.trip.TripRepository
import com.shawnkowalchuk.milo.platform.bluetooth.TruckConnectionSource
import java.time.ZoneId
import kotlin.math.max
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * The one app-wide owner of trip recording (ADR-002). Every trigger, on whatever thread it
 * fires, ends up in [onTrigger]; the trip rules in `core/trip/` decide; this class makes the
 * decision real and publishes the result as [activity] for the phone screens and the Android
 * Auto screen.
 *
 * **One at a time.** Everything handed to the controller goes into an inbox and is worked
 * through by a single coroutine, in the order it arrived ([TripWorker]). Two triggers firing
 * together on different threads cannot interleave.
 *
 * **The service comes first.** A trip is opened, or carried on, only while the trip service is
 * in the foreground. When an event would leave a trip open and the service is not running,
 * nothing is stored: the service is asked to start with the trigger in its start intent, and it
 * hands the trigger back through [onServiceStarted]. If Android refuses the service there is no
 * trip row claiming to record. A trigger that plainly begins recording (a connect, a press of
 * Start) asks for the service directly on the thread it fired on, because the allowance Android
 * attaches to a Bluetooth broadcast is measured in seconds.
 *
 * @param motionSensorWatching whether the phone reports entering a vehicle to MilO right now
 * (`DrivingAlert.reportsComing`). A wait beside the parked truck turns GPS off after its first
 * hour only then ([com.shawnkowalchuk.milo.core.trip.parkedGpsUntilMs]).
 * @param clock wall-clock milliseconds.
 * @param zone the phone's time zone. It is asked for at one moment only, when a trip is closed
 * and sorted into Business or Personal by the day and time of day it started.
 * @param scope the application scope. The worker runs in it for the life of the process.
 */
class TripController(
    trips: TripRepository,
    points: RawPointRepository,
    eventLog: EventLogRepository,
    settings: SettingsStore,
    truck: TruckConnectionSource,
    private val starter: RecordingStarter,
    motionSensorWatching: () -> Boolean,
    private val clock: () -> Long,
    zone: () -> ZoneId,
    scope: CoroutineScope,
) {
    private val inbox = Channel<TripWork>(Channel.UNLIMITED)

    private val worker =
        TripWorker(
            ledger = TripLedger(trips, points, settings, zone),
            eventLog = eventLog,
            settings = settings,
            truck = truck,
            motionSensorWatching = motionSensorWatching,
            clock = clock,
            startService = ::startService,
        )

    /** What the screens show. See [TripActivity]. */
    val activity: StateFlow<TripActivity> = worker.activity

    init {
        scope.launch {
            for (work in inbox) {
                // Counted off before the work is handled: while handling it the worker must see
                // only the triggers still waiting behind this one.
                if (work is TripWork.Trigger && work.fromService) worker.service.triggerReached()
                worker.handle(work)
            }
        }
    }

    /**
     * The single entry point for triggers (ADR-002). Safe to call from any thread, and it never
     * blocks: a receiver calls it from `onReceive`.
     *
     * @param source where the trigger came from, in words, for the event log.
     */
    fun onTrigger(trigger: TripTrigger, source: String) {
        val request = StartRequest(trigger, source, clock())
        if (worker.service.recorder == null && beginsRecording(request)) {
            startService(request)
        } else {
            inbox.trySend(TripWork.Trigger(request))
        }
    }

    /**
     * The service is in the foreground and recording may begin.
     *
     * @param request the trigger that rode in the start intent, or null when Android restarted
     * the service by itself after killing the process. That case is a reconcile: the stored trip
     * is picked up with a fresh reading of the truck.
     */
    fun onServiceStarted(recorder: TripRecorder, request: StartRequest?) {
        worker.service.attach(recorder)
        val handedBack =
            request ?: StartRequest(TripTrigger.RECONCILE, "service restarted by Android", clock())
        inbox.trySend(TripWork.Trigger(handedBack, fromService = true))
    }

    /**
     * Whether the controller still counts on [recorder]. The service asks before it acts on an
     * order to stop: if it has reported in again since the order was given, the order is stale.
     */
    fun holds(recorder: TripRecorder): Boolean = worker.service.recorder === recorder

    /** Android refused to let the service enter the foreground. Nothing was recorded. */
    fun onServiceStartFailed(request: StartRequest?, failure: StartFailure) {
        inbox.trySend(TripWork.StartFailed(request, failure, clock()))
    }

    /**
     * The service is gone. Usually the controller told it to stop and has already let go of it.
     * If not (Android destroyed it), the worker tries to bring it back for the open trip.
     */
    fun onServiceStopped(recorder: TripRecorder) {
        inbox.trySend(TripWork.ServiceStopped(recorder, clock()))
    }

    /** One GPS fix from the service's location callback. Its trip id is filled in here. */
    fun onFix(fix: RawPoint) {
        inbox.trySend(TripWork.Fix(fix))
    }

    /**
     * The phone's driving detection noticed Shawn getting into a vehicle at [atMs]. Beside a
     * parked truck this turns GPS on again, and the fixes decide whether the truck is driving
     * off. It starts no trip by itself: the phone cannot tell the truck from another vehicle.
     */
    fun onVehicleEntered(atMs: Long) {
        inbox.trySend(TripWork.VehicleEntered(atMs))
    }

    /** Android Auto connected or disconnected, as the service's watcher saw it. */
    fun onAndroidAuto(connected: Boolean, source: String) {
        inbox.trySend(TripWork.AndroidAuto(connected, source, clock()))
    }

    /**
     * A timer set for [deadlineMs] has run out. The reading it prompts is stamped no earlier
     * than the deadline, so a phone clock that was set back while the timer ran cannot stretch
     * the grace period (docs/FINDINGS_LOG.md, the wall-clock debt).
     */
    fun onCheckDue(deadlineMs: Long) {
        val request = StartRequest(TripTrigger.CHECK_DUE, "timer", max(clock(), deadlineMs))
        inbox.trySend(TripWork.Trigger(request))
    }

    /** A line for the event log, written in order with everything else. */
    fun note(category: EventCategory, message: String, detail: String? = null) {
        inbox.trySend(TripWork.Note(clock(), category, message, detail))
    }

    /**
     * Calls [done] once everything handed to the controller before this call has been dealt
     * with. A manifest receiver keeps its broadcast open until then, so that Android does not
     * freeze the process between `onReceive` returning and the worker reading the truck. The
     * address lookup's pass at process start is asked for through it too, so that it reads the
     * trips after the reconcile has closed what the last process left open.
     */
    fun whenCaughtUp(done: () -> Unit) {
        inbox.trySend(TripWork.CaughtUp(done))
    }

    /**
     * Whether [request] plainly begins recording, judged without waiting for the worker. Only
     * the three triggers that need no reading of the truck can be judged this way. Before the
     * stored state has been read (a process the trigger itself started) the answer is yes: that
     * is the case with seconds to act, and a service started for nothing stops again.
     */
    private fun beginsRecording(request: StartRequest): Boolean {
        val event =
            when (request.trigger) {
                TripTrigger.TRUCK_LINK_CONNECTED -> TripEvent.TruckLinkConnected(request.atMs)

                TripTrigger.TRUCK_APPEARED -> TripEvent.TruckAppeared(request.atMs)

                // Start always leaves a trip open, whatever the truck's connection is read as.
                TripTrigger.MANUAL_START -> TripEvent.ManualStart(false, request.atMs)

                else -> return false
            }
        val known = worker.known ?: return true
        return TripStateMachine.step(known, event, worker.rules).state.trip != null
    }

    /** Asks for the service, and tells the event log (in order, through the inbox) how it went. */
    private fun startService(request: StartRequest) {
        val failure = starter.start(request)
        if (failure != null) {
            inbox.trySend(TripWork.StartFailed(request, failure, clock()))
        } else {
            val what = "${request.source}: ${request.trigger}: asked Android for the trip service"
            inbox.trySend(TripWork.Note(clock(), EventCategory.TRIGGER, what, detail = null))
        }
    }
}

/** One thing in the controller's inbox. */
internal sealed interface TripWork {
    /**
     * @param fromService true when the trip service handed the trigger over after reaching the
     * foreground. The service is not stopped for being idle while such a trigger is waiting.
     */
    data class Trigger(val request: StartRequest, val fromService: Boolean = false) : TripWork

    data class AndroidAuto(val connected: Boolean, val source: String, val atMs: Long) : TripWork

    data class Fix(val fix: RawPoint) : TripWork

    data class VehicleEntered(val atMs: Long) : TripWork

    data class StartFailed(val request: StartRequest?, val failure: StartFailure, val atMs: Long) :
        TripWork

    data class ServiceStopped(val recorder: TripRecorder, val atMs: Long) : TripWork

    data class Note(
        val atMs: Long,
        val category: EventCategory,
        val message: String,
        val detail: String?,
    ) : TripWork

    data class CaughtUp(val done: () -> Unit) : TripWork
}

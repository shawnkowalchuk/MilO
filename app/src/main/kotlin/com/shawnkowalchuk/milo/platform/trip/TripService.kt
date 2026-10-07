package com.shawnkowalchuk.milo.platform.trip

import android.app.ForegroundServiceStartNotAllowedException
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.SystemClock
import com.shawnkowalchuk.milo.app.MiloApplication
import com.shawnkowalchuk.milo.core.trip.POLL_INTERVAL_MS
import com.shawnkowalchuk.milo.core.trip.PollPacer
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.point.RawPoint
import com.shawnkowalchuk.milo.platform.bluetooth.TruckBluetoothReceiver
import com.shawnkowalchuk.milo.platform.car.AndroidAutoWatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch

/** The trip notification's distance is refreshed at most this often. */
private const val NOTIFICATION_MIN_GAP_MS = 15_000L

/** What the event log calls the once-a-minute reading of the truck. */
private const val MINUTE_CHECK = "minute check"

/**
 * The foreground service that records a trip (ADR-002, "The service"). It is the part of the app
 * Android keeps alive during a drive, and everything that only makes sense while a trip is open
 * lives in it: the GPS fixes, the timers, the watch on Android Auto and on the truck's Bluetooth
 * broadcasts, the notification and the trip-start sound. It stays up, doing less, while MilO
 * waits beside a truck that is connected and parked ([Work.WATCHING_PARKED]).
 *
 * It decides nothing. It reports to the [TripController] (a fix, a timer running out, Android
 * Auto changing) and does what the controller tells it through [TripRecorder].
 *
 * Its life: a trigger asks Android to start it with the trigger in the intent
 * ([tripServiceIntent]). [onStartCommand] enters the foreground first and only then hands the
 * trigger to the controller, so no trip is ever opened by a service that Android refused. The
 * controller answers with [record], [watchParked] or [stop].
 *
 * In the manifest it has `stopWithTask="false"`: swiping MilO out of the recent apps must not
 * end a trip. It is `START_STICKY`: if Android kills the process, it creates the service again
 * with no intent, and the controller picks the stored trip up from the database.
 */
class TripService :
    Service(),
    TripRecorder {
    private val container get() = (application as MiloApplication).container
    private val controller get() = container.tripController

    /** For the timers. They run inside the service, so they end when it does. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private lateinit var location: LocationRecorder
    private lateinit var androidAuto: AndroidAutoWatcher
    private lateinit var sound: TripStartSound

    /** Hears the truck disconnect during a trip, even if the manifest receiver does not. */
    private val truckReceiver = TruckBluetoothReceiver()

    /**
     * What the controller last asked for. While [WATCHING_PARKED] no trip is open: the truck is
     * connected and parked, and its position is read at a low rate until it moves. Android Auto
     * is watched then as during a trip: with the truck's Bluetooth gone it holds the wait.
     */
    private enum class Work { NONE, RECORDING, WATCHING_PARKED }

    // Everything below is touched on the main thread only.
    private var inForeground = false
    private var newestStartId = 0
    private var work = Work.NONE
    private var destroyed = false
    private var session: Job? = null
    private val checkTimer = TripCheckTimer(scope) { controller.onCheckDue(it) }
    private var androidAutoConnected = false
    private val pollPacer = PollPacer()

    override fun onCreate() {
        super.onCreate()
        val clock = System::currentTimeMillis
        location =
            LocationRecorder(
                context = this,
                clock = clock,
                onFix = ::onFix,
                onNote = { controller.note(EventCategory.LOCATION, it) },
            )
        androidAuto =
            AndroidAutoWatcher(this) { connected, rawType ->
                androidAutoConnected = connected
                controller.onAndroidAuto(connected, "CarConnection reports type $rawType")
            }
        sound = TripStartSound(this) { controller.note(EventCategory.SERVICE, it) }
    }

    /** Not a bound service: nothing binds to it. */
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            // The first statement, before anything else is looked at (ADR-002). Android gives a
            // service started this way only seconds to get here.
            startForeground(
                TRIP_NOTIFICATION_ID,
                container.tripNotifications.ongoingFor(controller.activity.value),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            )
        } catch (notAllowed: ForegroundServiceStartNotAllowedException) {
            // MilO is in the background without an exemption that allows the start.
            return couldNotEnterForeground(intent, notAllowed, startId)
        } catch (denied: SecurityException) {
            // Location is not "Allow all the time", or the location permission is missing.
            return couldNotEnterForeground(intent, denied, startId)
        }
        inForeground = true
        newestStartId = startId
        val request = intent?.let(::startRequestFrom)
        val forWhat = request?.let { "${it.source}: ${it.trigger}" } ?: "restarted by Android"
        controller.note(EventCategory.SERVICE, "Trip service in the foreground ($forWhat)")
        controller.onServiceStarted(this, request)
        return START_STICKY
    }

    /** ADR-002: a failure is logged and falls back to the "tap to start" notification. */
    private fun couldNotEnterForeground(intent: Intent?, failure: Exception, startId: Int): Int {
        val shown = container.tripNotifications.showCouldNotStart()
        val unseen =
            if (shown) "" else "\nNotifications are off for MilO, so the warning was not shown."
        val request = intent?.let(::startRequestFrom)
        controller.onServiceStartFailed(request, StartFailure(emptyList(), "$failure$unseen"))
        // A service that is already in the foreground from an earlier start carries on: one
        // refused start must not end a trip that is being recorded.
        if (!inForeground) stopSelf(startId)
        return START_NOT_STICKY
    }

    // ---- What the controller asks for (called from its worker thread) ---------------------------

    override fun record(checkAtMs: Long?, tripJustStarted: Boolean) {
        mainExecutor.execute {
            if (destroyed) return@execute
            turnTo(Work.RECORDING)
            if (tripJustStarted) announceTripStart()
            checkTimer.set(checkAtMs)
        }
    }

    override fun watchParked(checkAtMs: Long?) {
        mainExecutor.execute {
            if (destroyed) return@execute
            turnTo(Work.WATCHING_PARKED)
            checkTimer.set(checkAtMs)
        }
    }

    override fun stop() {
        mainExecutor.execute {
            // A new start reached this service after the controller gave the order: it is
            // needed again, and the order no longer stands.
            if (controller.holds(this)) return@execute
            endSession()
            // Not a plain stopSelf(): if a new start is already on its way, this service must
            // stay up for it, and Android then ignores the request to stop.
            stopSelfResult(newestStartId)
        }
    }

    // ---- One recording session -------------------------------------------------------------------

    /** Begins the session with the first order, and moves between recording and watching. */
    private fun turnTo(next: Work) {
        if (work == next) return
        val wasWatching = work == Work.WATCHING_PARKED
        if (work == Work.NONE) beginSession()
        work = next
        location.start(if (next == Work.RECORDING) FixRate.RECORDING else FixRate.WATCHING_PARKED)
        // For the whole session. Starting a watch that is running does nothing.
        androidAuto.start()
        if (next == Work.RECORDING) {
            // At once, not when the trip's figures next change: the notification must not go
            // on saying that the truck is parked.
            val trip = controller.activity.value.trip
            if (wasWatching) container.tripNotifications.updateTripInProgress(trip)
        } else {
            container.tripNotifications.showParkedWaiting()
        }
    }

    private fun beginSession() {
        // The service is at work, so a warning that it could not start is out of date.
        container.tripNotifications.cancelCouldNotStart()
        truckReceiver.registerIn(this)
        pollPacer.start(SystemClock.elapsedRealtime())
        session =
            scope.launch {
                launch { pollTheTruck() }
                launch { keepNotificationCurrent() }
            }
    }

    private fun endSession() {
        if (work == Work.NONE) return
        work = Work.NONE
        location.stop()
        androidAuto.stop()
        truckReceiver.unregisterFrom(this)
        session?.cancel()
        checkTimer.set(null)
    }

    /** Recording has really begun: the moment for the trip-start sound, once per trip. */
    private fun announceTripStart() {
        val unreadable = { what: String -> controller.note(EventCategory.ERROR, what) }
        scope.launch { sound.playAsSet(container.settingsStore, mainExecutor, unreadable) }
    }

    // The debt of this timer and of the check timer, which can both fire late while the phone
    // sleeps, is set out in TripCheckTimer.kt.
    private suspend fun pollTheTruck() {
        // Ends when the session is cancelled: delay() stops waiting and the loop is left.
        while (true) {
            delay(POLL_INTERVAL_MS)
            mainExecutor.execute {
                if (pollPacer.timerFired(SystemClock.elapsedRealtime())) readTheTruck(MINUTE_CHECK)
            }
        }
    }

    /** One GPS fix. It also stands in for the minute timer when that is late ([PollPacer]). */
    private fun onFix(fix: RawPoint) {
        controller.onFix(fix)
        if (pollPacer.fixArrived(SystemClock.elapsedRealtime())) {
            readTheTruck("$MINUTE_CHECK (prompted by a GPS fix: the timer was late)")
        }
    }

    /**
     * ADR-002, amendment 6: the truck's connection is read again about once a minute. Beside a
     * parked truck too: it is how a disconnect that was never reported ends the wait.
     */
    private fun readTheTruck(source: String) {
        if (work == Work.NONE) return
        controller.onTrigger(TripTrigger.POLL, source)
        // While Android Auto is believed connected, ask it again: a missed "disconnected"
        // would otherwise hold the trip open (see AndroidAutoWatcher.refresh).
        if (androidAutoConnected) androidAuto.refresh()
    }

    /**
     * Keeps the notification's text in step with the trip, at a modest rate. The distance
     * changes with every fix; conflate() drops all but the newest value while this waits out
     * the gap, so the notification is posted at most once per gap.
     */
    private suspend fun keepNotificationCurrent() {
        val notifications = container.tripNotifications
        controller.activity
            .mapNotNull { it.trip }
            .distinctUntilChanged()
            .conflate()
            .collect { trip ->
                // Posted on the main thread and only while recording. Posted from here, it
                // could land just after the service stopped and leave a "Trip in progress"
                // notification behind that nothing would ever take away, or replace the one
                // that says the truck is parked.
                mainExecutor.execute {
                    if (work == Work.RECORDING) notifications.updateTripInProgress(trip)
                }
                delay(NOTIFICATION_MIN_GAP_MS)
            }
    }

    // ---- The end, wanted or not ------------------------------------------------------------------

    /** MilO was swiped out of the recent apps. The trip carries on (`stopWithTask="false"`). */
    override fun onTaskRemoved(rootIntent: Intent?) {
        controller.note(EventCategory.SERVICE, "MilO was removed from the recent apps")
    }

    override fun onDestroy() {
        destroyed = true
        endSession()
        scope.cancel()
        controller.note(EventCategory.SERVICE, "Trip service destroyed")
        controller.onServiceStopped(this)
        super.onDestroy()
    }
}

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
import com.shawnkowalchuk.milo.data.settings.MiloSettings
import com.shawnkowalchuk.milo.platform.bluetooth.TruckBluetoothReceiver
import com.shawnkowalchuk.milo.platform.car.AndroidAutoWatcher
import java.io.IOException
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
 * broadcasts, the notification and the trip-start sound.
 *
 * It decides nothing. It reports to the [TripController] (a fix, a timer running out, Android
 * Auto changing) and does what the controller tells it through [TripRecorder].
 *
 * Its life: a trigger asks Android to start it with the trigger in the intent
 * ([tripServiceIntent]). [onStartCommand] enters the foreground first and only then hands the
 * trigger to the controller, so no trip is ever opened by a service that Android refused. The
 * controller answers with [record] or [stop].
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

    // Everything below is touched on the main thread only.
    private var inForeground = false
    private var newestStartId = 0
    private var recording = false
    private var destroyed = false
    private var session: Job? = null
    private var checkTimer: Job? = null
    private var checkTimerFor: Long? = null
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
                container.tripNotifications.tripInProgress(controller.activity.value.trip),
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
            if (!recording) beginSession()
            if (tripJustStarted) announceTripStart()
            setCheckTimer(checkAtMs)
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

    private fun beginSession() {
        recording = true
        // Recording is under way, so a warning that it could not start is out of date.
        container.tripNotifications.cancelCouldNotStart()
        location.start()
        androidAuto.start()
        truckReceiver.registerIn(this)
        pollPacer.start(SystemClock.elapsedRealtime())
        session =
            scope.launch {
                launch { pollTheTruck() }
                launch { keepNotificationCurrent() }
            }
    }

    private fun endSession() {
        if (!recording) return
        recording = false
        location.stop()
        androidAuto.stop()
        truckReceiver.unregisterFrom(this)
        session?.cancel()
        setCheckTimer(null)
    }

    /** Recording has really begun: the moment for the trip-start sound, once per trip. */
    private fun announceTripStart() {
        scope.launch {
            val settings =
                try {
                    container.settingsStore.current()
                } catch (unreadable: IOException) {
                    // The controller logs the unreadable file. The sound is on by default, so
                    // the bundled chirp plays.
                    controller.note(EventCategory.ERROR, "Sound settings unreadable: $unreadable")
                    MiloSettings()
                }
            if (settings.soundEnabled) mainExecutor.execute { sound.play(settings.customSoundUri) }
        }
    }

    /**
     * The timer the trip rules ask for: the end of a grace period, the confirmation deadline of
     * a companion start, or the no-movement limit of a manual trip. When it runs out the
     * controller reads the truck's connection, and the rules decide.
     */
    private fun setCheckTimer(checkAtMs: Long?) {
        if (checkAtMs == checkTimerFor) return
        checkTimerFor = checkAtMs
        checkTimer?.cancel()
        if (checkAtMs == null) return
        checkTimer =
            scope.launch {
                delay((checkAtMs - System.currentTimeMillis()).coerceAtLeast(0))
                controller.onCheckDue(checkAtMs)
            }
    }

    // TODO(debt): the timers here are coroutines, and a coroutine's delay does not count time
    //  the phone spends asleep. With the screen off they can fire late. A GPS fix stands in for
    //  a late timer (a deadline in TripWorker.onFix, the minute check in onFix below), but with
    //  no fixes arriving a trip can stay open past its grace period, and a disconnect that was
    //  never reported is noticed late. The cure, if the phone shows it is needed, is a wake
    //  lock for the length of a trip. See docs/FINDINGS_LOG.md.
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

    /** ADR-002, amendment 6: the truck's connection is read again about once a minute. */
    private fun readTheTruck(source: String) {
        if (!recording) return
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
                // notification behind that nothing would ever take away.
                mainExecutor.execute { if (recording) notifications.updateTripInProgress(trip) }
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

package com.shawnkowalchuk.milo.clockjump

import com.shawnkowalchuk.milo.core.clock.DAY_MS
import com.shawnkowalchuk.milo.core.clock.JumpingPhone
import com.shawnkowalchuk.milo.core.trip.GOOD_ACCURACY_METRES
import com.shawnkowalchuk.milo.core.trip.ParkedGps
import com.shawnkowalchuk.milo.core.trip.TRACK_START_WALL_CLOCK_MS
import com.shawnkowalchuk.milo.core.trip.fixAt
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.point.RawPoint
import com.shawnkowalchuk.milo.data.point.RawPointRepository
import com.shawnkowalchuk.milo.data.settings.TripSound
import com.shawnkowalchuk.milo.data.trip.TripRepository
import com.shawnkowalchuk.milo.platform.trip.FakeWorld
import com.shawnkowalchuk.milo.platform.trip.FixRate
import com.shawnkowalchuk.milo.platform.trip.RecordingStarter
import com.shawnkowalchuk.milo.platform.trip.StartFailure
import com.shawnkowalchuk.milo.platform.trip.StartRequest
import com.shawnkowalchuk.milo.platform.trip.TripController
import com.shawnkowalchuk.milo.platform.trip.TripRecorder
import com.shawnkowalchuk.milo.platform.trip.TripTrigger
import com.shawnkowalchuk.milo.platform.trip.nextChangeAfter
import com.shawnkowalchuk.milo.platform.trip.rateAt
import kotlin.math.max
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent

/**
 * Where the clock of these tests starts: Friday 2 October 2026, 12:00 UTC. The default work
 * schedule tracks Monday to Friday, 08:00 to 16:30, so a trip that starts now is Business, and
 * the same trip dated a day ahead (Saturday) would be Personal.
 */
const val FRIDAY_NOON_MS = TRACK_START_WALL_CLOCK_MS - DAY_MS

/** The speed reading of a truck that covers 100 m between two fixes five seconds apart. */
const val DRIVING_SPEED = 20f

/**
 * A timer of the trip service, modelled exactly as `TripCheckTimer.set` is written: setting it
 * for the deadline it is already set for changes nothing; otherwise it waits "deadline minus
 * the clock at the moment it is set", never less than nought, and that wait runs on time since
 * boot (a coroutine's delay).
 */
class SceneTimer(private val clock: () -> Long, private val elapsedNow: () -> Long) {
    /** The deadline the timer is set for. */
    var setFor: Long? = null
        private set

    /** When it runs out, on the clock that counts from boot, or null if it is not set. */
    var dueAtElapsedMs: Long? = null
        private set

    fun set(deadlineMs: Long?) {
        if (deadlineMs == setFor) return
        setFor = deadlineMs
        dueAtElapsedMs = deadlineMs?.let { elapsedNow() + (it - clock()).coerceAtLeast(0) }
    }

    /** It has run out: the deadline it was set for, which is what the service hands on. */
    fun ranOut(): Long = checkNotNull(setFor) { "the timer is not set" }.also {
        dueAtElapsedMs = null
    }
}

/**
 * Stands in for Android and the trip service, like `FakeService`, and for the two things the
 * real service does with the time: its timers ([SceneTimer]) and, beside the parked truck, the
 * switching of GPS (`TripService.parkedGpsInLine`, through the same two functions).
 *
 * @param clock what the service is handed as its clock.
 */
class ClockJumpService(private val clock: () -> Long, elapsedNow: () -> Long) :
    RecordingStarter,
    TripRecorder {
    lateinit var controller: TripController

    /** False to hold a start back, as Android does for a moment: [comeUp] lets it through. */
    var comesUpAtOnce = true
    private val waiting = mutableListOf<StartRequest>()

    var recording = false
    var watchingParked = false
    var stops = 0
    var tripStartsAnnounced = 0

    /** How often the trip-start sound was asked for: a trip seen driving off. */
    var drivingOffsAnnounced = 0

    /** Every sound asked for, in order. */
    val sounds = mutableListOf<TripSound>()

    /** The timer the trip rules ask for (`TripService.checkTimer`). */
    val checkTimer = SceneTimer(clock, elapsedNow)

    /** The timer of the next change of how GPS is read beside the parked truck. */
    val gpsTimer = SceneTimer(clock, elapsedNow)

    /** The deadline the rules' timer is set for. */
    val checkAtMs: Long? get() = checkTimer.setFor

    /** Beside the parked truck: how the controller last said GPS is to be read. */
    var gps = ParkedGps()

    /** Beside the parked truck: how GPS is being read, or null once it has been switched off. */
    var parkedGpsRate: FixRate? = null

    override fun start(request: StartRequest): StartFailure? {
        waiting += request
        if (comesUpAtOnce) comeUp()
        return null
    }

    /** The service reaches the foreground and hands over the triggers it was started with. */
    fun comeUp() {
        val handOver = waiting.toList()
        waiting.clear()
        handOver.forEach { controller.onServiceStarted(this, it) }
    }

    override fun record(checkAtMs: Long?, sounds: List<TripSound>) {
        recording = true
        watchingParked = false
        this.sounds += sounds
        if (TripSound.CONNECT in sounds) tripStartsAnnounced++
        if (TripSound.DRIVING_OFF in sounds) drivingOffsAnnounced++
        checkTimer.set(checkAtMs)
        gpsTimer.set(null)
    }

    override fun watchParked(checkAtMs: Long?, gps: ParkedGps) {
        recording = false
        watchingParked = true
        checkTimer.set(checkAtMs)
        this.gps = gps
        parkedGpsInLine()
    }

    override fun stop() {
        recording = false
        watchingParked = false
        stops++
        checkTimer.set(null)
        gpsTimer.set(null)
    }

    /** `TripService.parkedGpsInLine`: with each order, each fix, and when its timer runs out. */
    fun parkedGpsInLine(nowMs: Long = clock()) {
        if (!watchingParked) return
        parkedGpsRate = gps.rateAt(nowMs)
        gpsTimer.set(gps.nextChangeAfter(nowMs))
    }
}

/**
 * A phone whose date is set a day ahead by hand and comes back a few seconds later, with MilO's
 * real trip controller, rules and storage (the stand-ins of `FakeWorld`) running on it, **and
 * MilO's real clock** (`TrustedClock`, on a [JumpingPhone]) handed to all of them, as the app's
 * wiring hands it.
 *
 * Two clocks, as on the phone: time since boot, which only this scene moves ([goTo]) and which
 * never jumps, and the wall clock, which is that time plus a day while the date is set ahead
 * ([dateSetAhead], [dateSetBack]). A GPS fix is stamped as `LocationRecorder` stamps it: with
 * the clock it is handed, at the moment it is delivered.
 */
// runCurrent() is how a test lets the controller's worker run; the API is marked experimental.
@OptIn(ExperimentalCoroutinesApi::class)
class ClockJumpScene(private val scope: TestScope, val world: FakeWorld = FakeWorld()) {
    val phone = JumpingPhone(FRIDAY_NOON_MS)
    lateinit var controller: TripController
    lateinit var service: ClockJumpService

    /** MilO's clock, as every part of MilO reads it. */
    val nowMs: Long get() = phone.clock.now()

    init {
        newProcess()
    }

    /** The true time of day [second] seconds into the scene, whatever the phone's clock says. */
    fun realTimeOf(second: Int): Long = FRIDAY_NOON_MS + second * 1000L

    /** How long from now the service's timer runs out, or null if it is not set. */
    val timerRunsOutInMs: Long?
        get() = service.checkTimer.dueAtElapsedMs?.let { it - phone.elapsedMs }

    /**
     * MilO's process is gone and a new one is built on the same storage, with a clock that
     * starts from the stored anchor. Nothing is triggered.
     */
    fun newProcess() {
        phone.newProcess()
        val clock = { phone.clock.now() }
        val next = ClockJumpService(clock = clock, elapsedNow = { phone.elapsedMs })
        next.controller =
            TripController(
                trips = TripRepository(world.trips),
                points = RawPointRepository(world.points),
                eventLog = EventLogRepository(world.log),
                settings = world.settings,
                truck = world.truck,
                starter = next,
                motionSensorWatching = { world.motionSensorWatching },
                clock = clock,
                zone = { world.zone },
                scope = scope.backgroundScope,
            )
        service = next
        controller = next.controller
        scope.runCurrent()
    }

    /** A new process as `MiloApplication.onCreate` starts it: with the reconcile. */
    fun processStarts() {
        newProcess()
        trigger(TripTrigger.RECONCILE, "process start")
    }

    /**
     * Time passes until [second] seconds into the scene. A timer of the service that runs out
     * on the way fires at its own moment.
     */
    fun goTo(second: Int) {
        val target = realTimeOf(second)
        var fired = 0
        while (true) {
            val check = service.checkTimer.dueAtElapsedMs
            val gps = service.gpsTimer.dueAtElapsedMs
            val due = listOfNotNull(check, gps).minOrNull() ?: break
            val dueAtTrueMs = phone.trueNowMs + (due - phone.elapsedMs)
            if (dueAtTrueMs > target) break
            check(++fired < MAX_TIMERS_PER_STEP) { "a timer keeps firing" }
            phone.advanceTo(max(phone.trueNowMs, dueAtTrueMs))
            if (due == check) {
                controller.onCheckDue(service.checkTimer.ranOut())
            } else {
                service.parkedGpsInLine(max(nowMs, service.gpsTimer.ranOut()))
            }
            scope.runCurrent()
        }
        phone.advanceTo(max(phone.trueNowMs, target))
    }

    /** Settings, "Set date": the phone's clock is now exactly 24 hours ahead. */
    fun dateSetAhead() = phone.setAhead()

    /** Automatic time is switched on again: network time puts the clock back. */
    fun dateSetBack() = phone.setBack()

    fun trigger(trigger: TripTrigger, source: String) {
        controller.onTrigger(trigger, source)
        scope.runCurrent()
    }

    /** The service's once-a-minute reading of the truck, at [second]. */
    fun minuteCheck(second: Int) {
        goTo(second)
        trigger(TripTrigger.POLL, "minute check")
    }

    /**
     * One GPS fix [northMetres] up the road at [second]. Beside the parked truck no fix comes
     * once the service has switched GPS off, whatever the truck does.
     *
     * @return whether a fix was delivered.
     */
    fun fix(northMetres: Double, second: Int, speedMetresPerSecond: Float? = null): Boolean {
        goTo(second)
        if (service.watchingParked && service.parkedGpsRate == null) return false
        val place = fixAt(northMetres = northMetres, second = 0)
        controller.onFix(
            RawPoint(
                tripId = 0,
                wallClockMs = nowMs,
                elapsedRealtimeMs = phone.elapsedMs,
                latitude = place.latitude,
                longitude = place.longitude,
                accuracyMetres = GOOD_ACCURACY_METRES,
                speedMetresPerSecond = speedMetresPerSecond,
            ),
        )
        // As `TripService.onFix`: the fix stands in for a GPS timer that is late.
        service.parkedGpsInLine()
        scope.runCurrent()
        return true
    }

    /**
     * A drive due north at 72 km/h, a fix every five seconds from [fromSecond] to [toSecond]:
     * the first fix at [fromNorthMetres], each next one 100 m on.
     *
     * @return where the last fix was, in metres north.
     */
    fun drive(fromSecond: Int, toSecond: Int, fromNorthMetres: Double = 0.0): Double {
        var north = fromNorthMetres
        for (second in fromSecond..toSecond step FIX_EVERY_SECONDS) {
            north = fromNorthMetres + (second - fromSecond) / FIX_EVERY_SECONDS * METRES_PER_FIX
            fix(north, second, DRIVING_SPEED)
        }
        return north
    }

    /** The truck stands at [northMetres]: a fix every five seconds, reading no speed. */
    fun stand(northMetres: Double, fromSecond: Int, toSecond: Int) {
        for (second in fromSecond..toSecond step FIX_EVERY_SECONDS) {
            fix(northMetres, second, speedMetresPerSecond = 0f)
        }
    }

    /** The truck's Bluetooth connects (the ACL broadcast) at [second], and a trip starts. */
    fun truckConnects(second: Int) {
        goTo(second)
        world.truck.connected = true
        trigger(TripTrigger.TRUCK_LINK_CONNECTED, "ACL connect")
    }

    /** The truck's Bluetooth is seen to drop at [second]. */
    fun truckDisconnects(second: Int) {
        goTo(second)
        world.truck.connected = false
        trigger(TripTrigger.TRUCK_DISCONNECTED, "ACL disconnect")
    }

    private companion object {
        const val FIX_EVERY_SECONDS = 5
        const val METRES_PER_FIX = 100.0
        const val MAX_TIMERS_PER_STEP = 50
    }
}

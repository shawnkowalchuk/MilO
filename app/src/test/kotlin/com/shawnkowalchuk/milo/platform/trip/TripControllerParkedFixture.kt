package com.shawnkowalchuk.milo.platform.trip

import com.shawnkowalchuk.milo.core.trip.GOOD_ACCURACY_METRES
import com.shawnkowalchuk.milo.core.trip.TRACK_START_WALL_CLOCK_MS
import com.shawnkowalchuk.milo.core.trip.fixAt
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent

/** How far the drive of [ParkedScene.driveAndPark] goes: 21 fixes, 100 m apart. */
const val DRIVEN_METRES = 2_000.0

/** When that drive's last fix was taken, seconds into the track: where and when it stopped. */
const val STOPPED_AT_SECOND = 100

/** When the truck of these tests drives off again: half an hour after it was parked. */
const val DRIVES_OFF_AT_SECOND = STOPPED_AT_SECOND + 1_800

/**
 * A truck that connects, is driven two kilometres north, and stops with Bluetooth still
 * connected: the afternoon of 2026-10-06 in the owner's driveway. Positions are metres north of
 * the track's origin and times are seconds into the track, as in `TrackBuilder.kt`.
 */
// See TripControllerTest for why runCurrent() needs the opt-in.
@OptIn(ExperimentalCoroutinesApi::class)
class ParkedScene(private val scope: TestScope, val world: FakeWorld) {
    lateinit var controller: TripController
    lateinit var service: FakeService

    /** The wall-clock time [second] seconds into the track. */
    fun timeOf(second: Int): Long = TRACK_START_WALL_CLOCK_MS + second * 1000L

    /**
     * One fix, [northMetres] up the road, with the phone's clock moved to it.
     *
     * @param accuracyMetres over 25 for a fix too poor to use, as a phone indoors gives.
     */
    fun fix(northMetres: Double, second: Int, accuracyMetres: Float = GOOD_ACCURACY_METRES) {
        world.nowMs = timeOf(second)
        val fix = fixAt(northMetres = northMetres, second = second, accuracyMetres = accuracyMetres)
        controller.onFix(fix.asFix())
        scope.runCurrent()
    }

    /** MilO's process is killed and a new one is built on the same storage. */
    fun newProcess() {
        val process = world.newProcess(scope.backgroundScope)
        controller = process.first
        service = process.second
    }

    /** The truck connects and a trip starts. */
    fun connect() {
        world.truck.connected = true
        val process = scope.process(world)
        controller = process.first
        service = process.second
        service.comesUpAtOnce = true
        controller.onTrigger(TripTrigger.TRUCK_LINK_CONNECTED, "ACL connect")
        scope.runCurrent()
    }

    /** Connects, drives [DRIVEN_METRES] and stands until the service's timer runs out. */
    fun driveAndPark() {
        connect()
        scope.drive(world, controller, fixCount = 21, metresPerFix = 100.0)
        // Standing, with the fixes wandering a metre or two as they do.
        for (second in STOPPED_AT_SECOND + 5..STOPPED_AT_SECOND + 595 step 5) {
            fix(northMetres = DRIVEN_METRES + second % 3, second = second)
        }
        timerRunsOut()
    }

    /** The service's timer fires at the time the controller last asked for. */
    fun timerRunsOut() {
        val due = checkNotNull(service.checkAtMs) { "the controller asked for no timer" }
        world.nowMs = maxOf(world.nowMs, due)
        controller.onCheckDue(due)
        scope.runCurrent()
    }

    /** The watch's fixes, one every 30 seconds: standing, then two that show the truck leave. */
    fun standThenDriveOff() {
        for (second in STOPPED_AT_SECOND + 630 until DRIVES_OFF_AT_SECOND step 30) {
            fix(northMetres = DRIVEN_METRES + second % 3, second = second)
        }
        fix(northMetres = DRIVEN_METRES + 150, second = DRIVES_OFF_AT_SECOND)
        fix(northMetres = DRIVEN_METRES + 450, second = DRIVES_OFF_AT_SECOND + 30)
    }

    /** The service's minute timer fires, [seconds] after the phone's clock stood last. */
    fun minuteCheck(seconds: Int = 60) {
        world.nowMs += seconds * 1000L
        controller.onTrigger(TripTrigger.POLL, "minute check")
        scope.runCurrent()
    }
}

package com.shawnkowalchuk.milo.clockjump

import com.shawnkowalchuk.milo.core.trip.GPS_AFTER_VEHICLE_REPORT_MS
import com.shawnkowalchuk.milo.core.trip.PARKED_GPS_MS
import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.core.trip.WAITING_LIMIT_MS
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.settings.TripSound
import com.shawnkowalchuk.milo.platform.trip.FakeWorld
import com.shawnkowalchuk.milo.platform.trip.FixRate
import com.shawnkowalchuk.milo.platform.trip.ParkedTruckWatch
import com.shawnkowalchuk.milo.platform.trip.TripTrigger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The drive of [driveAndPark] last moves here, seconds into the scene. */
private const val STOPPED_AT = 105

/** And the parked rule closes it here, ten minutes on: MilO waits from this moment. */
private const val WAIT_BEGAN = 705

/** How far that drive goes. */
private const val DRIVEN_METRES = 2_000.0

private const val HOUR_SECONDS = 3_600

/**
 * The phone's date is set one day ahead by hand for a few seconds and then comes back (the
 * owner's habit, ADR-006), **while MilO waits beside the parked, connected truck, or is idle.**
 * Each test runs the real trip controller, rules, ledger and storage stand-ins on MilO's real
 * clock ([ClockJumpScene]), and asserts that the wait is exactly what it was.
 *
 * They began as the investigation's proofs of what the jump did before MilO had a clock of its
 * own (2026-10-07): they then asserted the wait stopped, the trip started, the hour of GPS
 * over. The scenes are the same; the outcomes are now the right ones. The emulator's runs 4 and
 * "a weekend" of that day are the first and third test here.
 *
 * The scene starts on Friday 2 October 2026 at 12:00 UTC.
 */
// See TripControllerTest for why runCurrent() needs the opt-in.
@OptIn(ExperimentalCoroutinesApi::class)
class ClockJumpWaitingTest {
    private val world = FakeWorld()

    /** The truck connects, is driven two kilometres and stands, connected: MilO waits. */
    private fun ClockJumpScene.driveAndPark() {
        truckConnects(second = 0)
        drive(fromSecond = 5, toSecond = STOPPED_AT)
        stand(DRIVEN_METRES, fromSecond = STOPPED_AT + 5, toSecond = WAIT_BEGAN)
    }

    @Test
    fun `waiting, a fix and a minute check inside the jump change nothing`() = runTest {
        val scene = ClockJumpScene(this, world)
        scene.driveAndPark()
        val stored = world.settings.current().parkedTruck
        assertEquals(scene.realTimeOf(WAIT_BEGAN), stored?.sinceMs)

        // Forty minutes into the wait, as on 2026-10-07 at 18:32.
        val jump = WAIT_BEGAN + 2_400
        scene.goTo(jump)
        scene.dateSetAhead()
        scene.fix(DRIVEN_METRES, jump + 3, speedMetresPerSecond = 0f)
        scene.minuteCheck(jump + 6)
        scene.goTo(jump + 10)
        scene.dateSetBack()
        scene.minuteCheck(jump + 66)

        assertEquals(1, world.trips.rows.size)
        assertTrue(scene.service.watchingParked)
        assertEquals(0, scene.service.stops)
        assertEquals(stored, world.settings.current().parkedTruck)
        assertEquals(scene.realTimeOf(WAIT_BEGAN) + WAITING_LIMIT_MS, scene.service.checkAtMs)
        // No sound at the jump: the two of the drive that ended here are all there are.
        assertEquals(listOf(TripSound.CONNECT, TripSound.DRIVING_OFF), scene.service.sounds)

        // And the truck driving off afterwards starts its trip, at the true time, with its
        // trip-start sound and no connect sound.
        scene.fix(DRIVEN_METRES + 150, jump + 90, DRIVING_SPEED)
        val next = world.trips.rows.last()
        assertEquals(2, world.trips.rows.size)
        assertEquals(TripStatus.OPEN, next.status)
        assertEquals(scene.realTimeOf(jump + 90), next.startedAtMs)
        assertEquals(
            listOf(TripSound.CONNECT, TripSound.DRIVING_OFF, TripSound.DRIVING_OFF),
            scene.service.sounds,
        )
    }

    @Test
    fun `in the first hour of a wait a fix inside the jump leaves GPS on, and the drive counts`() =
        runTest {
            // The phone reports driving to MilO, so GPS is read for the first hour only. On the
            // emulator, before the fix, one reading inside the jump took the hour for over,
            // switched GPS off, and the drive that followed was recorded by nothing.
            world.motionSensorWatching = true
            val scene = ClockJumpScene(this, world)
            scene.driveAndPark()
            val hourEndsMs = scene.realTimeOf(WAIT_BEGAN) + PARKED_GPS_MS
            assertEquals(hourEndsMs, scene.service.gps.untilMs)
            assertEquals(FixRate.WATCHING_PARKED, scene.service.parkedGpsRate)

            val jump = WAIT_BEGAN + 2_400
            scene.goTo(jump)
            scene.dateSetAhead()
            assertTrue(scene.fix(DRIVEN_METRES, jump + 3, speedMetresPerSecond = 0f))
            scene.minuteCheck(jump + 6)
            scene.goTo(jump + 10)
            scene.dateSetBack()

            // GPS is read as before, and goes off when the hour really ends.
            assertEquals(FixRate.WATCHING_PARKED, scene.service.parkedGpsRate)
            assertEquals(hourEndsMs, scene.service.gps.untilMs)
            assertEquals(hourEndsMs, scene.service.gpsTimer.setFor)

            // Five minutes later the truck drives off: the fix comes, and the trip starts.
            assertTrue(scene.fix(DRIVEN_METRES + 150, jump + 300, DRIVING_SPEED))
            val next = world.trips.rows.last()
            assertEquals(2, world.trips.rows.size)
            assertEquals(TripStatus.OPEN, next.status)
            assertEquals(scene.realTimeOf(jump + 300), next.startedAtMs)
        }

    @Test
    fun `the hour of GPS still ends when it really ends, jump or no jump`() = runTest {
        world.motionSensorWatching = true
        val scene = ClockJumpScene(this, world)
        scene.driveAndPark()
        scene.goTo(WAIT_BEGAN + 2_400)
        scene.dateSetAhead()
        scene.fix(DRIVEN_METRES, WAIT_BEGAN + 2_403, speedMetresPerSecond = 0f)
        scene.goTo(WAIT_BEGAN + 2_410)
        scene.dateSetBack()

        scene.goTo(WAIT_BEGAN + HOUR_SECONDS - 1)
        assertEquals(FixRate.WATCHING_PARKED, scene.service.parkedGpsRate)
        scene.goTo(WAIT_BEGAN + HOUR_SECONDS + 1)

        assertNull(scene.service.parkedGpsRate)
        assertTrue(scene.service.watchingParked)
    }

    @Test
    fun `waiting for more than two days, a minute check inside the jump does not end the watch`() =
        runTest {
            val scene = ClockJumpScene(this, world)
            scene.driveAndPark()
            val stored = world.settings.current().parkedTruck
            val announcedBefore = scene.service.tripStartsAnnounced

            // Friday noon to Sunday afternoon: 50 hours parked at home, in range, connected.
            // Before the fix the 72-hour limit was "reached" here, 22 hours early, and MilO
            // stopped watching for good.
            val jump = WAIT_BEGAN + 50 * HOUR_SECONDS
            scene.goTo(jump)
            scene.dateSetAhead()
            scene.minuteCheck(jump + 5)
            scene.goTo(jump + 10)
            scene.dateSetBack()
            scene.minuteCheck(jump + 65)

            assertEquals(0, scene.service.stops)
            assertTrue(scene.service.watchingParked)
            assertEquals(stored, world.settings.current().parkedTruck)
            assertEquals(ParkedTruckWatch.WAITING_TO_MOVE, scene.controller.activity.value.parked)
            assertTrue(world.logged(EventCategory.TRIP).none { it.contains("stopped watching it") })
            assertEquals(1, world.trips.rows.size)
            assertEquals(announcedBefore, scene.service.tripStartsAnnounced)

            // On Monday morning the truck drives off, connected as ever, and its trip starts.
            val monday = WAIT_BEGAN + 66 * HOUR_SECONDS
            scene.fix(DRIVEN_METRES + 150, monday, DRIVING_SPEED)
            assertEquals(2, world.trips.rows.size)
            assertEquals(scene.realTimeOf(monday), world.trips.rows.last().startedAtMs)
        }

    @Test
    fun `the three days of watching still end when they really end`() = runTest {
        val scene = ClockJumpScene(this, world)
        scene.driveAndPark()
        val jump = WAIT_BEGAN + 50 * HOUR_SECONDS
        scene.goTo(jump)
        scene.dateSetAhead()
        scene.minuteCheck(jump + 5)
        scene.goTo(jump + 10)
        scene.dateSetBack()

        scene.goTo(WAIT_BEGAN + 72 * HOUR_SECONDS - 60)
        assertTrue(scene.service.watchingParked)
        scene.goTo(WAIT_BEGAN + 72 * HOUR_SECONDS + 60)

        assertEquals(1, scene.service.stops)
        assertEquals(ParkedTruckWatch.NO_LONGER_WATCHED, scene.controller.activity.value.parked)
    }

    @Test
    fun `a process started inside the jump finds a wait of under two days and carries it on`() =
        runTest {
            val scene = ClockJumpScene(this, world)
            scene.driveAndPark()
            val stored = world.settings.current().parkedTruck

            val jump = WAIT_BEGAN + 5 * HOUR_SECONDS
            scene.goTo(jump)
            scene.dateSetAhead()
            scene.processStarts()
            scene.goTo(jump + 10)
            scene.dateSetBack()

            assertEquals(1, world.trips.rows.size)
            assertTrue(scene.service.watchingParked)
            assertEquals(stored, world.settings.current().parkedTruck)
        }

    @Test
    fun `a process started inside the jump carries a wait of over two days on as well`() = runTest {
        val scene = ClockJumpScene(this, world)
        scene.driveAndPark()
        val stored = world.settings.current().parkedTruck

        // Before the fix: "past the three days", decided on a clock a day ahead, and a
        // trip started with the sound beside a truck that had not moved since Friday.
        val jump = WAIT_BEGAN + 50 * HOUR_SECONDS
        scene.goTo(jump)
        scene.dateSetAhead()
        scene.processStarts()
        scene.goTo(jump + 10)
        scene.dateSetBack()

        assertEquals(1, world.trips.rows.size)
        assertEquals(0, scene.service.tripStartsAnnounced)
        assertTrue(scene.service.watchingParked)
        assertEquals(stored, world.settings.current().parkedTruck)
    }

    @Test
    fun `a report of getting into a vehicle inside the jump turns GPS on for ten minutes`() =
        runTest {
            world.motionSensorWatching = true
            val scene = ClockJumpScene(this, world)
            scene.driveAndPark()

            // Three hours into the wait GPS is off. The report is dated with MilO's clock
            // (`DrivingReceiver`), which is what a test hands the controller here.
            val jump = WAIT_BEGAN + 3 * HOUR_SECONDS
            scene.goTo(jump)
            assertNull(scene.service.parkedGpsRate)
            scene.dateSetAhead()
            scene.controller.onVehicleEntered(scene.nowMs)
            runCurrent()

            val fastUntilMs = scene.realTimeOf(jump) + GPS_AFTER_VEHICLE_REPORT_MS
            assertEquals(fastUntilMs, scene.service.gps.fastUntilMs)
            assertEquals(FixRate.WATCHING_CLOSELY, scene.service.parkedGpsRate)
            scene.goTo(jump + 10)
            scene.dateSetBack()

            // Ten minutes on GPS is off again, and no trip was started by the report.
            scene.goTo(jump + 601)
            assertNull(scene.service.parkedGpsRate)
            assertEquals(1, world.trips.rows.size)
        }

    @Test
    fun `idle with no truck connected, a process started inside the jump does nothing`() = runTest {
        val scene = ClockJumpScene(this, world)
        scene.dateSetAhead()
        scene.processStarts()
        scene.goTo(10)
        scene.dateSetBack()
        scene.trigger(TripTrigger.RECONCILE, "app opened")

        assertTrue(world.trips.rows.isEmpty())
        assertFalse(scene.service.recording)
        assertFalse(scene.service.watchingParked)
        assertNull(world.settings.current().parkedTruck)
    }
}

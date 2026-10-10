package com.shawnkowalchuk.milo.clockjump

import com.shawnkowalchuk.milo.core.schedule.TripCategory
import com.shawnkowalchuk.milo.core.trip.PARKED_GPS_MS
import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.core.trip.WAITING_LIMIT_MS
import com.shawnkowalchuk.milo.core.util.DistanceUnit
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.settings.TripSound
import com.shawnkowalchuk.milo.data.trip.Trip
import com.shawnkowalchuk.milo.feature.tripedit.FormCheck
import com.shawnkowalchuk.milo.feature.tripedit.FormProblem
import com.shawnkowalchuk.milo.feature.tripedit.formFor
import com.shawnkowalchuk.milo.feature.tripedit.formProblems
import com.shawnkowalchuk.milo.platform.trip.FakeWorld
import com.shawnkowalchuk.milo.platform.trip.PARKED_LIMIT_MS
import com.shawnkowalchuk.milo.platform.trip.TripTrigger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A drive of two kilometres last moves here, seconds into the scene. */
private const val STOPPED_AT = 105

/** How far that drive goes. */
private const val DRIVEN_METRES = 2_000.0

private const val HOUR_SECONDS = 3_600

/**
 * The phone's date is set one day ahead by hand for a few seconds and then comes back (the
 * owner's habit, ADR-006), **while a trip is being recorded, or in the seconds in which one
 * starts.** The real trip controller, rules, ledger and storage stand-ins run on MilO's real
 * clock ([ClockJumpScene]), and each test asserts one trip, with its true times.
 *
 * They began as the investigation's proofs of what the jump did before MilO had a clock of its
 * own (2026-10-07): the drive cut in two at 72 km/h, the rest of a hand-started drive lost, a
 * trip dated tomorrow that ended before it started. The emulator's runs 2, 3 and 6 of that day
 * are among them, each named where it stands. The scenes are the same; the outcomes are now
 * the right ones.
 *
 * The scene starts on Friday 2 October 2026 at 12:00 UTC, a work day inside the default hours.
 */
// See TripControllerTest for why runCurrent() needs the opt-in.
@OptIn(ExperimentalCoroutinesApi::class)
class ClockJumpDrivingTest {
    private val world = FakeWorld()

    /** What the edit screen has to say about [trip] as it is stored: nothing, if it is sound. */
    private fun ClockJumpScene.editProblems(trip: Trip): List<FormProblem> = formProblems(
        form = formFor(trip, world.zone, DistanceUnit.KILOMETRES),
        stored = trip,
        check = FormCheck(nowMs = nowMs, recordingSinceMs = null),
        zone = world.zone,
    )

    // ---- During a drive --------------------------------------------------------------------------

    @Test
    fun `control - the same drive with no jump is one trip, and Business`() = runTest {
        val scene = ClockJumpScene(this, world)
        scene.truckConnects(second = 0)
        scene.drive(fromSecond = 5, toSecond = 300)
        scene.fix(6_000.0, second = 305, speedMetresPerSecond = DRIVING_SPEED)
        val end = scene.drive(fromSecond = 310, toSecond = 600, fromNorthMetres = 6_100.0)
        scene.stand(end, fromSecond = 605, toSecond = 1_205)

        val trip = world.trips.rows.single()
        assertEquals(TripStatus.FINISHED, trip.status)
        assertEquals(scene.realTimeOf(0), trip.startedAtMs)
        assertEquals(scene.realTimeOf(600), trip.endedAtMs)
        assertEquals(end, trip.distanceMetres, 1.0)
        assertEquals(TripCategory.BUSINESS, trip.category)
        assertEquals(scene.realTimeOf(1_200), world.settings.current().parkedTruck?.sinceMs)
        // The connect sound at the start, and the trip-start sound when the truck drove off.
        assertEquals(listOf(TripSound.CONNECT, TripSound.DRIVING_OFF), scene.service.sounds)
    }

    @Test
    fun `driving through the jump is the same one trip, to the metre and the second`() = runTest {
        // Before the fix, and on the emulator (run 2, with the truck connected): the first fix
        // of the jump closed the trip at 72 km/h, "parked for 24 hours", and the rest of the
        // drive was a second trip, dated tomorrow if a second fix fell into the jump.
        val scene = ClockJumpScene(this, world)
        scene.truckConnects(second = 0)
        val before = scene.drive(fromSecond = 5, toSecond = 300)

        scene.dateSetAhead()
        scene.fix(before + 100, second = 305, speedMetresPerSecond = DRIVING_SPEED)
        scene.fix(before + 200, second = 310, speedMetresPerSecond = DRIVING_SPEED)
        assertTrue(scene.service.recording)
        scene.goTo(312)
        scene.dateSetBack()
        val end = scene.drive(fromSecond = 315, toSecond = 600, fromNorthMetres = before + 300)
        scene.stand(end, fromSecond = 605, toSecond = 1_205)

        // Exactly what the control gives.
        val trip = world.trips.rows.single()
        assertEquals(TripStatus.FINISHED, trip.status)
        assertEquals(scene.realTimeOf(0), trip.startedAtMs)
        assertEquals(scene.realTimeOf(600), trip.endedAtMs)
        assertEquals(end, trip.distanceMetres, 1.0)
        assertEquals(TripCategory.BUSINESS, trip.category)
        assertEquals(scene.realTimeOf(1_200), world.settings.current().parkedTruck?.sinceMs)
        assertEquals(emptyList<FormProblem>(), scene.editProblems(trip))
        // Every stored point carries the time it was really taken at.
        assertTrue(world.points.rows.all { it.wallClockMs <= scene.realTimeOf(1_205) })
        // And each sound was played once. Cut in two, the drive's second half would be a trip
        // that a parked truck's moving started, with a trip-start sound of its own.
        assertEquals(listOf(TripSound.CONNECT, TripSound.DRIVING_OFF), scene.service.sounds)
    }

    @Test
    fun `a trip started by hand with no truck is recorded through the jump, to its End`() =
        runTest {
            // Emulator run 2: another vehicle, Start pressed, the truck not connected. Before
            // the fix the jump ended the trip, the service stopped, and the rest was lost.
            val scene = ClockJumpScene(this, world)
            scene.trigger(TripTrigger.MANUAL_START, "Start button")
            val before = scene.drive(fromSecond = 5, toSecond = 300)

            scene.dateSetAhead()
            scene.fix(before + 100, second = 305, speedMetresPerSecond = DRIVING_SPEED)
            scene.goTo(312)
            scene.dateSetBack()
            assertEquals(0, scene.service.stops)
            assertTrue(scene.service.recording)

            val end = scene.drive(fromSecond = 315, toSecond = 600, fromNorthMetres = before + 200)
            scene.goTo(610)
            scene.trigger(TripTrigger.MANUAL_END, "End button")

            val trip = world.trips.rows.single()
            assertEquals(TripStatus.FINISHED, trip.status)
            assertEquals(scene.realTimeOf(0), trip.startedAtMs)
            assertEquals(end, trip.distanceMetres, 1.0)
            assertTrue(checkNotNull(trip.endedAtMs) in scene.realTimeOf(600)..scene.realTimeOf(610))
        }

    // ---- Just arrived: the trip is open and the parked rule is counting down ---------------------

    @Test
    fun `counting down after arriving, the jump does not close the trip early`() = runTest {
        // Emulator run 3. Before the fix the first fix of the jump closed the trip seven
        // minutes early, and the wait that followed was dated a day ahead: its hour of GPS
        // lasted 25 hours, its three days four.
        world.motionSensorWatching = true
        val scene = ClockJumpScene(this, world)
        scene.truckConnects(second = 0)
        scene.drive(fromSecond = 5, toSecond = STOPPED_AT)
        // Three minutes of standing, of the ten the parked rule waits for.
        scene.stand(DRIVEN_METRES, fromSecond = STOPPED_AT + 5, toSecond = 280)

        scene.dateSetAhead()
        scene.fix(DRIVEN_METRES, second = 285, speedMetresPerSecond = 0f)
        scene.minuteCheck(288)
        assertEquals(TripStatus.OPEN, world.trips.rows.single().status)
        assertTrue(scene.service.recording)
        scene.goTo(292)
        scene.dateSetBack()

        // The ten minutes run out when they really have: at second 705.
        scene.stand(DRIVEN_METRES, fromSecond = 295, toSecond = 700)
        assertEquals(TripStatus.OPEN, world.trips.rows.single().status)
        scene.stand(DRIVEN_METRES, fromSecond = 705, toSecond = 710)

        val trip = world.trips.rows.single()
        assertEquals(TripStatus.FINISHED, trip.status)
        assertEquals(scene.realTimeOf(STOPPED_AT), trip.endedAtMs)
        assertEquals(DRIVEN_METRES, trip.distanceMetres, 1.0)
        assertEquals(TripCategory.BUSINESS, trip.category)
        val line = world.logged(EventCategory.TRIP).first { it.startsWith("Trip 1: finished") }
        assertTrue(line, line.contains("then stood still for 10 min"))

        // The wait that follows is dated when it began, and its two limits are the real ones.
        val parked = checkNotNull(world.settings.current().parkedTruck)
        assertEquals(scene.realTimeOf(705), parked.sinceMs)
        assertEquals(parked.sinceMs + PARKED_GPS_MS, scene.service.gps.untilMs)
        assertEquals(parked.sinceMs + WAITING_LIMIT_MS, scene.service.checkAtMs)

        // A restart of the process finds the same wait.
        scene.processStarts()
        assertTrue(scene.service.watchingParked)
        assertEquals(parked, world.settings.current().parkedTruck)
        assertEquals(parked.sinceMs + PARKED_GPS_MS, scene.service.gps.untilMs)
    }

    // ---- A trip that starts inside the jump ------------------------------------------------------

    @Test
    fun `a truck that connects inside the jump starts a trip dated now, and it is not cut`() =
        runTest {
            // Before the fix: dated tomorrow (a Saturday, so Personal), cut by the parked rule
            // ten minutes into the drive, ending before it started, and refused by the edit
            // screen until its times were put right by hand.
            val scene = ClockJumpScene(this, world)
            scene.dateSetAhead()
            scene.truckConnects(second = 3)
            assertEquals(scene.realTimeOf(3), world.trips.rows.single().startedAtMs)
            scene.goTo(10)
            scene.dateSetBack()

            // Driven without a stop for more than eleven minutes.
            val end = scene.drive(fromSecond = 15, toSecond = 700)
            assertEquals(TripStatus.OPEN, world.trips.rows.single().status)
            assertTrue(scene.service.recording)
            assertEquals(scene.realTimeOf(700) + PARKED_LIMIT_MS, scene.service.checkAtMs)

            scene.stand(end, fromSecond = 705, toSecond = 1_305)

            val trip = world.trips.rows.single()
            assertEquals(TripStatus.FINISHED, trip.status)
            assertEquals(scene.realTimeOf(3), trip.startedAtMs)
            assertEquals(scene.realTimeOf(700), trip.endedAtMs)
            assertEquals(TripCategory.BUSINESS, trip.category)
            assertEquals(emptyList<FormProblem>(), scene.editProblems(trip))
            assertEquals(listOf(TripSound.CONNECT, TripSound.DRIVING_OFF), scene.service.sounds)
        }

    @Test
    fun `a trip whose service comes up as the clock comes back is timed from its real start`() =
        runTest {
            // The connect arrives in the last moment of the jump. Its time rides in the
            // service's start intent; the service comes up a moment later, when the clock is
            // back, and only then is the timer set. Before the fix that timer was a day and
            // ten minutes away, nothing closed the trip, and two drives two hours apart became
            // one record that started tomorrow and ended today.
            val scene = ClockJumpScene(this, world)
            scene.service.comesUpAtOnce = false
            scene.dateSetAhead()
            scene.truckConnects(second = 3)
            scene.goTo(4)
            scene.dateSetBack()
            scene.service.comeUp()
            runCurrent()

            assertEquals(scene.realTimeOf(3), world.trips.rows.single().startedAtMs)
            assertEquals(PARKED_LIMIT_MS - 1_000, scene.timerRunsOutInMs)

            // Two kilometres, then parked with the truck still connected.
            val parkedAt = scene.drive(fromSecond = 15, toSecond = 115)
            scene.stand(parkedAt, fromSecond = 120, toSecond = 720)
            val first = world.trips.rows.single()
            assertEquals(TripStatus.FINISHED, first.status)
            assertEquals(scene.realTimeOf(115), first.endedAtMs)
            assertEquals(DRIVEN_METRES, first.distanceMetres, 1.0)
            assertTrue(scene.service.watchingParked)

            // Two hours later the next drive is a trip of its own.
            val driveAgain = 120 + 2 * HOUR_SECONDS
            scene.drive(driveAgain, driveAgain + 100, fromNorthMetres = parkedAt + 100)
            assertEquals(2, world.trips.rows.size)
            val second = world.trips.rows.last()
            assertEquals(TripStatus.OPEN, second.status)
            assertEquals(scene.realTimeOf(driveAgain), second.startedAtMs)
        }

    @Test
    fun `Start pressed while the date is ahead gives a trip that starts when it was pressed`() =
        runTest {
            // Emulator run 6. Before the fix the trip began a day after it ended.
            val scene = ClockJumpScene(this, world)
            scene.dateSetAhead()
            scene.trigger(TripTrigger.MANUAL_START, "Start button")
            scene.goTo(4)
            scene.dateSetBack()

            val end = scene.drive(fromSecond = 5, toSecond = 105)
            scene.goTo(110)
            scene.trigger(TripTrigger.MANUAL_END, "End button")

            val trip = world.trips.rows.single()
            val ended = checkNotNull(trip.endedAtMs)
            assertEquals(scene.realTimeOf(0), trip.startedAtMs)
            assertTrue("the trip ends after it starts", ended > trip.startedAtMs)
            assertTrue(ended <= scene.realTimeOf(110))
            assertEquals(end, trip.distanceMetres, 1.0)
            assertEquals(TripCategory.BUSINESS, trip.category)
        }

    @Test
    fun `a trip started while the date is ahead is not cut one parked limit after its start`() =
        runTest {
            // Emulator run 6 again: Start pressed in the jump, then driven for more than the
            // ten minutes without a stop. Before the fix the timer set inside the jump ran out
            // ten minutes after Start, at 72 km/h, and with no truck the rest was lost.
            val scene = ClockJumpScene(this, world)
            scene.dateSetAhead()
            scene.trigger(TripTrigger.MANUAL_START, "Start button")
            assertEquals(scene.realTimeOf(0) + PARKED_LIMIT_MS, scene.service.checkAtMs)
            scene.goTo(4)
            scene.dateSetBack()

            val end = scene.drive(fromSecond = 5, toSecond = 720)

            // Every movement has moved the limit on, as it does on any day.
            assertEquals(TripStatus.OPEN, world.trips.rows.single().status)
            assertTrue(scene.service.recording)
            assertEquals(0, scene.service.stops)
            assertEquals(scene.realTimeOf(720) + PARKED_LIMIT_MS, scene.service.checkAtMs)
            assertTrue(world.logged(EventCategory.TRIP).none { it.contains("NO_MOVEMENT") })

            // It ends where it really stops: no truck, so nothing waits.
            scene.stand(end, fromSecond = 725, toSecond = 1_325)
            val trip = world.trips.rows.single()
            assertEquals(TripStatus.FINISHED, trip.status)
            assertEquals(scene.realTimeOf(720), trip.endedAtMs)
            assertEquals(end, trip.distanceMetres, 1.0)
            assertFalse(scene.service.watchingParked)
            assertNull(world.settings.current().parkedTruck)
        }
}

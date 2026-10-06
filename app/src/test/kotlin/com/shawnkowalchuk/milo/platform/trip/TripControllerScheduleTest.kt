package com.shawnkowalchuk.milo.platform.trip

import com.shawnkowalchuk.milo.core.schedule.DEFAULT_WORK_SCHEDULE
import com.shawnkowalchuk.milo.core.schedule.DayHours
import com.shawnkowalchuk.milo.core.schedule.TripCategory
import com.shawnkowalchuk.milo.core.schedule.WorkSchedule
import com.shawnkowalchuk.milo.core.trip.RESTART_GAP_LIMIT_MS
import com.shawnkowalchuk.milo.core.trip.TripStartCause
import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.core.trip.driveNorth
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.trip.Trip
import java.time.DayOfWeek
import java.time.LocalTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The work schedule beside the trip controller: a trip is sorted into Business or Personal at
 * the moment it is closed, and at no other. The rule itself is tested in `core/schedule`; these
 * tests are about where it is applied. What "Ignore them" does, and what the schedule must never
 * do, is in `TripControllerIgnoreTest`.
 *
 * The stand-in clock starts on Saturday 3 October 2026 at 12:00, in a phone set to UTC. The
 * default schedule does not track Saturdays, so a test that wants a Business trip switches
 * Saturday on.
 */
// See TripControllerTest for why runCurrent() needs the opt-in.
@OptIn(ExperimentalCoroutinesApi::class)
class TripControllerScheduleTest {
    private val world = FakeWorld()

    private fun saturdays(end: LocalTime = LocalTime.of(16, 30)): WorkSchedule =
        DEFAULT_WORK_SCHEDULE.with(
            DayOfWeek.SATURDAY,
            DayHours(tracked = true, start = LocalTime.of(8, 0), end = end),
        )

    /** Presses End and returns the trip as it is stored afterwards. */
    private fun TestScope.end(controller: TripController): Trip {
        controller.onTrigger(TripTrigger.MANUAL_END, "End button")
        runCurrent()
        return world.trips.rows.last()
    }

    /** A trip started with the button, driven [fixCount] fixes of 100 m each, and ended. */
    private fun TestScope.manualTrip(fixCount: Int = 11): Trip {
        val (controller, _) = recordingManualTrip(world)
        drive(world, controller, fixCount, metresPerFix = 100.0)
        return end(controller)
    }

    private fun lastTripLine(): String = world.logged(EventCategory.TRIP).last()

    // ---- Sorted when it is closed -----------------------------------------------------------------

    @Test
    fun `a trip that started inside the schedule is stored as Business when it ends`() = runTest {
        world.settings.setSchedule(saturdays())

        val trip = manualTrip()

        assertEquals(TripStatus.FINISHED, trip.status)
        assertEquals(TripCategory.BUSINESS, trip.category)
        assertFalse(trip.ranPastSchedule)
        assertFalse(trip.categorySetByHand)
        assertFalse(trip.ignoredOutsideSchedule)
        assertEquals(
            "Trip 1: finished, 1000 m; ended by MANUAL; 11 fixes stored, 11 used, " +
                "0 too inaccurate, 0 jumps; saved as Business: it started on a Sat at 12:00:00; " +
                "that day's hours are 08:00 to 16:30 (UTC)",
            lastTripLine(),
        )
    }

    @Test
    fun `a trip that started outside the schedule is kept, as Personal`() = runTest {
        val trip = manualTrip()

        assertEquals(TripStatus.FINISHED, trip.status)
        assertEquals(TripCategory.PERSONAL, trip.category)
        assertEquals(1_000.0, trip.distanceMetres, 1.0)
        assertFalse(trip.ignoredOutsideSchedule)
        assertTrue(
            lastTripLine().endsWith(
                "; saved as Personal: it started on a Sat at 12:00:00; " +
                    "Sat is not a tracked day (UTC)",
            ),
        )
    }

    @Test
    fun `a trip is not sorted while it is open`() = runTest {
        world.settings.setSchedule(saturdays())
        val (controller, _) = recordingManualTrip(world)
        drive(world, controller, fixCount = 11, metresPerFix = 100.0)

        val open = world.openTrips.single()

        assertNull(open.category)
        assertFalse(open.ranPastSchedule)
    }

    @Test
    fun `a trip the truck started is sorted by the same rule as one started by hand`() = runTest {
        world.settings.setSchedule(saturdays())
        world.truck.connected = true
        val (controller, service) = process(world)
        service.comesUpAtOnce = true
        controller.onTrigger(TripTrigger.TRUCK_LINK_CONNECTED, "ACL connect")
        runCurrent()
        drive(world, controller, fixCount = 11, metresPerFix = 100.0)

        val byTruck = end(controller)

        assertEquals(TripStartCause.TRUCK, byTruck.startedBy)
        assertEquals(TripCategory.BUSINESS, byTruck.category)
        // And outside the hours both are Personal: a Start button earns no exception.
        world.settings.setSchedule(DEFAULT_WORK_SCHEDULE)
        world.truck.connected = false
        val byHand = manualTrip()
        assertEquals(TripStartCause.MANUAL, byHand.startedBy)
        assertEquals(TripCategory.PERSONAL, byHand.category)
    }

    @Test
    fun `the schedule as it is when the trip ends is the one that sorts it`() = runTest {
        val (controller, _) = recordingManualTrip(world)
        drive(world, controller, fixCount = 11, metresPerFix = 100.0)

        // Changed on the Settings screen while the trip is being recorded.
        world.settings.setSchedule(saturdays())

        assertEquals(TripCategory.BUSINESS, end(controller).category)
    }

    @Test
    fun `a trip closed by the restart rules is sorted as it closes`() = runTest {
        world.settings.setSchedule(saturdays())
        val (killed, _) = recordingManualTrip(world)
        drive(world, killed, fixCount = 11, metresPerFix = 100.0)
        world.nowMs += RESTART_GAP_LIMIT_MS + 60_000

        val (controller, _) = world.newProcess(backgroundScope)
        controller.onTrigger(TripTrigger.RECONCILE, "process start")
        runCurrent()

        val trip = world.trips.rows.single()
        assertEquals(TripStatus.FINISHED, trip.status)
        assertEquals(TripCategory.BUSINESS, trip.category)
        assertTrue(lastTripLine().contains("ended by STALE_AT_RESTART"))
    }

    // ---- Ran past schedule ------------------------------------------------------------------------

    @Test
    fun `a Business trip still running at the end time is recorded to its end and flagged`() =
        runTest {
            // Saturday's hours end at 12:01. The trip starts at 12:00:00 and its fourteenth
            // fix is at 12:01:05.
            world.settings.setSchedule(saturdays(end = LocalTime.of(12, 1)))

            val trip = manualTrip(fixCount = 14)

            assertEquals(TripStatus.FINISHED, trip.status)
            assertEquals(TripCategory.BUSINESS, trip.category)
            assertTrue(trip.ranPastSchedule)
            // Nothing was cut at the end time: every fix is stored and counted.
            val fixes = driveNorth(fixCount = 14, metresPerFix = 100.0)
            assertEquals(14, world.points.rows.size)
            assertEquals(fixes.last().wallClockMs, trip.endedAtMs)
            assertEquals(1_300.0, trip.distanceMetres, 1.0)
            assertTrue(lastTripLine().contains("; saved as Business, ran past schedule: "))
        }

    @Test
    fun `a Business trip that ends before the end time is not flagged`() = runTest {
        world.settings.setSchedule(saturdays(end = LocalTime.of(12, 1)))

        // Eleven fixes: the last one is at 12:00:50.
        assertFalse(manualTrip(fixCount = 11).ranPastSchedule)
    }
}

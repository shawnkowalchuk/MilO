package com.shawnkowalchuk.milo.platform.trip

import com.shawnkowalchuk.milo.core.schedule.DEFAULT_WORK_SCHEDULE
import com.shawnkowalchuk.milo.core.schedule.TripCategory
import com.shawnkowalchuk.milo.core.trip.TripStartCause
import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.core.trip.driveNorth
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.trip.Trip
import java.time.DayOfWeek
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
 * "Ignore them", and what the work schedule must never do: decide whether a trip starts or is
 * recorded. A trip is always recorded to its end; only then is it put away, and with "Ignore
 * them" chosen a trip that turns out Personal is put away as discarded, whole.
 *
 * The stand-in clock starts on Saturday 3 October 2026 at 12:00, in a phone set to UTC, and the
 * default schedule does not track Saturdays: a trip here is Personal unless a test says so.
 */
// See TripControllerTest for why runCurrent() needs the opt-in.
@OptIn(ExperimentalCoroutinesApi::class)
class TripControllerIgnoreTest {
    private val world = FakeWorld()

    /** Presses End and returns the trip as it is stored afterwards. */
    private fun TestScope.end(controller: TripController, inWorld: FakeWorld = world): Trip {
        controller.onTrigger(TripTrigger.MANUAL_END, "End button")
        runCurrent()
        return inWorld.trips.rows.last()
    }

    /** A trip started with the button, driven [fixCount] fixes of 100 m each, and ended. */
    private fun TestScope.manualTrip(fixCount: Int = 11): Trip {
        val (controller, _) = recordingManualTrip(world)
        drive(world, controller, fixCount, metresPerFix = 100.0)
        return end(controller)
    }

    private fun lastTripLine(): String = world.logged(EventCategory.TRIP).last()

    // ---- Ignore them ------------------------------------------------------------------------------

    @Test
    fun `with ignore chosen a Personal trip is recorded in full and stored as discarded`() =
        runTest {
            world.settings.setIgnoreTripsOutsideSchedule(true)

            val trip = manualTrip()

            assertEquals(TripStatus.DISCARDED, trip.status)
            assertTrue(trip.ignoredOutsideSchedule)
            assertEquals(TripCategory.PERSONAL, trip.category)
            // Everything a kept trip has, so that it can be counted after all.
            val fixes = driveNorth(fixCount = 11, metresPerFix = 100.0)
            assertEquals(1_000.0, trip.distanceMetres, 1.0)
            assertEquals(fixes.last().wallClockMs, trip.endedAtMs)
            assertEquals(fixes.first().latitude, trip.startLatitude)
            assertEquals(fixes.last().latitude, trip.endLatitude)
            assertEquals(11, world.points.rows.count { it.tripId == trip.id })
            assertTrue(
                lastTripLine().startsWith(
                    "Trip 1: discarded: it started outside the work schedule, and trips outside " +
                        "it are set to be ignored (1000 m). It can be counted on the Trips " +
                        "screen; ended by MANUAL; ",
                ),
            )
            // Not "saved as": it is not in the list. What it would be is said all the same.
            assertTrue(
                lastTripLine().endsWith(
                    "; if it is counted, it is Personal: it started on a Sat at 12:00:00; " +
                        "Sat is not a tracked day (UTC)",
                ),
            )
        }

    @Test
    fun `with ignore chosen a Business trip is kept`() = runTest {
        world.settings.setSchedule(DEFAULT_WORK_SCHEDULE.withTracked(DayOfWeek.SATURDAY, true))
        world.settings.setIgnoreTripsOutsideSchedule(true)

        val trip = manualTrip()

        assertEquals(TripStatus.FINISHED, trip.status)
        assertEquals(TripCategory.BUSINESS, trip.category)
        assertFalse(trip.ignoredOutsideSchedule)
    }

    @Test
    fun `a trip that is too short is discarded for that reason, with ignore chosen or not`() =
        runTest {
            world.settings.setIgnoreTripsOutsideSchedule(true)

            val trip = manualTrip(fixCount = 3)

            assertEquals(TripStatus.DISCARDED, trip.status)
            // Not marked as ignored: the Trips screen says "too short", which is the reason.
            assertFalse(trip.ignoredOutsideSchedule)
            // Sorted all the same, for the day it is counted after all.
            assertEquals(TripCategory.PERSONAL, trip.category)
            assertTrue(lastTripLine().contains("discarded: 200 m is under the minimum of 300 m"))
            assertTrue(lastTripLine().contains("; if it is counted, it is Personal: "))
        }

    // ---- The schedule never decides whether a trip starts -----------------------------------------

    @Test
    fun `with no day tracked and ignore chosen a trip still starts and is recorded`() = runTest {
        val noDays =
            DayOfWeek.entries.fold(DEFAULT_WORK_SCHEDULE) { schedule, day ->
                schedule.withTracked(day, false)
            }
        world.settings.setSchedule(noDays)
        world.settings.setIgnoreTripsOutsideSchedule(true)
        world.truck.connected = true
        val (controller, service) = process(world)
        service.comesUpAtOnce = true

        // The truck connects, as on any day.
        controller.onTrigger(TripTrigger.TRUCK_LINK_CONNECTED, "ACL connect")
        runCurrent()
        drive(world, controller, fixCount = 11, metresPerFix = 100.0)

        val open = world.openTrips.single()
        assertEquals(TripStartCause.TRUCK, open.startedBy)
        assertTrue(service.recording)
        assertEquals(1, service.tripStartsAnnounced)
        assertEquals(11, world.points.rows.count { it.tripId == open.id })
        assertEquals(1_000.0, controller.activity.value.trip?.distanceMetres ?: 0.0, 1.0)
        // Only when it ends is it put away, and then with everything that was recorded.
        val ended = end(controller)
        assertEquals(TripStatus.DISCARDED, ended.status)
        assertTrue(ended.ignoredOutsideSchedule)
        assertEquals(1_000.0, ended.distanceMetres, 1.0)
    }

    // ---- Without settings -------------------------------------------------------------------------

    @Test
    fun `with settings that cannot be read a trip is closed as usual and left unsorted`() =
        runTest {
            val damaged = FakeWorld(UnreadableSettingsFile)
            val (controller, _) = recordingManualTrip(damaged)
            drive(damaged, controller, fixCount = 11, metresPerFix = 100.0)

            val trip = end(controller, inWorld = damaged)

            assertEquals(TripStatus.FINISHED, trip.status)
            assertEquals(1_000.0, trip.distanceMetres, 1.0)
            // Not sorted by hours that are not Shawn's. The catch-up sorts it later.
            assertNull(trip.category)
            assertFalse(trip.ignoredOutsideSchedule)
            assertTrue(
                damaged.logged(EventCategory.TRIP).last().endsWith(
                    "; not sorted into Business or Personal: the settings cannot be read. " +
                        "It is sorted at a later start of MilO",
                ),
            )
        }
}

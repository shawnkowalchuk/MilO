package com.shawnkowalchuk.milo.data.trip

import com.shawnkowalchuk.milo.core.schedule.DEFAULT_WORK_SCHEDULE
import com.shawnkowalchuk.milo.core.schedule.TripCategory
import com.shawnkowalchuk.milo.core.trip.TripStartCause
import com.shawnkowalchuk.milo.core.trip.TripStatus
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What an edit by hand makes of a stored trip: what counts as an edit and what is kept of the
 * recording. What "Restore recorded values" puts back is in `TripRestoreTest`. Times are Edmonton wall-clock times
 * on Monday 5 October 2026; the schedule is Monday to Friday, 08:00 to 16:30.
 */
class TripEditTest {
    private val edmonton = ZoneId.of("America/Edmonton")
    private val schedule = DEFAULT_WORK_SCHEDULE

    private fun local(dateTime: String): Long =
        LocalDateTime.parse(dateTime).atZone(edmonton).toInstant().toEpochMilli()

    /** A trip as MilO recorded it: exact to the second, both addresses looked up. */
    private val recorded =
        Trip(
            id = 12,
            startedAtMs = local("2026-10-05T08:14:27"),
            endedAtMs = local("2026-10-05T08:39:02"),
            status = TripStatus.FINISHED,
            startedBy = TripStartCause.TRUCK,
            truckSeen = true,
            distanceMetres = 12_344.7,
            startLatitude = 53.5461,
            startLongitude = -113.4938,
            endLatitude = 53.2594,
            endLongitude = -113.5492,
            startAddress = "12 Shop Rd, Edmonton",
            endAddress = "48 Main St, Leduc",
            addressAttempts = 1,
            addressLastAttemptAtMs = local("2026-10-05T08:41:00"),
            category = TripCategory.BUSINESS,
        )

    /** What "Restore recorded values" has to put back, whatever was typed in between. */
    private val asRecorded =
        RecordedValues(
            startedAtMs = local("2026-10-05T08:14:27"),
            endedAtMs = local("2026-10-05T08:39:02"),
            distanceMetres = 12_344.7,
        )

    private fun edit(trip: Trip, edit: TripEdit): Trip = editedTrip(trip, edit, schedule, edmonton)

    // ---- What counts as an edit -----------------------------------------------------------------

    @Test
    fun `an edit that asks for nothing leaves the row exactly as it is`() {
        assertEquals(recorded, edit(recorded, TripEdit()))
    }

    @Test
    fun `asking for the values a trip already has is not an edit`() {
        val same =
            TripEdit(
                startedAtMs = recorded.startedAtMs,
                endedAtMs = recorded.endedAtMs,
                distanceMetres = recorded.distanceMetres,
                startAddress = TypedAddress(recorded.startAddress),
                endAddress = TypedAddress(recorded.endAddress),
            )

        assertEquals(recorded, edit(recorded, same))
    }

    @Test
    fun `a changed time, distance or address each marks the trip as edited`() {
        val edits =
            listOf(
                TripEdit(startedAtMs = local("2026-10-05T08:10:00")),
                TripEdit(endedAtMs = local("2026-10-05T08:45:00")),
                TripEdit(distanceMetres = 13_000.0),
                TripEdit(startAddress = TypedAddress("14 Shop Rd, Edmonton")),
                TripEdit(endAddress = TypedAddress(null)),
            )

        for (one in edits) {
            val after = edit(recorded, one)

            assertTrue("$one", after.editedByHand)
            assertEquals("$one", ByHandMark.EDITED, after.byHandMark)
        }
    }

    @Test
    fun `Business or Personal alone is set by hand, and is not an edit`() {
        val after = edit(recorded, TripEdit(category = TripCategory.PERSONAL))

        assertEquals(
            recorded.copy(category = TripCategory.PERSONAL, categorySetByHand = true),
            after,
        )
        assertFalse(after.editedByHand)
        assertNull(after.byHandMark)
        assertNull("Nothing recorded is kept apart for it", after.recordedStartedAtMs)
    }

    @Test
    fun `an edit changes only what it names, and never the status or the positions`() {
        val after = edit(recorded, TripEdit(distanceMetres = 13_000.0))

        assertEquals(
            recorded.copy(
                distanceMetres = 13_000.0,
                editedByHand = true,
                recordedStartedAtMs = recorded.startedAtMs,
                recordedEndedAtMs = recorded.endedAtMs,
                recordedDistanceMetres = recorded.distanceMetres,
            ),
            after,
        )
    }

    // ---- What is kept of the recording ----------------------------------------------------------

    @Test
    fun `the first edit keeps what was recorded, and a later one does not overwrite it`() {
        val first = edit(recorded, TripEdit(distanceMetres = 13_000.0))
        val second =
            edit(
                first,
                TripEdit(startedAtMs = local("2026-10-05T08:00:00"), distanceMetres = 14_000.0),
            )

        assertEquals(asRecorded, first.recordedValues)
        // Not 13 000 m and not the start that was just replaced: what MilO recorded.
        assertEquals(asRecorded, second.recordedValues)
    }

    @Test
    fun `an address that was only typed keeps the recorded figures too`() {
        val after = edit(recorded, TripEdit(startAddress = TypedAddress("Home")))

        assertEquals(asRecorded, after.recordedValues)
    }

    // ---- Addresses ------------------------------------------------------------------------------

    @Test
    fun `a typed address is marked as by hand, and the other side is left to the lookup`() {
        val after = edit(recorded, TripEdit(startAddress = TypedAddress("Home")))

        assertEquals("Home", after.startAddress)
        assertTrue(after.startAddressByHand)
        assertEquals(recorded.endAddress, after.endAddress)
        assertFalse(after.endAddressByHand)
    }

    @Test
    fun `an address that is emptied is by hand as well, so the lookup does not put it back`() {
        val after = edit(recorded, TripEdit(endAddress = TypedAddress(null)))

        assertNull(after.endAddress)
        assertTrue(after.endAddressByHand)
    }

    @Test
    fun `leaving an empty address empty does not take it away from the lookup`() {
        val stillLookingUp = recorded.copy(startAddress = null)

        val after = edit(stillLookingUp, TripEdit(startAddress = TypedAddress(null)))

        assertEquals(stillLookingUp, after)
    }

    // ---- Business or Personal after an edit of the times ----------------------------------------

    @Test
    fun `a start moved out of the work hours makes the trip Personal`() {
        val after = edit(recorded, TripEdit(startedAtMs = local("2026-10-05T07:50:00")))

        assertEquals(TripCategory.PERSONAL, after.category)
        assertFalse(after.categorySetByHand)
    }

    @Test
    fun `a trip set by hand keeps its category when its start is moved`() {
        val byHand = recorded.copy(category = TripCategory.PERSONAL, categorySetByHand = true)

        val after = edit(byHand, TripEdit(startedAtMs = local("2026-10-05T09:00:00")))

        assertEquals(TripCategory.PERSONAL, after.category)
        assertTrue(after.categorySetByHand)
    }

    @Test
    fun `an end moved past the day's hours flags the trip, and moved back unflags it`() {
        val late = edit(recorded, TripEdit(endedAtMs = local("2026-10-05T16:45:00")))
        val back = edit(late, TripEdit(endedAtMs = local("2026-10-05T09:00:00")))

        assertTrue(late.ranPastSchedule)
        assertTrue(late.ranPastScheduleShown)
        assertFalse(back.ranPastSchedule)
    }

    // ---- A trip that was added by hand ----------------------------------------------------------

    @Test
    fun `a trip added by hand is changed like any other and never marked as edited`() {
        val added =
            tripAddedByHand(
                TypedTrip(
                    startedAtMs = local("2026-10-05T09:00:00"),
                    endedAtMs = local("2026-10-05T09:40:00"),
                    distanceMetres = 23_400.0,
                    startAddress = "Shop",
                    endAddress = null,
                    category = null,
                ),
                schedule,
                edmonton,
            ).copy(id = 31)

        val after =
            edit(added, TripEdit(distanceMetres = 25_000.0, endAddress = TypedAddress("Site")))

        assertEquals(added.copy(distanceMetres = 25_000.0, endAddress = "Site"), after)
        assertEquals(ByHandMark.ADDED, after.byHandMark)
        assertNull("It has nothing recorded to put back", after.recordedValues)
        assertNull(restoredTrip(after, schedule, edmonton))
    }

    // ---- Which trips ----------------------------------------------------------------------------

    @Test
    fun `only a finished trip can be edited`() {
        for (status in TripStatus.entries) {
            assertEquals(
                "$status",
                status == TripStatus.FINISHED,
                recorded.copy(status = status).canBeEdited,
            )
        }
    }
}

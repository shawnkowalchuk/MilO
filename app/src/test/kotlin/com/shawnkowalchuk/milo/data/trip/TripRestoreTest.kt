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
 * What "Restore recorded values" makes of an edited trip. Times are Edmonton wall-clock times on
 * Monday 5 October 2026; the schedule is Monday to Friday, 08:00 to 16:30.
 */
class TripRestoreTest {
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

    private fun edit(trip: Trip, edit: TripEdit): Trip = editedTrip(trip, edit, schedule, edmonton)

    private fun restore(trip: Trip): Trip? = restoredTrip(trip, schedule, edmonton)

    @Test
    fun `restore puts back the recorded times and distance and takes the mark away`() {
        val edited =
            edit(
                recorded,
                TripEdit(
                    startedAtMs = local("2026-10-05T08:00:00"),
                    endedAtMs = local("2026-10-05T09:00:00"),
                    distanceMetres = 20_000.0,
                ),
            )

        val restored = restore(edited)

        // To the millisecond and the metre. The recorded figures stay on the row: they are
        // written once, and a second edit will find them there.
        assertEquals(
            recorded.copy(
                recordedStartedAtMs = recorded.startedAtMs,
                recordedEndedAtMs = recorded.endedAtMs,
                recordedDistanceMetres = recorded.distanceMetres,
            ),
            restored,
        )
        assertNull(restored?.byHandMark)
    }

    @Test
    fun `restore puts back what was recorded, not what an earlier edit had made of it`() {
        val first = edit(recorded, TripEdit(distanceMetres = 13_000.0))
        val second = edit(first, TripEdit(distanceMetres = 14_000.0))

        assertEquals(12_344.7, restore(second)?.distanceMetres)
    }

    @Test
    fun `restore removes a typed address and hands it back to the lookup`() {
        val givenUp = recorded.copy(addressAttempts = 4)
        val edited =
            edit(
                givenUp,
                TripEdit(startAddress = TypedAddress("Home"), distanceMetres = 13_000.0),
            )

        val restored = checkNotNull(restore(edited))

        assertNull(restored.startAddress)
        assertFalse(restored.startAddressByHand)
        // The address the lookup had found itself is kept.
        assertEquals(recorded.endAddress, restored.endAddress)
        // The attempts start afresh, or a trip MilO had given up on would never be asked about.
        assertEquals(0, restored.addressAttempts)
        assertNull(restored.addressLastAttemptAtMs)
    }

    @Test
    fun `restore hands back an address that was emptied by hand as well`() {
        val edited = edit(recorded, TripEdit(endAddress = TypedAddress(null)))

        val restored = checkNotNull(restore(edited))

        assertFalse(restored.endAddressByHand)
        assertNull(restored.endAddress)
        assertEquals(0, restored.addressAttempts)
    }

    @Test
    fun `restore leaves the lookup's own count alone when no address was typed`() {
        val edited = edit(recorded, TripEdit(distanceMetres = 13_000.0))

        val restored = checkNotNull(restore(edited))

        assertEquals(recorded.addressAttempts, restored.addressAttempts)
        assertEquals(recorded.addressLastAttemptAtMs, restored.addressLastAttemptAtMs)
        assertEquals(recorded.startAddress, restored.startAddress)
    }

    @Test
    fun `restore sorts the trip again by its recorded start, unless it was set by hand`() {
        val moved = edit(recorded, TripEdit(startedAtMs = local("2026-10-05T07:50:00")))
        val movedAndMarked = edit(moved, TripEdit(category = TripCategory.PERSONAL))

        assertEquals(TripCategory.PERSONAL, moved.category)
        assertEquals(TripCategory.BUSINESS, restore(moved)?.category)
        assertEquals(TripCategory.PERSONAL, restore(movedAndMarked)?.category)
        assertTrue(restore(movedAndMarked)?.categorySetByHand == true)
    }

    @Test
    fun `restore works the ran-past note out again for the recorded times`() {
        val late = edit(recorded, TripEdit(endedAtMs = local("2026-10-05T16:45:00")))

        assertTrue(late.ranPastSchedule)
        assertEquals(false, restore(late)?.ranPastSchedule)
    }

    @Test
    fun `restore changes nothing but the figures, the typed addresses and the marks`() {
        val edited =
            edit(
                recorded,
                TripEdit(distanceMetres = 13_000.0, startAddress = TypedAddress("Home")),
            )

        val restored = checkNotNull(restore(edited))

        // The status, what started it, the positions and the kept figures are as they were.
        assertEquals(
            edited.copy(
                distanceMetres = recorded.distanceMetres,
                startAddress = null,
                startAddressByHand = false,
                addressAttempts = 0,
                addressLastAttemptAtMs = null,
                editedByHand = false,
            ),
            restored,
        )
    }

    @Test
    fun `only an edited, recorded, finished trip can be restored`() {
        val edited = edit(recorded, TripEdit(distanceMetres = 13_000.0))

        assertNull("Never edited", restore(recorded))
        assertNull("Deleted since", restore(edited.copy(status = TripStatus.DELETED)))
        assertNull("Restored already", restore(checkNotNull(restore(edited))))
        assertNull(
            "Marked Business or Personal only",
            restore(edit(recorded, TripEdit(category = TripCategory.PERSONAL))),
        )
    }

    @Test
    fun `a trip can be edited again after a restore, and restored again`() {
        val once = checkNotNull(restore(edit(recorded, TripEdit(distanceMetres = 13_000.0))))
        val twice = edit(once, TripEdit(distanceMetres = 9_000.0))

        assertEquals(ByHandMark.EDITED, twice.byHandMark)
        assertEquals(once, restore(twice))
    }
}

package com.shawnkowalchuk.milo.data.trip

import com.shawnkowalchuk.milo.core.schedule.DEFAULT_WORK_SCHEDULE
import com.shawnkowalchuk.milo.core.schedule.TripCategory
import com.shawnkowalchuk.milo.core.trip.TripStartCause
import com.shawnkowalchuk.milo.core.trip.TripStatus
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The event-log lines for a change by hand: every value that changed is named, old and new,
 * because the log is the only place the value in between survives.
 */
class TripEditLogTextTest {
    private val edmonton = ZoneId.of("America/Edmonton")

    private fun local(dateTime: String): Long =
        LocalDateTime.parse(dateTime).atZone(edmonton).toInstant().toEpochMilli()

    private val recorded =
        Trip(
            id = 12,
            startedAtMs = local("2026-10-05T08:14:27"),
            endedAtMs = local("2026-10-05T08:39:02"),
            status = TripStatus.FINISHED,
            startedBy = TripStartCause.TRUCK,
            truckSeen = true,
            distanceMetres = 12_344.7,
            startAddress = "12 Shop Rd, Edmonton",
            endAddress = null,
            category = TripCategory.BUSINESS,
        )

    private fun edited(edit: TripEdit, from: Trip = recorded): Trip =
        editedTrip(from, edit, DEFAULT_WORK_SCHEDULE, edmonton)

    // ---- Before and after -----------------------------------------------------------------------

    @Test
    fun `every changed value is named with what it was and what it is`() {
        val after =
            edited(
                TripEdit(
                    startedAtMs = local("2026-10-05T07:50:00"),
                    endedAtMs = local("2026-10-06T00:10:00"),
                    distanceMetres = 13_000.0,
                    startAddress = TypedAddress("14 Shop Rd, Edmonton"),
                    endAddress = TypedAddress("Site 7"),
                ),
            )

        assertEquals(
            listOf(
                "start 2026-10-05 08:14:27 → 2026-10-05 07:50:00",
                "end 2026-10-05 08:39:02 → 2026-10-06 00:10:00",
                "distance 12345 m → 13000 m",
                "from \"12 Shop Rd, Edmonton\" → \"14 Shop Rd, Edmonton\"",
                "to none → \"Site 7\"",
                "Business → Personal (sorted again by the schedule)",
            ),
            changesInWords(recorded, after, edmonton),
        )
    }

    @Test
    fun `a value that did not change is not named`() {
        val after = edited(TripEdit(distanceMetres = 13_000.0))

        assertEquals(
            listOf("distance 12345 m → 13000 m"),
            changesInWords(recorded, after, edmonton),
        )
        assertEquals(emptyList<String>(), changesInWords(recorded, recorded, edmonton))
    }

    @Test
    fun `an address that is taken out reads as none`() {
        val after = edited(TripEdit(startAddress = TypedAddress(null)))

        assertEquals(
            listOf("from \"12 Shop Rd, Edmonton\" → none"),
            changesInWords(recorded, after, edmonton),
        )
    }

    @Test
    fun `a category by hand and a changed ran-past note are named`() {
        val marked = edited(TripEdit(category = TripCategory.PERSONAL))
        val late = edited(TripEdit(endedAtMs = local("2026-10-05T16:45:00")))
        val backInTime = edited(TripEdit(endedAtMs = local("2026-10-05T09:00:00")), from = late)

        assertEquals(
            listOf("Business → Personal (set by hand)"),
            changesInWords(recorded, marked, edmonton),
        )
        assertEquals(
            listOf("end 2026-10-05 08:39:02 → 2026-10-05 16:45:00", "now ran past schedule"),
            changesInWords(recorded, late, edmonton),
        )
        assertEquals(
            "no longer ran past schedule",
            changesInWords(late, backInTime, edmonton).last(),
        )
    }

    // ---- The lines ------------------------------------------------------------------------------

    @Test
    fun `the line for an edit says that the trip is now marked and how to go back`() {
        val after = edited(TripEdit(distanceMetres = 13_000.0))

        assertEquals(
            "Trip 12: changed by hand on the edit screen: distance 12345 m → 13000 m (times in " +
                "America/Edmonton). It is now marked as edited, and \"Restore recorded values\" " +
                "puts back what MilO recorded",
            editText(12, ByHandOutcome.Done(recorded, after), edmonton),
        )
    }

    @Test
    fun `the line for a second edit says the trip was marked already`() {
        val first = edited(TripEdit(distanceMetres = 13_000.0))
        val second = edited(TripEdit(distanceMetres = 14_000.0), from = first)

        val line = editText(12, ByHandOutcome.Done(first, second), edmonton)

        assertTrue(line, line.contains("distance 13000 m → 14000 m"))
        assertTrue(line, line.endsWith("It was marked as edited already"))
    }

    @Test
    fun `the line for Business or Personal alone says the trip is not marked as edited`() {
        val after = edited(TripEdit(category = TripCategory.PERSONAL))

        assertEquals(
            "Trip 12: changed by hand on the edit screen: Business → Personal (set by hand) " +
                "(times in America/Edmonton). Its times, addresses and distance are as " +
                "recorded, so it is not marked as edited",
            editText(12, ByHandOutcome.Done(recorded, after), edmonton),
        )
    }

    @Test
    fun `a save that changed nothing and a refused one each have a line`() {
        assertEquals(
            "Trip 12: saved on the edit screen with nothing changed. Nothing was written",
            editText(12, ByHandOutcome.Unchanged(recorded), edmonton),
        )
        assertEquals(
            "Trip 12: edit refused: it is deleted, not finished. Nothing changed",
            editText(
                12,
                ByHandOutcome.Refused(recorded.copy(status = TripStatus.DELETED)),
                edmonton,
            ),
        )
        assertEquals(
            "Trip 12: edit refused: it is still being recorded. Nothing changed",
            editText(12, ByHandOutcome.Refused(recorded.copy(status = TripStatus.OPEN)), edmonton),
        )
        assertEquals(
            "Trip 12: edit refused: there is no such trip. Nothing changed",
            editText(12, ByHandOutcome.Refused(found = null), edmonton),
        )
    }

    @Test
    fun `the line for a restore names what went back and that typed addresses are looked up`() {
        val typed =
            edited(
                TripEdit(distanceMetres = 13_000.0, startAddress = TypedAddress("Home")),
            )
        val restored = checkNotNull(restoredTrip(typed, DEFAULT_WORK_SCHEDULE, edmonton))

        assertEquals(
            "Trip 12: recorded values restored on the edit screen: distance 13000 m → 12345 m; " +
                "from \"Home\" → none (times in America/Edmonton). It is no longer marked as " +
                "edited. An address typed by hand was removed and is looked up again.",
            restoreText(12, ByHandOutcome.Done(typed, restored), edmonton),
        )
    }

    @Test
    fun `a refused restore says why`() {
        assertEquals(
            "Trip 12: restore recorded values refused: it has no recorded values to put back. " +
                "Nothing changed",
            restoreText(12, ByHandOutcome.Refused(recorded), edmonton),
        )
        assertEquals(
            "Trip 12: restore recorded values refused: it is deleted, not finished. " +
                "Nothing changed",
            restoreText(
                12,
                ByHandOutcome.Refused(recorded.copy(status = TripStatus.DELETED)),
                edmonton,
            ),
        )
    }

    @Test
    fun `the line for a trip added by hand names everything that was typed`() {
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
                DEFAULT_WORK_SCHEDULE,
                edmonton,
            ).copy(id = 31)

        assertEquals(
            "Trip 31: added by hand on the edit screen: 2026-10-05 09:00:00 to " +
                "2026-10-05 09:40:00 (America/Edmonton), 23400 m, from \"Shop\" to none, " +
                "Business (sorted by the work schedule). It has no GPS points, is marked as " +
                "added by hand and is counted like any finished trip",
            addedText(added, edmonton),
        )
    }
}

package com.shawnkowalchuk.milo.feature.trips

import com.shawnkowalchuk.milo.core.trip.TripStartCause
import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.data.trip.Trip
import com.shawnkowalchuk.milo.data.trip.TripCorrection
import com.shawnkowalchuk.milo.platform.address.TripPlace
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private const val MINUTE_MS = 60_000L

/**
 * The Trips screen's month once trips can be deleted: what a deleted trip does to the totals,
 * where it is listed, and what each kind of trip offers. The rest of the month logic is in
 * `TripMonthTest`.
 */
class TripMonthLeftOutTest {
    private val edmonton = ZoneId.of("America/Edmonton")
    private var nextId = 1L

    /** A closed trip that started at [startUtc] and ended twenty minutes later. */
    private fun trip(
        startUtc: String,
        metres: Double,
        status: TripStatus = TripStatus.FINISHED,
    ): Trip {
        val startedAtMs = Instant.parse(startUtc).toEpochMilli()
        return Trip(
            id = nextId++,
            startedAtMs = startedAtMs,
            endedAtMs = startedAtMs + 20 * MINUTE_MS,
            status = status,
            startedBy = TripStartCause.TRUCK,
            truckSeen = true,
            distanceMetres = metres,
        )
    }

    private fun summary(trips: List<Trip>, showLeftOut: Boolean = false): MonthSummary =
        monthSummary(
            trips = trips,
            zone = edmonton,
            showLeftOut = showLeftOut,
            liveTripId = null,
            liveDistanceMetres = null,
            liveStart = null,
        )

    @Test
    fun `a deleted trip leaves the total and the count at once`() {
        val kept = trip("2026-10-05T14:00:00Z", metres = 12_300.0)
        val deleted = trip("2026-10-05T20:00:00Z", metres = 8_200.0, status = TripStatus.DELETED)

        val month = summary(listOf(kept, deleted))

        assertEquals(12_300.0, month.totalMetres, 0.0)
        assertEquals(1, month.tripCount)
        assertEquals(listOf(kept.id), month.days.single().trips.map { it.id })
        assertEquals(1, month.hiddenLeftOut)
    }

    @Test
    fun `deleted trips are listed with the discarded ones, each marked as what it is`() {
        val counted = trip("2026-10-05T14:00:00Z", metres = 12_300.0)
        val discarded = trip("2026-10-05T16:00:00Z", metres = 250.0, status = TripStatus.DISCARDED)
        val deleted = trip("2026-10-05T18:00:00Z", metres = 8_200.0, status = TripStatus.DELETED)

        val hidden = summary(listOf(counted, discarded, deleted), showLeftOut = false)
        val shown = summary(listOf(counted, discarded, deleted), showLeftOut = true)

        assertEquals(2, hidden.hiddenLeftOut)
        assertEquals(
            listOf(
                deleted.id to TripKind.DELETED,
                discarded.id to TripKind.DISCARDED,
                counted.id to TripKind.COUNTED,
            ),
            shown.days.single().trips.map { it.id to it.kind },
        )
        // Listing them changes nothing about what is counted.
        for (month in listOf(hidden, shown)) {
            assertEquals(12_300.0, month.totalMetres, 0.0)
            assertEquals(1, month.tripCount)
        }
    }

    @Test
    fun `a trip that is restored, or counted after all, is in the total again`() {
        val deleted = trip("2026-10-05T14:00:00Z", metres = 8_200.0, status = TripStatus.DELETED)
        val discarded = trip("2026-10-05T16:00:00Z", metres = 250.0, status = TripStatus.DISCARDED)
        assertEquals(0, summary(listOf(deleted, discarded)).tripCount)

        // What Restore and "Count this trip" do to a row: the status, and nothing else.
        val month =
            summary(
                listOf(
                    deleted.copy(status = TripStatus.FINISHED),
                    discarded.copy(status = TripStatus.FINISHED),
                ),
            )

        assertEquals(2, month.tripCount)
        assertEquals(8_450.0, month.totalMetres, 0.0)
        assertEquals(0, month.hiddenLeftOut)
    }

    @Test
    fun `a listed trip's kind names the status its row has`() {
        // What a row offers follows from its kind's status, and storage makes a change only
        // from the status the row really has. The two must be the same status.
        val trips = TripStatus.entries.map { trip("2026-10-05T14:00:00Z", 5_000.0, status = it) }

        val month = summary(trips, showLeftOut = true)

        val lines = month.days.flatMap { it.trips } + listOfNotNull(month.inProgress)
        assertEquals(
            trips.associate { it.id to it.status },
            lines.associate { it.id to it.kind.status },
        )
    }

    @Test
    fun `each kind of trip offers the one change that fits it`() {
        assertEquals(TripCorrection.DELETE, TripKind.COUNTED.correction)
        assertEquals(TripCorrection.RESTORE, TripKind.DELETED.correction)
        assertEquals(TripCorrection.COUNT, TripKind.DISCARDED.correction)
        // A trip that is still being recorded is ended, never deleted.
        assertNull(TripKind.IN_PROGRESS.correction)
    }

    @Test
    fun `a deleted trip's line shows the addresses it already had, and never a lookup`() {
        val located =
            trip("2026-10-05T14:00:00Z", metres = 8_200.0, status = TripStatus.DELETED).copy(
                startLatitude = 53.5,
                startLongitude = -113.5,
                endLatitude = 53.6,
                endLongitude = -113.4,
            )
        val withStart = located.copy(id = nextId++, startAddress = "12 Shop Rd, Edmonton")

        val lines = summary(listOf(located, withStart), showLeftOut = true).days.single().trips
        val byId = lines.associateBy { it.id }

        // Nothing is looked up for a trip while it is deleted, so nothing is "being looked up".
        assertNull(byId.getValue(located.id).from)
        assertNull(byId.getValue(located.id).to)
        assertEquals(TripPlace.Known("12 Shop Rd, Edmonton"), byId.getValue(withStart.id).from)
        assertNull(byId.getValue(withStart.id).to)
    }
}

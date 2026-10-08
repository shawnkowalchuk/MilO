package com.shawnkowalchuk.milo.feature.trips

import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.schedule.TripCategory
import com.shawnkowalchuk.milo.core.trip.TripStartCause
import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.core.util.DistanceUnit
import com.shawnkowalchuk.milo.data.trip.Tally
import com.shawnkowalchuk.milo.data.trip.Trip
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val MINUTE_MS = 60_000L

/**
 * The Trips screen's month once trips are Business or Personal: the month's totals, each day's
 * heading, what a row says about its trip and what it offers. The rest of the month logic is in
 * `TripMonthTest` and `TripMonthLeftOutTest`.
 */
class TripMonthCategoryTest {
    private val edmonton = ZoneId.of("America/Edmonton")
    private var nextId = 1L

    /** A closed trip that started at [startUtc] and ended twenty minutes later. */
    private fun trip(
        startUtc: String,
        metres: Double,
        category: TripCategory?,
        status: TripStatus = TripStatus.FINISHED,
        ranPastSchedule: Boolean = false,
        ignored: Boolean = false,
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
            category = category,
            ranPastSchedule = ranPastSchedule,
            ignoredOutsideSchedule = ignored,
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
            unit = DistanceUnit.KILOMETRES,
        )

    private fun line(trip: Trip, showLeftOut: Boolean = true): TripLine =
        summary(listOf(trip), showLeftOut).days.single().trips.single()

    // ---- The month's totals -----------------------------------------------------------------------

    @Test
    fun `the month adds Business and Personal up apart`() {
        val month =
            summary(
                listOf(
                    trip("2026-10-05T14:00:00Z", 12_300.0, TripCategory.BUSINESS),
                    trip("2026-10-05T20:00:00Z", 8_200.0, TripCategory.BUSINESS),
                    trip("2026-10-06T02:00:00Z", 5_000.0, TripCategory.PERSONAL),
                    trip("2026-10-10T18:00:00Z", 40_000.0, TripCategory.PERSONAL),
                    trip("2026-10-12T14:00:00Z", 900.0, TripCategory.BUSINESS, TripStatus.DELETED),
                ),
            )

        assertEquals(Tally(2, 205), month.totals.business)
        assertEquals(Tally(2, 450), month.totals.personal)
        assertEquals(Tally(0, 0), month.totals.unsorted)
        // Every finished trip, for the line that says a trip can be tapped.
        assertEquals(4, month.tripCount)
    }

    @Test
    fun `a finished trip that is not sorted yet is in neither total`() {
        val month = summary(listOf(trip("2026-10-05T14:00:00Z", 12_300.0, category = null)))

        assertEquals(Tally(0, 0), month.totals.business)
        assertEquals(Tally(0, 0), month.totals.personal)
        assertEquals(Tally(1, 123), month.totals.unsorted)
    }

    // ---- A day's heading --------------------------------------------------------------------------

    @Test
    fun `a day's heading has the day's number of sessions and its Business distance`() {
        val month =
            summary(
                listOf(
                    // Monday 5 October, local time: two Business trips and a Personal one.
                    trip("2026-10-05T14:00:00Z", 12_300.0, TripCategory.BUSINESS),
                    trip("2026-10-05T20:00:00Z", 8_200.0, TripCategory.BUSINESS),
                    trip("2026-10-06T02:00:00Z", 5_000.0, TripCategory.PERSONAL),
                    // Saturday 10 October: one Personal trip.
                    trip("2026-10-10T18:00:00Z", 40_000.0, TripCategory.PERSONAL),
                ),
            )

        val (saturday, monday) = month.days
        assertEquals(LocalDate.of(2026, 10, 5), monday.date)
        assertEquals(3, monday.sessionCount)
        assertEquals(205, monday.businessTenths)
        assertEquals(LocalDate.of(2026, 10, 10), saturday.date)
        assertEquals(1, saturday.sessionCount)
        assertEquals(0, saturday.businessTenths)
    }

    @Test
    fun `listing the deleted and discarded trips does not change a day's heading`() {
        val trips =
            listOf(
                trip("2026-10-05T14:00:00Z", 12_300.0, TripCategory.BUSINESS),
                trip("2026-10-05T16:00:00Z", 9_000.0, TripCategory.BUSINESS, TripStatus.DELETED),
                trip("2026-10-05T18:00:00Z", 150.0, TripCategory.BUSINESS, TripStatus.DISCARDED),
            )

        for (shown in listOf(false, true)) {
            val day = summary(trips, showLeftOut = shown).days.single()
            assertEquals(1, day.sessionCount)
            assertEquals(123, day.businessTenths)
        }
    }

    @Test
    fun `a day with nothing but left-out trips has no sessions and no Business distance`() {
        val ignored =
            trip(
                "2026-10-10T18:00:00Z",
                40_000.0,
                TripCategory.PERSONAL,
                TripStatus.DISCARDED,
                ignored = true,
            )

        val day = summary(listOf(ignored), showLeftOut = true).days.single()

        assertEquals(0, day.sessionCount)
        assertEquals(0, day.businessTenths)
    }

    // ---- What a row says --------------------------------------------------------------------------

    @Test
    fun `a row says Business or Personal, and ran past schedule where it applies`() {
        val business = line(trip("2026-10-05T14:00:00Z", 12_300.0, TripCategory.BUSINESS))
        val late =
            trip("2026-10-05T22:20:00Z", 9_000.0, TripCategory.BUSINESS, ranPastSchedule = true)
        val ranPast = line(late)
        val personal = line(trip("2026-10-10T18:00:00Z", 5_000.0, TripCategory.PERSONAL))
        val unsorted = line(trip("2026-10-10T19:00:00Z", 5_000.0, category = null))

        assertEquals(R.string.trip_business, business.categoryNoteRes())
        assertEquals(R.string.trip_business_ran_past, ranPast.categoryNoteRes())
        assertEquals(R.string.trip_personal, personal.categoryNoteRes())
        assertEquals(R.string.trip_unsorted, unsorted.categoryNoteRes())
    }

    @Test
    fun `a trip marked Personal no longer says that it ran past the schedule`() {
        // The stored flag outlives the marking; the row goes by what the trip is now.
        val marked =
            trip("2026-10-05T22:20:00Z", 9_000.0, TripCategory.PERSONAL, ranPastSchedule = true)

        assertEquals(R.string.trip_personal, line(marked).categoryNoteRes())
    }

    @Test
    fun `the trip in progress says nothing about Business or Personal`() {
        val open =
            trip("2026-10-05T14:00:00Z", 0.0, category = null, status = TripStatus.OPEN)
                .copy(endedAtMs = null)

        val inProgress = checkNotNull(summary(listOf(open)).inProgress)

        assertNull(inProgress.categoryNoteRes())
        assertTrue(inProgress.markableAs.isEmpty())
    }

    @Test
    fun `a trip left out by the ignore setting says so, and not that it was too short`() {
        val ignored =
            line(
                trip(
                    "2026-10-10T18:00:00Z",
                    40_000.0,
                    TripCategory.PERSONAL,
                    TripStatus.DISCARDED,
                    ignored = true,
                ),
            )
        val tooShort =
            line(trip("2026-10-10T19:00:00Z", 120.0, TripCategory.PERSONAL, TripStatus.DISCARDED))

        assertEquals(R.string.trips_ignored_note, ignored.leftOutNoteRes())
        assertEquals(R.string.trips_discarded_note, tooShort.leftOutNoteRes())
        // Both are what they will be if they are counted, and both can be counted.
        assertEquals(R.string.trip_personal, ignored.categoryNoteRes())
        assertEquals(TripKind.DISCARDED, ignored.kind)
    }

    @Test
    fun `an ignored trip that was counted after all is an ordinary Personal trip`() {
        // "Count this trip" changes the status and nothing else: the stored mark stays.
        val counted =
            line(trip("2026-10-10T18:00:00Z", 40_000.0, TripCategory.PERSONAL, ignored = true))

        assertEquals(TripKind.COUNTED, counted.kind)
        assertNull(counted.leftOutNoteRes())
        assertEquals(R.string.trip_personal, counted.categoryNoteRes())
    }

    // ---- What a row offers ------------------------------------------------------------------------

    @Test
    fun `a counted trip offers to be marked as what it is not`() {
        val business = line(trip("2026-10-05T14:00:00Z", 12_300.0, TripCategory.BUSINESS))
        val personal = line(trip("2026-10-10T18:00:00Z", 5_000.0, TripCategory.PERSONAL))
        val unsorted = line(trip("2026-10-10T19:00:00Z", 5_000.0, category = null))

        assertEquals(listOf(TripCategory.PERSONAL), business.markableAs)
        assertEquals(listOf(TripCategory.BUSINESS), personal.markableAs)
        assertEquals(TripCategory.entries, unsorted.markableAs)
    }

    @Test
    fun `a deleted or a discarded trip is restored or counted first, not marked`() {
        for (status in listOf(TripStatus.DELETED, TripStatus.DISCARDED)) {
            val leftOut = line(trip("2026-10-05T14:00:00Z", 9_000.0, TripCategory.BUSINESS, status))

            assertTrue(status.name, leftOut.markableAs.isEmpty())
        }
    }

    @Test
    fun `each button of a row has words of its own`() {
        assertEquals(R.string.trips_action_mark_business, TripCategory.BUSINESS.markLabelRes())
        assertEquals(R.string.trips_action_mark_personal, TripCategory.PERSONAL.markLabelRes())
    }
}

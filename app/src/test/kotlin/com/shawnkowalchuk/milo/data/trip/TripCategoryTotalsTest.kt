package com.shawnkowalchuk.milo.data.trip

import com.shawnkowalchuk.milo.core.report.ReportDay
import com.shawnkowalchuk.milo.core.report.ReportTrip
import com.shawnkowalchuk.milo.core.schedule.TripCategory
import com.shawnkowalchuk.milo.core.trip.TripStartCause
import com.shawnkowalchuk.milo.core.trip.TripStatus
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val MINUTE_MS = 60_000L
private const val MORNING_MS = 1_791_028_800_000L

/**
 * Totals once trips are Business or Personal: the two are added up apart, on the Trips screen
 * and on Home alike, and a trip that is not counted is in neither. What counts at all is in
 * `TripTotalsTest`.
 */
class TripCategoryTotalsTest {
    private var nextId = 1L

    /** A closed trip that started [startMinute] minutes into the morning and lasted [minutes]. */
    private fun trip(
        startMinute: Long,
        minutes: Long,
        metres: Double,
        category: TripCategory?,
        status: TripStatus = TripStatus.FINISHED,
        ranPastSchedule: Boolean = false,
    ): Trip {
        val startedAtMs = MORNING_MS + startMinute * MINUTE_MS
        return Trip(
            id = nextId++,
            startedAtMs = startedAtMs,
            endedAtMs = startedAtMs + minutes * MINUTE_MS,
            status = status,
            startedBy = TripStartCause.TRUCK,
            truckSeen = true,
            distanceMetres = metres,
            category = category,
            ranPastSchedule = ranPastSchedule,
        )
    }

    private val mixedDay
        get() =
            listOf(
                trip(0, 25, 20_000.0, TripCategory.BUSINESS),
                trip(60, 30, 21_249.0, TripCategory.BUSINESS, ranPastSchedule = true),
                trip(120, 15, 6_500.0, TripCategory.PERSONAL),
                trip(180, 10, 3_000.0, category = null),
                // None of these three is in any total, whatever it is saved as.
                trip(240, 40, 30_000.0, TripCategory.BUSINESS, TripStatus.DELETED),
                trip(300, 20, 9_000.0, TripCategory.PERSONAL, TripStatus.DISCARDED),
                trip(360, 0, 0.0, category = null, status = TripStatus.OPEN),
            )

    // ---- Adding up by category --------------------------------------------------------------------

    @Test
    fun `Business and Personal are added up apart, and only counted trips are in either`() {
        val totals = categoryTotals(mixedDay)

        // 20.0 km and 21.2 km, as each is printed: 41.2 km.
        assertEquals(Tally(2, 412), totals.business)
        assertEquals(Tally(1, 65), totals.personal)
        assertEquals(Tally(1, 30), totals.unsorted)
        assertEquals(4, totals.count)
    }

    @Test
    fun `a Personal trip never adds to the Business total, however long it is`() {
        val business = trip(0, 20, 5_000.0, TripCategory.BUSINESS)
        val longPersonal = trip(60, 240, 400_000.0, TripCategory.PERSONAL)

        val totals = categoryTotals(listOf(business, longPersonal))

        assertEquals(Tally(1, 50), totals.business)
        assertEquals(Tally(1, 4_000), totals.personal)
    }

    @Test
    fun `no trips add up to three empty totals`() {
        val nothing = Tally(0, 0)

        assertEquals(CategoryTotals(nothing, nothing, nothing), categoryTotals(emptyList()))
    }

    @Test
    fun `marking a trip moves it from one total to the other and changes nothing else`() {
        val one = trip(0, 20, 5_000.0, TripCategory.BUSINESS)
        val other = trip(60, 20, 7_000.0, TripCategory.BUSINESS)

        val before = categoryTotals(listOf(one, other))
        // What "Mark as Personal" does to a row.
        val marked = other.copy(category = TripCategory.PERSONAL, categorySetByHand = true)
        val after = categoryTotals(listOf(one, marked))

        assertEquals(Tally(2, 120), before.business)
        assertEquals(Tally(1, 50), after.business)
        assertEquals(Tally(1, 70), after.personal)
        assertEquals(before.count, after.count)
    }

    @Test
    fun `each total is the sum of the figures printed for its trips, as on the report`() {
        // Each prints as 0.1 km, so three of them are 0.3 km. Their 447 m rounded once would be
        // 0.4 km: a total that the rows above it do not add up to, and not the report's.
        val totals = categoryTotals(List(3) { trip(it * 10L, 5, 149.0, TripCategory.BUSINESS) })

        assertEquals(3, totals.business.tenths)
    }

    @Test
    fun `the Business total is the total the report for the accountant prints`() {
        // 193 trips that each round down by 4 m: the case in which the screens used to show a
        // month one kilometre longer than its report.
        val distances = List(193) { 11_504.0 }
        val trips = distances.mapIndexed { i, metres ->
            trip(i * 10L, 5, metres, TripCategory.BUSINESS)
        }
        val onReport = distances.map { ReportTrip(0, null, null, null, it) }

        val business = categoryTotals(trips).business

        assertEquals(ReportDay(LocalDate.of(2026, 10, 5), onReport).tenths, business.tenths)
        assertEquals(193 * 115L, business.tenths)
    }

    // ---- Today ------------------------------------------------------------------------------------

    @Test
    fun `today's figures are split the same way, with the drive time of the Business trips`() {
        val today = todayTrips(mixedDay)

        assertEquals(Tally(2, 412), today.totals.business)
        assertEquals(Tally(1, 65), today.totals.personal)
        assertEquals(Tally(1, 30), today.totals.unsorted)
        assertEquals(55 * MINUTE_MS, today.businessDriveTimeMs)
    }

    @Test
    fun `today's count and distance of every trip are still there for the Android Auto screen`() {
        val today = todayTrips(mixedDay)

        // Business, Personal and unsorted together, each trip as it is printed:
        // 20.0 + 21.2 + 6.5 + 3.0 km.
        assertEquals(4, today.count)
        assertEquals(507, today.totalTenths)
        assertEquals(80 * MINUTE_MS, today.driveTimeMs)
    }

    @Test
    fun `each of today's sessions says what it is saved as`() {
        val sessions = todayTrips(mixedDay).sessions

        assertEquals(
            listOf(null, TripCategory.PERSONAL, TripCategory.BUSINESS, TripCategory.BUSINESS),
            sessions.map { it.category },
        )
        assertEquals(listOf(false, false, true, false), sessions.map { it.ranPastSchedule })
    }

    @Test
    fun `a day of Personal trips only has no Business figures, and is not an empty day`() {
        val today = todayTrips(listOf(trip(0, 30, 12_000.0, TripCategory.PERSONAL)))

        assertEquals(Tally(0, 0), today.totals.business)
        assertEquals(0L, today.businessDriveTimeMs)
        assertEquals(1, today.count)
    }

    // ---- Ran past schedule ------------------------------------------------------------------------

    @Test
    fun `ran past schedule is said of a Business trip only`() {
        val flagged = trip(0, 60, 30_000.0, TripCategory.BUSINESS, ranPastSchedule = true)

        assertTrue(flagged.ranPastScheduleShown)
        assertFalse(trip(0, 60, 30_000.0, TripCategory.BUSINESS).ranPastScheduleShown)
        // Marked Personal by hand: the stored flag stays, and the note is no longer shown.
        val markedPersonal = flagged.copy(category = TripCategory.PERSONAL)
        assertFalse(markedPersonal.ranPastScheduleShown)
        assertFalse(todayTrips(listOf(markedPersonal)).sessions.single().ranPastSchedule)
        // Marked Business again: it comes back.
        assertTrue(markedPersonal.copy(category = TripCategory.BUSINESS).ranPastScheduleShown)
    }

    // ---- What a trip can be marked as -------------------------------------------------------------

    @Test
    fun `a finished trip can be marked as whatever it is not`() {
        val finished = TripStatus.FINISHED

        assertEquals(
            listOf(TripCategory.PERSONAL),
            categoriesOffered(finished, TripCategory.BUSINESS),
        )
        assertEquals(
            listOf(TripCategory.BUSINESS),
            categoriesOffered(finished, TripCategory.PERSONAL),
        )
        // Not sorted yet: either.
        assertEquals(TripCategory.entries, categoriesOffered(finished, category = null))
    }

    @Test
    fun `a trip that is not a finished one cannot be marked`() {
        for (status in TripStatus.entries.filter { it != TripStatus.FINISHED }) {
            for (category in TripCategory.entries + null) {
                val offered = categoriesOffered(status, category)
                assertEquals("$status, $category", emptyList<TripCategory>(), offered)
            }
        }
    }
}

package com.shawnkowalchuk.milo.data.trip

import com.shawnkowalchuk.milo.core.schedule.TripCategory
import com.shawnkowalchuk.milo.core.trip.TripStartCause
import com.shawnkowalchuk.milo.core.trip.TripStatus
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

        assertEquals(Tally(2, 41_249.0), totals.business)
        assertEquals(Tally(1, 6_500.0), totals.personal)
        assertEquals(Tally(1, 3_000.0), totals.unsorted)
        assertEquals(4, totals.count)
    }

    @Test
    fun `a Personal trip never adds to the Business total, however long it is`() {
        val business = trip(0, 20, 5_000.0, TripCategory.BUSINESS)
        val longPersonal = trip(60, 240, 400_000.0, TripCategory.PERSONAL)

        val totals = categoryTotals(listOf(business, longPersonal))

        assertEquals(Tally(1, 5_000.0), totals.business)
        assertEquals(Tally(1, 400_000.0), totals.personal)
    }

    @Test
    fun `no trips add up to three empty totals`() {
        val nothing = Tally(0, 0.0)

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

        assertEquals(Tally(2, 12_000.0), before.business)
        assertEquals(Tally(1, 5_000.0), after.business)
        assertEquals(Tally(1, 7_000.0), after.personal)
        assertEquals(before.count, after.count)
    }

    @Test
    fun `each total is added up in metres, so the rounding of single trips does not accumulate`() {
        // Each prints as 0.1 km, three of them as 0.4 km: 149 m each is 447 m.
        val totals = categoryTotals(List(3) { trip(it * 10L, 5, 149.0, TripCategory.BUSINESS) })

        assertEquals(447.0, totals.business.metres, 0.0)
    }

    // ---- Today ------------------------------------------------------------------------------------

    @Test
    fun `today's figures are split the same way, with the drive time of the Business trips`() {
        val today = todayTrips(mixedDay)

        assertEquals(Tally(2, 41_249.0), today.totals.business)
        assertEquals(Tally(1, 6_500.0), today.totals.personal)
        assertEquals(Tally(1, 3_000.0), today.totals.unsorted)
        assertEquals(55 * MINUTE_MS, today.businessDriveTimeMs)
    }

    @Test
    fun `today's count and distance of every trip are still there for the Android Auto screen`() {
        val today = todayTrips(mixedDay)

        // Business, Personal and unsorted together: the car's row has not changed.
        assertEquals(4, today.count)
        assertEquals(50_749.0, today.totalMetres, 0.0)
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

        assertEquals(Tally(0, 0.0), today.totals.business)
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

package com.shawnkowalchuk.milo.platform.widget

import com.shawnkowalchuk.milo.core.schedule.TripCategory
import com.shawnkowalchuk.milo.core.trip.TripStartCause
import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.data.trip.Trip
import com.shawnkowalchuk.milo.platform.car.CarAction
import com.shawnkowalchuk.milo.platform.car.STARTED_AT_MS
import com.shawnkowalchuk.milo.platform.car.carTrip
import com.shawnkowalchuk.milo.platform.trip.TripActivity
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the home-screen widget shows: the Android Auto screen's trip line and button, and the
 * business kilometres of this month and this year priced at the CRA rate.
 */
class HomeWidgetContentTest {
    private val zone: ZoneId = ZoneId.of("America/Edmonton")
    private var nextId = 1L

    private fun at(month: Int, day: Int, year: Int = 2026): Long =
        LocalDate.of(year, month, day).atTime(9, 0).atZone(zone).toInstant().toEpochMilli()

    private fun trip(
        startedAtMs: Long,
        km: Double,
        category: TripCategory? = TripCategory.BUSINESS,
        status: TripStatus = TripStatus.FINISHED,
    ) = Trip(
        id = nextId++,
        startedAtMs = startedAtMs,
        endedAtMs = (startedAtMs + 3_600_000L).takeIf { status != TripStatus.OPEN },
        status = status,
        startedBy = TripStartCause.TRUCK,
        truckSeen = true,
        distanceMetres = km * 1000,
        category = category,
    )

    private fun content(
        yearTrips: List<Trip>?,
        nowMs: Long = at(10, 7),
        activity: TripActivity = TripActivity(),
    ) = homeWidgetContent(activity, yearTrips, nowMs, zone, Locale.US)

    @Test
    fun `this month and this year are priced at the year's two rates`() {
        val trips =
            listOf(
                trip(at(2, 10), 4_000.0),
                // This month: the year passes 5,000 km here, 1,000 at 73 cents, 500 at 67.
                trip(at(10, 1), 1_500.0),
            )

        val dollars = checkNotNull(content(trips).dollars)

        assertEquals("Oct", dollars.monthLabel)
        assertEquals("$1,065", dollars.month)
        assertEquals("2026", dollars.yearLabel)
        // 4,000 km at 73 cents and the month's $1,065.
        assertEquals("$3,985", dollars.year)
        assertEquals(2026, dollars.rateYear)
        assertTrue(dollars.rateIsTheYears)
    }

    @Test
    fun `only counted business trips are priced`() {
        val trips =
            listOf(
                trip(at(10, 2), 100.0),
                trip(at(10, 3), 50.0, category = TripCategory.PERSONAL),
                trip(at(10, 4), 50.0, category = null),
                trip(at(10, 5), 50.0, status = TripStatus.DELETED),
                trip(at(10, 6), 50.0, status = TripStatus.DISCARDED),
                trip(at(10, 7), 50.0, status = TripStatus.OPEN),
            )

        val dollars = checkNotNull(content(trips).dollars)

        assertEquals("$73", dollars.month)
        assertEquals("$73", dollars.year)
    }

    @Test
    fun `a year MilO has no rate for yet is priced at the newest, and says which`() {
        val trips = listOf(trip(at(1, 5, 2027), 10.0))

        val dollars = checkNotNull(content(trips, nowMs = at(1, 6, 2027)).dollars)

        assertEquals("Jan", dollars.monthLabel)
        assertEquals("2027", dollars.yearLabel)
        assertEquals(2026, dollars.rateYear)
        assertFalse(dollars.rateIsTheYears)
    }

    @Test
    fun `without this year's trips there are no dollars, but the trip is still shown`() {
        val shown = content(yearTrips = null)

        assertNull(shown.dollars)
        assertEquals(CarAction.START_TRIP, shown.screen.action)
        assertNull(shown.tripStartedAtMs)
    }

    @Test
    fun `an open trip shows its figures, its start for the clock, and End`() {
        val shown = content(emptyList(), activity = TripActivity(trip = carTrip()))

        assertEquals(CarAction.END_TRIP, shown.screen.action)
        assertEquals("12.4", shown.screen.trip?.kilometres)
        assertEquals(STARTED_AT_MS, shown.tripStartedAtMs)
        // The widget does not show today's trips.
        assertNull(shown.screen.today)
    }

    @Test
    fun `the year runs from the 1st of January to the 31st of December, in the phone's zone`() {
        fun midnightOf(day: LocalDate) = day.atStartOfDay(zone).toInstant().toEpochMilli()

        val span = yearSpanOf(at(10, 7), zone)

        assertEquals(midnightOf(LocalDate.of(2026, 1, 1)), span.fromMs)
        assertEquals(midnightOf(LocalDate.of(2027, 1, 1)), span.untilMs)
    }
}

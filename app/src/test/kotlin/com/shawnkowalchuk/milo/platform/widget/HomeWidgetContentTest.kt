package com.shawnkowalchuk.milo.platform.widget

import com.shawnkowalchuk.milo.core.schedule.TripCategory
import com.shawnkowalchuk.milo.core.trip.TripStartCause
import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.core.util.DistanceUnit
import com.shawnkowalchuk.milo.data.trip.Trip
import com.shawnkowalchuk.milo.platform.car.CarAction
import com.shawnkowalchuk.milo.platform.car.STARTED_AT_MS
import com.shawnkowalchuk.milo.platform.car.carTrip
import com.shawnkowalchuk.milo.platform.trip.TripActivity
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * What the home-screen widget shows: the Android Auto screen's trip line and button, and the
 * business kilometres of this month and this year priced at the rate set in Settings.
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
        centsPerKm: Int? = 70,
        nowMs: Long = at(10, 7),
        activity: TripActivity = TripActivity(),
        unit: DistanceUnit = DistanceUnit.KILOMETRES,
    ) = homeWidgetContent(activity, yearTrips, centsPerKm, nowMs, zone, Locale.US, unit)

    @Test
    fun `this month and this year are priced at the one rate, and the rate is named`() {
        val trips =
            listOf(
                trip(at(2, 10), 4_000.0),
                // This month: the year passes 5,000 km here, and every km is still 70 cents.
                trip(at(10, 1), 1_500.0),
            )

        val dollars = checkNotNull(content(trips).dollars)

        assertEquals("Oct", dollars.monthLabel)
        assertEquals("$1,050", dollars.month)
        assertEquals("2026", dollars.yearLabel)
        // 5,500 km at 70 cents.
        assertEquals("$3,850", dollars.year)
        assertEquals("$0.70", dollars.rate)
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

        assertEquals("$70", dollars.month)
        assertEquals("$70", dollars.year)
    }

    @Test
    fun `a rate set in Settings prices both figures`() {
        val trips = listOf(trip(at(2, 10), 1_000.0), trip(at(10, 2), 100.0))

        val dollars = checkNotNull(content(trips, centsPerKm = 73).dollars)

        assertEquals("$73", dollars.month)
        assertEquals("$803", dollars.year)
        assertEquals("$0.73", dollars.rate)
    }

    // ---- Kilometres or miles (2026-10-07): the money is not converted ----------------------------

    @Test
    fun `the dollars are the same whichever unit distances are shown in`() {
        // Distances that round differently in the two units, a Personal trip, and months.
        val trips =
            listOf(
                trip(at(2, 10), 0.349),
                trip(at(3, 1), 12.35),
                trip(at(10, 1), 100.05),
                trip(at(10, 2), 1_500.0),
                trip(at(10, 3), 0.05),
                trip(at(10, 4), 80.0, category = TripCategory.PERSONAL),
            )

        for (cents in listOf(1, 70, 73, 113, 500)) {
            val inKilometres = content(trips, centsPerKm = cents)
            val inMiles = content(trips, centsPerKm = cents, unit = DistanceUnit.MILES)

            assertEquals("$cents cents", inKilometres.dollars, inMiles.dollars)
        }
    }

    @Test
    fun `in miles the dollars are still business kilometres at the rate per kilometre`() {
        // 193 trips of 11.504 km print as 11.5 km each: 2 219.5 km, $1,553.65 at 70 cents.
        // Priced from the miles on screen (7.1 mi each, at $1.13 a mile) it would be $1,548.
        val trips = List(193) { trip(at(10, 1), 11.504) }

        val dollars = checkNotNull(content(trips, unit = DistanceUnit.MILES).dollars)

        assertEquals("$1,554", dollars.month)
        assertEquals("$1,554", dollars.year)
        // The rate is the one set in Settings, a rate per kilometre, and is named as that.
        assertEquals("$0.70", dollars.rate)
    }

    @Test
    fun `in miles the open trip's figure is in miles`() {
        val shown =
            content(
                emptyList(),
                activity = TripActivity(trip = carTrip()),
                unit = DistanceUnit.MILES,
            )

        assertEquals("7.7", shown.screen.trip?.kilometres)
        assertEquals(DistanceUnit.MILES, shown.screen.unit)
        assertEquals(STARTED_AT_MS, shown.tripStartedAtMs)
    }

    @Test
    fun `without the rate there are no dollars, but the trip is still shown`() {
        val shown = content(listOf(trip(at(10, 2), 100.0)), centsPerKm = null)

        assertNull(shown.dollars)
        assertEquals(CarAction.START_TRIP, shown.screen.action)
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

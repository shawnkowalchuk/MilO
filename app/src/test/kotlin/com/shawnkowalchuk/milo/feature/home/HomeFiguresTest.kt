package com.shawnkowalchuk.milo.feature.home

import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.text.PlaceSide
import com.shawnkowalchuk.milo.core.designsystem.text.PlacesText
import com.shawnkowalchuk.milo.core.schedule.TripCategory
import com.shawnkowalchuk.milo.core.trip.TripStartCause
import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.core.util.DistanceUnit
import com.shawnkowalchuk.milo.data.trip.CategoryTotals
import com.shawnkowalchuk.milo.data.trip.Tally
import com.shawnkowalchuk.milo.data.trip.Trip
import com.shawnkowalchuk.milo.platform.address.OpenTripStart
import com.shawnkowalchuk.milo.platform.address.TripPlace
import com.shawnkowalchuk.milo.platform.trip.CurrentTrip
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val MINUTE_MS = 60_000L

/**
 * What Home's tiles make of the stored trips: the month's Business share, today's trips picked
 * out of the month's, the last trip, where a trip in progress started, and when the minutes of
 * a trip in progress next change.
 */
class HomeFiguresTest {
    private val edmonton = ZoneId.of("America/Edmonton")
    private val today = LocalDate.of(2026, 10, 6)
    private var nextId = 1L

    /** A local date and time in Edmonton, as stored time. */
    private fun at(text: String): Long =
        LocalDateTime.parse(text).atZone(edmonton).toInstant().toEpochMilli()

    /** A trip that started at [start], local time, and ended twenty minutes later. */
    private fun trip(
        start: String,
        metres: Double,
        category: TripCategory? = TripCategory.BUSINESS,
        status: TripStatus = TripStatus.FINISHED,
        from: String? = null,
        to: String? = null,
    ): Trip {
        val startedAtMs = at(start)
        return Trip(
            id = nextId++,
            startedAtMs = startedAtMs,
            endedAtMs = startedAtMs + 20 * MINUTE_MS,
            status = status,
            startedBy = TripStartCause.TRUCK,
            truckSeen = true,
            distanceMetres = metres,
            category = category,
            startAddress = from,
            endAddress = to,
            startAddressByHand = true,
            endAddressByHand = true,
        )
    }

    private fun totals(business: Long, personal: Long = 0, unsorted: Long = 0) = CategoryTotals(
        business = Tally(1, business),
        personal = Tally(1, personal),
        unsorted = Tally(1, unsorted),
    )

    // ---- The month's Business share ---------------------------------------------------------------

    @Test
    fun `the share is the Business kilometres out of all the month's kilometres`() {
        // The drawing's month: 214 km of Business in 240.4 km.
        assertEquals(89, businessPercent(totals(business = 2140, personal = 264)))
        assertEquals(50, businessPercent(totals(business = 100, personal = 100)))
    }

    @Test
    fun `kilometres that are not sorted yet count as not Business`() {
        assertEquals(25, businessPercent(totals(business = 100, personal = 100, unsorted = 200)))
    }

    @Test
    fun `a month without a kilometre has no share, and nothing is divided by zero`() {
        assertNull(businessPercent(totals(business = 0)))
        assertNull(
            homeTrips(today, edmonton, emptyList(), DistanceUnit.KILOMETRES).month.businessPercent,
        )
    }

    @Test
    fun `all Business is 100 and none is 0`() {
        assertEquals(100, businessPercent(totals(business = 2140)))
        assertEquals(0, businessPercent(totals(business = 0, personal = 264)))
    }

    @Test
    fun `a share is rounded to the nearest percent`() {
        assertEquals(33, businessPercent(totals(business = 1, personal = 2)))
        assertEquals(67, businessPercent(totals(business = 2, personal = 1)))
        assertEquals(13, businessPercent(totals(business = 1, personal = 7)))
    }

    @Test
    fun `a month with any other kilometre never reads 100, and one with any Business never 0`() {
        assertEquals(99, businessPercent(totals(business = 9_990, personal = 1)))
        assertEquals(1, businessPercent(totals(business = 1, personal = 9_990)))
    }

    // ---- Today and the month, from one reading ----------------------------------------------------

    @Test
    fun `with miles chosen today and the month are added up in miles, each trip as printed`() {
        val month =
            listOf(
                // 1.149 km is printed as 0.7 mi, three times: 2.1.
                trip("2026-10-01T08:00", 1_149.0),
                trip("2026-10-02T08:00", 1_149.0),
                trip("2026-10-06T08:00", 1_149.0),
                trip("2026-10-06T09:00", 12_300.0),
                trip("2026-10-06T10:00", 5_000.0, TripCategory.PERSONAL),
                trip("2026-10-03T08:00", 9_000.0, TripCategory.PERSONAL),
            )

        val figures = homeTrips(today, edmonton, month, DistanceUnit.MILES)

        assertEquals(DistanceUnit.MILES, figures.unit)
        // The month: 0.7 + 0.7 + 0.7 + 7.6 mi of Business.
        assertEquals(97L, figures.month.businessTenths)
        // Today: 0.7 + 7.6 mi of Business, and 3.1 mi Personal kept apart.
        assertEquals(Tally(2, 83), figures.todayTotals.business)
        assertEquals(Tally(1, 31), figures.todayTotals.personal)
        // The rows keep their metres: each is written from them, in the unit of the figures.
        assertEquals(listOf(5_000.0, 12_300.0, 1_149.0), figures.rows.map { it.distanceMetres })
        // The same reading in kilometres is what it was.
        val inKilometres = homeTrips(today, edmonton, month, DistanceUnit.KILOMETRES)
        assertEquals(156L, inKilometres.month.businessTenths)
        assertEquals(Tally(2, 134), inKilometres.todayTotals.business)
        assertEquals(DistanceUnit.KILOMETRES, inKilometres.unit)
    }

    @Test
    fun `the month's figure is its counted Business trips, each added as it is printed`() {
        val month =
            listOf(
                // 1.149 km is printed as 1.1, three times: 3.3, not the 3.4 the metres add up to.
                trip("2026-10-01T08:00", 1_149.0),
                trip("2026-10-02T08:00", 1_149.0),
                trip("2026-10-06T08:00", 1_149.0),
                trip("2026-10-03T08:00", 9_000.0, TripCategory.PERSONAL),
                trip("2026-10-04T08:00", 50_000.0, status = TripStatus.DELETED),
                trip("2026-10-05T08:00", 50_000.0, status = TripStatus.DISCARDED),
            )

        val figures = homeTrips(today, edmonton, month, DistanceUnit.KILOMETRES).month

        assertEquals(YearMonth.of(2026, 10), figures.month)
        assertEquals(33L, figures.businessTenths)
        assertEquals(27, figures.businessPercent)
    }

    @Test
    fun `today is the trips that started today, in the phone's time zone`() {
        val month =
            listOf(
                trip("2026-10-05T23:50", 5_000.0),
                trip("2026-10-06T00:00", 6_000.0),
                trip("2026-10-06T23:59", 7_000.0),
                trip("2026-10-07T00:00", 8_000.0),
            )

        val figures = homeTrips(today, edmonton, month, DistanceUnit.KILOMETRES)

        assertEquals(2, figures.today.count)
        assertEquals(130L, figures.today.totals(DistanceUnit.KILOMETRES).business.tenths)
        assertEquals(listOf(7_000.0, 6_000.0), figures.rows.map { it.distanceMetres })
    }

    @Test
    fun `today counts what the Trips screen counts, and keeps Personal apart`() {
        val month =
            listOf(
                trip("2026-10-06T08:00", 12_300.0),
                trip("2026-10-06T09:00", 5_000.0, TripCategory.PERSONAL),
                trip("2026-10-06T10:00", 9_900.0, status = TripStatus.OPEN),
                trip("2026-10-06T11:00", 9_900.0, status = TripStatus.DELETED),
            )

        val figures = homeTrips(today, edmonton, month, DistanceUnit.KILOMETRES)

        assertEquals(2, figures.today.count)
        assertEquals(Tally(1, 123), figures.today.totals(DistanceUnit.KILOMETRES).business)
        assertEquals(Tally(1, 50), figures.today.totals(DistanceUnit.KILOMETRES).personal)
        assertEquals(2, figures.rows.size)
    }

    // ---- Today's trips, the newest first ---------------------------------------------------------

    @Test
    fun `the first row is the newest finished trip of today, written as Trips writes it`() {
        val month =
            listOf(
                trip("2026-10-06T07:42", 18_600.0, from = "Shop", to = "Windermere site"),
                trip("2026-10-06T12:40", 20_300.0, from = "Supplier", to = "Shop"),
                trip("2026-10-06T13:30", 4_000.0, status = TripStatus.OPEN),
                trip("2026-10-05T17:00", 9_000.0, from = "Shop", to = "Home"),
            )

        val last = homeTrips(today, edmonton, month, DistanceUnit.KILOMETRES).rows.firstOrNull()

        assertEquals(
            PlacesText.FromTo(PlaceSide.Address("Supplier"), PlaceSide.Address("Shop")),
            last?.places,
        )
        assertEquals(at("2026-10-06T12:40"), last?.startedAtMs)
        assertEquals(20_300.0, last?.distanceMetres)
        assertEquals(TripCategory.BUSINESS, last?.category)
    }

    @Test
    fun `a trip without addresses says so in words`() {
        val unknown = trip("2026-10-06T12:40", 20_300.0)

        val rows = homeTrips(today, edmonton, listOf(unknown), DistanceUnit.KILOMETRES).rows

        assertEquals(
            PlacesText.Sentence(R.string.trips_addresses_left_blank),
            rows.single().places,
        )
    }

    @Test
    fun `before the first finished trip of today there is no row`() {
        val month =
            listOf(
                trip("2026-10-05T17:00", 9_000.0),
                trip("2026-10-06T08:00", 0.0, status = TripStatus.OPEN),
            )

        val figures = homeTrips(today, edmonton, month, DistanceUnit.KILOMETRES)

        assertTrue(figures.rows.isEmpty())
        assertEquals(0, figures.today.count)
        assertEquals(90L, figures.month.businessTenths)
    }

    // ---- Where the trip in progress started -------------------------------------------------------

    private val open =
        CurrentTrip(
            tripId = 7,
            startedAtMs = at("2026-10-06T14:14"),
            startedBy = TripStartCause.TRUCK,
            distanceMetres = 12_400.0,
            waitingForTruck = false,
        )

    private fun start(tripId: Long, place: TripPlace) = OpenTripStart(tripId, 53.5, -113.5, place)

    @Test
    fun `the start address is shown once the lookup knows it for this trip`() {
        assertEquals("Shop", startAddressOf(open, start(7, TripPlace.Known("Shop"))))
    }

    @Test
    fun `no start line is written from a guess`() {
        assertNull(startAddressOf(open, null))
        assertNull(startAddressOf(open, start(7, TripPlace.LookingUp)))
        assertNull(startAddressOf(open, start(7, TripPlace.NotFound)))
        // What the lookup still holds for the trip before this one.
        assertNull(startAddressOf(open, start(6, TripPlace.Known("Shop"))))
        assertNull(startAddressOf(null, start(7, TripPlace.Known("Shop"))))
    }

    // ---- The minutes of a trip in progress --------------------------------------------------------

    @Test
    fun `the clock is looked at again when the trip has run another whole minute`() {
        assertEquals(60_000L, msUntilNextMinute(0))
        assertEquals(45_000L, msUntilNextMinute(15_000))
        assertEquals(30_000L, msUntilNextMinute(18 * MINUTE_MS + 30_000))
    }

    @Test
    fun `it is never looked at more than once a second, nor less than once a minute`() {
        assertEquals(1_000L, msUntilNextMinute(59_999))
        // A start that lies in the future: the phone's clock was set back during the trip.
        assertEquals(15_000L, msUntilNextMinute(-15_000))
        assertEquals(60_000L, msUntilNextMinute(-120_000))
    }
}

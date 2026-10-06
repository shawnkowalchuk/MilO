package com.shawnkowalchuk.milo.feature.trips

import com.shawnkowalchuk.milo.core.schedule.TripCategory
import com.shawnkowalchuk.milo.core.trip.TripStartCause
import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.data.trip.Trip
import com.shawnkowalchuk.milo.platform.address.MAX_ADDRESS_ATTEMPTS
import com.shawnkowalchuk.milo.platform.address.OpenTripStart
import com.shawnkowalchuk.milo.platform.address.TripPlace
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val MINUTE_MS = 60_000L

/**
 * The Trips screen's month logic: what is counted, how the trips are grouped, and which month
 * may be shown. Where a month begins and ends is tested in `core/util/TimeSpanTest`.
 */
class TripMonthTest {
    private val edmonton = ZoneId.of("America/Edmonton")
    private var nextId = 1L

    /** A trip that started at [startUtc] and, unless it is open, ended [minutes] later. */
    private fun trip(
        startUtc: String,
        metres: Double,
        status: TripStatus = TripStatus.FINISHED,
        minutes: Long = 20,
    ): Trip {
        val startedAtMs = Instant.parse(startUtc).toEpochMilli()
        return Trip(
            id = nextId++,
            startedAtMs = startedAtMs,
            endedAtMs = if (status == TripStatus.OPEN) null else startedAtMs + minutes * MINUTE_MS,
            status = status,
            startedBy = TripStartCause.TRUCK,
            truckSeen = true,
            distanceMetres = if (status == TripStatus.OPEN) 0.0 else metres,
            // All Business: mixed months are in TripMonthCategoryTest.
            category = TripCategory.BUSINESS.takeIf { status != TripStatus.OPEN },
        )
    }

    private fun summary(
        trips: List<Trip>,
        showLeftOut: Boolean = false,
        liveTripId: Long? = null,
        liveDistanceMetres: Double? = null,
        liveStart: OpenTripStart? = null,
    ): MonthSummary =
        monthSummary(trips, edmonton, showLeftOut, liveTripId, liveDistanceMetres, liveStart)

    // ---- Totals -----------------------------------------------------------------------------------

    @Test
    fun `the total and the count are of the finished trips only`() {
        val trips =
            listOf(
                trip("2026-10-05T14:00:00Z", metres = 12_300.0),
                trip("2026-10-05T20:00:00Z", metres = 8_200.0),
                // Moved the truck in the yard: under the minimum distance.
                trip("2026-10-06T14:00:00Z", metres = 120.0, status = TripStatus.DISCARDED),
                trip("2026-10-06T15:00:00Z", metres = 0.0, status = TripStatus.OPEN),
            )

        val month = summary(trips)

        assertEquals(20_500.0, month.totals.business.metres, 0.0)
        assertEquals(2, month.tripCount)
    }

    @Test
    fun `showing the discarded trips changes the list and not the totals`() {
        val counted = trip("2026-10-05T14:00:00Z", metres = 12_300.0)
        val discarded = trip("2026-10-05T16:00:00Z", metres = 250.0, status = TripStatus.DISCARDED)

        val hidden = summary(listOf(counted, discarded), showLeftOut = false)
        val shown = summary(listOf(counted, discarded), showLeftOut = true)

        assertEquals(listOf(counted.id), hidden.days.single().trips.map { it.id })
        assertEquals(1, hidden.hiddenLeftOut)

        // Listed newest first, and marked so it is not taken for a counted trip.
        assertEquals(
            listOf(discarded.id to TripKind.DISCARDED, counted.id to TripKind.COUNTED),
            shown.days.single().trips.map { it.id to it.kind },
        )
        assertEquals(0, shown.hiddenLeftOut)

        for (month in listOf(hidden, shown)) {
            assertEquals(12_300.0, month.totals.business.metres, 0.0)
            assertEquals(1, month.tripCount)
        }
    }

    @Test
    fun `a month with only discarded trips is not empty once they are shown`() {
        val onlyDiscarded =
            listOf(trip("2026-10-05T14:00:00Z", metres = 90.0, status = TripStatus.DISCARDED))

        val hidden = summary(onlyDiscarded, showLeftOut = false)
        val shown = summary(onlyDiscarded, showLeftOut = true)

        assertTrue(hidden.isEmpty)
        assertEquals(1, hidden.hiddenLeftOut)
        assertFalse(shown.isEmpty)
        assertEquals(0, shown.tripCount)
    }

    @Test
    fun `a month without trips is empty`() {
        val month = summary(emptyList())

        assertTrue(month.isEmpty)
        assertEquals(0.0, month.totals.business.metres, 0.0)
        assertEquals(0, month.tripCount)
        assertEquals(0, month.hiddenLeftOut)
    }

    // ---- Grouping ---------------------------------------------------------------------------------

    @Test
    fun `trips are grouped by day, newest day first and newest trip first within a day`() {
        val mondayMorning = trip("2026-10-05T14:00:00Z", metres = 1_000.0)
        val mondayAfternoon = trip("2026-10-05T21:00:00Z", metres = 2_000.0)
        val wednesday = trip("2026-10-07T15:00:00Z", metres = 3_000.0)
        val friday = trip("2026-10-02T15:00:00Z", metres = 4_000.0)

        // Handed over in no particular order.
        val month = summary(listOf(mondayAfternoon, friday, wednesday, mondayMorning))

        assertEquals(
            listOf(
                LocalDate.of(2026, 10, 7) to listOf(wednesday.id),
                LocalDate.of(2026, 10, 5) to listOf(mondayAfternoon.id, mondayMorning.id),
                LocalDate.of(2026, 10, 2) to listOf(friday.id),
            ),
            month.days.map { day -> day.date to day.trips.map { it.id } },
        )
    }

    @Test
    fun `a trip belongs to the local day it started on`() {
        // 23:30 on 5 October in Edmonton is 05:30 on 6 October in UTC.
        val lateEvening = trip("2026-10-06T05:30:00Z", metres = 5_000.0)

        val month = summary(listOf(lateEvening))

        assertEquals(LocalDate.of(2026, 10, 5), month.days.single().date)
    }

    @Test
    fun `a trip that runs past midnight stays whole, under the day it started on`() {
        // Starts 23:50 on the 5th and ends 00:30 on the 6th, local time.
        val overMidnight = trip("2026-10-06T05:50:00Z", metres = 30_000.0, minutes = 40)

        val month = summary(listOf(overMidnight))

        assertEquals(listOf(LocalDate.of(2026, 10, 5)), month.days.map { it.date })
        assertEquals(30_000.0, month.totals.business.metres, 0.0)
    }

    @Test
    fun `on the day the clocks go back, both 1 o'clock hours are the same day`() {
        // 1 November 2026 in Edmonton: 01:30 summer time, then 01:30 winter time an hour later.
        val beforeTheChange = trip("2026-11-01T07:30:00Z", metres = 1_000.0)
        val afterTheChange = trip("2026-11-01T08:30:00Z", metres = 2_000.0)

        val month = summary(listOf(beforeTheChange, afterTheChange))

        val day = month.days.single()
        assertEquals(LocalDate.of(2026, 11, 1), day.date)
        // Ordered by when they really happened, not by the time on the clock, which is equal.
        assertEquals(listOf(afterTheChange.id, beforeTheChange.id), day.trips.map { it.id })
    }

    // ---- The trip in progress ---------------------------------------------------------------------

    @Test
    fun `a trip in progress is set apart, with its running distance, and not counted`() {
        val finished = trip("2026-10-05T14:00:00Z", metres = 12_300.0)
        val open = trip("2026-10-05T20:00:00Z", metres = 0.0, status = TripStatus.OPEN)

        val month =
            summary(listOf(finished, open), liveTripId = open.id, liveDistanceMetres = 3_400.0)

        val inProgress = checkNotNull(month.inProgress)
        assertEquals(open.id, inProgress.id)
        assertEquals(TripKind.IN_PROGRESS, inProgress.kind)
        assertEquals(3_400.0, inProgress.distanceMetres)
        assertNull(inProgress.endedAtMs)
        // Not among the day's trips, and not in the total until it ends.
        assertEquals(listOf(finished.id), month.days.single().trips.map { it.id })
        assertEquals(12_300.0, month.totals.business.metres, 0.0)
        assertEquals(1, month.tripCount)
        assertFalse(month.isEmpty)
    }

    @Test
    fun `a running distance is shown only for the trip it belongs to`() {
        val open = trip("2026-10-05T20:00:00Z", metres = 0.0, status = TripStatus.OPEN)

        val anotherTripIsLive =
            summary(listOf(open), liveTripId = open.id + 1, liveDistanceMetres = 9_999.0)
        val nothingIsLive = summary(listOf(open))

        // No figure, rather than another trip's figure or the 0 the stored row holds.
        assertNull(anotherTripIsLive.inProgress?.distanceMetres)
        assertNull(nothingIsLive.inProgress?.distanceMetres)
    }

    // ---- Where the trips went ---------------------------------------------------------------------

    @Test
    fun `a finished trip's line carries what its row says about both ends`() {
        val found =
            trip("2026-10-05T14:00:00Z", metres = 12_300.0)
                .copy(startAddress = "12 Shop Rd, Edmonton", endAddress = "48 Main St, Leduc")
        val waiting =
            trip("2026-10-05T16:00:00Z", metres = 8_200.0)
                .copy(startLatitude = 53.5, startLongitude = -113.5)
        val givenUp = waiting.copy(id = nextId++, addressAttempts = MAX_ADDRESS_ATTEMPTS)

        val lines = summary(listOf(found, waiting, givenUp)).days.single().trips

        val byId = lines.associateBy { it.id }
        assertEquals(TripPlace.Known("12 Shop Rd, Edmonton"), byId.getValue(found.id).from)
        assertEquals(TripPlace.Known("48 Main St, Leduc"), byId.getValue(found.id).to)
        assertEquals(TripPlace.LookingUp, byId.getValue(waiting.id).from)
        assertEquals(TripPlace.NotFound, byId.getValue(givenUp.id).from)
        // No position was stored for the end of either: there is nothing to look up.
        assertEquals(TripPlace.NotFound, byId.getValue(waiting.id).to)
    }

    @Test
    fun `a discarded trip's line says nothing about places`() {
        val discarded =
            trip("2026-10-05T14:00:00Z", metres = 120.0, status = TripStatus.DISCARDED)
                .copy(startLatitude = 53.5, startLongitude = -113.5)

        val line = summary(listOf(discarded), showLeftOut = true).days.single().trips.single()

        assertNull(line.from)
        assertNull(line.to)
    }

    @Test
    fun `a trip in progress shows the start the lookup found for it, and for no other trip`() {
        val open = trip("2026-10-05T14:00:00Z", metres = 0.0, status = TripStatus.OPEN)
        val start = OpenTripStart(open.id, 53.5, -113.5, TripPlace.Known("12 Shop Rd, Edmonton"))

        val own = summary(listOf(open), liveStart = start).inProgress
        val stale = summary(listOf(open), liveStart = start.copy(tripId = open.id + 1)).inProgress
        val none = summary(listOf(open)).inProgress

        assertEquals(TripPlace.Known("12 Shop Rd, Edmonton"), own?.from)
        assertNull(stale?.from)
        assertNull(none?.from)
        assertNull(own?.to)
    }

    // ---- Which month may be shown -----------------------------------------------------------------

    @Test
    fun `stepping back goes one month at a time, across the turn of the year`() {
        val current = YearMonth.of(2026, 2)

        val january = stepMonth(current, months = -1, current = current)
        val december = stepMonth(january, months = -1, current = current)

        assertEquals(YearMonth.of(2026, 1), january)
        assertEquals(YearMonth.of(2025, 12), december)
    }

    @Test
    fun `stepping forward stops at the current month`() {
        val current = YearMonth.of(2026, 10)

        assertEquals(current, stepMonth(YearMonth.of(2026, 9), months = 1, current = current))
        // Already there: a month that has not begun is never shown.
        assertEquals(current, stepMonth(current, months = 1, current = current))
        assertEquals(current, stepMonth(current, months = 12, current = current))
    }

    @Test
    fun `the way forward is offered only from an earlier month`() {
        val current = YearMonth.of(2027, 1)

        assertFalse(canStepForward(current, current))
        assertTrue(canStepForward(YearMonth.of(2026, 12), current))
        assertTrue(canStepForward(YearMonth.of(2025, 1), current))
    }
}

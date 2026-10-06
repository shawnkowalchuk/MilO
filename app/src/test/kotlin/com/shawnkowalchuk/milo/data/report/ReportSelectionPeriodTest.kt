package com.shawnkowalchuk.milo.data.report

import com.shawnkowalchuk.milo.core.report.ReportPeriod
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Where a report's period begins and ends: the edges of a month and of a date range, the
 * phone's time zone, and the two nights a year on which the clocks change.
 */
class ReportSelectionPeriodTest {
    private val october = ReportPeriod.Month(YearMonth.of(2026, 10))

    @Test
    fun `a month takes the trips from its first instant to the last before the next month`() {
        val trips =
            listOf(
                storedTrip("2026-09-30T23:59:59"),
                storedTrip("2026-10-01T00:00"),
                storedTrip("2026-10-31T23:59:59"),
                storedTrip("2026-11-01T00:00"),
            )

        val selection = selectForReport(trips, october, EDMONTON)

        assertEquals(listOf(at("2026-10-01T00:00"), at("2026-10-31T23:59:59")), starts(selection))
    }

    @Test
    fun `only the start decides, so a trip past the end of the period is on it whole`() {
        // It started at 23:50 on the 31st and ended in November: October's, with all its km.
        val acrossTheEnd = storedTrip("2026-10-31T23:50", metres = 30_000.0)
        // It started in September and ran into October: September's.
        val acrossTheStart = storedTrip("2026-09-30T23:50", metres = 30_000.0)

        val selection = selectForReport(listOf(acrossTheEnd, acrossTheStart), october, EDMONTON)

        assertEquals(listOf(acrossTheEnd.startedAtMs), starts(selection))
        assertEquals(30_000.0, selection.trips.single().distanceMetres, 0.0)
    }

    @Test
    fun `a date range includes both its days, to the last instant of the last one`() {
        val range = ReportPeriod.Range(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 18))
        val trips =
            listOf(
                storedTrip("2026-10-04T23:59:59"),
                storedTrip("2026-10-05T00:00"),
                storedTrip("2026-10-12T12:00"),
                storedTrip("2026-10-18T23:59:59"),
                storedTrip("2026-10-19T00:00"),
            )

        val selection = selectForReport(trips, range, EDMONTON)

        assertEquals(
            listOf(at("2026-10-05T00:00"), at("2026-10-12T12:00"), at("2026-10-18T23:59:59")),
            starts(selection),
        )
    }

    @Test
    fun `a range of one day takes that day's trips and no other`() {
        val day = LocalDate.of(2026, 10, 5)
        val trips =
            listOf(
                storedTrip("2026-10-04T22:00"),
                storedTrip("2026-10-05T08:00"),
                storedTrip("2026-10-06T08:00"),
            )

        val selection = selectForReport(trips, ReportPeriod.Range(day, day), EDMONTON)

        assertEquals(listOf(at("2026-10-05T08:00")), starts(selection))
    }

    @Test
    fun `the period is measured in the phone's time zone, not in UTC`() {
        // 22:30 on 30 September in Edmonton is 04:30 on 1 October in UTC, and 00:30 on 1
        // October in Toronto.
        val lateInSeptember = storedTrip("2026-09-30T22:30")
        // 22:30 on 31 October in Edmonton is already November in both of the others.
        val lateInOctober = storedTrip("2026-10-31T22:30")
        val trips = listOf(lateInSeptember, lateInOctober)

        val inEdmonton = selectForReport(trips, october, EDMONTON)
        val inUtc = selectForReport(trips, october, ZoneId.of("UTC"))
        val inToronto = selectForReport(trips, october, ZoneId.of("America/Toronto"))

        assertEquals(listOf(lateInOctober.startedAtMs), starts(inEdmonton))
        assertEquals(listOf(lateInSeptember.startedAtMs), starts(inUtc))
        assertEquals(listOf(lateInSeptember.startedAtMs), starts(inToronto))
    }

    @Test
    fun `the night the clocks go back, a trip in the repeated hour belongs to its day`() {
        // Sunday 1 November 2026: 02:00 summer time becomes 01:00 winter time, so 01:30 comes
        // twice. Both are 1 November, and the day is 25 hours long.
        val first = at("2026-11-01T00:30") + 60 * 60_000
        val second = first + 60 * 60_000
        val lastOfTheDay = at("2026-11-01T23:59:59")
        val nextDay = at("2026-11-02T00:00")
        val trips =
            listOf(first, second, lastOfTheDay, nextDay).map {
                storedTrip("2026-11-01T00:00").copy(startedAtMs = it)
            }
        val sunday = LocalDate.of(2026, 11, 1)

        val selection = selectForReport(trips, ReportPeriod.Range(sunday, sunday), EDMONTON)

        assertEquals(listOf(first, second, lastOfTheDay), starts(selection))
        // A day's worth of milliseconds counted from midnight would have ended an hour early.
        assertEquals(25 * 60 * 60_000L, nextDay - at("2026-11-01T00:00"))
    }

    @Test
    fun `the night the clocks go forward, the short day ends at its own midnight`() {
        // Sunday 8 March 2026 is 23 hours long.
        val sunday = LocalDate.of(2026, 3, 8)
        val lastOfTheDay = storedTrip("2026-03-08T23:30")
        val nextDay = storedTrip("2026-03-09T00:00")
        // 23 hours after Sunday's midnight is Monday already, though less than a day has passed.
        assertEquals(23 * 60 * 60_000L, nextDay.startedAtMs - at("2026-03-08T00:00"))

        val day =
            selectForReport(
                listOf(lastOfTheDay, nextDay),
                ReportPeriod.Range(sunday, sunday),
                EDMONTON,
            )
        val march =
            selectForReport(
                listOf(lastOfTheDay, nextDay),
                ReportPeriod.Month(YearMonth.of(2026, 3)),
                EDMONTON,
            )

        assertEquals(listOf(lastOfTheDay.startedAtMs), starts(day))
        assertEquals(2, march.trips.size)
    }

    @Test
    fun `November is an hour longer than its days, and a trip in its last hour is November's`() {
        val november = ReportPeriod.Month(YearMonth.of(2026, 11))
        // Winter time by then: 23:30 on the 30th is 06:30 UTC on 1 December.
        val lastHour = storedTrip("2026-11-30T23:30")
        val december = storedTrip("2026-12-01T00:00")

        val selection = selectForReport(listOf(lastHour, december), november, EDMONTON)

        assertEquals(listOf(lastHour.startedAtMs), starts(selection))
    }
}

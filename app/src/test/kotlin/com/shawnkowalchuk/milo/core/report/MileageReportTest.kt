package com.shawnkowalchuk.milo.core.report

import com.shawnkowalchuk.milo.core.util.formatTenths
import com.shawnkowalchuk.milo.core.util.metresOfTenths
import com.shawnkowalchuk.milo.core.util.tenthsOfAKilometre
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * The figures of the report: what a trip is rounded to, and that every subtotal and the total
 * are the sums of the figures printed above them.
 */
class MileageReportTest {
    private val monday = LocalDate.of(2026, 10, 5)
    private val tuesday = LocalDate.of(2026, 10, 6)

    @Test
    fun `a trip is rounded to the nearest tenth of a kilometre`() {
        assertEquals(123, tenthsOfAKilometre(12_340.0))
        assertEquals(123, tenthsOfAKilometre(12_349.9))
        assertEquals(124, tenthsOfAKilometre(12_350.0))
        assertEquals(0, tenthsOfAKilometre(49.9))
        assertEquals(1, tenthsOfAKilometre(50.0))
        assertEquals(0, tenthsOfAKilometre(0.0))
    }

    @Test
    fun `a distance that cannot be a distance is refused, not printed`() {
        for (corrupt in listOf(-1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) { tenthsOfAKilometre(corrupt) }
        }
    }

    @Test
    fun `tenths are written with one decimal, and with the language's own separator`() {
        assertEquals("12.3", formatTenths(123, Locale.CANADA))
        assertEquals("0.0", formatTenths(0, Locale.CANADA))
        assertEquals("0.4", formatTenths(4, Locale.CANADA))
        assertEquals("1234.5", formatTenths(12_345, Locale.CANADA))
        assertEquals("12,3", formatTenths(123, Locale.GERMANY))
        // The CSV's form: a dot, whatever the phone's language.
        assertEquals("12.3", formatTenths(123, Locale.ROOT))
    }

    @Test
    fun `a day's subtotal is the sum of the figures printed for its trips`() {
        // Three trips of 1.04 km print as 1.0 each. Their metres add up to 3.12 km, which would
        // print as 3.1; the report says 3.0, the sum of what stands in the column.
        val starts = listOf("08:00", "09:00", "10:00")
        val day = ReportDay(monday, starts.map { trip(monday, it, metres = 1_040.0) })

        assertEquals(listOf(10L, 10L, 10L), day.trips.map { it.tenths })
        assertEquals(30, day.tenths)
    }

    @Test
    fun `the total is the sum of the subtotals, and so of every figure printed`() {
        val trips =
            listOf(
                trip(monday, "08:00", metres = 1_260.0),
                trip(monday, "09:00", metres = 1_260.0),
                trip(tuesday, "08:00", metres = 23_449.0),
                trip(tuesday, "13:00", metres = 150.0),
            )
        val report = report(trips)

        // 1.3 + 1.3, and 23.4 + 0.2 (150 m rounds up).
        assertEquals(listOf(26L, 236L), report.days.map { it.tenths })
        assertEquals(262, report.totalTenths)
        assertEquals(report.days.sumOf { day -> day.trips.sumOf { it.tenths } }, report.totalTenths)
        assertEquals(4, report.tripCount)
    }

    @Test
    fun `a report without trips adds up to nothing`() {
        val empty = report(emptyList())

        assertEquals(0, empty.totalTenths)
        assertEquals(0, empty.tripCount)
        assertEquals(emptyList<ReportDay>(), empty.days)
    }

    @Test
    fun `tenths go back to metres as a whole number of hundred metres`() {
        assertEquals(412_300.0, metresOfTenths(4123), 0.0)
        assertEquals(4123, tenthsOfAKilometre(metresOfTenths(4123)))
    }

    @Test
    fun `days are oldest first, and a day's trips in the order they started`() {
        val trips =
            listOf(
                trip(tuesday, "13:00"),
                trip(monday, "15:30"),
                trip(tuesday, "08:00"),
                trip(monday, "08:14"),
            )

        val days = reportDays(trips, EDMONTON)

        assertEquals(listOf(monday, tuesday), days.map { it.date })
        assertEquals(
            listOf(edmonton("2026-10-05T08:14"), edmonton("2026-10-05T15:30")),
            days[0].trips.map { it.startedAtMs },
        )
        assertEquals(
            listOf(edmonton("2026-10-06T08:00"), edmonton("2026-10-06T13:00")),
            days[1].trips.map { it.startedAtMs },
        )
    }

    @Test
    fun `a trip past midnight is listed once, under the day it started`() {
        val late = trip(monday, "23:40", minutes = 45)

        val days = reportDays(listOf(late), EDMONTON)

        assertEquals(listOf(monday), days.map { it.date })
        assertEquals(1, days.single().trips.size)
    }

    @Test
    fun `the day a trip belongs to is worked out in the report's time zone`() {
        // 23:30 on Monday in Edmonton is already Tuesday in UTC and in Toronto.
        val late = trip(monday, "23:30")

        assertEquals(monday, reportDays(listOf(late), EDMONTON).single().date)
        assertEquals(tuesday, reportDays(listOf(late), ZoneId.of("UTC")).single().date)
        assertEquals(tuesday, reportDays(listOf(late), ZoneId.of("America/Toronto")).single().date)
    }

    @Test
    fun `the trips that carry a mark are counted, whichever mark it is`() {
        val trips =
            listOf(
                trip(monday, "08:00"),
                trip(monday, "09:00", mark = ReportMark.ADDED),
                trip(tuesday, "08:00", mark = ReportMark.EDITED),
            )

        assertEquals(2, report(trips).markedCount)
        assertEquals(0, report(trips.take(1)).markedCount)
    }
}

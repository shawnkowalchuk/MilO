package com.shawnkowalchuk.milo.core.report

import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

private const val HOUR_MS = 60L * 60 * 1000
private const val DAY_MS = 24 * HOUR_MS

/** What a period covers, as days and as stored time, and how it is written. */
class ReportPeriodTest {
    private val to = "%1\$s to %2\$s"

    @Test
    fun `a month covers its first day to its last`() {
        val october = ReportPeriod.Month(OCTOBER)
        val february = ReportPeriod.Month(YearMonth.of(2028, 2))

        assertEquals(LocalDate.of(2026, 10, 1), october.firstDay)
        assertEquals(LocalDate.of(2026, 10, 31), october.lastDay)
        // A leap year.
        assertEquals(LocalDate.of(2028, 2, 29), february.lastDay)
    }

    @Test
    fun `a month's span is the Trips screen's own span of that month`() {
        val span = ReportPeriod.Month(OCTOBER).span(EDMONTON)

        // Summer time: local midnight is 06:00 UTC.
        assertEquals(utc("2026-10-01T06:00:00Z"), span.fromMs)
        assertEquals(utc("2026-11-01T06:00:00Z"), span.untilMs)
    }

    @Test
    fun `a range includes its last day to the last millisecond, and nothing of the next`() {
        val range = ReportPeriod.Range(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 18))
        val span = range.span(EDMONTON)

        assertEquals(edmonton("2026-10-05T00:00"), span.fromMs)
        assertEquals(edmonton("2026-10-19T00:00"), span.untilMs)
        assertEquals(14 * DAY_MS, span.untilMs - span.fromMs)
    }

    @Test
    fun `a range of one day is that whole day`() {
        val day = LocalDate.of(2026, 10, 5)
        val span = ReportPeriod.Range(day, day).span(EDMONTON)

        assertEquals(DAY_MS, span.untilMs - span.fromMs)
    }

    @Test
    fun `a range over a clock change is as long as it really was`() {
        // The clocks go back at 02:00 on Sunday 1 November 2026, and forward on 8 March.
        val autumn = ReportPeriod.Range(LocalDate.of(2026, 10, 31), LocalDate.of(2026, 11, 1))
        val spring = ReportPeriod.Range(LocalDate.of(2026, 3, 8), LocalDate.of(2026, 3, 8))

        val autumnSpan = autumn.span(EDMONTON)
        val springSpan = spring.span(EDMONTON)
        assertEquals(2 * DAY_MS + HOUR_MS, autumnSpan.untilMs - autumnSpan.fromMs)
        assertEquals(DAY_MS - HOUR_MS, springSpan.untilMs - springSpan.fromMs)
        // It ends at local midnight in winter time, an hour later in UTC than in summer.
        assertEquals(utc("2026-11-02T07:00:00Z"), autumnSpan.untilMs)
    }

    @Test
    fun `a range cannot run backwards`() {
        assertThrows(IllegalArgumentException::class.java) {
            ReportPeriod.Range(LocalDate.of(2026, 10, 18), LocalDate.of(2026, 10, 5))
        }
    }

    @Test
    fun `a range that covers a whole month is still not that month`() {
        val month: ReportPeriod = ReportPeriod.Month(OCTOBER)
        val sameDays: ReportPeriod = ReportPeriod.Range(month.firstDay, month.lastDay)

        // The same span of time, and two different periods: only the first marks a month as
        // submitted.
        assertEquals(month.span(EDMONTON), sameDays.span(EDMONTON))
        assertNotEquals(month, sameDays)
    }

    @Test
    fun `a month is written as its name and year`() {
        assertEquals("October 2026", periodInWords(ReportPeriod.Month(OCTOBER), Locale.CANADA, to))
    }

    @Test
    fun `a range is written as its two dates, joined by the words it is handed`() {
        val range = ReportPeriod.Range(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 18))
        val overNewYear = ReportPeriod.Range(LocalDate.of(2026, 12, 28), LocalDate.of(2027, 1, 3))

        assertEquals(
            "October 5, 2026 to October 18, 2026",
            periodInWords(range, Locale.CANADA, to),
        )
        assertEquals(
            "December 28, 2026 to January 3, 2027",
            periodInWords(overNewYear, Locale.CANADA, to),
        )
    }

    @Test
    fun `a range of one day is written as that one date`() {
        val day = LocalDate.of(2026, 10, 5)

        assertEquals(
            "October 5, 2026",
            periodInWords(ReportPeriod.Range(day, day), Locale.CANADA, to),
        )
    }
}

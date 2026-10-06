package com.shawnkowalchuk.milo.feature.report

import com.shawnkowalchuk.milo.core.report.ReportPeriod
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which period the Report screen is set to, and what each press makes of it. */
class ReportChoiceTest {
    private val today = LocalDate.of(2026, 10, 6)
    private val october = YearMonth.of(2026, 10)
    private val september = YearMonth.of(2026, 9)

    @Test
    fun `the screen opens on the whole month the Trips screen was showing`() {
        val choice = openingChoice(september, today)

        assertEquals(PeriodKind.MONTH, choice.kind)
        assertEquals(ReportPeriod.Month(september), choice.period)
    }

    @Test
    fun `a date range starts from that month's days`() {
        val choice = openingChoice(september, today).copy(kind = PeriodKind.RANGE)

        assertEquals(
            ReportPeriod.Range(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)),
            choice.period,
        )
    }

    @Test
    fun `in the current month the range ends today, not on a day that has not come`() {
        val choice = openingChoice(october, today).copy(kind = PeriodKind.RANGE)

        assertEquals(ReportPeriod.Range(LocalDate.of(2026, 10, 1), today), choice.period)
        // The month itself is still the whole month.
        assertEquals(ReportPeriod.Month(october), choice.copy(kind = PeriodKind.MONTH).period)
    }

    @Test
    fun `a month that has not begun is brought back to the current one`() {
        assertEquals(october, openingChoice(YearMonth.of(2027, 1), today).month)
    }

    @Test
    fun `stepping goes one month at a time, back without limit and never past the current`() {
        val current = openingChoice(october, today)

        val back = current.steppedMonth(-1, today)
        assertEquals(september, back.month)
        assertEquals(YearMonth.of(2025, 10), current.steppedMonth(-12, today).month)
        assertEquals(october, back.steppedMonth(1, today).month)
        assertEquals(october, current.steppedMonth(1, today).month)
        assertFalse(current.canStepForward(today))
        assertTrue(back.canStepForward(today))
    }

    @Test
    fun `stepping the month takes the range along, and keeps which of the two is chosen`() {
        val ranging = openingChoice(october, today).copy(kind = PeriodKind.RANGE)

        val back = ranging.steppedMonth(-1, today)

        assertEquals(PeriodKind.RANGE, back.kind)
        assertEquals(LocalDate.of(2026, 9, 1), back.rangeFirst)
        assertEquals(LocalDate.of(2026, 9, 30), back.rangeLast)
    }

    @Test
    fun `changing a day of the range leaves the month where it is`() {
        val choice =
            openingChoice(september, today)
                .withRangeFirst(LocalDate.of(2026, 8, 20), today)
                .withRangeLast(LocalDate.of(2026, 10, 3), today)

        assertEquals(september, choice.month)
        assertEquals(LocalDate.of(2026, 8, 20), choice.rangeFirst)
        assertEquals(LocalDate.of(2026, 10, 3), choice.rangeLast)
    }

    @Test
    fun `a first day after the last takes the last along, so the range never runs backwards`() {
        val choice = openingChoice(september, today)

        val moved = choice.withRangeFirst(LocalDate.of(2026, 10, 2), today)

        assertEquals(LocalDate.of(2026, 10, 2), moved.rangeFirst)
        assertEquals(LocalDate.of(2026, 10, 2), moved.rangeLast)
        // A period can always be made of it.
        assertEquals(moved.rangeFirst, moved.copy(kind = PeriodKind.RANGE).period.firstDay)
    }

    @Test
    fun `a last day before the first takes the first along`() {
        val choice = openingChoice(september, today)

        val moved = choice.withRangeLast(LocalDate.of(2026, 8, 15), today)

        assertEquals(LocalDate.of(2026, 8, 15), moved.rangeFirst)
        assertEquals(LocalDate.of(2026, 8, 15), moved.rangeLast)
    }

    @Test
    fun `no day of the range is after today`() {
        val tomorrow = today.plusDays(1)
        val choice = openingChoice(september, today)

        assertEquals(today, choice.withRangeLast(tomorrow, today).rangeLast)
        val both = choice.withRangeFirst(tomorrow, today)
        assertEquals(today, both.rangeFirst)
        assertEquals(today, both.rangeLast)
    }

    @Test
    fun `a range within one day is that day`() {
        val day = LocalDate.of(2026, 9, 14)
        val choice =
            openingChoice(september, today)
                .copy(kind = PeriodKind.RANGE)
                .withRangeFirst(day, today)
                .withRangeLast(day, today)

        assertEquals(ReportPeriod.Range(day, day), choice.period)
    }
}

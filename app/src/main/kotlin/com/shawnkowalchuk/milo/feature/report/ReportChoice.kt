package com.shawnkowalchuk.milo.feature.report

import com.shawnkowalchuk.milo.core.report.ReportPeriod
import java.time.LocalDate
import java.time.YearMonth

// Which period the Report screen is set to, and what each press makes of it. Pure values and
// functions, so they are tested without a phone.

/** The two kinds of period a report can cover. */
enum class PeriodKind { MONTH, RANGE }

/**
 * What Shawn has chosen on the Report screen.
 *
 * Both a month and a range are kept at all times, whichever of the two is in force, so that
 * switching to "A date range" and back does not lose either.
 *
 * @param rangeFirst and [rangeLast] are the two days of the range, both inside it. The first is
 * never after the last.
 */
data class ReportChoice(
    val kind: PeriodKind,
    val month: YearMonth,
    val rangeFirst: LocalDate,
    val rangeLast: LocalDate,
) {
    /** The period a report made now would cover. */
    val period: ReportPeriod
        get() = when (kind) {
            PeriodKind.MONTH -> ReportPeriod.Month(month)
            PeriodKind.RANGE -> ReportPeriod.Range(rangeFirst, rangeLast)
        }
}

/**
 * The choice the screen opens with: the whole of [month], the month the Trips screen was
 * showing. The range that "A date range" starts from is that month's days.
 *
 * @param today no report reaches past it: a day that has not come has no trips. A month that
 * has not begun is brought back to the current one.
 */
fun openingChoice(month: YearMonth, today: LocalDate): ReportChoice {
    val shown = minOf(month, YearMonth.from(today))
    return ReportChoice(
        kind = PeriodKind.MONTH,
        month = shown,
        rangeFirst = shown.atDay(1),
        rangeLast = minOf(shown.atEndOfMonth(), today),
    )
}

/**
 * The choice after stepping [months] away (negative for back), never past the current month.
 * The range follows to the new month's days, so that "A date range" always starts from the
 * month in view.
 */
fun ReportChoice.steppedMonth(months: Long, today: LocalDate): ReportChoice =
    openingChoice(month.plusMonths(months), today).copy(kind = kind)

/** Whether there is a later month to step to. */
fun ReportChoice.canStepForward(today: LocalDate): Boolean = month < YearMonth.from(today)

/**
 * The choice with [day] as the first day of the range. A first day that is later than the last
 * takes the last day along, so the range is one day long and never runs backwards. A day after
 * [today] is brought back to today.
 */
fun ReportChoice.withRangeFirst(day: LocalDate, today: LocalDate): ReportChoice {
    val first = minOf(day, today)
    return copy(rangeFirst = first, rangeLast = maxOf(rangeLast, first))
}

/** The same for the last day: one that is earlier than the first takes the first day along. */
fun ReportChoice.withRangeLast(day: LocalDate, today: LocalDate): ReportChoice {
    val last = minOf(day, today)
    return copy(rangeFirst = minOf(rangeFirst, last), rangeLast = last)
}

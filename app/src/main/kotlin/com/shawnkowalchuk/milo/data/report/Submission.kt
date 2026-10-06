package com.shawnkowalchuk.milo.data.report

import com.shawnkowalchuk.milo.core.report.ReportPeriod
import java.time.YearMonth

// What the list of sent reports says about a period: whether it was sent before, which revision
// the next report for it is, whether a month counts as submitted, and what one more report for
// it would do. Pure functions, shared by the Trips screen's month card and the Report screen, so
// the two cannot disagree.

/**
 * The reports among [sent] that were sent for exactly [period], oldest first: the same kind and
 * the same days. A date range that happens to cover a whole month is not a report for that
 * month, and the month's report is not one for the range.
 */
fun sentFor(period: ReportPeriod, sent: List<SentReport>): List<SentReport> {
    val firstDay = period.firstDay.toEpochDay()
    val lastDay = period.lastDay.toEpochDay()
    return sent
        .filter { it.kind == period.kind && it.firstDay == firstDay && it.lastDay == lastDay }
        .sortedWith(compareBy<SentReport> { it.revision }.thenBy { it.id })
}

/**
 * The revision number of the next report for a period of which [sentBefore] were already sent:
 * 0 if none was, which makes it the original, and otherwise one more than the highest number
 * given so far.
 */
fun nextRevision(sentBefore: List<SentReport>): Int =
    sentBefore.maxOfOrNull { it.revision + 1 } ?: 0

/**
 * That a month has been submitted, and how often.
 *
 * @param first the report that made the month submitted.
 * @param latest the newest report for the month. It is [first] itself until a revision is sent.
 */
data class MonthSubmission(val first: SentReport, val latest: SentReport) {
    /** How many times a report replaced the first one. 0 for a month that was sent once. */
    val revisions: Int get() = latest.revision
}

/**
 * Whether [month] is submitted: it is once a report for the whole month has been sent, and
 * null means it is not.
 *
 * **Only a report for the whole month counts.** A report for a date range never marks a month
 * as submitted, not even one that runs from the month's first day to its last: Shawn sends the
 * month's report for that, and a range is for something else (a correction, a part of a month
 * that accounts asked for).
 */
fun monthSubmission(month: YearMonth, sent: List<SentReport>): MonthSubmission? {
    val ofMonth = sentFor(ReportPeriod.Month(month), sent)
    val first = ofMonth.firstOrNull() ?: return null
    return MonthSubmission(first = first, latest = ofMonth.last())
}

/**
 * What recording one more report as sent would do. "Did you send it?" says it before Shawn
 * answers, and it must not promise a new submission date to a month that keeps its first one.
 */
sealed interface SentEffect {
    /** A whole month that was never sent: it becomes submitted, on the day of this report. */
    data object MarksMonth : SentEffect

    /**
     * A whole month that was sent before. The report is listed as [revision], and the month
     * stays submitted on the day its first report was sent, [firstSentAtMs].
     */
    data class RevisesMonth(val revision: Int, val firstSentAtMs: Long) : SentEffect

    /** A date range: it is listed, and marks no month. */
    data object ListsRange : SentEffect
}

/** What recording a report for [period] as sent would do, after what was [sent] so far. */
fun sentEffect(period: ReportPeriod, sent: List<SentReport>): SentEffect = when (period) {
    is ReportPeriod.Range -> SentEffect.ListsRange

    is ReportPeriod.Month -> {
        val sentBefore = sentFor(period, sent)
        when (val first = sentBefore.firstOrNull()) {
            null -> SentEffect.MarksMonth
            else -> SentEffect.RevisesMonth(nextRevision(sentBefore), first.sentAtMs)
        }
    }
}

package com.shawnkowalchuk.milo.data.report

import com.shawnkowalchuk.milo.core.report.ReportPeriod
import com.shawnkowalchuk.milo.core.util.DistanceUnit
import java.time.YearMonth

// What the list of sent reports says about a period: whether it was sent before, which revision
// the next report for it is, whether a month counts as submitted, what one more report for it
// would do, what removing one would do, and whether a month's trips are still what was sent.
// Pure functions, shared by the Trips screen's month card, the Report screen and the monthly
// reminder, so the three cannot disagree.

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
 * @param first the report that makes the month submitted: the one with the lowest revision
 * number that is in the list. That is the original, unless the original was removed from the
 * list; then it is the earliest revision that is left.
 * @param latest the newest report for the month. It is [first] itself until a revision is sent.
 */
data class MonthSubmission(val first: SentReport, val latest: SentReport) {
    /** The newest report's revision number. 0 for a month whose original is its only report. */
    val revisions: Int get() = latest.revision

    /** Whether the list holds a later report for the month than [first]. */
    val sentAgain: Boolean get() = latest.id != first.id
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

/**
 * What removing one report from the list would do. The question before it is removed says it,
 * so that nobody takes a month back to "not submitted" without having been told.
 */
sealed interface RemovalEffect {
    /** The month's only report: the month goes back to not submitted. */
    data object UnmarksMonth : RemovalEffect

    /**
     * One of several reports for a month: the month stays submitted, from then on by the day
     * the earliest report still listed was sent, [submittedAtMs]. The others keep their numbers.
     */
    data class MonthStaysSubmitted(val submittedAtMs: Long) : RemovalEffect

    /** A date range: it leaves the list, and no month was marked by it. */
    data object UnlistsRange : RemovalEffect
}

/**
 * What removing [report] would do, given every report that was [sent] (with [report] still
 * among them or not: it is left out of the count either way).
 */
fun removalEffect(report: SentReport, sent: List<SentReport>): RemovalEffect =
    when (val period = report.period) {
        is ReportPeriod.Range -> RemovalEffect.UnlistsRange

        is ReportPeriod.Month -> {
            val left = sentFor(period, sent).filter { it.id != report.id }
            when (val first = left.firstOrNull()) {
                null -> RemovalEffect.UnmarksMonth
                else -> RemovalEffect.MonthStaysSubmitted(first.sentAtMs)
            }
        }
    }

/**
 * That a submitted month's Business trips are not what its newest report held: the month may
 * need to be sent again, as a revision.
 *
 * @param sentTripCount and [sentTenths] are the newest report's figures.
 * @param tripCount and [tenths] are what a report made now, in the same unit, would hold.
 * @param unit the unit both totals are in: the one the newest report was printed in, so that
 * the two figures can be held against each other. It is not the unit MilO is set to now if
 * that was changed since.
 */
data class ChangedSinceSent(
    val sentTripCount: Int,
    val sentTenths: Long,
    val tripCount: Int,
    val tenths: Long,
    val unit: DistanceUnit,
)

/**
 * Whether a submitted month's Business trips have changed since its report was sent, or null
 * if they have not, as far as MilO can tell, or if the month is not submitted.
 *
 * **What it goes by** is what the list of sent reports already stores for each report: how many
 * Business trips it listed and the total it printed. Both are compared with the month's newest
 * report. So it notices a Business trip that was added, deleted, restored or marked Personal,
 * and a distance that was changed, since then. It does **not** notice a change that leaves both
 * figures as they were: a time or an address that was edited, or two changes that cancel out.
 * Nothing stores when a trip was last changed.
 *
 * **It is judged in the unit the newest report was printed in,** not in the unit MilO is set
 * to (since 2026-10-07). The stored total is that report's own figure, and the month's trips
 * are added up in the same unit to be held against it. So choosing the other unit in Settings
 * changes neither side, and can never raise this by itself.
 *
 * @param tripCount how many Business trips the month has now.
 * @param tenthsIn what they add up to in a unit, as the report adds up (`sumOfTenths`): each
 * trip rounded to a tenth of that unit first. It is asked for the newest report's unit.
 */
fun changedSinceSent(
    submission: MonthSubmission?,
    tripCount: Int,
    tenthsIn: (DistanceUnit) -> Long,
): ChangedSinceSent? {
    val newest = submission?.latest ?: return null
    val unit = newest.distanceUnit
    val sentTenths = newest.printedTenths
    val tenths = tenthsIn(unit)
    if (newest.tripCount == tripCount && sentTenths == tenths) return null
    return ChangedSinceSent(newest.tripCount, sentTenths, tripCount, tenths, unit)
}

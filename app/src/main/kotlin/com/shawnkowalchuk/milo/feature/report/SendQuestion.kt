package com.shawnkowalchuk.milo.feature.report

import com.shawnkowalchuk.milo.core.report.ReportPeriod
import java.time.LocalDate

// What "Send to accountant" asks before it opens the email app. A pure function, so the cases
// are tested without a phone.

/** A question that is asked before a report is sent. */
enum class SendQuestion {
    /**
     * The report is for a whole month that has not ended: trips made later in the month would
     * be on no report until the month is sent again.
     */
    MONTH_NOT_ENDED,

    /** A report for this period was sent before: this one would be a revision. */
    SENT_BEFORE,
}

/**
 * The questions to ask before a report for [period] is sent on [today], in the order they are
 * asked. Empty for the ordinary case, last month's first report, which is sent at once.
 *
 * **A month that has not ended** is one whose last day is today or still to come: on the last
 * day itself the month's last trips may not have been driven yet. The Report screen opens on
 * the month the Trips screen was showing, which is the current one unless Shawn stepped back,
 * so sending an unfinished month is one press away from where he starts.
 *
 * A date range is not asked about. Its days were picked one by one, none of them later than
 * today, and it marks no month as submitted.
 *
 * @param sentBefore whether a report for exactly this period is in the list of sent reports.
 */
fun sendQuestions(
    period: ReportPeriod,
    today: LocalDate,
    sentBefore: Boolean,
): List<SendQuestion> = buildList {
    if (period is ReportPeriod.Month && !today.isAfter(period.lastDay)) {
        add(SendQuestion.MONTH_NOT_ENDED)
    }
    if (sentBefore) add(SendQuestion.SENT_BEFORE)
}

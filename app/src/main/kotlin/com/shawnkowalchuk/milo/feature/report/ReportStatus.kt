package com.shawnkowalchuk.milo.feature.report

import com.shawnkowalchuk.milo.core.report.ReportPeriod
import com.shawnkowalchuk.milo.data.report.MonthSubmission
import com.shawnkowalchuk.milo.data.report.SentReport
import com.shawnkowalchuk.milo.data.report.monthSubmission
import com.shawnkowalchuk.milo.data.report.sentFor
import com.shawnkowalchuk.milo.data.settings.MiloSettings
import com.shawnkowalchuk.milo.platform.reminder.ReminderMoment
import com.shawnkowalchuk.milo.platform.reminder.ReminderStep
import com.shawnkowalchuk.milo.platform.reminder.judgeReminder
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

// What the Report screen's Status tile says about the chosen period. A pure function, so the
// cases are tested without a phone.

/** Whether a report for the chosen period is in the list of sent reports. */
sealed interface ReportStatus {
    /**
     * None is. The tile is the amber one: this is still to do.
     *
     * @param reminding true while the monthly reminder is running for this month: it brings a
     * notification once a day until the month's report is recorded as sent. Never true for a
     * date range.
     */
    data class NotSent(val reminding: Boolean) : ReportStatus

    /** A whole month that is submitted: when, and whether it was sent again since. */
    data class MonthSent(val submission: MonthSubmission) : ReportStatus

    /**
     * A date range that was sent: the newest report for exactly these days. It marks no month.
     *
     * @param sentAtMs when it was sent, and [tripCount] how many trips it listed.
     */
    data class RangeSent(val sentAtMs: Long, val tripCount: Int) : ReportStatus
}

/**
 * The status of [period], from the list of sent reports.
 *
 * **"Reminding daily" is the monthly reminder's own verdict,** not a second rule ([judgeReminder],
 * which the notification and Home's amber tile go by too): the reminder is switched on, this
 * month's reminder day has come, the month is the one before the current month, it has a
 * Business trip, and no report for the whole of it is recorded as sent. Whether today's
 * notification has been shown already makes no difference to that.
 *
 * @param today the day it is, in [zone]: it decides which month is "last month".
 * @param businessTrips how many trips the report of [period] would list, or null while they
 * are being read. Nothing is said about the reminder until they are.
 */
fun reportStatus(
    period: ReportPeriod,
    today: LocalDate,
    zone: ZoneId,
    businessTrips: Int?,
    settings: MiloSettings,
    sent: List<SentReport>,
): ReportStatus = when (period) {
    is ReportPeriod.Range ->
        sentFor(period, sent).lastOrNull()?.let {
            ReportStatus.RangeSent(it.sentAtMs, it.tripCount)
        }
            ?: ReportStatus.NotSent(reminding = false)

    is ReportPeriod.Month ->
        monthSubmission(period.month, sent)?.let { ReportStatus.MonthSent(it) }
            ?: ReportStatus.NotSent(
                reminding =
                    businessTrips != null &&
                        reminderRunsFor(period.month, today, zone, businessTrips, settings),
            )
}

/** Whether the monthly reminder is running for [month], which has no report recorded as sent. */
private fun reminderRunsFor(
    month: YearMonth,
    today: LocalDate,
    zone: ZoneId,
    businessTrips: Int,
    settings: MiloSettings,
): Boolean {
    val verdict =
        judgeReminder(
            ReminderMoment(
                // Any moment of today will do: the rule goes by the day, not the hour.
                nowMs = today.atStartOfDay(zone).toInstant().toEpochMilli(),
                zone = zone,
                enabled = settings.reminderEnabled,
                reminderDay = settings.reminderDay,
                submitted = false,
                businessTrips = businessTrips,
                // Left out on purpose: it only says whether today's notification was posted.
                shown = null,
            ),
        )
    return verdict.month == month && verdict.step == ReminderStep.SHOW
}

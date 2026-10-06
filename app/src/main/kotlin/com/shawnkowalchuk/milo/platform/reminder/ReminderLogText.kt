package com.shawnkowalchuk.milo.platform.reminder

import com.shawnkowalchuk.milo.core.util.formatLogTime
import java.time.ZoneId

// The reminder's lines in the event log. English and plain, like the rest of the log: they are
// what is read when a reminder did not come.

/**
 * The line for one look at the reminder.
 *
 * @param source what prompted the look, in words.
 * @param seen false if the notification was posted and nobody can see it, because notifications
 * are switched off for MilO or for this kind of notification.
 * @param notStored why the fact that it was shown could not be stored, or null if it was.
 */
internal fun judgedText(
    verdict: ReminderVerdict,
    moment: ReminderMoment,
    source: String,
    seen: Boolean = true,
    notStored: String? = null,
): String {
    val month = verdict.month
    val why =
        when (verdict.reason) {
            ReminderReason.SWITCHED_OFF -> "the reminder is switched off in Settings"

            ReminderReason.DAY_NOT_REACHED ->
                "the reminder starts on ${verdict.startsOn} (day ${moment.reminderDay} of the " +
                    "month, as set), and today is ${verdict.today}"

            ReminderReason.SUBMITTED -> "a report for $month is recorded as sent"

            ReminderReason.NO_BUSINESS_TRIPS -> "$month has no Business trip to report"

            ReminderReason.SHOWN_TODAY -> "it was already shown today, ${verdict.today}"

            ReminderReason.DUE ->
                "today, ${verdict.today}, is on or after ${verdict.startsOn}, $month has " +
                    "${moment.businessTrips} Business trips, and no report for it is recorded " +
                    "as sent"
        }
    return when (verdict.step) {
        ReminderStep.SHOW -> {
            val unseen =
                if (seen) {
                    ""
                } else {
                    " Notifications are switched off for MilO or for the monthly reminder, " +
                        "so nobody saw it."
                }
            val again = notStored?.let { " $it" }.orEmpty()
            "Monthly reminder shown for $month ($source): $why.$unseen$again"
        }

        ReminderStep.WITHDRAW, ReminderStep.LEAVE ->
            "Monthly reminder: nothing shown for $month ($source): $why."
    }
}

/** The line for the daily alarm being asked for. */
internal fun alarmText(atMs: Long, zone: ZoneId, source: String): String =
    "Monthly reminder: the next daily look is asked for at ${formatLogTime(atMs, zone)}, or " +
        "within about an hour after it ($source). MilO also looks at every start and every " +
        "time it is opened."

package com.shawnkowalchuk.milo.platform.reminder

import com.shawnkowalchuk.milo.core.util.formatLogTime
import com.shawnkowalchuk.milo.data.settings.ReminderShown
import com.shawnkowalchuk.milo.platform.clock.DailyAlarmAsked
import com.shawnkowalchuk.milo.platform.clock.HELD_BACK_WHY
import com.shawnkowalchuk.milo.platform.clock.askAgainText
import com.shawnkowalchuk.milo.platform.clock.spanText
import java.time.LocalDate
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

/**
 * What a line about this decision says: the reason, the month, the day, and the day the
 * reminder starts on where that is the reason. Two decisions that say the same are written
 * once: stepping the day in Settings through days that have already come writes nothing new,
 * and each day beyond today is said once, with the day it then starts on.
 *
 * @param heldBack true for a reminder that is due and is not shown, because MilO's clock is on
 * probation ([heldBackText]). That is another line than the one for the reminder being shown.
 */
internal fun ReminderVerdict.said(heldBack: Boolean = false): List<Any?> = listOf(
    reason,
    month,
    today,
    startsOn.takeIf { reason == ReminderReason.DAY_NOT_REACHED },
    heldBack,
)

/**
 * The line for a reminder that is due by a clock on probation, and so is not shown: the phone's
 * date may be set ahead, and the month may not have ended.
 */
internal fun heldBackText(verdict: ReminderVerdict, source: String): String =
    "Monthly reminder: nothing shown for ${verdict.month} yet ($source): it is due by today's " +
        "date, ${verdict.today}, but $HELD_BACK_WHY"

/** The line for the daily alarm being asked for, in whichever way it was ([DailyAlarmAsked]). */
internal fun alarmAskedText(
    asked: DailyAlarmAsked,
    atMs: Long,
    zone: ZoneId,
    source: String,
): String = when (asked) {
    DailyAlarmAsked.AtTimeOfDay -> alarmText(atMs, zone, source)
    is DailyAlarmAsked.Counted -> alarmCountedText(atMs, zone, asked.leftMs, source)
    is DailyAlarmAsked.ToAskAgain -> "Monthly reminder: " + askAgainText(asked.waitMs, source)
}

/** The line for the daily alarm being asked for. */
internal fun alarmText(atMs: Long, zone: ZoneId, source: String): String =
    "Monthly reminder: the next daily look is asked for at ${formatLogTime(atMs, zone)}, or " +
        "within about an hour after it ($source). MilO also looks at every start and every " +
        "time it is opened."

/**
 * The line for the daily alarm being asked for while MilO does not believe the phone's clock:
 * not for a time of day, which Android would judge by the phone's clock, but after [delayMs].
 */
internal fun alarmCountedText(atMs: Long, zone: ZoneId, delayMs: Long, source: String): String =
    "Monthly reminder: the next daily look is asked for at ${formatLogTime(atMs, zone)}, or " +
        "within about an hour after it ($source). The phone's clock is set differently from " +
        "MilO's own, so Android was asked to count ${spanText(delayMs)} from now and not to go " +
        "by the phone's clock. It is asked in the normal way as soon as the two agree."

/**
 * The line for a stored "shown" that names a day after today.
 *
 * @param notForgotten why it could not be taken out of the settings, or null if it was.
 */
internal fun shownAheadText(shown: ReminderShown, today: LocalDate, notForgotten: String?): String =
    "Monthly reminder: the settings said a reminder for ${shown.month} was shown on " +
        "${shown.onDay}, a day after today, $today. That was stored while the phone's date " +
        "was set ahead. It counts as not shown." + notForgotten?.let { " $it" }.orEmpty()

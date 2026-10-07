package com.shawnkowalchuk.milo.platform.reminder

import com.shawnkowalchuk.milo.core.report.ReportPeriod
import com.shawnkowalchuk.milo.core.util.localDateOf
import com.shawnkowalchuk.milo.data.report.SentReport
import com.shawnkowalchuk.milo.data.report.monthSubmission
import com.shawnkowalchuk.milo.data.report.selectForReport
import com.shawnkowalchuk.milo.data.settings.MiloSettings
import com.shawnkowalchuk.milo.data.settings.REMINDER_DAYS
import com.shawnkowalchuk.milo.data.settings.ReminderShown
import com.shawnkowalchuk.milo.data.trip.Trip
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId

// When the monthly reminder is shown. Pure functions: every condition is decided here, from
// plain values, so it is tested without a phone, an alarm or a clock.
//
// The reminder is decided by what is true at the moment of asking, not by an alarm that fires
// on a date. MilO asks once a day and on every occasion it is running anyway; the answer is
// the same however often it is asked, and a phone that was switched off on the 1st, a changed
// reminder day and a changed time zone all sort themselves out at the next asking.

/**
 * The time of day the daily alarm is asked for. A reminder is something to act on, so it comes
 * at a time when the day has started and not at whatever hour MilO last started.
 */
val DAILY_LOOK_AT: LocalTime = LocalTime.of(9, 0)

/**
 * The day in [month] from which the reminder is shown: the chosen day, or the month's last day
 * if the month is shorter. A reminder set to the 31st comes on 30 April and on 28 February (on
 * the 29th in a leap year), never a day late in the month after.
 */
fun reminderStartsOn(month: YearMonth, reminderDay: Int): LocalDate {
    val day = reminderDay.coerceIn(REMINDER_DAYS.first, REMINDER_DAYS.last)
    return month.atDay(minOf(day, month.lengthOfMonth()))
}

/**
 * The month a reminder at [nowMs] is about: the calendar month before the one [nowMs] falls in,
 * in [zone]. On 1 January it is December of the year before.
 */
fun monthToRemindOf(nowMs: Long, zone: ZoneId): YearMonth =
    YearMonth.from(localDateOf(nowMs, zone)).minusMonths(1)

/**
 * The next time the daily alarm is asked for: the next [DAILY_LOOK_AT] in [zone] that is later
 * than [nowMs]. Worked out from the calendar and not by adding 24 hours, so it stays at the
 * same time of day when the clocks change.
 */
fun nextDailyLookMs(nowMs: Long, zone: ZoneId): Long {
    val today = localDateOf(nowMs, zone)
    val todayAt = today.atTime(DAILY_LOOK_AT).atZone(zone).toInstant().toEpochMilli()
    if (todayAt > nowMs) return todayAt
    return today.plusDays(1).atTime(DAILY_LOOK_AT).atZone(zone).toInstant().toEpochMilli()
}

/**
 * Everything the decision goes by.
 *
 * @param nowMs wall-clock milliseconds, and [zone] the phone's time zone: together they say
 * which day today is, and so which month is "last month".
 * @param enabled the Settings switch.
 * @param reminderDay the day of the month chosen in Settings, 1 to 31.
 * @param submitted whether a report for the whole of last month is recorded as sent
 * (`monthSubmission`).
 * @param businessTrips how many trips last month's report would list (`selectForReport`).
 * @param shown the month the reminder was last shown for and the day it was shown on, as
 * stored, or null if it never was.
 */
data class ReminderMoment(
    val nowMs: Long,
    val zone: ZoneId,
    val enabled: Boolean,
    val reminderDay: Int,
    val submitted: Boolean,
    val businessTrips: Int,
    val shown: ReminderShown?,
)

/**
 * Everything [judgeReminder] goes by, gathered from what is stored. One function, so that the
 * notification and the home screen's tile for the same report look at the same month, the
 * same trips and the same sent reports, and cannot disagree about whether it is waiting.
 *
 * @param stored the settings: the switch, the reminder day, and what was last shown.
 * @param sent every report recorded as sent.
 * @param lastMonthTrips the stored trips that started in the month the reminder is about
 * ([monthToRemindOf]), whatever their status. Which of them the month's report would list is
 * decided by the report's own rule, so that the reminder and the Report screen cannot disagree
 * about whether there is anything to send.
 */
fun reminderMoment(
    nowMs: Long,
    zone: ZoneId,
    stored: MiloSettings,
    sent: List<SentReport>,
    lastMonthTrips: List<Trip>,
): ReminderMoment {
    val month = monthToRemindOf(nowMs, zone)
    return ReminderMoment(
        nowMs = nowMs,
        zone = zone,
        enabled = stored.reminderEnabled,
        reminderDay = stored.reminderDay,
        submitted = monthSubmission(month, sent) != null,
        businessTrips =
            selectForReport(lastMonthTrips, ReportPeriod.Month(month), zone).trips.size,
        shown = stored.reminderShown,
    )
}

/** What is done about the reminder's notification. */
enum class ReminderStep {
    /** The notification is posted, and that it was is stored. */
    SHOW,

    /** A notification that may still be showing is taken away: it is no longer true. */
    WITHDRAW,

    /** Nothing: today's reminder has been shown, and is left where it is. */
    LEAVE,
}

/** Why. Each is one condition of the reminder, in the order they are looked at. */
enum class ReminderReason {
    /** The reminder is switched off in Settings. */
    SWITCHED_OFF,

    /** This month's reminder day has not come yet. */
    DAY_NOT_REACHED,

    /** A report for the whole of last month is recorded as sent. */
    SUBMITTED,

    /** Last month has no Business trip, so there is nothing to send. */
    NO_BUSINESS_TRIPS,

    /** The reminder for last month was already shown today. */
    SHOWN_TODAY,

    /** Every condition holds. */
    DUE,
}

/**
 * The decision, with the two things it was worked out for.
 *
 * @param month the month the reminder is about: last month.
 * @param today the calendar day [ReminderMoment.nowMs] falls on.
 * @param startsOn the day of this month from which the reminder is shown.
 */
data class ReminderVerdict(
    val step: ReminderStep,
    val reason: ReminderReason,
    val month: YearMonth,
    val today: LocalDate,
    val startsOn: LocalDate,
)

/**
 * Decides whether the reminder is shown now.
 *
 * **It is shown when all of this holds:** the reminder is switched on; today is this month's
 * reminder day or later ([reminderStartsOn]); no report for the whole of last month is recorded
 * as sent; last month has at least one Business trip; and the reminder for last month has not
 * been shown yet today. So it comes once a day, from the reminder day to the end of the month,
 * until the month is submitted or the reminder is switched off.
 *
 * When one of the first four does not hold, a notification that is still showing is withdrawn:
 * it would be saying something that is no longer so.
 */
fun judgeReminder(moment: ReminderMoment): ReminderVerdict {
    val today = localDateOf(moment.nowMs, moment.zone)
    val month = monthToRemindOf(moment.nowMs, moment.zone)
    val startsOn = reminderStartsOn(YearMonth.from(today), moment.reminderDay)
    val reason =
        when {
            !moment.enabled -> ReminderReason.SWITCHED_OFF
            today.isBefore(startsOn) -> ReminderReason.DAY_NOT_REACHED
            moment.submitted -> ReminderReason.SUBMITTED
            moment.businessTrips <= 0 -> ReminderReason.NO_BUSINESS_TRIPS
            moment.shown == ReminderShown(month, today) -> ReminderReason.SHOWN_TODAY
            else -> ReminderReason.DUE
        }
    val step =
        when (reason) {
            ReminderReason.DUE -> ReminderStep.SHOW
            ReminderReason.SHOWN_TODAY -> ReminderStep.LEAVE
            else -> ReminderStep.WITHDRAW
        }
    return ReminderVerdict(step, reason, month, today, startsOn)
}

package com.shawnkowalchuk.milo.platform.nothingrecorded

import com.shawnkowalchuk.milo.core.schedule.DayHours
import com.shawnkowalchuk.milo.core.schedule.WorkSchedule
import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.core.util.TimeSpan
import com.shawnkowalchuk.milo.core.util.daySpan
import com.shawnkowalchuk.milo.core.util.localDateOf
import com.shawnkowalchuk.milo.data.trip.Trip
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

// When MilO says that no trip has been recorded today. Pure functions: every condition is
// decided here, from plain values, so it is tested without a phone, an alarm or a clock.
//
// Like the monthly reminder, the check is decided by what is true at the moment of asking, not
// by an alarm that fires at a time. MilO asks once a day and on every occasion it is running
// anyway; the answer is the same however often it is asked, and a phone that was switched off
// at noon, a changed time and a changed schedule all sort themselves out at the next asking.

/**
 * The time of day from which a work day is checked: the time Shawn set, or the start of that
 * day's work hours if they start later. Before his work day has begun, a day without a trip
 * says nothing about whether MilO still notices the truck.
 */
fun checkedFrom(hours: DayHours, checkAt: LocalTime): LocalTime = maxOf(checkAt, hours.start)

/**
 * The moment from which [day] is checked, or null if the schedule does not track that day.
 *
 * It is worked out as a point in time and compared as one, not as a time of day: once it has
 * passed it stays passed until midnight, also on the day the clocks go back and a time of day
 * comes round twice. If the clocks jump over the time itself, java.time moves it on by the
 * length of the jump; if it happens twice, it takes the first.
 */
fun checkedFromMs(day: LocalDate, zone: ZoneId, checkAt: LocalTime, schedule: WorkSchedule): Long? {
    val hours = schedule.on(day.dayOfWeek)
    if (!hours.tracked) return null
    return day.atTime(checkedFrom(hours, checkAt)).atZone(zone).toInstant().toEpochMilli()
}

/**
 * The next time the daily alarm is asked for: the moment today is checked from if that is
 * still to come, otherwise tomorrow's. A day the schedule does not track is looked at as well,
 * at the time Shawn set: the look takes away a notification left over from the day before, and
 * asks for the next alarm. Worked out from the calendar and not by adding 24 hours, so it
 * stays at the same time of day when the clocks change.
 */
fun nextNothingRecordedLookMs(
    nowMs: Long,
    zone: ZoneId,
    checkAt: LocalTime,
    schedule: WorkSchedule,
): Long {
    fun lookOn(day: LocalDate): Long = checkedFromMs(day, zone, checkAt, schedule)
        ?: day.atTime(checkAt).atZone(zone).toInstant().toEpochMilli()

    val today = localDateOf(nowMs, zone)
    val todayAt = lookOn(today)
    return if (todayAt > nowMs) todayAt else lookOn(today.plusDays(1))
}

/**
 * Whether a stored trip counts as "a trip was recorded today".
 *
 * **Any trip MilO itself opened counts, whatever became of it:** one that is being recorded,
 * a finished one, one discarded for being too short or for starting outside the work hours,
 * and one Shawn deleted afterwards. Each shows that a trip was started today. A trip that is
 * being recorded counts even if it began before midnight.
 *
 * **A trip added by hand never counts.** He typed it in because MilO missed it, so it shows
 * nothing about whether MilO notices the truck.
 *
 * A trip whose start was changed by hand is judged by the start MilO recorded: a trip moved
 * into today on the edit screen was not started today.
 *
 * @param today the span of time the calendar day covers.
 */
fun countsAsRecordedToday(trip: Trip, today: TimeSpan): Boolean {
    if (trip.addedByHand) return false
    if (trip.status == TripStatus.OPEN) return true
    val recordedStartMs = trip.recordedStartedAtMs ?: trip.startedAtMs
    return recordedStartMs >= today.fromMs && recordedStartMs < today.untilMs
}

/**
 * Everything the decision goes by.
 *
 * @param nowMs wall-clock milliseconds, and [zone] the phone's time zone: together they say
 * which day today is.
 * @param enabled the Settings switch.
 * @param checkAt the time of day Shawn set.
 * @param schedule the work schedule: which days are work days, and when each starts.
 * @param trips the stored trips that started today, whatever their status, and the trip that
 * is open now, if there is one. Trips of another day may be among them; they are not counted.
 * @param shownOn the day the notification was last shown, as stored, or null if it never was.
 */
data class NothingRecordedMoment(
    val nowMs: Long,
    val zone: ZoneId,
    val enabled: Boolean,
    val checkAt: LocalTime,
    val schedule: WorkSchedule,
    val trips: List<Trip>,
    val shownOn: LocalDate?,
)

/** What is done about the notification. */
enum class NothingRecordedStep {
    /** The notification is posted, and that it was is stored. */
    SHOW,

    /** A notification that may still be showing is taken away: it is no longer true. */
    WITHDRAW,

    /** Nothing: today's notification has been shown, and is left where it is. */
    LEAVE,
}

/** Why. Each is one condition of the check, in the order they are looked at. */
enum class NothingRecordedReason {
    /** The check is switched off in Settings. */
    SWITCHED_OFF,

    /** The schedule does not track today. */
    NOT_A_WORK_DAY,

    /** Today is a work day, and the time from which it is checked has not come yet. */
    TOO_EARLY,

    /** A trip was started today, or one is being recorded. */
    TRIP_RECORDED,

    /** Today's notification was already shown. */
    SHOWN_TODAY,

    /** Every condition holds: a work day, past the time, and no trip. */
    DUE,
}

/**
 * The decision, with what it was worked out from.
 *
 * @param today the calendar day [NothingRecordedMoment.nowMs] falls on.
 * @param checkedFromMs the moment from which today is checked, or null if today is not a work
 * day.
 * @param tripsCounted how many of the trips count as recorded today ([countsAsRecordedToday]).
 * @param tripsByHand how many trips of today were added by hand, and so were not counted.
 */
data class NothingRecordedVerdict(
    val step: NothingRecordedStep,
    val reason: NothingRecordedReason,
    val today: LocalDate,
    val checkedFromMs: Long?,
    val tripsCounted: Int,
    val tripsByHand: Int,
)

/**
 * Decides whether MilO says now that no trip has been recorded today.
 *
 * **It says so when all of this holds:** the check is switched on; the schedule tracks today;
 * the time from which today is checked has come ([checkedFromMs]); no trip counts as recorded
 * today ([countsAsRecordedToday]); and today's notification has not been shown yet. So a day
 * brings one notification at most.
 *
 * When one of the first four does not hold, a notification that is still showing is withdrawn:
 * it would be saying something that is no longer so. That is how a trip that starts after the
 * notification takes it away, and how yesterday's is gone after midnight.
 */
fun judgeNothingRecorded(moment: NothingRecordedMoment): NothingRecordedVerdict {
    val today = localDateOf(moment.nowMs, moment.zone)
    val span = daySpan(today, moment.zone)
    val fromMs = checkedFromMs(today, moment.zone, moment.checkAt, moment.schedule)
    val counted = moment.trips.count { countsAsRecordedToday(it, span) }
    val byHand =
        moment.trips.count {
            it.addedByHand && it.startedAtMs >= span.fromMs && it.startedAtMs < span.untilMs
        }
    val reason =
        when {
            !moment.enabled -> NothingRecordedReason.SWITCHED_OFF
            fromMs == null -> NothingRecordedReason.NOT_A_WORK_DAY
            moment.nowMs < fromMs -> NothingRecordedReason.TOO_EARLY
            counted > 0 -> NothingRecordedReason.TRIP_RECORDED
            moment.shownOn == today -> NothingRecordedReason.SHOWN_TODAY
            else -> NothingRecordedReason.DUE
        }
    val step =
        when (reason) {
            NothingRecordedReason.DUE -> NothingRecordedStep.SHOW
            NothingRecordedReason.SHOWN_TODAY -> NothingRecordedStep.LEAVE
            else -> NothingRecordedStep.WITHDRAW
        }
    return NothingRecordedVerdict(step, reason, today, fromMs, counted, byHand)
}

package com.shawnkowalchuk.milo.platform.nothingrecorded

import com.shawnkowalchuk.milo.core.util.formatLogTime
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

// The check's lines in the event log. English and plain, like the rest of the log: they are
// what is read when a notification did not come, or came on a day it should not have.

/** A time of day in a log line: fixed, 24 hours, the same in every language. */
private val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)

/**
 * The line for one asking, with what was decided and why. One is written every time the
 * question is asked, also when the answer is the one before: the line is the proof that MilO
 * looked, which is what is wanted on the day no notification came.
 *
 * @param source what prompted the asking, in words.
 * @param unanswered true if the trip controller had not said within the wait that it has dealt
 * with everything handed to it, and the question was asked all the same.
 * @param afterWaitingForATrip true if this is the second of two looks, the first of which found
 * no trip, and a trip was started in the wait between them ([TRIP_START_WAIT_MS]). The line
 * then says so: it is why a look and a trip start stand side by side with no notification.
 */
internal fun judgedText(
    verdict: NothingRecordedVerdict,
    moment: NothingRecordedMoment,
    source: String,
    unanswered: Boolean = false,
    afterWaitingForATrip: Boolean = false,
): String {
    val today = verdict.today
    val day = today.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
    val decided =
        when (verdict.reason) {
            NothingRecordedReason.SWITCHED_OFF ->
                "no notification: the check is switched off in Settings"

            NothingRecordedReason.NOT_A_WORK_DAY ->
                "no notification: today, $day $today, is not a work day in the schedule"

            NothingRecordedReason.TOO_EARLY ->
                "no notification yet: today, $day $today, is checked from " +
                    "${fromText(verdict, moment)}, and it is ${clock(moment.nowMs, moment.zone)}"

            NothingRecordedReason.TRIP_RECORDED ->
                "no notification: ${tripsText(verdict.tripsCounted)} today, $day $today"

            NothingRecordedReason.SHOWN_TODAY ->
                "no second notification: today's was already shown, and still no trip has " +
                    "been started today, $day $today"

            NothingRecordedReason.DUE ->
                "notify: no trip has been started today, $day $today. It is a work day, " +
                    "checked from ${fromText(verdict, moment)}, and it is " +
                    clock(moment.nowMs, moment.zone)
        }
    val byHand =
        when (verdict.tripsByHand) {
            0 -> ""
            1 -> " One trip of today was added by hand and is not counted."
            else -> " ${verdict.tripsByHand} trips of today were added by hand and are not counted."
        }
    val waited =
        if (afterWaitingForATrip) {
            " A first look, a moment earlier, found no trip and waited before notifying: one " +
                "was just being started."
        } else {
            ""
        }
    val notWaitedFor =
        if (unanswered) {
            " Asked without the trip controller having caught up: it did not answer in time."
        } else {
            ""
        }
    return "Nothing-recorded check ($source): $decided.$byHand$waited$notWaitedFor"
}

/** "12:00, the time set" or "08:00, the start of the day's work hours (the time set is 07:00)". */
private fun fromText(verdict: NothingRecordedVerdict, moment: NothingRecordedMoment): String {
    val fromMs = verdict.checkedFromMs ?: return "no time"
    val hours = moment.schedule.on(verdict.today.dayOfWeek)
    val from = clock(fromMs, moment.zone)
    return if (hours.start > moment.checkAt) {
        "$from, the start of the day's work hours (the time set, " +
            "${CLOCK.format(moment.checkAt)}, is earlier)"
    } else {
        "$from, the time set"
    }
}

private fun tripsText(counted: Int): String = if (counted == 1) {
    "1 trip was started or is being recorded"
} else {
    "$counted trips were started or are being recorded"
}

private fun clock(epochMs: Long, zone: ZoneId): String =
    CLOCK.withZone(zone).format(Instant.ofEpochMilli(epochMs))

/**
 * The facts a decision went by, for the line's detail: the zone, the two settings, today's
 * hours and each trip that was looked at. No position and no address.
 */
internal fun factsText(moment: NothingRecordedMoment, verdict: NothingRecordedVerdict): String {
    val hours = moment.schedule.on(verdict.today.dayOfWeek)
    val day = verdict.today.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
    val hoursText =
        if (hours.tracked) {
            "tracked, ${CLOCK.format(hours.start)} to ${CLOCK.format(hours.end)}"
        } else {
            "not tracked"
        }
    val shown = moment.shownOn?.toString() ?: "never"
    val trips =
        if (moment.trips.isEmpty()) {
            "none"
        } else {
            moment.trips.joinToString("; ") { trip ->
                val byHand = if (trip.addedByHand) ", added by hand" else ""
                "trip ${trip.id} ${trip.status}, started " +
                    "${formatLogTime(trip.startedAtMs, moment.zone)}$byHand"
            }
        }
    return "Time zone ${moment.zone}. Switch ${if (moment.enabled) "on" else "off"}, time set " +
        "${CLOCK.format(moment.checkAt)}. $day: $hoursText. Notification last shown: $shown. " +
        "Trips looked at: $trips."
}

/**
 * The line for the notification being posted.
 *
 * @param seen false if it was posted and nobody can see it, because notifications are
 * switched off for MilO or for this kind of notification.
 * @param notStored why the fact that it was shown could not be stored, or null if it was.
 */
internal fun shownText(verdict: NothingRecordedVerdict, seen: Boolean, notStored: String?): String {
    val unseen =
        if (seen) {
            ""
        } else {
            " Notifications are switched off for MilO or for this kind of notification, so " +
                "nobody saw it."
        }
    val again = notStored?.let { " $it" }.orEmpty()
    return "Nothing-recorded notification shown for ${verdict.today}: \"No trip recorded " +
        "today\". A tap opens MilO on the Home screen.$unseen$again"
}

/** The line for the daily alarm being asked for. */
internal fun alarmText(atMs: Long, zone: ZoneId, source: String): String =
    "Nothing-recorded check: the next daily look is asked for at ${formatLogTime(atMs, zone)}, " +
        "or soon after it ($source). MilO also looks at every start and every time it is opened."

/** The line for the daily alarm being taken back. */
internal fun noAlarmText(source: String): String =
    "Nothing-recorded check: no daily look is asked for, because the check is switched off " +
        "in Settings ($source)."

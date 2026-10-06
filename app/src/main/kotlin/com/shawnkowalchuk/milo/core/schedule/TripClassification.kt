package com.shawnkowalchuk.milo.core.schedule

import java.time.Instant
import java.time.ZoneId

// Business or Personal, from when a trip started. Pure functions: the same rule sorts a trip
// when it is finalised and catches up the trips recorded before there was a schedule.
//
// Nothing here is asked while a trip is being recorded. A trip starts and runs by the trip
// rules alone (ADR-002); the schedule only decides what the finished trip is saved as.

/**
 * What the work schedule makes of one trip.
 *
 * @param ranPastSchedule true for a Business trip that was still running when its day's hours
 * ended. It was recorded to its end all the same. Never true for a Personal trip.
 */
data class TripClassification(val category: TripCategory, val ranPastSchedule: Boolean)

/**
 * Sorts a trip by its **start**, read as a day and a time of day in [zone], the phone's time
 * zone: Business if that day is tracked and the time is at or after the day's start and before
 * its end, otherwise Personal. A trip that starts on the stroke of the start time is Business;
 * one that starts on the stroke of the end time is Personal.
 *
 * Only the start decides. A truck that connects at 07:55 for a drive at 08:05 is one trip that
 * started at 07:55, and is Personal until Shawn marks it otherwise. What started the trip, the
 * truck or a Start button, makes no difference.
 *
 * A Business trip has run past the schedule if it ended after the end time **of the day it
 * started on**. That moment is worked out as a point in time, not as a time of day, so a trip
 * that runs past midnight is judged against the day it began, and a day on which the clocks
 * change is as long as it really was.
 *
 * @param startedAtMs and [endedAtMs] are wall-clock milliseconds since 1970, as stored.
 * @param endedAtMs null for a trip without a stored end, which is never flagged.
 */
fun classifyTrip(
    startedAtMs: Long,
    endedAtMs: Long?,
    schedule: WorkSchedule,
    zone: ZoneId,
): TripClassification {
    val start = Instant.ofEpochMilli(startedAtMs).atZone(zone)
    val hours = schedule.on(start.dayOfWeek)
    val timeOfDay = start.toLocalTime()
    val insideHours = hours.tracked && timeOfDay >= hours.start && timeOfDay < hours.end
    if (!insideHours) return TripClassification(TripCategory.PERSONAL, ranPastSchedule = false)
    // If the clocks jump over the end time itself, java.time moves it on by the length of the
    // jump; if the end time happens twice, it takes the first.
    val hoursEndMs = start.toLocalDate().atTime(hours.end).atZone(zone).toInstant().toEpochMilli()
    return TripClassification(
        category = TripCategory.BUSINESS,
        ranPastSchedule = endedAtMs != null && endedAtMs > hoursEndMs,
    )
}

/**
 * The two settings a trip is put away by when it is finalised.
 *
 * @param ignoreOutsideSchedule false saves a trip that started outside the schedule as
 * Personal; true has it discarded instead ("Ignore them" on the Settings screen).
 */
data class FilingRules(val schedule: WorkSchedule, val ignoreOutsideSchedule: Boolean)

/**
 * How a trip is put away at the moment it is finalised.
 *
 * @param classification what the schedule made of it, or null if there was no schedule to go by
 * (the settings could not be read). The trip then stays unsorted, and the catch-up at a later
 * process start sorts it.
 * @param ignored true if the trip is to be stored as discarded for one reason only: it turned
 * out Personal while trips outside the schedule are set to be ignored. Its fixes, its distance
 * and its times are stored as for any trip, so it can be found among the discarded trips and
 * counted after all.
 */
data class TripFiling(val classification: TripClassification?, val ignored: Boolean)

/**
 * Decides how a trip that has just ended is put away. This is the only place the "ignore"
 * setting is applied, and it is applied to a trip that was recorded in full: the schedule never
 * stops a trip from starting or from being recorded.
 *
 * @param kept false if the trip rules have already discarded the trip (too short, or a start
 * that was never confirmed). Such a trip is sorted too, so that it has its category if it is
 * counted after all, but it is never marked as ignored: it is discarded for another reason.
 * @param rules null if the settings could not be read.
 */
fun fileTrip(
    startedAtMs: Long,
    endedAtMs: Long,
    kept: Boolean,
    rules: FilingRules?,
    zone: ZoneId,
): TripFiling {
    if (rules == null) return TripFiling(classification = null, ignored = false)
    val classification = classifyTrip(startedAtMs, endedAtMs, rules.schedule, zone)
    val personal = classification.category == TripCategory.PERSONAL
    return TripFiling(classification, ignored = kept && personal && rules.ignoreOutsideSchedule)
}

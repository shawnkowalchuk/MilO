package com.shawnkowalchuk.milo.core.schedule

import java.time.DayOfWeek
import java.time.LocalTime

// The work schedule read as one work week: what its work days have in common, and the change
// that gives every day the same hours. The schedule itself still keeps hours for each day
// (WorkSchedule.kt); this is how the Settings screen shows and sets them with one slider while
// the work days agree. Pure Kotlin, so it is tested without a phone.

private const val SECONDS_PER_MINUTE = 60

/** The days that are tracked, Monday first. */
fun WorkSchedule.workDays(): List<DayOfWeek> = DayOfWeek.entries.filter { on(it).tracked }

/**
 * The hours the week is shown with while one slider stands for all of it: those of the first
 * work day, or Monday's while no day is tracked. The answer carries no "tracked" of its own
 * worth reading; only its start and its end are meant.
 */
fun WorkSchedule.weekHours(): DayHours = on(workDays().firstOrNull() ?: DayOfWeek.MONDAY)

/**
 * Whether every work day has the same start and end, so that one pair of times says the whole
 * week. A week without a work day agrees: nothing in it differs.
 */
fun WorkSchedule.workDaysAgree(): Boolean {
    val shared = weekHours()
    return workDays().all { on(it).hasHoursOf(shared) }
}

/** How many minutes of work hours the week has: the sum of its work days' hours. */
fun WorkSchedule.weeklyMinutes(): Int = workDays().sumOf { minutesOf(on(it)) }

private fun minutesOf(hours: DayHours): Int =
    (hours.end.toSecondOfDay() - hours.start.toSecondOfDay()) / SECONDS_PER_MINUTE

/**
 * This schedule with [start] and [end] on all seven days, or null for hours that
 * [areValidHours] refuses. Which days are tracked does not change.
 *
 * Every day gets them, the days that are off as well, and not only the tracked ones as with
 * [WorkSchedule.withHoursOfOnTrackedDays]: this is the change behind the one slider that
 * stands for the whole week, and a day that is switched on afterwards then has the hours that
 * slider shows.
 */
fun WorkSchedule.withHoursOnEveryDay(start: LocalTime, end: LocalTime): WorkSchedule? {
    if (!areValidHours(start, end)) return null
    return DayOfWeek.entries.fold(this) { schedule, day ->
        schedule.with(day, schedule.on(day).copy(start = start, end = end))
    }
}

/**
 * This schedule with [start] as the start of [day], or of every day if [day] is null, in which
 * case every day also gets the end the week is shown with ([weekHours]). Null on the same terms
 * as [WorkSchedule.withStart].
 */
fun WorkSchedule.withStartOn(day: DayOfWeek?, start: LocalTime): WorkSchedule? =
    if (day != null) withStart(day, start) else withHoursOnEveryDay(start, weekHours().end)

/** The same for the end of [day], or of every day. */
fun WorkSchedule.withEndOn(day: DayOfWeek?, end: LocalTime): WorkSchedule? =
    if (day != null) withEnd(day, end) else withHoursOnEveryDay(weekHours().start, end)

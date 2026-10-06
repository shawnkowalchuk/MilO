package com.shawnkowalchuk.milo.data.settings

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import com.shawnkowalchuk.milo.core.schedule.DEFAULT_WORK_SCHEDULE
import com.shawnkowalchuk.milo.core.schedule.DayHours
import com.shawnkowalchuk.milo.core.schedule.WorkSchedule
import com.shawnkowalchuk.milo.core.schedule.areValidHours
import java.time.DayOfWeek
import java.time.LocalTime

// How the work schedule is kept in the settings file: three values for each day of the week.
// A time is stored as whole minutes since midnight, which is all a day's hours are made of.
//
// The key names are what is written to the file. They are spelled from this table and never
// from the constants' own names, so nothing that is renamed in code can reset a day.

private const val MINUTES_PER_DAY = 24 * 60
private const val SECONDS_PER_MINUTE = 60

private val DAY_IN_KEYS: Map<DayOfWeek, String> =
    mapOf(
        DayOfWeek.MONDAY to "monday",
        DayOfWeek.TUESDAY to "tuesday",
        DayOfWeek.WEDNESDAY to "wednesday",
        DayOfWeek.THURSDAY to "thursday",
        DayOfWeek.FRIDAY to "friday",
        DayOfWeek.SATURDAY to "saturday",
        DayOfWeek.SUNDAY to "sunday",
    )

private fun DayOfWeek.inKeys(): String = DAY_IN_KEYS.getValue(this)

private fun trackedKey(day: DayOfWeek) = booleanPreferencesKey("schedule_${day.inKeys()}_tracked")

private fun startKey(day: DayOfWeek) = intPreferencesKey("schedule_${day.inKeys()}_start_minute")

private fun endKey(day: DayOfWeek) = intPreferencesKey("schedule_${day.inKeys()}_end_minute")

/**
 * The stored schedule. A day that was never written reads as the default (Monday to Friday,
 * 08:00 to 16:30).
 *
 * The setters cannot store hours that end before they start, so a day whose stored times make
 * no sense means the file was written by something else. Such a day reads with the default
 * hours and keeps its own "tracked", which is the cautious reading: the schedule decides only
 * what a trip is saved as, and Shawn can change any trip by hand.
 */
internal fun Preferences.readSchedule(): WorkSchedule = WorkSchedule(
    DayOfWeek.entries.associateWith { day ->
        val fallback = DEFAULT_WORK_SCHEDULE.on(day)
        val tracked = this[trackedKey(day)] ?: fallback.tracked
        val start = timeOfMinute(this[startKey(day)]) ?: fallback.start
        val end = timeOfMinute(this[endKey(day)]) ?: fallback.end
        if (areValidHours(start, end)) {
            DayHours(tracked, start, end)
        } else {
            fallback.copy(tracked = tracked)
        }
    },
)

/** Writes all seven days. Called inside one edit, so the schedule changes as a whole. */
internal fun MutablePreferences.writeSchedule(schedule: WorkSchedule) {
    for (day in DayOfWeek.entries) {
        val hours = schedule.on(day)
        this[trackedKey(day)] = hours.tracked
        this[startKey(day)] = hours.start.toSecondOfDay() / SECONDS_PER_MINUTE
        this[endKey(day)] = hours.end.toSecondOfDay() / SECONDS_PER_MINUTE
    }
}

/** The time of day [minute] minutes after midnight, or null for a value that is no such time. */
private fun timeOfMinute(minute: Int?): LocalTime? = minute
    ?.takeIf { it in 0 until MINUTES_PER_DAY }
    ?.let { LocalTime.ofSecondOfDay(it.toLong() * SECONDS_PER_MINUTE) }

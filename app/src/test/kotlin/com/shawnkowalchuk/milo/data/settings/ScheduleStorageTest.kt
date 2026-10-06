package com.shawnkowalchuk.milo.data.settings

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.preferencesOf
import com.shawnkowalchuk.milo.core.schedule.DEFAULT_WORK_SCHEDULE
import com.shawnkowalchuk.milo.core.schedule.DayHours
import com.shawnkowalchuk.milo.core.schedule.WorkSchedule
import java.time.DayOfWeek
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * How the work schedule is kept in the settings file, and what is made of a file that holds
 * something the setters could not have written.
 */
class ScheduleStorageTest {
    private fun at(hour: Int, minute: Int = 0): LocalTime = LocalTime.of(hour, minute)

    /** What is read back after [schedule] has been written. */
    private fun stored(schedule: WorkSchedule): Preferences =
        mutablePreferencesOf().also { it.writeSchedule(schedule) }

    @Test
    fun `a file that has never held a schedule reads as Monday to Friday, 08 00 to 16 30`() {
        assertEquals(DEFAULT_WORK_SCHEDULE, emptyPreferences().readSchedule())
    }

    @Test
    fun `every day is read back as it was written`() {
        // Seven different days, the first and the last minute of a day among them.
        val schedule =
            WorkSchedule(
                DayOfWeek.entries.associateWith { day ->
                    DayHours(
                        tracked = day.value % 2 == 1,
                        start = at(day.value - 1, 5 * day.value),
                        end = at(16 + day.value, 59 - day.value),
                    )
                },
            ).with(DayOfWeek.SUNDAY, DayHours(true, LocalTime.MIDNIGHT, at(23, 59)))

        assertEquals(schedule, stored(schedule).readSchedule())
    }

    @Test
    fun `the names the schedule is stored under never change`() {
        // These are written to the settings file. A renamed key would silently put that day
        // back to its default hours.
        val written = stored(DEFAULT_WORK_SCHEDULE).asMap().mapKeys { it.key.name }

        assertEquals(21, written.size)
        assertEquals(true, written["schedule_monday_tracked"])
        assertEquals(8 * 60, written["schedule_monday_start_minute"])
        assertEquals(16 * 60 + 30, written["schedule_monday_end_minute"])
        assertEquals(false, written["schedule_sunday_tracked"])
        val days = written.keys.map { it.removePrefix("schedule_").substringBefore('_') }.toSet()
        assertEquals(
            setOf("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"),
            days,
        )
    }

    @Test
    fun `a day with only its switch stored keeps the default hours`() {
        val saturdayOn = preferencesOf(booleanPreferencesKey("schedule_saturday_tracked") to true)

        val schedule = saturdayOn.readSchedule()

        assertEquals(DayHours(true, at(8), at(16, 30)), schedule.on(DayOfWeek.SATURDAY))
        assertEquals(DEFAULT_WORK_SCHEDULE.on(DayOfWeek.MONDAY), schedule.on(DayOfWeek.MONDAY))
    }

    @Test
    fun `stored hours that end before they start read as the default hours of that day`() {
        // Not something a setter can write: the file was changed by something else.
        val backwards =
            preferencesOf(
                booleanPreferencesKey("schedule_monday_tracked") to false,
                intPreferencesKey("schedule_monday_start_minute") to 17 * 60,
                intPreferencesKey("schedule_monday_end_minute") to 9 * 60,
                intPreferencesKey("schedule_tuesday_start_minute") to 7 * 60,
            )

        val schedule = backwards.readSchedule()

        // The hours are the default ones; whether the day is tracked is kept as stored.
        assertEquals(DayHours(false, at(8), at(16, 30)), schedule.on(DayOfWeek.MONDAY))
        // A day whose stored hours make sense is not affected by its neighbour.
        assertEquals(DayHours(true, at(7), at(16, 30)), schedule.on(DayOfWeek.TUESDAY))
    }

    @Test
    fun `a stored time that is no time of day reads as the default for that time`() {
        val nonsense =
            preferencesOf(
                intPreferencesKey("schedule_monday_start_minute") to -5,
                intPreferencesKey("schedule_monday_end_minute") to 24 * 60,
                intPreferencesKey("schedule_tuesday_end_minute") to 99_999,
            )

        val schedule = nonsense.readSchedule()

        assertEquals(DEFAULT_WORK_SCHEDULE.on(DayOfWeek.MONDAY), schedule.on(DayOfWeek.MONDAY))
        assertEquals(DEFAULT_WORK_SCHEDULE.on(DayOfWeek.TUESDAY), schedule.on(DayOfWeek.TUESDAY))
    }
}

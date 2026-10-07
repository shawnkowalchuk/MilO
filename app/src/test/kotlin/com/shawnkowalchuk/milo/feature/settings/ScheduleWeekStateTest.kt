package com.shawnkowalchuk.milo.feature.settings

import com.shawnkowalchuk.milo.core.schedule.DEFAULT_WORK_SCHEDULE
import com.shawnkowalchuk.milo.data.settings.MiloSettings
import java.time.DayOfWeek
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The work schedule as the Settings screen's one slider needs it: the week's hours, whether
 * the work days agree on them, and where a refused time is said.
 */
class ScheduleWeekStateTest {
    private fun shown(
        settings: MiloSettings = MiloSettings(),
        problem: SettingsProblem? = null,
        problemDay: DayOfWeek? = null,
    ) = settingsUiState(settings, copyingSound = false, problem, problemDay)

    @Test
    fun `a fresh install shows one week of 08 00 to 16 30, as the design draws it`() {
        val week = shown().week

        assertEquals(LocalTime.of(8, 0), week.start)
        assertEquals(LocalTime.of(16, 30), week.end)
        assertTrue(week.daysAgree)
        assertFalse(week.hoursRefused)
    }

    @Test
    fun `a work day with hours of its own, and the week is shown day by day`() {
        val schedule = DEFAULT_WORK_SCHEDULE.withEnd(DayOfWeek.FRIDAY, LocalTime.of(13, 0))!!

        assertFalse(shown(MiloSettings(schedule = schedule)).week.daysAgree)
        // With that day switched off, the others agree again.
        val without = schedule.withTracked(DayOfWeek.FRIDAY, false)
        assertTrue(shown(MiloSettings(schedule = without)).week.daysAgree)
    }

    @Test
    fun `a time refused for the whole week is said under the week's hours, and under no day`() {
        val state = shown(problem = SettingsProblem.HOURS_END_NOT_AFTER_START, problemDay = null)

        assertTrue(state.week.hoursRefused)
        assertTrue(state.schedule.none { it.hoursRefused })
    }

    @Test
    fun `a time refused for one day is said under that day, and not under the week's hours`() {
        val state =
            shown(
                problem = SettingsProblem.HOURS_END_NOT_AFTER_START,
                problemDay = DayOfWeek.TUESDAY,
            )

        assertFalse(state.week.hoursRefused)
        assertEquals(
            listOf(DayOfWeek.TUESDAY),
            state.schedule.filter { it.hoursRefused }.map { it.day },
        )
    }

    @Test
    fun `no other problem is taken for refused hours`() {
        for (problem in SettingsProblem.entries - SettingsProblem.HOURS_END_NOT_AFTER_START) {
            assertFalse("$problem", shown(problem = problem).week.hoursRefused)
        }
    }

    @Test
    fun `the monthly reminder's line ends as English ends the day it names`() {
        val endings = (1..31).associateWith(::ordinalEnding)

        assertEquals(setOf(1, 21, 31), endings.filterValues { it == OrdinalEnding.ST }.keys)
        assertEquals(setOf(2, 22), endings.filterValues { it == OrdinalEnding.ND }.keys)
        assertEquals(setOf(3, 23), endings.filterValues { it == OrdinalEnding.RD }.keys)
        // Every other day, the 11th, the 12th and the 13th among them.
        assertEquals(24, endings.count { it.value == OrdinalEnding.TH })
        assertEquals(OrdinalEnding.TH, ordinalEnding(11))
        assertEquals(OrdinalEnding.TH, ordinalEnding(12))
        assertEquals(OrdinalEnding.TH, ordinalEnding(13))
    }
}

package com.shawnkowalchuk.milo.core.schedule

import java.time.DayOfWeek
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** The work schedule itself: its defaults, what hours a day may have, and the copy. */
class WorkScheduleTest {
    private fun at(hour: Int, minute: Int = 0): LocalTime = LocalTime.of(hour, minute)

    // ---- The defaults ---------------------------------------------------------------------------

    @Test
    fun `the default is Monday to Friday, 08 00 to 16 30, as the brief says`() {
        for (day in DayOfWeek.entries) {
            val hours = DEFAULT_WORK_SCHEDULE.on(day)
            val weekend = day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY

            assertEquals(day.name, !weekend, hours.tracked)
            // The weekend carries the same hours, for the day one of them is switched on.
            assertEquals(day.name, at(8), hours.start)
            assertEquals(day.name, at(16, 30), hours.end)
        }
    }

    @Test
    fun `a schedule has all seven days`() {
        val sixDays =
            DayOfWeek.entries
                .filter { it != DayOfWeek.SUNDAY }
                .associateWith { DayHours(true, at(8), at(16, 30)) }

        assertThrows(IllegalArgumentException::class.java) { WorkSchedule(sixDays) }
    }

    // ---- What hours a day may have --------------------------------------------------------------

    @Test
    fun `a day's end must be after its start`() {
        assertTrue(areValidHours(at(8), at(16, 30)))
        // The shortest day there is, and the longest.
        assertTrue(areValidHours(at(8), at(8, 1)))
        assertTrue(areValidHours(LocalTime.MIDNIGHT, at(23, 59)))

        assertFalse("Equal times leave no moment inside the day", areValidHours(at(8), at(8)))
        assertFalse(areValidHours(at(16, 30), at(8)))
        // No overnight spans: 22:00 to 06:00 is an end before a start, and is refused.
        assertFalse(areValidHours(at(22), at(6)))
    }

    @Test
    fun `hours are whole minutes, because that is what is stored`() {
        assertFalse(areValidHours(LocalTime.of(8, 0, 30), at(16, 30)))
        assertFalse(areValidHours(at(8), LocalTime.of(16, 30, 0, 1)))
    }

    @Test
    fun `hours that are not valid cannot be held at all`() {
        assertThrows(IllegalArgumentException::class.java) { DayHours(true, at(9), at(9)) }
        assertThrows(IllegalArgumentException::class.java) { DayHours(false, at(17), at(8)) }
    }

    // ---- Changing one day -----------------------------------------------------------------------

    @Test
    fun `a start or an end that would leave the day ending before it starts is refused`() {
        val schedule = DEFAULT_WORK_SCHEDULE

        // Monday is 08:00 to 16:30.
        assertNull(schedule.withStart(DayOfWeek.MONDAY, at(16, 30)))
        assertNull(schedule.withStart(DayOfWeek.MONDAY, at(17)))
        assertNull(schedule.withEnd(DayOfWeek.MONDAY, at(8)))
        assertNull(schedule.withEnd(DayOfWeek.MONDAY, at(7, 59)))
        assertNotNull(schedule.withStart(DayOfWeek.MONDAY, at(16, 29)))
        assertNotNull(schedule.withEnd(DayOfWeek.MONDAY, at(8, 1)))
    }

    @Test
    fun `changing one day's time changes that time and nothing else`() {
        val earlier = checkNotNull(DEFAULT_WORK_SCHEDULE.withStart(DayOfWeek.TUESDAY, at(7)))
        val later = checkNotNull(earlier.withEnd(DayOfWeek.TUESDAY, at(17, 15)))

        assertEquals(DayHours(true, at(7), at(17, 15)), later.on(DayOfWeek.TUESDAY))
        for (day in DayOfWeek.entries.filter { it != DayOfWeek.TUESDAY }) {
            assertEquals(day.name, DEFAULT_WORK_SCHEDULE.on(day), later.on(day))
        }
    }

    @Test
    fun `a day that is switched off keeps its hours for when it is switched on again`() {
        val custom = checkNotNull(DEFAULT_WORK_SCHEDULE.withStart(DayOfWeek.FRIDAY, at(6)))

        val off = custom.withTracked(DayOfWeek.FRIDAY, false)
        val onAgain = off.withTracked(DayOfWeek.FRIDAY, true)

        assertEquals(DayHours(false, at(6), at(16, 30)), off.on(DayOfWeek.FRIDAY))
        assertEquals(custom, onAgain)
    }

    // ---- The same hours on every tracked day ----------------------------------------------------

    @Test
    fun `while every tracked day has the same hours there is nothing to copy`() {
        for (day in DayOfWeek.entries) {
            assertFalse(day.name, DEFAULT_WORK_SCHEDULE.canCopyHoursOf(day))
        }
    }

    @Test
    fun `once a tracked day differs, every tracked day can be copied, and no other`() {
        val schedule = checkNotNull(DEFAULT_WORK_SCHEDULE.withStart(DayOfWeek.MONDAY, at(7)))

        for (day in DayOfWeek.entries) {
            val weekend = day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY
            assertEquals(day.name, !weekend, schedule.canCopyHoursOf(day))
        }
    }

    @Test
    fun `different hours on a day that is not tracked are nothing to copy over`() {
        val schedule = checkNotNull(DEFAULT_WORK_SCHEDULE.withStart(DayOfWeek.SATURDAY, at(10)))

        for (day in DayOfWeek.entries) assertFalse(day.name, schedule.canCopyHoursOf(day))
    }

    @Test
    fun `copying gives one day's hours to every tracked day and leaves the others alone`() {
        val saturdayOwnHours =
            checkNotNull(DEFAULT_WORK_SCHEDULE.withEnd(DayOfWeek.SATURDAY, at(12)))
        val mondayChanged =
            checkNotNull(
                saturdayOwnHours
                    .withStart(DayOfWeek.MONDAY, at(7))
                    ?.withEnd(DayOfWeek.MONDAY, at(17)),
            )

        val copied = mondayChanged.withHoursOfOnTrackedDays(DayOfWeek.MONDAY)

        for (day in DayOfWeek.entries.filter { it <= DayOfWeek.FRIDAY }) {
            assertEquals(day.name, DayHours(true, at(7), at(17)), copied.on(day))
        }
        // Not tracked: it is not switched on by the copy, and keeps its own hours.
        assertEquals(DayHours(false, at(8), at(12)), copied.on(DayOfWeek.SATURDAY))
        assertEquals(DEFAULT_WORK_SCHEDULE.on(DayOfWeek.SUNDAY), copied.on(DayOfWeek.SUNDAY))
        // And afterwards there is nothing left to copy.
        for (day in DayOfWeek.entries) assertFalse(day.name, copied.canCopyHoursOf(day))
    }

    @Test
    fun `copying from another day undoes a change made to one`() {
        val mondayChanged = checkNotNull(DEFAULT_WORK_SCHEDULE.withStart(DayOfWeek.MONDAY, at(7)))

        val copied = mondayChanged.withHoursOfOnTrackedDays(DayOfWeek.TUESDAY)

        assertEquals(DEFAULT_WORK_SCHEDULE, copied)
    }
}

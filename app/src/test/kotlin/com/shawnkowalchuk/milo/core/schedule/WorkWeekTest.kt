package com.shawnkowalchuk.milo.core.schedule

import java.time.DayOfWeek
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The schedule read as one work week: what the Settings screen's one slider shows, and the
 * change it makes. The schedule itself keeps hours for each day (`WorkScheduleTest`).
 */
class WorkWeekTest {
    private fun at(hour: Int, minute: Int = 0): LocalTime = LocalTime.of(hour, minute)

    private val default = DEFAULT_WORK_SCHEDULE

    private fun noWorkDays(): WorkSchedule =
        DayOfWeek.entries.fold(default) { schedule, day -> schedule.withTracked(day, false) }

    @Test
    fun `out of the box the week is five days of 08 00 to 16 30, which is 42 and a half hours`() {
        assertEquals(DayOfWeek.entries.take(5), default.workDays())
        assertTrue(default.workDaysAgree())
        assertEquals(at(8), default.weekHours().start)
        assertEquals(at(16, 30), default.weekHours().end)
        assertEquals(5 * (8 * 60 + 30), default.weeklyMinutes())
    }

    @Test
    fun `one work day with other hours, and the week no longer agrees`() {
        val shortFriday = default.withEnd(DayOfWeek.FRIDAY, at(13))!!

        assertFalse(shortFriday.workDaysAgree())
        // Four days of eight and a half hours, and one of five.
        assertEquals(4 * 510 + 300, shortFriday.weeklyMinutes())
    }

    @Test
    fun `other hours on a day that is switched off do not count against the week`() {
        val oddSaturday = default.withStart(DayOfWeek.SATURDAY, at(10))!!

        assertTrue(oddSaturday.workDaysAgree())
        assertEquals(default.weeklyMinutes(), oddSaturday.weeklyMinutes())
        // Switched on, that day brings its own hours back, and the week differs.
        assertFalse(oddSaturday.withTracked(DayOfWeek.SATURDAY, true).workDaysAgree())
    }

    @Test
    fun `the week is shown with the hours of its first work day`() {
        val weekend =
            noWorkDays()
                .with(DayOfWeek.SATURDAY, DayHours(tracked = true, start = at(9), end = at(12)))
                .withTracked(DayOfWeek.SUNDAY, true)

        assertEquals(at(9), weekend.weekHours().start)
        assertEquals(at(12), weekend.weekHours().end)
        assertFalse(weekend.workDaysAgree())
    }

    @Test
    fun `a week without a work day agrees, has no hours, and is shown with Monday's`() {
        val none = noWorkDays().withStart(DayOfWeek.MONDAY, at(6))!!

        assertTrue(none.workDays().isEmpty())
        assertTrue(none.workDaysAgree())
        assertEquals(0, none.weeklyMinutes())
        assertEquals(at(6), none.weekHours().start)
    }

    @Test
    fun `the week's hours go to all seven days, and no day is switched on or off by it`() {
        val moved = default.withHoursOnEveryDay(at(7), at(15, 45))!!

        for (day in DayOfWeek.entries) {
            assertEquals(at(7), moved.on(day).start)
            assertEquals(at(15, 45), moved.on(day).end)
            assertEquals(default.on(day).tracked, moved.on(day).tracked)
        }
        // So a day that is switched on afterwards has the hours the one slider shows.
        assertTrue(moved.withTracked(DayOfWeek.SATURDAY, true).workDaysAgree())
    }

    @Test
    fun `the week's hours make days that differed agree again`() {
        val shortFriday = default.withEnd(DayOfWeek.FRIDAY, at(13))!!

        assertTrue(shortFriday.withHoursOnEveryDay(at(8), at(16))!!.workDaysAgree())
    }

    @Test
    fun `hours that do not end after they start are refused for the week as for a day`() {
        assertNull(default.withHoursOnEveryDay(at(16), at(16)))
        assertNull(default.withHoursOnEveryDay(at(17), at(9)))
        assertNull(default.withHoursOnEveryDay(at(8), LocalTime.of(16, 30, 5)))
    }

    @Test
    fun `a new start or end for no day in particular is one for the whole week`() {
        val earlier = default.withStartOn(null, at(6, 30))!!
        val later = default.withEndOn(null, at(18))!!

        for (day in DayOfWeek.entries) {
            assertEquals(DayHours(default.on(day).tracked, at(6, 30), at(16, 30)), earlier.on(day))
            assertEquals(DayHours(default.on(day).tracked, at(8), at(18)), later.on(day))
        }
        // A start after the week's end, or an end before its start, is refused.
        assertNull(default.withStartOn(null, at(17)))
        assertNull(default.withEndOn(null, at(8)))
    }

    @Test
    fun `a new start or end for one day changes that day only, as before`() {
        assertEquals(
            default.withStart(DayOfWeek.TUESDAY, at(9)),
            default.withStartOn(DayOfWeek.TUESDAY, at(9)),
        )
        assertEquals(
            default.withEnd(DayOfWeek.TUESDAY, at(12)),
            default.withEndOn(DayOfWeek.TUESDAY, at(12)),
        )
        assertNull(default.withStartOn(DayOfWeek.TUESDAY, at(20)))
    }
}

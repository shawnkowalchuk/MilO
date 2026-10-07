package com.shawnkowalchuk.milo.feature.settings

import com.shawnkowalchuk.milo.core.designsystem.component.Span
import com.shawnkowalchuk.milo.core.schedule.areValidHours
import java.time.DayOfWeek
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How the work schedule's hours stand on the Settings screen's slider: which line is drawn,
 * and how a place on it becomes a time of day and back.
 */
class ScheduleSliderTest {
    private fun at(hour: Int, minute: Int = 0): LocalTime = LocalTime.of(hour, minute)

    private fun span(start: LocalTime, end: LocalTime) =
        Span(start.minuteOfDay(), end.minuteOfDay())

    private fun day(day: DayOfWeek, tracked: Boolean, start: LocalTime, end: LocalTime) =
        ScheduleDay(day, tracked, start, end, canCopy = false, hoursRefused = false)

    @Test
    fun `the drawn line runs from 4 AM to 10 PM in quarters of an hour, as the design's script`() {
        val drawn = DRAWN_HOURS_SCALE

        assertEquals(at(4), timeAtMinute(drawn.from))
        assertEquals(at(22), timeAtMinute(drawn.to))
        assertEquals(15, drawn.step)
        assertEquals(30, drawn.minimumSpan)
        assertEquals(
            listOf(at(4), at(9), at(13), at(18), at(22)),
            drawn.marks.map(::hourOfMark),
        )
    }

    @Test
    fun `a time of day and the slider's minute are each other's inverse`() {
        assertEquals(480, at(8).minuteOfDay())
        assertEquals(990, at(16, 30).minuteOfDay())
        for (minute in 0 until 24 * 60) {
            assertEquals(minute, timeAtMinute(minute).minuteOfDay())
        }
    }

    @Test
    fun `hours inside the drawn line are shown on it, its two ends included`() {
        assertSame(DRAWN_HOURS_SCALE, hoursScale(listOf(span(at(8), at(16, 30)))))
        assertSame(DRAWN_HOURS_SCALE, hoursScale(listOf(span(at(4), at(22)))))
        assertSame(DRAWN_HOURS_SCALE, hoursScale(emptyList()))
    }

    @Test
    fun `hours that reach outside the drawn line are shown on the whole day`() {
        assertSame(WHOLE_DAY_SCALE, hoursScale(listOf(span(at(3, 59), at(16, 30)))))
        assertSame(WHOLE_DAY_SCALE, hoursScale(listOf(span(at(8), at(22, 1)))))
        // One day outside is enough: every slider of the tile stands on the same line.
        assertSame(
            WHOLE_DAY_SCALE,
            hoursScale(listOf(span(at(8), at(16, 30)), span(at(0), at(23, 59)))),
        )
    }

    @Test
    fun `every hours a day can be stored with stand on a line, where they are`() {
        // The earliest start and the latest end there are, and a time between two quarters.
        for (stored in listOf(
            span(at(0), at(0, 1)),
            span(at(0), at(23, 59)),
            span(at(8, 7), at(9)),
        )) {
            val line = hoursScale(listOf(stored))

            assertTrue(stored.start >= line.from && stored.end <= line.to)
            assertEquals(
                (stored.start - line.from).toFloat() / (line.to - line.from),
                line.fractionOf(stored.start),
                1e-6f,
            )
        }
    }

    @Test
    fun `the whole day's line names midnight at both ends`() {
        assertEquals(
            listOf(at(0), at(6), at(12), at(18), at(0)),
            WHOLE_DAY_SCALE.marks.map(::hourOfMark),
        )
    }

    @Test
    fun `a handle at the very end of the whole day is 23 59, the latest end there is`() {
        val dragged = WHOLE_DAY_SCALE.withEndAt(1f, span(at(8), at(16, 30)))

        assertEquals(24 * 60, dragged.end)
        assertEquals(at(23, 59), timeAtMinute(dragged.storable().end))
        assertEquals(at(8), timeAtMinute(dragged.storable().start))
        // And it stays where it was let go: one minute before the end of the line.
        assertEquals(1f, WHOLE_DAY_SCALE.fractionOf(dragged.storable().end), 0.001f)
    }

    @Test
    fun `whatever a handle is moved to, on either line, is hours the schedule takes`() {
        for (line in listOf(DRAWN_HOURS_SCALE, WHOLE_DAY_SCALE)) {
            val whole = Span(line.from, line.to)
            for (step in 0..(line.to - line.from) / line.step) {
                val fraction = step.toFloat() / ((line.to - line.from) / line.step)
                for (moved in listOf(
                    line.withStartAt(fraction, whole),
                    line.withEndAt(fraction, whole),
                )) {
                    val stored = moved.storable()
                    assertTrue(
                        "$moved",
                        areValidHours(timeAtMinute(stored.start), timeAtMinute(stored.end)),
                    )
                }
            }
        }
    }

    @Test
    fun `a slider shows what is stored until its own handle is moved`() {
        val stored = span(at(8), at(16, 30))
        val moving = HeldHours(DayOfWeek.FRIDAY, span(at(7), at(16, 30)), DRAWN_HOURS_SCALE)

        assertEquals(stored, shownSpan(DayOfWeek.MONDAY, stored, held = null))
        assertEquals(stored, shownSpan(DayOfWeek.MONDAY, stored, moving))
        assertEquals(moving.span, shownSpan(DayOfWeek.FRIDAY, stored, moving))
        // The week's own slider is not a day's.
        assertEquals(stored, shownSpan(null, stored, moving))
    }

    @Test
    fun `the one slider of the week stands for every day while its handle is moved`() {
        val stored = span(at(8), at(16, 30))
        val moving = HeldHours(day = null, span(at(9), at(16, 30)), DRAWN_HOURS_SCALE)

        assertEquals(moving.span, shownSpan(null, stored, moving))
        assertEquals(moving.span, shownSpan(DayOfWeek.TUESDAY, stored, moving))
    }

    @Test
    fun `the hours a week counts along with a moving handle, and only work days count`() {
        val days =
            DayOfWeek.entries.map { day(it, tracked = it < DayOfWeek.SATURDAY, at(8), at(16, 30)) }

        assertEquals(5 * 510, shownWeeklyMinutes(days, held = null))
        // The week's handle an hour later: five days, an hour less each.
        val week = HeldHours(day = null, span(at(9), at(16, 30)), DRAWN_HOURS_SCALE)
        assertEquals(5 * 450, shownWeeklyMinutes(days, week))
        // One day's handle: that day only.
        val friday = HeldHours(DayOfWeek.FRIDAY, span(at(8), at(12)), DRAWN_HOURS_SCALE)
        assertEquals(4 * 510 + 240, shownWeeklyMinutes(days, friday))
        // A day that is switched off adds nothing, whatever its slider would show.
        val saturday = HeldHours(DayOfWeek.SATURDAY, span(at(8), at(12)), DRAWN_HOURS_SCALE)
        assertEquals(5 * 510, shownWeeklyMinutes(days, saturday))
    }
}

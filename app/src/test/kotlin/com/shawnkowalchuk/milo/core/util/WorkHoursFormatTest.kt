package com.shawnkowalchuk.milo.core.util

import java.time.DayOfWeek
import java.time.LocalTime
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** What the work schedule's tile writes on its day buttons, under its slider and as hours. */
class WorkHoursFormatTest {
    @Test
    fun `a day's button carries its short name`() {
        assertEquals("Mon", formatShortDayOfWeek(DayOfWeek.MONDAY, Locale.US))
        assertEquals("Sun", formatShortDayOfWeek(DayOfWeek.SUNDAY, Locale.US))
    }

    @Test
    fun `an hour under the slider is written as the design writes it`() {
        assertEquals("4 AM", formatHourMark(LocalTime.of(4, 0), Locale.US, false))
        assertEquals("1 PM", formatHourMark(LocalTime.of(13, 0), Locale.US, false))
        assertEquals("10 PM", formatHourMark(LocalTime.of(22, 0), Locale.US, false))
        assertEquals("12 AM", formatHourMark(LocalTime.MIDNIGHT, Locale.US, false))
        assertEquals("12 PM", formatHourMark(LocalTime.NOON, Locale.US, false))
    }

    @Test
    fun `on a phone set to 24 hours an hour is written with its minutes`() {
        assertEquals("04:00", formatHourMark(LocalTime.of(4, 0), Locale.US, true))
        assertEquals("22:00", formatHourMark(LocalTime.of(22, 0), Locale.US, true))
        assertEquals("00:00", formatHourMark(LocalTime.MIDNIGHT, Locale.US, true))
    }

    @Test
    fun `whole hours carry no decimal, and the design's own week is 42 and a half`() {
        assertEquals("40", formatHours(40 * 60, Locale.US))
        assertEquals("42.5", formatHours(5 * 510, Locale.US))
        assertEquals("0", formatHours(0, Locale.US))
    }

    @Test
    fun `hours are rounded to a tenth, and a tenth that rounds to a whole hour is one`() {
        // Five days of 8:07 to 16:30: 41 hours and 55 minutes.
        assertEquals("41.9", formatHours(5 * 503, Locale.US))
        // Three minutes short of 42 hours.
        assertEquals("42", formatHours(42 * 60 - 3, Locale.US))
        assertEquals("0.5", formatHours(30, Locale.US))
    }

    @Test
    fun `the decimal separator is the language's`() {
        assertEquals("42,5", formatHours(5 * 510, Locale.GERMANY))
    }

    @Test
    fun `hours that are negative are refused`() {
        assertThrows(IllegalArgumentException::class.java) { formatHours(-1, Locale.US) }
    }
}

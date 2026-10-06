package com.shawnkowalchuk.milo.core.util

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

private const val MINUTE_MS = 60_000L

/** A length of time as hours and minutes, and a number of seconds as a minutes figure. */
class DurationFormatTest {
    @Test
    fun `under an hour there are only minutes`() {
        assertEquals(HoursAndMinutes(hours = 0, minutes = 23), wholeHoursAndMinutes(23 * MINUTE_MS))
    }

    @Test
    fun `an hour and more is split into hours and the minutes left over`() {
        assertEquals(HoursAndMinutes(1, 5), wholeHoursAndMinutes(65 * MINUTE_MS))
        assertEquals(HoursAndMinutes(2, 0), wholeHoursAndMinutes(120 * MINUTE_MS))
        // A long day of driving does not wrap round at 24 hours.
        assertEquals(HoursAndMinutes(26, 30), wholeHoursAndMinutes(1_590 * MINUTE_MS))
    }

    @Test
    fun `minutes are rounded down, as a stopwatch counts them`() {
        assertEquals(HoursAndMinutes(0, 0), wholeHoursAndMinutes(MINUTE_MS - 1))
        assertEquals(HoursAndMinutes(0, 59), wholeHoursAndMinutes(60 * MINUTE_MS - 1))
        assertEquals(HoursAndMinutes(1, 0), wholeHoursAndMinutes(60 * MINUTE_MS))
    }

    @Test
    fun `a negative length reads as nothing`() {
        // The phone's clock was set back while a trip was open.
        assertEquals(HoursAndMinutes(0, 0), wholeHoursAndMinutes(-5 * MINUTE_MS))
    }

    @Test
    fun `whole minutes carry no decimal`() {
        assertEquals("2", formatMinutes(120, Locale.CANADA))
        assertEquals("10", formatMinutes(600, Locale.CANADA))
        assertEquals("0", formatMinutes(0, Locale.CANADA))
    }

    @Test
    fun `half minutes carry one`() {
        assertEquals("0.5", formatMinutes(30, Locale.CANADA))
        assertEquals("2.5", formatMinutes(150, Locale.CANADA))
    }

    @Test
    fun `the decimal separator follows the language`() {
        assertEquals("2,5", formatMinutes(150, Locale.GERMANY))
    }

    @Test
    fun `a value that is no half minute is shown to one decimal, not hidden`() {
        // Not offered by the Settings screen, but a stored value is shown as it is.
        assertEquals("0.8", formatMinutes(45, Locale.CANADA))
    }

    @Test
    fun `a negative number of seconds is refused`() {
        assertThrows(IllegalArgumentException::class.java) { formatMinutes(-30, Locale.CANADA) }
    }
}

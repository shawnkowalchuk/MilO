package com.shawnkowalchuk.milo.data.settings

import com.shawnkowalchuk.milo.core.util.DistanceUnit
import com.shawnkowalchuk.milo.core.util.formatDistance
import com.shawnkowalchuk.milo.core.util.formatMinutes
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The values the Settings screen offers for the grace period and the minimum trip distance, and
 * what they look like on the screen.
 */
class SettingChoicesTest {
    /** Every value reached by pressing plus from the bottom of the range. */
    private fun SteppedChoice.allValues(): List<Int> =
        generateSequence(min) { value -> stepUp(value).takeIf { it != value } }.toList()

    // ---- The grace period -------------------------------------------------------------------------

    @Test
    fun `the grace period runs from half a minute to ten minutes in half minutes`() {
        val shown = GRACE_PERIOD_CHOICE.allValues().map { formatMinutes(it, Locale.CANADA) }

        assertEquals(20, shown.size)
        assertEquals(listOf("0.5", "1", "1.5", "2", "2.5"), shown.take(5))
        assertEquals(listOf("9", "9.5", "10"), shown.takeLast(3))
    }

    @Test
    fun `the default grace period is one of the values offered`() {
        assertTrue(DEFAULT_GRACE_PERIOD_SECONDS in GRACE_PERIOD_CHOICE.allValues())
        assertEquals("2", formatMinutes(DEFAULT_GRACE_PERIOD_SECONDS, Locale.CANADA))
    }

    @Test
    fun `one step from the default grace period is half a minute either way`() {
        assertEquals(150, GRACE_PERIOD_CHOICE.stepUp(DEFAULT_GRACE_PERIOD_SECONDS))
        assertEquals(90, GRACE_PERIOD_CHOICE.stepDown(DEFAULT_GRACE_PERIOD_SECONDS))
    }

    // ---- The minimum trip distance ----------------------------------------------------------------

    @Test
    fun `the minimum distance runs from 100 m to 2 km in tenths of a kilometre`() {
        val shown =
            MINIMUM_TRIP_DISTANCE_CHOICE.allValues().map {
                formatDistance(it.toDouble(), DistanceUnit.KILOMETRES, Locale.CANADA)
            }

        assertEquals(20, shown.size)
        assertEquals(listOf("0.1", "0.2", "0.3"), shown.take(3))
        assertEquals(listOf("1.9", "2.0"), shown.takeLast(2))
    }

    @Test
    fun `the default minimum distance is one of the values offered`() {
        val metres = DEFAULT_MINIMUM_TRIP_DISTANCE_METRES

        assertTrue(metres in MINIMUM_TRIP_DISTANCE_CHOICE.allValues())
        assertEquals(
            "0.3",
            formatDistance(metres.toDouble(), DistanceUnit.KILOMETRES, Locale.CANADA),
        )
    }

    // ---- Stepping ---------------------------------------------------------------------------------

    @Test
    fun `stepping stops at both ends, and says so beforehand`() {
        val choice = GRACE_PERIOD_CHOICE

        assertEquals(choice.max, choice.stepUp(choice.max))
        assertEquals(choice.min, choice.stepDown(choice.min))
        assertFalse(choice.canStepUp(choice.max))
        assertFalse(choice.canStepDown(choice.min))
        assertTrue(choice.canStepDown(choice.max))
        assertTrue(choice.canStepUp(choice.min))
    }

    @Test
    fun `stepping down undoes stepping up`() {
        for (choice in listOf(GRACE_PERIOD_CHOICE, MINIMUM_TRIP_DISTANCE_CHOICE)) {
            for (value in choice.allValues().dropLast(1)) {
                assertEquals(value, choice.stepDown(choice.stepUp(value)))
            }
        }
    }

    @Test
    fun `a stored value between two steps goes to the nearest step in the direction pressed`() {
        // 45 s is not offered; an older version or a hand edit could have stored it.
        assertEquals(60, GRACE_PERIOD_CHOICE.stepUp(45))
        assertEquals(30, GRACE_PERIOD_CHOICE.stepDown(45))
        assertEquals(400, MINIMUM_TRIP_DISTANCE_CHOICE.stepUp(350))
        assertEquals(300, MINIMUM_TRIP_DISTANCE_CHOICE.stepDown(350))
    }

    @Test
    fun `a stored value outside the range is brought to its nearest end`() {
        val choice = GRACE_PERIOD_CHOICE

        // Zero is a value the settings store accepts and the screen does not offer.
        assertEquals(choice.min, choice.stepUp(0))
        assertEquals(choice.min, choice.stepDown(0))
        assertTrue(choice.canStepUp(0))
        assertFalse(choice.canStepDown(0))

        assertEquals(choice.max, choice.stepDown(3_600))
        assertEquals(choice.max, choice.stepUp(3_600))
        assertTrue(choice.canStepDown(3_600))
        assertFalse(choice.canStepUp(3_600))
    }

    @Test
    fun `a range that its step does not divide is refused`() {
        assertThrows(IllegalArgumentException::class.java) { SteppedChoice(0, 100, 30) }
        assertThrows(IllegalArgumentException::class.java) { SteppedChoice(100, 0, 10) }
        assertThrows(IllegalArgumentException::class.java) { SteppedChoice(0, 100, 0) }
    }
}

package com.shawnkowalchuk.milo.core.designsystem.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which digit stands on each wheel of a row, and which wheels turn together: the carry of a
 * dashboard's odometer, held by numbers and not by the eye.
 */
class WheelPositionsTest {
    private fun digits(steps: Long, rolling: Boolean = false): List<Int> =
        wheelsOf(steps, rolling).map { it.digit }

    /** The wheels that are turning, counted from the last one: 0 is the tenth's. */
    private fun turning(steps: Long): List<Int> =
        wheelsOf(steps, rolling = true).reversed().mapIndexedNotNull { place, wheel ->
            place.takeIf { wheel.turning }
        }

    @Test
    fun `each digit has a wheel of its own, the tenth's last`() {
        // 123,460.6 km.
        assertEquals(listOf(1, 2, 3, 4, 6, 0, 6), digits(1_234_606))
        assertEquals(listOf(8, 6, 4, 1, 2, 0), digits(864_120))
    }

    @Test
    fun `a figure under one whole unit has a nought before its tenth`() {
        assertEquals(listOf(0, 5), digits(5))
        assertEquals(listOf(0, 0), digits(0))
        assertEquals(listOf(1, 0), digits(10))
    }

    @Test
    fun `a row that stands has no wheel that turns`() {
        assertTrue(wheelsOf(1_234_609, rolling = false).none { it.turning })
        assertTrue(wheelsOf(999, rolling = false).none { it.turning })
    }

    @Test
    fun `while the row rolls the last wheel turns, and the others stand`() {
        assertEquals(listOf(0), turning(1_234_606))
        assertEquals(listOf(0), turning(1_234_690))
    }

    @Test
    fun `while the last wheel passes from 9 to 0 the wheel before it rolls on by one`() {
        val wheels = wheelsOf(1_234_609, rolling = true)

        assertEquals(listOf(0, 1), turning(1_234_609))
        // The whole kilometres' last wheel shows 0 and is on its way to 1.
        assertEquals(0, wheels[wheels.lastIndex - 1].digit)
        assertEquals(1, wheels[wheels.lastIndex - 1].next)
        assertEquals(9, wheels.last().digit)
        assertEquals(0, wheels.last().next)
    }

    @Test
    fun `a carry runs through every wheel at 9, and stops at the first that is not`() {
        // 129,999.9 km: the tenth, four nines and the 2 turn; the 1 stands.
        assertEquals(listOf(0, 1, 2, 3, 4, 5), turning(1_299_999))
        assertFalse(wheelsOf(1_299_999, rolling = true).first().turning)
        // 123,499.9: the tenth, two nines and the 4.
        assertEquals(listOf(0, 1, 2, 3), turning(1_234_999))
    }

    @Test
    fun `when every wheel stands at 9 a nought comes in front for the carry to turn`() {
        assertEquals(listOf(0, 9, 9, 9), digits(999, rolling = true))
        assertEquals(listOf(0, 1, 2, 3), turning(999))
        // Standing, the same figure has no such wheel.
        assertEquals(listOf(9, 9, 9), digits(999))
        // And once the carry is through, the wheel it turned shows 1.
        assertEquals(listOf(1, 0, 0, 0), digits(1_000, rolling = true))
    }

    @Test
    fun `a row cannot show a figure below nought`() {
        assertThrows(IllegalArgumentException::class.java) { wheelsOf(-1, rolling = false) }
    }

    @Test
    fun `what a row has rolled is the whole steps it passed and the part of the next`() {
        assertEquals(RolledBy(2, 0.25f), rolledBy(2.25f))
        assertEquals(RolledBy(0, 0f), rolledBy(0f))
        assertEquals(RolledBy(7, 0f), rolledBy(7f))
        // Half-way up to the digit it started at: the step before, half turned.
        assertEquals(RolledBy(-1, 0.5f), rolledBy(-0.5f))
        assertEquals(RolledBy(-3, 0.75f), rolledBy(-2.25f))
    }
}

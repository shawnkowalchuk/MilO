package com.shawnkowalchuk.milo.core.util

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DistanceFormatTest {
    @Test
    fun `formats metres as kilometres with one decimal`() {
        assertEquals("23.4", formatKilometres(23_400.0, Locale.ROOT))
    }

    @Test
    fun `keeps the decimal for zero and for whole kilometres`() {
        assertEquals("0.0", formatKilometres(0.0, Locale.ROOT))
        assertEquals("5.0", formatKilometres(5_000.0, Locale.ROOT))
    }

    @Test
    fun `rounds to the nearest tenth of a kilometre`() {
        assertEquals("23.4", formatKilometres(23_449.0, Locale.ROOT))
        assertEquals("23.5", formatKilometres(23_451.0, Locale.ROOT))
        assertEquals("1.0", formatKilometres(999.0, Locale.ROOT))
    }

    @Test
    fun `shows the minimum trip distance of 300 metres as 0 point 3`() {
        assertEquals("0.3", formatKilometres(300.0, Locale.ROOT))
    }

    @Test
    fun `does not group thousands so a month total stays one plain number`() {
        assertEquals("1234.6", formatKilometres(1_234_560.0, Locale.ROOT))
    }

    @Test
    fun `uses the decimal separator of the given locale`() {
        assertEquals("23,4", formatKilometres(23_400.0, Locale.FRANCE))
    }

    @Test
    fun `rejects a negative distance`() {
        assertThrows(IllegalArgumentException::class.java) {
            formatKilometres(-1.0, Locale.ROOT)
        }
    }

    @Test
    fun `rejects a distance that is not a finite number`() {
        assertThrows(IllegalArgumentException::class.java) {
            formatKilometres(Double.NaN, Locale.ROOT)
        }
        assertThrows(IllegalArgumentException::class.java) {
            formatKilometres(Double.POSITIVE_INFINITY, Locale.ROOT)
        }
    }

    // ---- One rule for a single distance and for a total ------------------------------------------

    @Test
    fun `a distance is rounded to tenths of a kilometre, half a tenth upwards`() {
        assertEquals(123, tenthsOfAKilometre(12_349.9))
        assertEquals(124, tenthsOfAKilometre(12_350.0))
        assertEquals(3, tenthsOfAKilometre(250.0))
        assertEquals(0, tenthsOfAKilometre(49.9))
    }

    @Test
    fun `the figure shown for a trip is the figure that is added up for it`() {
        // Every distance from nothing to five kilometres, a metre at a time and in between.
        var metres = 0.0
        while (metres <= 5_000.0) {
            val shown = formatKilometres(metres, Locale.ROOT)
            assertEquals(shown, formatTenths(tenthsOfAKilometre(metres), Locale.ROOT))
            metres += 0.5
        }
    }

    @Test
    fun `a total is the sum of the figures printed for its trips, not of their metres`() {
        // Each of these prints as 0.1 km. Their 447 m, rounded once, would print as 0.4.
        assertEquals(3, sumOfTenths(listOf(149.0, 149.0, 149.0)))
        // And the other way: three times 0.2 km is 0.6, where 453 m would print as 0.5.
        assertEquals(6, sumOfTenths(listOf(151.0, 151.0, 151.0)))
        assertEquals(0, sumOfTenths(emptyList()))
    }

    @Test
    fun `whoever adds the printed figures up by hand gets the printed total`() {
        val trips = listOf(12_349.0, 8_251.0, 149.0, 30_050.0, 999.0, 0.0, 23_449.9)

        val byHand = trips.sumOf { formatKilometres(it, Locale.ROOT).toBigDecimal() }

        assertEquals(byHand.toPlainString(), formatTenths(sumOfTenths(trips), Locale.ROOT))
    }

    @Test
    fun `over a month the two ways of adding up drift apart, which is why there is one`() {
        // 193 trips that each lose 4 m to the rounding, as in the month that was looked at.
        val trips = List(193) { 11_504.0 }

        val asPrinted = formatTenths(sumOfTenths(trips), Locale.ROOT)
        val metresRoundedOnce = formatKilometres(trips.sum(), Locale.ROOT)

        assertEquals("2219.5", asPrinted)
        assertEquals("2220.3", metresRoundedOnce)
    }

    @Test
    fun `a corrupt distance among the trips stops the total, like a single figure`() {
        assertThrows(IllegalArgumentException::class.java) { sumOfTenths(listOf(5.0, -1.0)) }
        assertThrows(IllegalArgumentException::class.java) {
            sumOfTenths(listOf(5.0, Double.NaN))
        }
    }

    @Test
    fun `tenths go back to metres without a remainder`() {
        assertEquals(412_300.0, metresOfTenths(4_123), 0.0)
        assertEquals(4_123, tenthsOfAKilometre(metresOfTenths(4_123)))
    }
}

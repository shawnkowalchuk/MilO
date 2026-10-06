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
}

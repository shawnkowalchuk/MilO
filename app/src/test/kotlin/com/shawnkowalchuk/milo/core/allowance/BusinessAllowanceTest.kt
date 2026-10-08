package com.shawnkowalchuk.milo.core.allowance

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Business kilometres priced at the one rate Shawn sets, for reference. */
class BusinessAllowanceTest {
    @Test
    fun `out of the box the rate is 70 cents`() {
        assertEquals(70, DEFAULT_CENTS_PER_KM)
    }

    @Test
    fun `every kilometre is at the same rate, however many came before`() {
        // 123.4 km at 70 cents: $86.38.
        assertEquals(8_638L, allowanceCents(1_234, 70))
        // 6,000 km, well past the CRA's 5,000: still 70 cents each.
        assertEquals(420_000L, allowanceCents(60_000, 70))
    }

    @Test
    fun `cents are rounded once, at the end`() {
        // 0.1 km at 73 cents is 7.3 cents: 7. Two of them are 14.6: 15.
        assertEquals(7L, allowanceCents(1, 73))
        assertEquals(15L, allowanceCents(2, 73))
        assertEquals(0L, allowanceCents(0, 70))
    }

    @Test
    fun `a rate is typed in dollars a kilometre`() {
        assertEquals(70, parseCentsPerKm("0.70"))
        assertEquals(70, parseCentsPerKm(".7"))
        assertEquals(70, parseCentsPerKm(" $0.70 "))
        assertEquals(73, parseCentsPerKm("0,73"))
        assertEquals(100, parseCentsPerKm("1"))
        assertEquals(100, parseCentsPerKm("1."))
        assertEquals(1, parseCentsPerKm("0.01"))
        assertEquals(500, parseCentsPerKm("5.00"))
    }

    @Test
    fun `what is not a rate in whole cents from a cent to five dollars is refused`() {
        listOf("", " ", "$", ".", "abc", "0.705", "0", "0.00", "5.01", "-0.70", "1.2.3", "70¢")
            .forEach { assertNull("\"$it\"", parseCentsPerKm(it)) }
    }

    @Test
    fun `a rate is written in dollars, to the cent`() {
        assertEquals("$0.70", formatCentsPerKm(70, Locale.CANADA))
        assertEquals("$0.05", formatCentsPerKm(5, Locale.CANADA))
        assertEquals("$5.00", formatCentsPerKm(500, Locale.CANADA))
    }

    @Test
    fun `dollars are whole and grouped`() {
        assertEquals("$1,235", formatWholeDollars(123_450, Locale.CANADA))
        assertEquals("$0", formatWholeDollars(49, Locale.CANADA))
    }
}

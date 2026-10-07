package com.shawnkowalchuk.milo.core.allowance

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Business kilometres priced at the CRA's per-kilometre rate, for reference. */
class CraAllowanceTest {
    private val rate2026 = CraRate(2026, firstTierCents = 73, afterCents = 67)

    @Test
    fun `the 2026 rates are 73 cents for the first 5000 km and 67 after`() {
        assertEquals(rate2026, craRateFor(2026))
        assertEquals(CraRate(2025, 72, 66), craRateFor(2025))
    }

    @Test
    fun `a year without its own rates uses the newest known, and says so`() {
        assertEquals(rate2026, craRateFor(2027))
        val priced = businessAllowance(2027, tenthsBeforeMonth = 0, monthTenths = 10)
        assertFalse(priced.rateIsTheYears)
        assertTrue(businessAllowance(2026, 0, 10).rateIsTheYears)
    }

    @Test
    fun `kilometres within the first 5000 are at the first rate`() {
        // 123.4 km at 73 cents: $90.08.
        assertEquals(9_008L, allowanceCents(0, 1_234, rate2026))
    }

    @Test
    fun `kilometres past 5000 are at the second rate, also when a trip crosses the line`() {
        // 4,990 km driven before; 30 km now: 10 at 73 cents, 20 at 67.
        assertEquals(730L + 1_340L, allowanceCents(49_900, 300, rate2026))
        // All past it.
        assertEquals(6_700L, allowanceCents(60_000, 1_000, rate2026))
    }

    @Test
    fun `the month is priced where it falls in the year`() {
        // 4,900 km in the months before, 200 km this month.
        val priced = businessAllowance(2026, tenthsBeforeMonth = 49_000, monthTenths = 2_000)

        assertEquals(100 * 73L + 100 * 67L, priced.monthCents)
        assertEquals(5_000 * 73L + 100 * 67L, priced.yearCents)
    }

    @Test
    fun `cents are rounded once, at the end`() {
        // 0.1 km at 73 cents is 7.3 cents: 7. Two of them are 14.6: 15.
        assertEquals(7L, allowanceCents(0, 1, rate2026))
        assertEquals(15L, allowanceCents(0, 2, rate2026))
    }

    @Test
    fun `dollars are whole and grouped`() {
        assertEquals("$1,235", formatWholeDollars(123_450, Locale.CANADA))
        assertEquals("$0", formatWholeDollars(49, Locale.CANADA))
    }
}

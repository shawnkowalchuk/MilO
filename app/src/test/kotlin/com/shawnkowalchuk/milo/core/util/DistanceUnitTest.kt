package com.shawnkowalchuk.milo.core.util

import java.math.BigDecimal
import java.util.Locale
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * The one rule for a printed distance, in either unit (Shawn's request of 2026-10-07 to choose
 * kilometres or miles in Settings): a trip is rounded once to a tenth of the unit it is shown
 * in, and a total is the sum of those figures.
 *
 * The first half holds kilometres to what they were before there was a choice, to the digit.
 * The second half is the same rule in miles.
 */
class DistanceUnitTest {
    private val km = DistanceUnit.KILOMETRES
    private val mi = DistanceUnit.MILES

    /** The rule as it was written before 2026-10-07, when kilometres were the only unit. */
    private fun asItWas(metres: Double): Long = Math.round(metres / 100.0)

    // ---- Kilometres do not change by a digit -----------------------------------------------------

    @Test
    fun `in kilometres the awkward distances print what they always printed`() {
        val printed =
            mapOf(
                0.0 to "0.0",
                49.0 to "0.0",
                50.0 to "0.1",
                12_349.0 to "12.3",
                12_350.0 to "12.4",
                1_609.344 to "1.6",
                100_050.0 to "100.1",
                9_999_999_949.0 to "9999999.9",
            )

        for ((metres, figure) in printed) {
            assertEquals("$metres m", figure, formatDistance(metres, km, Locale.ROOT))
            assertEquals("$metres m", asItWas(metres), tenthsOf(metres, km))
        }
    }

    @Test
    fun `in kilometres every distance is rounded exactly as it was before there was a choice`() {
        // Every metre and half metre of a long trip, the binary fractions next to each half
        // tenth, where a different way of dividing would show first, and distances at random.
        var metres = 0.0
        while (metres <= 30_000.0) {
            assertEquals("$metres m", asItWas(metres), tenthsOf(metres, km))
            metres += 0.5
        }
        for (tenth in 0L..20_000L) {
            val half = tenth * 100.0 + 50.0
            for (near in listOf(Math.nextDown(half), half, Math.nextUp(half))) {
                assertEquals("$near m", asItWas(near), tenthsOf(near, km))
            }
        }
        val random = Random(20261007)
        repeat(200_000) {
            val any = random.nextDouble() * 2_000_000.0
            assertEquals("$any m", asItWas(any), tenthsOf(any, km))
        }
    }

    @Test
    fun `a total in kilometres is the total it was`() {
        val trips = listOf(12_349.0, 8_251.0, 149.0, 30_050.0, 999.0, 0.0, 23_449.9)

        assertEquals(trips.sumOf { asItWas(it) }, sumOfTenths(trips, km))
        assertEquals("75.2", formatTenths(sumOfTenths(trips, km), Locale.ROOT))
    }

    // ---- The same rule in miles ------------------------------------------------------------------

    @Test
    fun `a mile is 1609 point 344 metres, exactly`() {
        assertEquals(BigDecimal("1609.344"), mi.metresPerUnit)
        assertEquals(10L, tenthsOf(1_609.344, mi))
        assertEquals("1.0", formatDistance(1_609.344, mi, Locale.ROOT))
        assertEquals(1_609.344, metresOfTenths(10, mi), 0.0)
        assertEquals(19_794.9312, metresOf(BigDecimal("12.3"), mi), 0.0)
        assertEquals(12_300.0, metresOf(BigDecimal("12.3"), km), 0.0)
    }

    @Test
    fun `in miles a distance is rounded to tenths of a mile, half a tenth upwards`() {
        // Half a tenth of a mile is 80.4672 m.
        assertEquals(0L, tenthsOf(80.4671, mi))
        assertEquals(1L, tenthsOf(80.4672, mi))
        assertEquals(0L, tenthsOf(0.0, mi))
        assertEquals("7.7", formatDistance(12_349.0, mi, Locale.ROOT))
        assertEquals("14.6", formatDistance(23_449.0, mi, Locale.ROOT))
        assertEquals("62.2", formatDistance(100_050.0, mi, Locale.ROOT))
        assertEquals("6213711.9", formatDistance(9_999_999_949.0, mi, Locale.ROOT))
        assertEquals("7,7", formatDistance(12_349.0, mi, Locale.FRANCE))
    }

    @Test
    fun `a distance typed in miles that ends on half a tenth always goes up`() {
        // 0.35 mi is 563.2704 m. Divided as binary fractions that is 3.4999999999999996 tenths,
        // and four such distances in ten would be shown a tenth too low.
        assertEquals(4L, tenthsOf(563.2704, mi))
        for (hundredths in 5L..200_005L step 10) {
            val typed = BigDecimal.valueOf(hundredths, 2)
            val shown = tenthsOf(metresOf(typed, mi), mi)

            assertEquals("$typed mi", (hundredths + 5) / 10, shown)
        }
    }

    @Test
    fun `what is typed in a unit is what is shown in it`() {
        for (unit in DistanceUnit.entries) {
            for (tenths in 0L..50_000L) {
                val typed = BigDecimal.valueOf(tenths, 1)

                assertEquals("$typed $unit", tenths, tenthsOf(metresOf(typed, unit), unit))
            }
        }
    }

    @Test
    fun `tenths go back to metres and come back the same tenths, in both units`() {
        for (unit in DistanceUnit.entries) {
            for (tenths in (0L..300_000L) + listOf(9_999_999L, 62_137_119L)) {
                assertEquals("$tenths $unit", tenths, tenthsOf(metresOfTenths(tenths, unit), unit))
            }
        }
        assertEquals(412_300.0, metresOfTenths(4_123, km), 0.0)
    }

    @Test
    fun `a column of trips adds up to the total printed under it, in both units`() {
        val trips = listOf(12_349.0, 8_251.0, 149.0, 30_050.0, 999.0, 0.0, 23_449.9, 300.0)

        for (unit in DistanceUnit.entries) {
            val byHand = trips.sumOf { formatDistance(it, unit, Locale.ROOT).toBigDecimal() }

            assertEquals(
                "$unit",
                byHand.toPlainString(),
                formatTenths(sumOfTenths(trips, unit), Locale.ROOT),
            )
        }
    }

    @Test
    fun `a total in miles is the sum of the miles printed, not the kilometre total converted`() {
        val trips = listOf(12_349.0, 8_251.0, 149.0, 30_050.0, 999.0, 0.0, 23_449.9)

        val inMiles = sumOfTenths(trips, mi)
        val kilometresConverted = tenthsOf(metresOfTenths(sumOfTenths(trips, km), km), mi)

        // 7.7 + 5.1 + 0.1 + 18.7 + 0.6 + 0.0 + 14.6: what stands in the column.
        assertEquals(468L, inMiles)
        // 75.2 km turned into miles would be 46.7, which the column does not add up to.
        assertEquals(467L, kilometresConverted)
        assertNotEquals(inMiles, kilometresConverted)
    }

    @Test
    fun `over a month the printed miles and the metres rounded once drift apart too`() {
        val trips = List(193) { 11_504.0 }

        assertEquals("1370.3", formatTenths(sumOfTenths(trips, mi), Locale.ROOT))
        assertEquals("1379.6", formatDistance(trips.sum(), mi, Locale.ROOT))
    }

    @Test
    fun `a corrupt distance is refused in miles as in kilometres`() {
        for (unit in DistanceUnit.entries) {
            for (corrupt in listOf(-1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
                assertThrows(IllegalArgumentException::class.java) { tenthsOf(corrupt, unit) }
                assertThrows(IllegalArgumentException::class.java) {
                    formatShortDistance(corrupt, unit, Locale.ROOT)
                }
            }
        }
    }

    // ---- A setting that is a distance, a reading in the other unit, a limit ----------------------

    @Test
    fun `the shortest trip that counts is written with two decimals in miles, one in kilometres`() {
        assertEquals("0.3", formatShortDistance(300.0, km, Locale.ROOT))
        assertEquals("0.19", formatShortDistance(300.0, mi, Locale.ROOT))
        assertEquals("0,19", formatShortDistance(300.0, mi, Locale.FRANCE))
        // The setting's twenty steps of 100 m. With one decimal the first two would both read
        // 0.1 mi, and four more pairs would read alike.
        val steps = (100..2_000 step 100).map { it.toDouble() }
        val inMiles = steps.map { formatShortDistance(it, mi, Locale.ROOT) }

        assertEquals("0.06", inMiles.first())
        assertEquals("1.24", inMiles.last())
        assertEquals(steps.size, inMiles.toSet().size)
        assertNotEquals(steps.size, steps.map { formatDistance(it, mi, Locale.ROOT) }.toSet().size)
        // In kilometres each step is the figure it always was.
        assertEquals(
            steps.map { formatDistance(it, km, Locale.ROOT) },
            steps.map { formatShortDistance(it, km, Locale.ROOT) },
        )
    }

    @Test
    fun `a whole number of one unit is itself in that unit, and is turned into the other once`() {
        assertEquals(1_234_560L, tenthsOfWhole(123_456, from = km, to = km))
        assertEquals(765_430L, tenthsOfWhole(76_543, from = mi, to = mi))
        // 123 456 km is 76 712.0 mi; 76 543 mi is 123 184.0 km.
        assertEquals(767_120L, tenthsOfWhole(123_456, from = km, to = mi))
        assertEquals(1_231_840L, tenthsOfWhole(76_543, from = mi, to = km))
        assertEquals(0L, tenthsOfWhole(0, from = mi, to = km))
    }

    @Test
    fun `a limit kept in metres is named in whole units, rounded down`() {
        assertEquals(2_000L, wholeUnitsBelow(2_000_000.0, km))
        assertEquals(1_242L, wholeUnitsBelow(2_000_000.0, mi))
        assertEquals(111L, wholeUnitsBelow(180_000.0, mi))
    }
}

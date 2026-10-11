package com.shawnkowalchuk.milo.core.odometer

import com.shawnkowalchuk.milo.core.util.DistanceUnit
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The odometer as Home's row of wheels shows it: the figure to the tenth, how far the last
 * wheel has turned, and that the wheels never say something else than Settings' whole number.
 * Every figure here is made by [odometerAt] itself, so a change of its rule shows in this test.
 */
class OdometerWheelsTest {
    private val zone: ZoneId = ZoneId.of("America/Edmonton")
    private val km = DistanceUnit.KILOMETRES
    private val mi = DistanceUnit.MILES

    private fun at(day: Int, hour: Int): Long =
        LocalDate.of(2026, 10, day).atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    private val reading = OdometerReading(at(7, 18), 123_456, km)

    private fun figure(
        vararg tripMetres: Double,
        unit: DistanceUnit = km,
        typed: OdometerReading = reading,
    ): OdometerFigure = checkNotNull(
        odometerAt(
            at(9, 20),
            LocalDate.of(2026, 10, 9),
            zone,
            listOf(typed),
            tripMetres.map { DrivenTrip(at(8, 9), it) },
            unit,
        ),
    )

    // ---- The figure to the tenth ------------------------------------------------------------------

    @Test
    fun `the wheels show the reading and the trips since, to the tenth`() {
        val wheels = figure(23_400.0, 10_200.0, 5_000.0).wheels()

        // 123,456 and 38.6 km: 123494 on the whole wheels, and a 6 on the tenth's.
        assertEquals(1_234_946L, wheels.tenths)
        assertEquals(km, wheels.unit)
    }

    @Test
    fun `a reading with no trip since stands at a tenth of nought`() {
        assertEquals(1_234_560L, figure().inTenths())
    }

    @Test
    fun `Settings' whole number is the wheels' figure rounded to the nearest whole`() {
        // Up to 4 tenths Settings shows the wheels' whole digits; from 5 on, one more.
        for (hundredMetres in 0..25) {
            val figure = figure(hundredMetres * 100.0)

            val tenths = figure.inTenths()

            assertEquals(1_234_560L + hundredMetres, tenths)
            assertEquals("after $hundredMetres tenths", figure.value, (tenths + 5) / 10)
        }
        assertEquals(123_456L, figure(400.0).value)
        assertEquals(123_457L, figure(500.0).value)
    }

    @Test
    fun `in miles the wheels count tenths of a mile from a reading typed in miles`() {
        val typed = OdometerReading(at(7, 18), 76_543, mi)

        // 16,093.44 m is ten miles to the metre.
        val figure = figure(16_093.44, 804.672, unit = mi, typed = typed)

        assertEquals(765_535L, figure.inTenths())
        assertEquals(76_554L, figure.value)
    }

    @Test
    fun `a reading typed in the other unit is turned into the wheels' unit once`() {
        // 123,456 km is 76,712.0 mi, and 2,011.68 m on is a mile and a quarter more: 1.3.
        val figure = figure(2_011.68, unit = mi)

        assertEquals(767_133L, figure.inTenths())
        assertEquals(76_713L, figure.value)
    }

    @Test
    fun `for a moment before the reading the trips between are taken away`() {
        val before =
            checkNotNull(
                odometerAt(
                    at(1, 0),
                    LocalDate.of(2026, 10, 1),
                    zone,
                    listOf(reading),
                    listOf(DrivenTrip(at(3, 9), 100_300.0)),
                    km,
                ),
            )

        assertEquals(1_233_557L, before.inTenths())
        assertEquals(123_356L, before.value)
    }

    @Test
    fun `a figure the wheels cannot account for is shown as its whole number`() {
        // No rule of today's makes such a figure. Should one ever, the wheels are less exact
        // than Settings, and never say something else.
        val odd = figure(4_600.0).copy(value = 200_000)

        assertEquals(2_000_000L, odd.inTenths())
    }

    // ---- How far the last wheel has turned --------------------------------------------------------

    @Test
    fun `the last wheel stands on its digit when the trip is at exactly that tenth`() {
        assertEquals(0.0, lastWheelTurn(4_600.0, km), 1e-9)
        assertEquals(0.0, lastWheelTurn(0.0, km), 1e-9)
    }

    @Test
    fun `it is half-way to the next digit just before the printed figure changes`() {
        assertEquals(0.25, lastWheelTurn(4_625.0, km), 1e-9)
        assertEquals(0.49, lastWheelTurn(4_649.0, km), 1e-9)
        // At 4,650 m the trip is printed as 4.7, and the wheel is half-way up to that 7.
        assertEquals(-0.5, lastWheelTurn(4_650.0, km), 1e-9)
        assertEquals(-0.25, lastWheelTurn(4_675.0, km), 1e-9)
    }

    @Test
    fun `in miles a tenth of a mile is one digit of the wheel`() {
        assertEquals(0.0, lastWheelTurn(1_609.344, mi), 1e-9)
        assertEquals(0.25, lastWheelTurn(1_609.344 + 40.2336, mi), 1e-9)
        assertEquals(-0.25, lastWheelTurn(1_609.344 - 40.2336, mi), 1e-9)
    }

    @Test
    fun `the wheels turn evenly with the distance of the trip being counted`() {
        // The figure and the turn together are the reading and the trip's exact distance: no
        // metre of the trip moves the wheel more than any other, and none moves it back.
        var last = Double.NEGATIVE_INFINITY
        for (metres in 0..2_500 step 7) {
            val wheels = figure(metres.toDouble()).wheels(tripMetres = metres.toDouble())

            val stands = wheels.tenths + checkNotNull(wheels.turn)

            assertEquals("at $metres m", 1_234_560.0 + metres / 100.0, stands, 1e-6)
            assertTrue("the wheel went back at $metres m", stands > last)
            last = stands
        }
    }

    @Test
    fun `an odometer that counts no trip has wheels that stand`() {
        assertNull(figure(4_600.0).wheels().turn)
        assertEquals(0.0, checkNotNull(figure(4_600.0).wheels(tripMetres = 4_600.0).turn), 1e-9)
    }
}

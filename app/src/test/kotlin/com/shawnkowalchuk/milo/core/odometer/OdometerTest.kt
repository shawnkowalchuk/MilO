package com.shawnkowalchuk.milo.core.odometer

import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The odometer: Shawn's reading, plus or minus the truck trips recorded since or before it. */
class OdometerTest {
    private val zone: ZoneId = ZoneId.of("America/Edmonton")

    private fun at(day: Int, hour: Int, minute: Int = 0): Long =
        LocalDate.of(2026, 10, day).atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun october(day: Int): LocalDate = LocalDate.of(2026, 10, day)

    private fun trip(day: Int, hour: Int, km: Double) = DrivenTrip(at(day, hour), km * 1000)

    private val reading = OdometerReading(at(7, 18), 123_456)

    @Test
    fun `with no reading there is no odometer`() {
        assertNull(odometerAt(at(7, 20), october(7), zone, emptyList(), listOf(trip(7, 9, 10.0))))
    }

    @Test
    fun `trips after the reading are added to it, and the figure is an estimate`() {
        val trips = listOf(trip(8, 8, 23.4), trip(8, 12, 10.2), trip(9, 9, 5.0))

        val figure = checkNotNull(odometerAt(at(9, 20), october(9), zone, listOf(reading), trips))

        // 38.6 km on: 123 494.6, shown as the dashboard would show it.
        assertEquals(123_495L, figure.km)
        assertEquals(386L, figure.drivenTenths)
        assertTrue(figure.estimated)
        assertEquals(reading, figure.reading)
    }

    @Test
    fun `trips between a moment and a later reading are taken away`() {
        val trips = listOf(trip(1, 9, 100.0), trip(5, 9, 50.4))

        val figure = checkNotNull(odometerAt(at(1, 0), october(1), zone, listOf(reading), trips))

        assertEquals(123_306L, figure.km)
        assertTrue(figure.estimated)
    }

    @Test
    fun `the reading itself, typed that day with nothing driven since, is not an estimate`() {
        val trips = listOf(trip(7, 9, 40.0))

        // The end of the 7th: the reading was typed at 18:00, after the day's only trip.
        val endOfDay = at(8, 0)
        val figure = checkNotNull(odometerAt(endOfDay, october(7), zone, listOf(reading), trips))

        assertEquals(123_456L, figure.km)
        assertFalse(figure.estimated)
    }

    @Test
    fun `the same figure on another day is an estimate, as MilO assumes nothing was driven`() {
        val figure =
            checkNotNull(odometerAt(at(9, 0), october(8), zone, listOf(reading), emptyList()))

        assertEquals(123_456L, figure.km)
        assertTrue(figure.estimated)
    }

    @Test
    fun `a trip counts by when it started, so the trip still open at a reading is in it`() {
        // Parked at 17:55, the trip still open for its ten minutes, the reading typed at 18:00.
        val trips = listOf(DrivenTrip(at(7, 17, 30), 20_000.0))

        val figure = checkNotNull(odometerAt(at(8, 0), october(7), zone, listOf(reading), trips))

        assertEquals(123_456L, figure.km)
    }

    @Test
    fun `the reading with the least driving between it and the moment is used`() {
        val start = OdometerReading(at(1, 7), 120_000)
        val trips = listOf(trip(2, 9, 30.0), trip(5, 9, 3_000.0))

        // The 3rd: 30 km from the reading of the 1st, 3 000 km before the reading of the 7th.
        val third =
            checkNotNull(odometerAt(at(3, 0), october(3), zone, listOf(start, reading), trips))

        assertEquals(start, third.reading)
        assertEquals(120_030L, third.km)
    }

    @Test
    fun `a reading corrected with nothing driven in between gives way to the correction`() {
        val typo = OdometerReading(at(7, 18), 12_345)
        val corrected = OdometerReading(at(7, 18, 2), 123_456)
        val trips = listOf(trip(6, 9, 10.0))

        assertEquals(listOf(corrected), standingReadings(listOf(typo, corrected), trips))
        // Before both, the wrong one would have been as close as the right one.
        val before =
            checkNotNull(odometerAt(at(6, 0), october(6), zone, listOf(typo, corrected), trips))
        assertEquals(123_446L, before.km)
    }

    @Test
    fun `a reading with driving after it stands beside a later one`() {
        val first = OdometerReading(at(1, 7), 120_000)
        val trips = listOf(trip(2, 9, 30.0))

        assertEquals(listOf(first, reading), standingReadings(listOf(reading, first), trips))
    }

    @Test
    fun `a period's start and end`() {
        val start = OdometerReading(at(1, 7), 120_000)
        val trips = listOf(trip(1, 9, 100.0), trip(31, 9, 20.0))

        val span = checkNotNull(odometerOver(october(1), october(31), zone, listOf(start), trips))

        // The reading was typed on the 1st before any trip: the start is the reading as typed.
        assertEquals(120_000L, span.start.km)
        assertFalse(span.start.estimated)
        // The end is after every trip of the 31st.
        assertEquals(120_120L, span.end.km)
        assertTrue(span.end.estimated)
    }

    @Test
    fun `what is typed is read as whole kilometres`() {
        assertEquals(123_456L, parseOdometerKm("123456"))
        assertEquals(123_456L, parseOdometerKm(" 123,456 "))
        assertEquals(123_456L, parseOdometerKm("123 456"))
        assertEquals(123_457L, parseOdometerKm("123456.5"))
        assertEquals(123_456L, parseOdometerKm("123456.4"))
        assertEquals(0L, parseOdometerKm("0"))
        assertEquals(MAX_ODOMETER_KM, parseOdometerKm("9999999"))
    }

    @Test
    fun `anything that is not a reading is refused`() {
        val notReadings = listOf("", " ", "12a", "-5", "1.2.3", ".5", "123.", "10000000")
        for (typed in notReadings) {
            assertNull(typed, parseOdometerKm(typed))
        }
        // Rounded past the largest reading.
        assertNull(parseOdometerKm("9999999.5"))
    }

    @Test
    fun `the figure is grouped as the phone's language groups it`() {
        assertEquals("123,456", formatOdometerKm(123_456, Locale.CANADA))
        assertEquals("7", formatOdometerKm(7, Locale.CANADA))
    }
}

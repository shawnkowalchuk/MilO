package com.shawnkowalchuk.milo.core.odometer

import com.shawnkowalchuk.milo.core.util.DistanceUnit
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

    private val reading = OdometerReading(at(7, 18), 123_456, DistanceUnit.KILOMETRES)

    @Test
    fun `with no reading there is no odometer`() {
        assertNull(
            odometerAt(
                at(7, 20),
                october(7),
                zone,
                emptyList(),
                listOf(trip(7, 9, 10.0)),
                DistanceUnit.KILOMETRES,
            ),
        )
    }

    @Test
    fun `trips after the reading are added to it, and the figure is an estimate`() {
        val trips = listOf(trip(8, 8, 23.4), trip(8, 12, 10.2), trip(9, 9, 5.0))

        val figure =
            checkNotNull(
                odometerAt(
                    at(9, 20),
                    october(9),
                    zone,
                    listOf(reading),
                    trips,
                    DistanceUnit.KILOMETRES,
                ),
            )

        // 38.6 km on: 123 494.6, shown as the dashboard would show it.
        assertEquals(123_495L, figure.value)
        assertEquals(386L, figure.drivenTenths)
        assertTrue(figure.estimated)
        assertEquals(reading, figure.reading)
    }

    @Test
    fun `trips between a moment and a later reading are taken away`() {
        val trips = listOf(trip(1, 9, 100.0), trip(5, 9, 50.4))

        val figure =
            checkNotNull(
                odometerAt(
                    at(1, 0),
                    october(1),
                    zone,
                    listOf(reading),
                    trips,
                    DistanceUnit.KILOMETRES,
                ),
            )

        assertEquals(123_306L, figure.value)
        assertTrue(figure.estimated)
    }

    @Test
    fun `the reading itself, typed that day with nothing driven since, is not an estimate`() {
        val trips = listOf(trip(7, 9, 40.0))

        // The end of the 7th: the reading was typed at 18:00, after the day's only trip.
        val endOfDay = at(8, 0)
        val figure =
            checkNotNull(
                odometerAt(
                    endOfDay,
                    october(7),
                    zone,
                    listOf(reading),
                    trips,
                    DistanceUnit.KILOMETRES,
                ),
            )

        assertEquals(123_456L, figure.value)
        assertFalse(figure.estimated)
    }

    @Test
    fun `the same figure on another day is an estimate, as MilO assumes nothing was driven`() {
        val figure =
            checkNotNull(
                odometerAt(
                    at(9, 0),
                    october(8),
                    zone,
                    listOf(reading),
                    emptyList(),
                    DistanceUnit.KILOMETRES,
                ),
            )

        assertEquals(123_456L, figure.value)
        assertTrue(figure.estimated)
    }

    @Test
    fun `a trip counts by when it started, so the trip still open at a reading is in it`() {
        // Parked at 17:55, the trip still open for its ten minutes, the reading typed at 18:00.
        val trips = listOf(DrivenTrip(at(7, 17, 30), 20_000.0))

        val figure =
            checkNotNull(
                odometerAt(
                    at(8, 0),
                    october(7),
                    zone,
                    listOf(reading),
                    trips,
                    DistanceUnit.KILOMETRES,
                ),
            )

        assertEquals(123_456L, figure.value)
    }

    @Test
    fun `the reading with the least driving between it and the moment is used`() {
        val start = OdometerReading(at(1, 7), 120_000, DistanceUnit.KILOMETRES)
        val trips = listOf(trip(2, 9, 30.0), trip(5, 9, 3_000.0))

        // The 3rd: 30 km from the reading of the 1st, 3 000 km before the reading of the 7th.
        val third =
            checkNotNull(
                odometerAt(
                    at(3, 0),
                    october(3),
                    zone,
                    listOf(start, reading),
                    trips,
                    DistanceUnit.KILOMETRES,
                ),
            )

        assertEquals(start, third.reading)
        assertEquals(120_030L, third.value)
    }

    @Test
    fun `a reading corrected with nothing driven in between gives way to the correction`() {
        val typo = OdometerReading(at(7, 18), 12_345, DistanceUnit.KILOMETRES)
        val corrected = OdometerReading(at(7, 18, 2), 123_456, DistanceUnit.KILOMETRES)
        val trips = listOf(trip(6, 9, 10.0))

        assertEquals(listOf(corrected), standingReadings(listOf(typo, corrected), trips))
        // Before both, the wrong one would have been as close as the right one.
        val before =
            checkNotNull(
                odometerAt(
                    at(6, 0),
                    october(6),
                    zone,
                    listOf(typo, corrected),
                    trips,
                    DistanceUnit.KILOMETRES,
                ),
            )
        assertEquals(123_446L, before.value)
    }

    @Test
    fun `a reading with driving after it stands beside a later one`() {
        val first = OdometerReading(at(1, 7), 120_000, DistanceUnit.KILOMETRES)
        val trips = listOf(trip(2, 9, 30.0))

        assertEquals(listOf(first, reading), standingReadings(listOf(reading, first), trips))
    }

    @Test
    fun `a period's start and end`() {
        val start = OdometerReading(at(1, 7), 120_000, DistanceUnit.KILOMETRES)
        val trips = listOf(trip(1, 9, 100.0), trip(31, 9, 20.0))

        val span =
            checkNotNull(
                odometerOver(
                    october(1),
                    october(31),
                    zone,
                    listOf(start),
                    trips,
                    DistanceUnit.KILOMETRES,
                ),
            )

        // The reading was typed on the 1st before any trip: the start is the reading as typed.
        assertEquals(120_000L, span.start.value)
        assertFalse(span.start.estimated)
        // The end is after every trip of the 31st.
        assertEquals(120_120L, span.end.value)
        assertTrue(span.end.estimated)
    }

    // ---- Kilometres or miles (2026-10-07) ---------------------------------------------------------

    private val km = DistanceUnit.KILOMETRES
    private val mi = DistanceUnit.MILES
    private val readingInMiles = OdometerReading(at(7, 18), 76_543, mi)

    @Test
    fun `a reading typed in miles comes back exactly as typed, in miles`() {
        // Typed at 18:00 on the 7th, after the day's only trip: the figure is the reading.
        val trips = listOf(trip(7, 9, 40.0))
        val figure =
            checkNotNull(odometerAt(at(8, 0), october(7), zone, listOf(readingInMiles), trips, mi))

        assertEquals(76_543L, figure.value)
        assertEquals(mi, figure.unit)
        assertEquals(readingInMiles, figure.reading)
        assertFalse(figure.estimated)
        // And every other reading a dashboard in miles can show, to the last mile.
        for (typed in listOf(0L, 1L, 9L, 99_999L, 123_455L, 123_456L, MAX_ODOMETER_READING)) {
            val reading = OdometerReading(at(7, 18), typed, mi)
            val back = odometerAt(at(8, 0), october(7), zone, listOf(reading), emptyList(), mi)

            assertEquals(typed, back?.value)
        }
    }

    @Test
    fun `in miles the trips since are added as they are printed in miles`() {
        val trips = listOf(trip(8, 8, 23.4), trip(8, 12, 10.2), trip(9, 9, 5.0))

        val figure =
            checkNotNull(odometerAt(at(9, 20), october(9), zone, listOf(readingInMiles), trips, mi))

        // 14.5 + 6.3 + 3.1 mi, as the Trips screen writes the three trips: 23.9 mi on.
        assertEquals(239L, figure.drivenTenths)
        assertEquals(76_567L, figure.value)
        assertTrue(figure.estimated)
    }

    @Test
    fun `a reading typed in kilometres is shown in miles, and stays the reading it was`() {
        val trips = listOf(trip(8, 8, 23.4), trip(8, 12, 10.2), trip(9, 9, 5.0))

        val typedThatDay =
            checkNotNull(odometerAt(at(8, 0), october(7), zone, listOf(reading), emptyList(), mi))
        val later =
            checkNotNull(odometerAt(at(9, 20), october(9), zone, listOf(reading), trips, mi))

        // 123 456 km is 76 712.0 mi. It is still his reading of that day, not an estimate.
        assertEquals(76_712L, typedThatDay.value)
        assertFalse(typedThatDay.estimated)
        assertEquals(reading, typedThatDay.reading)
        assertEquals(76_736L, later.value)
        // Shown in kilometres again, the reading is the reading: nothing was converted.
        assertEquals(
            123_456L,
            odometerAt(at(8, 0), october(7), zone, listOf(reading), emptyList(), km)?.value,
        )
    }

    @Test
    fun `a reading typed in miles is shown in kilometres by the same rule`() {
        val trips = listOf(trip(8, 8, 23.4), trip(8, 12, 10.2), trip(9, 9, 5.0))

        val later =
            checkNotNull(odometerAt(at(9, 20), october(9), zone, listOf(readingInMiles), trips, km))

        // 76 543 mi is 123 184.0 km, and 38.6 km of trips since.
        assertEquals(386L, later.drivenTenths)
        assertEquals(123_223L, later.value)
        assertEquals(km, later.unit)
    }

    @Test
    fun `which reading is used does not depend on the unit it is shown in`() {
        val start = OdometerReading(at(1, 7), 120_000, km)
        val trips = listOf(trip(2, 9, 30.0), trip(5, 9, 3_000.0))
        val readings = listOf(start, readingInMiles)

        for (unit in DistanceUnit.entries) {
            val third = checkNotNull(odometerAt(at(3, 0), october(3), zone, readings, trips, unit))

            assertEquals("$unit", start, third.reading)
        }
    }

    @Test
    fun `a period's start and end in miles`() {
        val start = OdometerReading(at(1, 7), 74_500, mi)
        val trips = listOf(trip(1, 9, 100.0), trip(31, 9, 20.0))

        val span =
            checkNotNull(odometerOver(october(1), october(31), zone, listOf(start), trips, mi))

        assertEquals(74_500L, span.start.value)
        assertFalse(span.start.estimated)
        // 62.1 mi and 12.4 mi on: 74 574.5, which a dashboard shows as 74 575.
        assertEquals(745L, span.end.drivenTenths)
        assertEquals(74_575L, span.end.value)
        assertTrue(span.end.estimated)
    }

    @Test
    fun `what is typed is read as whole kilometres`() {
        assertEquals(123_456L, parseOdometer("123456"))
        assertEquals(123_456L, parseOdometer(" 123,456 "))
        assertEquals(123_456L, parseOdometer("123 456"))
        assertEquals(123_457L, parseOdometer("123456.5"))
        assertEquals(123_456L, parseOdometer("123456.4"))
        assertEquals(0L, parseOdometer("0"))
        assertEquals(MAX_ODOMETER_READING, parseOdometer("9999999"))
    }

    @Test
    fun `anything that is not a reading is refused`() {
        val notReadings = listOf("", " ", "12a", "-5", "1.2.3", ".5", "123.", "10000000")
        for (typed in notReadings) {
            assertNull(typed, parseOdometer(typed))
        }
        // Rounded past the largest reading.
        assertNull(parseOdometer("9999999.5"))
    }

    @Test
    fun `the figure is grouped as the phone's language groups it`() {
        assertEquals("123,456", formatOdometer(123_456, Locale.CANADA))
        assertEquals("7", formatOdometer(7, Locale.CANADA))
    }
}

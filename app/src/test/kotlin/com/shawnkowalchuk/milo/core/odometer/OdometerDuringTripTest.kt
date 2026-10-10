package com.shawnkowalchuk.milo.core.odometer

import com.shawnkowalchuk.milo.core.util.DistanceUnit
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A reading typed while a trip is being recorded (2026-10-10). Shawn: "i have my phone connected
 * i made a trip reset the mileage and it was off by 9 km when complete", and "yes i typed the km
 * to the accurate reading before driving today and the kms are now 102729". MilO had opened the
 * trip when the phone connected to the truck, so the reading was typed with the trip open, and
 * the whole drive was taken to be in the reading.
 *
 * A reading says what the dashboard showed when it was typed: what the trip drove after that
 * moment is added, and what it had driven before is not.
 */
class OdometerDuringTripTest {
    private val zone: ZoneId = ZoneId.of("America/Edmonton")
    private val km = DistanceUnit.KILOMETRES
    private val mi = DistanceUnit.MILES

    private fun at(hour: Int, minute: Int = 0, day: Int = 10): Long =
        LocalDate.of(2026, 10, day).atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    private val today: LocalDate = LocalDate.of(2026, 10, 10)

    /** The morning's trip: MilO opened it at 7:40, when the phone connected to the truck. */
    private fun morningTrip(km: Double, id: Long = 41) = DrivenTrip(at(7, 40), km * 1000, id = id)

    /** A reading typed at 7:42 with that trip open, [metres] into it. */
    private fun typedDuring(
        value: Long = 102_720,
        metres: Double = 0.0,
        hour: Int = 7,
        minute: Int = 42,
        unit: DistanceUnit = km,
        tripId: Long = 41,
    ) = OdometerReading(at(hour, minute), value, unit, duringTrip = TripAtReading(tripId, metres))

    private fun odometer(
        atMs: Long,
        readings: List<OdometerReading>,
        trips: List<DrivenTrip>,
        unit: DistanceUnit = km,
        day: LocalDate = today,
    ): OdometerFigure = checkNotNull(odometerAt(atMs, day, zone, readings, trips, unit))

    // ---- The report of 2026-10-10 -------------------------------------------------------------

    @Test
    fun `Shawn's day - typed before driving off, then three trips, all of them are added`() {
        val reading = typedDuring(value = 102_720, metres = 0.0)
        val first = morningTrip(km = 9.2)
        val second = DrivenTrip(at(11, 5), 6_400.0, id = 42)
        val third = DrivenTrip(at(15, 30), 7_200.0, id = 43)

        // After the first trip the dashboard showed 102,729, and MilO showed 102,720.
        val afterTheFirst = odometer(at(9), listOf(reading), listOf(first))
        assertEquals(102_729L, afterTheFirst.value)
        assertEquals(92L, afterTheFirst.drivenTenths)

        // At the end of the day: the reading plus all three, 22.8 km.
        val atTheEnd = odometer(at(20), listOf(reading), listOf(first, second, third))
        assertEquals(228L, atTheEnd.drivenTenths)
        assertEquals(102_743L, atTheEnd.value)
        assertTrue(atTheEnd.estimated)
        assertEquals(reading, atTheEnd.reading)
    }

    @Test
    fun `typed two minutes into an open trip that then drives 9 km - it ends 9 km higher`() {
        val figure = odometer(at(9), listOf(typedDuring()), listOf(morningTrip(km = 9.0)))

        assertEquals(102_729L, figure.value)
    }

    // ---- Where in the trip it was typed --------------------------------------------------------

    @Test
    fun `typed after parking with the trip still open - nothing more is added`() {
        // Parked at 8:05 with 9.0 km driven, the reading typed at 8:08, the trip closed at 8:15.
        val reading = typedDuring(value = 102_729, metres = 9_000.0, hour = 8, minute = 8)

        val figure = odometer(at(20), listOf(reading), listOf(morningTrip(km = 9.0)))

        assertEquals(102_729L, figure.value)
        assertEquals(0L, figure.drivenTenths)
        // It is the reading itself, typed that day: no "est." on the report.
        assertFalse(figure.estimated)
    }

    @Test
    fun `a trip that closes a little shorter than it stood at the reading takes nothing away`() {
        // The closed trip is measured again without what came after the truck disconnected.
        val reading = typedDuring(value = 102_729, metres = 9_030.0, hour = 8, minute = 8)

        val figure = odometer(at(20), listOf(reading), listOf(morningTrip(km = 8.99)))

        assertEquals(102_729L, figure.value)
        assertEquals(0L, figure.drivenTenths)
    }

    @Test
    fun `typed on the way - the rest of the trip is added`() {
        // 4.0 km into a trip of 9.0 km.
        val reading = typedDuring(value = 102_724, metres = 4_000.0, minute = 50)

        val figure = odometer(at(9), listOf(reading), listOf(morningTrip(km = 9.0)))

        assertEquals(102_729L, figure.value)
        assertEquals(50L, figure.drivenTenths)
        assertTrue(figure.estimated)
    }

    @Test
    fun `a few metres the phone counted while the truck stood are no driving`() {
        // Parked with 9,020 m counted. The GPS wandered another 30 m before the trip closed:
        // a stretch that is printed as 0.0 km is not a truck trip since the reading.
        val reading = typedDuring(value = 102_729, metres = 9_020.0, hour = 8, minute = 8)
        val closed = DrivenTrip(at(7, 40), 9_050.0, id = 41)

        val after = odometer(at(20), listOf(reading), listOf(closed))

        assertEquals(102_729L, after.value)
        assertFalse(after.estimated)
    }

    // ---- A moment before the reading -----------------------------------------------------------

    @Test
    fun `before a reading typed on the way, only what the trip had driven by then is taken away`() {
        val reading = typedDuring(value = 102_724, metres = 4_000.0, minute = 50)

        // Midnight before: the trip's first 4.0 km lie between, its last 5.0 km do not.
        val atMidnight = odometer(at(0), listOf(reading), listOf(morningTrip(km = 9.0)))

        assertEquals(102_720L, atMidnight.value)
        assertEquals(40L, atMidnight.drivenTenths)
        assertTrue(atMidnight.estimated)
    }

    @Test
    fun `before a reading typed before driving off, nothing is taken away`() {
        val atMidnight = odometer(at(0), listOf(typedDuring()), listOf(morningTrip(km = 9.0)))

        assertEquals(102_720L, atMidnight.value)
        assertEquals(0L, atMidnight.drivenTenths)
        // The reading itself, typed that day with nothing driven before it.
        assertFalse(atMidnight.estimated)
    }

    // ---- Two readings in one trip --------------------------------------------------------------

    @Test
    fun `two readings in one trip - each holds what was driven before it`() {
        val beforeDrivingOff = typedDuring(value = 102_720, metres = 0.0)
        val onTheWay = typedDuring(value = 102_724, metres = 4_000.0, minute = 50)
        val readings = listOf(beforeDrivingOff, onTheWay)
        val trips = listOf(morningTrip(km = 9.0))

        // 4 km were driven between the two, so neither is a correction of the other.
        assertEquals(readings, standingReadings(readings, trips))
        // Now: the later reading and the 5.0 km after it.
        val now = odometer(at(9), readings, trips)
        assertEquals(onTheWay, now.reading)
        assertEquals(50L, now.drivenTenths)
        assertEquals(102_729L, now.value)
        // Midnight before: the first reading, with nothing driven before it.
        val atMidnight = odometer(at(0), readings, trips)
        assertEquals(beforeDrivingOff, atMidnight.reading)
        assertEquals(102_720L, atMidnight.value)
    }

    @Test
    fun `a slip corrected before driving off gives way, though both were typed in the trip`() {
        val slip = typedDuring(value = 10_272, metres = 0.0)
        val corrected = typedDuring(value = 102_720, metres = 0.0, minute = 43)
        val readings = listOf(slip, corrected)
        val trips = listOf(morningTrip(km = 9.0))

        assertEquals(listOf(corrected), standingReadings(readings, trips))
        assertEquals(102_729L, odometer(at(9), readings, trips).value)
        // Before both: the wrong one must not be the reading a figure is worked out from.
        assertEquals(102_720L, odometer(at(0), readings, trips).value)
    }

    @Test
    fun `a slip corrected while the phone counted a few metres gives way too`() {
        val slip = typedDuring(value = 10_272, metres = 10.0)
        val corrected = typedDuring(value = 102_720, metres = 40.0, minute = 43)
        val readings = listOf(slip, corrected)

        assertEquals(listOf(corrected), standingReadings(readings, listOf(morningTrip(9.0))))
    }

    // ---- Which trip is cut ---------------------------------------------------------------------

    @Test
    fun `only the trip it was typed during is cut - every other trip counts by its start`() {
        val yesterday = DrivenTrip(at(16, day = 9), 30_000.0, id = 40)
        val later = DrivenTrip(at(11, 5), 6_400.0, id = 42)
        val trips = listOf(yesterday, morningTrip(km = 9.0), later)

        val now = odometer(at(20), listOf(typedDuring()), trips)
        val twoDaysAgo =
            odometer(at(0, day = 9), listOf(typedDuring()), trips, day = LocalDate.of(2026, 10, 9))

        assertEquals(102_735L, now.value)
        assertEquals(154L, now.drivenTenths)
        // Yesterday's 30 km started before the reading and are in it, as they always were.
        assertEquals(102_690L, twoDaysAgo.value)
    }

    @Test
    fun `the trip is gone - discarded as too short, or deleted - and the reading stands alone`() {
        val figure = odometer(at(20), listOf(typedDuring()), emptyList())

        assertEquals(102_720L, figure.value)
        assertFalse(figure.estimated)
    }

    @Test
    fun `a trip that only shares the number and started after the reading is not cut`() {
        // After an import the numbers can be given out again, to a later trip.
        val reading = typedDuring(value = 102_720, metres = 4_000.0)
        val other = DrivenTrip(at(11), 9_000.0, id = 41)

        val figure = odometer(at(20), listOf(reading), listOf(other))

        assertEquals(102_729L, figure.value)
        assertEquals(90L, figure.drivenTenths)
    }

    @Test
    fun `a trip whose distance Shawn lowered below the reading's adds nothing and takes nothing`() {
        val reading = typedDuring(value = 102_724, metres = 4_000.0, minute = 50)
        val shortened = morningTrip(km = 3.0)

        assertEquals(102_724L, odometer(at(20), listOf(reading), listOf(shortened)).value)
        // Before it: the 3.0 km the trip is now said to have driven, all of them before the reading.
        assertEquals(102_721L, odometer(at(0), listOf(reading), listOf(shortened)).value)
    }

    // ---- Readings from before 2026-10-10 -------------------------------------------------------

    @Test
    fun `a reading stored without the trip's distance behaves as it always did`() {
        // Typed during the trip by an older build: MilO does not know how far the trip had gone,
        // and takes the whole trip to be in the reading, as before.
        val old = OdometerReading(at(7, 42), 102_720, km)

        val figure = odometer(at(9), listOf(old), listOf(morningTrip(km = 9.0)))

        assertEquals(102_720L, figure.value)
        assertEquals(0L, figure.drivenTenths)
    }

    // ---- Miles ---------------------------------------------------------------------------------

    @Test
    fun `a reading in miles is cut by the trip's metres all the same`() {
        // 63,827 mi on the dashboard, typed 4.0 km into a trip of 9.0 km: 5.0 km are 3.1 mi.
        val reading = typedDuring(value = 63_827, metres = 4_000.0, minute = 50, unit = mi)
        val trips = listOf(morningTrip(km = 9.0))

        val inMiles = odometer(at(9), listOf(reading), trips, unit = mi)
        val inKilometres = odometer(at(9), listOf(reading), trips, unit = km)

        assertEquals(31L, inMiles.drivenTenths)
        assertEquals(63_830L, inMiles.value)
        // 63,827 mi are 102,719.6 km, and 5.0 km on.
        assertEquals(50L, inKilometres.drivenTenths)
        assertEquals(102_725L, inKilometres.value)
        // And the reading comes back as typed where nothing was driven after it.
        val parked = typedDuring(value = 63_830, metres = 9_000.0, hour = 8, minute = 8, unit = mi)
        val asTyped = odometer(at(20), listOf(parked), trips, unit = mi)
        assertEquals(63_830L, asTyped.value)
        assertFalse(asTyped.estimated)
    }

    // ---- The report's start and end ------------------------------------------------------------

    @Test
    fun `a period that ends on the day of the reading ends with every trip of that day`() {
        val trips = listOf(morningTrip(km = 9.2), DrivenTrip(at(11, 5), 13_600.0, id = 42))

        val span =
            checkNotNull(
                odometerOver(
                    LocalDate.of(2026, 10, 1),
                    today,
                    zone,
                    listOf(typedDuring()),
                    trips,
                    km,
                ),
            )

        // Nothing was recorded before the reading, so the start is the reading, worked back.
        assertEquals(102_720L, span.start.value)
        assertTrue(span.start.estimated)
        assertEquals(102_743L, span.end.value)
        assertTrue(span.end.estimated)
    }

    @Test
    fun `a period that begins on the day of the reading begins with the reading as typed`() {
        val trips = listOf(morningTrip(km = 9.2), DrivenTrip(at(11, 5), 13_600.0, id = 42))

        val span =
            checkNotNull(
                odometerOver(
                    today,
                    LocalDate.of(2026, 10, 31),
                    zone,
                    listOf(typedDuring()),
                    trips,
                    km,
                ),
            )

        // Typed before driving off: the truck had not moved that day, so this is no estimate.
        assertEquals(102_720L, span.start.value)
        assertFalse(span.start.estimated)
        assertEquals(102_743L, span.end.value)
        assertTrue(span.end.estimated)
    }

    @Test
    fun `a period that begins on the day of a reading typed on the way begins before that trip`() {
        val reading = typedDuring(value = 102_724, metres = 4_000.0, minute = 50)
        val trips = listOf(morningTrip(km = 9.0))

        val span =
            checkNotNull(odometerOver(today, today, zone, listOf(reading), trips, km))

        assertEquals(102_720L, span.start.value)
        assertTrue(span.start.estimated)
        assertEquals(102_729L, span.end.value)
        assertTrue(span.end.estimated)
    }
}

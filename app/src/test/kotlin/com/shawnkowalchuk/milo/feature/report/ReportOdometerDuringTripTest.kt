package com.shawnkowalchuk.milo.feature.report

import com.shawnkowalchuk.milo.core.odometer.OdometerReading
import com.shawnkowalchuk.milo.core.odometer.OdometerSpan
import com.shawnkowalchuk.milo.core.odometer.TripAtReading
import com.shawnkowalchuk.milo.core.report.ReportPeriod
import com.shawnkowalchuk.milo.core.trip.TripStartCause
import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.core.util.DistanceUnit
import com.shawnkowalchuk.milo.data.report.ReportSelection
import com.shawnkowalchuk.milo.data.settings.MiloSettings
import com.shawnkowalchuk.milo.data.trip.Trip
import com.shawnkowalchuk.milo.data.trip.drivenTrips
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val TRUCK = "AA:BB:CC:DD:EE:FF"

/**
 * The odometer the report for the accountant prints at the start and the end of its period,
 * where a reading was typed during a trip (2026-10-10). Made as the Report screen makes it:
 * from the stored trips (`drivenTrips`) and the stored readings.
 */
class ReportOdometerDuringTripTest {
    private val zone = ZoneId.of("America/Edmonton")
    private val readingDay = LocalDate.of(2026, 10, 10)

    private fun at(text: String): Long =
        LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()

    private fun trip(id: Long, start: String, metres: Double) = Trip(
        id = id,
        startedAtMs = at(start),
        endedAtMs = at(start) + 1_500_000,
        status = TripStatus.FINISHED,
        startedBy = TripStartCause.TRUCK,
        truckSeen = true,
        distanceMetres = metres,
        vehicleAddress = TRUCK,
    )

    /** Shawn's day: three trips, 22.8 km, the first of them open when he typed the reading. */
    private val trips =
        listOf(
            trip(41, "2026-10-10T07:40", 9_200.0),
            trip(42, "2026-10-10T11:05", 6_400.0),
            trip(43, "2026-10-10T15:30", 7_200.0),
        )

    private fun settings(metresIntoTheTrip: Double?, value: Long = 102_720) = MiloSettings(
        truckAddress = TRUCK,
        truckName = "Work truck",
        odometerReadings =
            listOf(
                OdometerReading(
                    at("2026-10-10T07:42"),
                    value,
                    DistanceUnit.KILOMETRES,
                    TRUCK,
                    metresIntoTheTrip?.let { TripAtReading(tripId = 41, metres = it) },
                ),
            ),
    )

    private fun odometerOn(period: ReportPeriod, settings: MiloSettings): OdometerSpan =
        mileageReport(
            period = period,
            selection = ReportSelection(emptyList(), 0, 0, 0, false, emptyList()),
            name = "Sam Driver",
            settings = settings,
            sent = emptyList(),
            today = LocalDate.of(2026, 11, 1),
            zone = zone,
            truckTrips = drivenTrips(trips),
        ).odometers.single().span

    @Test
    fun `October ends with the reading plus all three trips of its day`() {
        val span = odometerOn(ReportPeriod.Month(YearMonth.of(2026, 10)), settings(0.0))

        // No trip was recorded before the reading: the start is the reading, worked back.
        assertEquals(102_720L, span.start.value)
        assertTrue(span.start.estimated)
        assertEquals(102_743L, span.end.value)
        assertTrue(span.end.estimated)
    }

    @Test
    fun `a period that begins on the reading's day begins with the reading as typed`() {
        val period = ReportPeriod.Range(readingDay, LocalDate.of(2026, 10, 16))

        val span = odometerOn(period, settings(0.0))

        // Typed before driving off: nothing was driven that day before it. Not an estimate.
        assertEquals(102_720L, span.start.value)
        assertFalse(span.start.estimated)
        assertEquals(102_743L, span.end.value)
        assertTrue(span.end.estimated)
    }

    @Test
    fun `typed on the way, the day begins before the trip and ends after it`() {
        // 102,724 on the dashboard, 4.0 km into the first trip.
        val span =
            odometerOn(ReportPeriod.Range(readingDay, readingDay), settings(4_000.0, 102_724))

        assertEquals(102_720L, span.start.value)
        assertTrue(span.start.estimated)
        // 5.2 km of the first trip, and the other two: 18.8 km after the reading.
        assertEquals(102_743L, span.end.value)
        assertTrue(span.end.estimated)
    }

    @Test
    fun `a reading from before 2026-10-10 prints what it always printed`() {
        // It does not know how far its trip had gone, so the whole trip is taken to be in it:
        // 9 km short at the end, and the day's start 9 km too low. Typing the reading again
        // puts both right.
        val period = ReportPeriod.Range(readingDay, readingDay)

        val span = odometerOn(period, settings(metresIntoTheTrip = null))

        assertEquals(102_711L, span.start.value)
        assertEquals(102_734L, span.end.value)
    }
}

package com.shawnkowalchuk.milo.feature.settings

import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.odometer.OdometerReading
import com.shawnkowalchuk.milo.core.trip.TripStartCause
import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.core.util.DistanceUnit
import com.shawnkowalchuk.milo.data.trip.Trip
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the odometer's tile shows: the last reading plus the truck trips since. */
class OdometerCardStateTest {
    private val zone = ZoneId.of("America/Edmonton")
    private val readAt = 1_791_000_000_000L
    private val now = readAt + 3 * 24 * 3_600_000L

    private fun trip(startedAtMs: Long, metres: Double, truckSeen: Boolean = true) = Trip(
        startedAtMs = startedAtMs,
        endedAtMs = startedAtMs + 1_800_000,
        status = TripStatus.FINISHED,
        startedBy = if (truckSeen) TripStartCause.TRUCK else TripStartCause.MANUAL,
        truckSeen = truckSeen,
        distanceMetres = metres,
    )

    @Test
    fun `before the first reading there is no figure`() {
        val state =
            odometerCardState(
                emptyList(),
                listOf(trip(readAt, 5_000.0)),
                now,
                zone,
                false,
                DistanceUnit.KILOMETRES,
            )

        assertNull(state.figure)
    }

    @Test
    fun `the truck trips since the reading are added, and only those`() {
        val trips =
            listOf(
                trip(readAt - 3_600_000, 9_000.0),
                trip(readAt + 3_600_000, 40_000.0),
                trip(readAt + 7_200_000, 15_000.0, truckSeen = false),
            )

        val state =
            odometerCardState(
                listOf(OdometerReading(readAt, 123_456, DistanceUnit.KILOMETRES)),
                trips,
                now,
                zone,
                false,
                DistanceUnit.KILOMETRES,
            )

        val figure = checkNotNull(state.figure)
        assertEquals(123_496L, figure.value)
        assertEquals(400L, figure.drivenTenths)
        assertTrue(figure.estimated)
    }

    @Test
    fun `with miles chosen the tile is in miles, and a reading typed in miles is itself`() {
        val typed = OdometerReading(readAt, 76_543, DistanceUnit.MILES)
        val trips = listOf(trip(readAt + 3_600_000, 40_000.0))

        val untouched =
            odometerCardState(listOf(typed), emptyList(), now, zone, false, DistanceUnit.MILES)
        val driven = odometerCardState(listOf(typed), trips, now, zone, false, DistanceUnit.MILES)

        assertEquals(DistanceUnit.MILES, untouched.unit)
        assertEquals(76_543L, untouched.figure?.value)
        assertEquals(typed, untouched.figure?.reading)
        // 40 km is printed as 24.9 mi: 76 567.9, which a dashboard shows as 76 568.
        assertEquals(249L, driven.figure?.drivenTenths)
        assertEquals(76_568L, driven.figure?.value)
        assertEquals(DistanceUnit.MILES, driven.figure?.unit)
    }

    @Test
    fun `a reading typed in kilometres stays his reading after miles are chosen`() {
        val typed = OdometerReading(readAt, 123_456, DistanceUnit.KILOMETRES)

        val inMiles =
            odometerCardState(listOf(typed), emptyList(), now, zone, false, DistanceUnit.MILES)
        val inKilometres =
            odometerCardState(listOf(typed), emptyList(), now, zone, false, DistanceUnit.KILOMETRES)

        // Shown as 76 712 mi, with the line under it still naming the 123 456 km he typed.
        assertEquals(76_712L, inMiles.figure?.value)
        assertEquals(typed, inMiles.figure?.reading)
        // And back in kilometres it is the figure it was: nothing stored was converted.
        assertEquals(123_456L, inKilometres.figure?.value)
    }

    @Test
    fun `with miles chosen the tile says that the number typed is taken as miles`() {
        // A reading typed in the wrong unit would stand on the report for the accountant, so
        // the sentence before the first reading and the field's label both name the unit.
        assertEquals(
            R.string.settings_odometer_none_miles,
            odometerFirstReadingRes(DistanceUnit.MILES),
        )
        assertEquals(R.string.settings_odometer_field_miles, odometerFieldRes(DistanceUnit.MILES))
    }

    @Test
    fun `in kilometres the tile's words are the ones it always had`() {
        assertEquals(
            R.string.settings_odometer_none,
            odometerFirstReadingRes(DistanceUnit.KILOMETRES),
        )
        assertEquals(R.string.settings_odometer_field, odometerFieldRes(DistanceUnit.KILOMETRES))
    }
}

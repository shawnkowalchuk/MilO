package com.shawnkowalchuk.milo.feature.settings

import com.shawnkowalchuk.milo.core.odometer.OdometerReading
import com.shawnkowalchuk.milo.core.trip.TripStartCause
import com.shawnkowalchuk.milo.core.trip.TripStatus
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
        val state = odometerCardState(emptyList(), listOf(trip(readAt, 5_000.0)), now, zone, false)

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
            odometerCardState(listOf(OdometerReading(readAt, 123_456)), trips, now, zone, false)

        val figure = checkNotNull(state.figure)
        assertEquals(123_496L, figure.km)
        assertEquals(400L, figure.drivenTenths)
        assertTrue(figure.estimated)
    }
}

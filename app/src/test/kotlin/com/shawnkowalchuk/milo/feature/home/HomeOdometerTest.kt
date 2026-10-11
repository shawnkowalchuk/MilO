package com.shawnkowalchuk.milo.feature.home

import com.shawnkowalchuk.milo.core.odometer.OdometerReading
import com.shawnkowalchuk.milo.core.trip.TripStartCause
import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.core.util.DistanceUnit
import com.shawnkowalchuk.milo.data.settings.MiloSettings
import com.shawnkowalchuk.milo.data.settings.StoredVehicle
import com.shawnkowalchuk.milo.data.trip.Trip
import com.shawnkowalchuk.milo.data.trip.TripSoFar
import com.shawnkowalchuk.milo.data.trip.vehicleOdometers
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val TRUCK = "AA:BB:CC:DD:EE:FF"
private const val VAN = "22:33:44:55:66:77"

/**
 * Home's odometers: which one the trip being recorded moves, and what its wheels then show. The
 * odometers themselves are `vehicleOdometers`'; this is about what Home adds to them.
 */
class HomeOdometerTest {
    private val zone = ZoneId.of("America/Edmonton")
    private val readAt = 1_791_000_000_000L
    private val now = readAt + 3 * 24 * 3_600_000L
    private val tripStartedAt = now - 600_000

    private val truckOnly =
        MiloSettings(
            truckAddress = TRUCK,
            truckName = "Work truck",
            odometerReadings = listOf(OdometerReading(readAt, 123_456, DistanceUnit.KILOMETRES)),
        )

    private val withVan =
        truckOnly.copy(
            moreVehicles = listOf(StoredVehicle(VAN, "Van", 8, pairedAtMs = 5L)),
            odometerReadings =
                truckOnly.odometerReadings +
                    OdometerReading(readAt, 50_000, DistanceUnit.KILOMETRES, vehicle = VAN),
        )

    /** A trip of 40 km in the truck, an hour after the reading. */
    private val earlier = ended(id = 1, startedAtMs = readAt + 3_600_000, metres = 40_000.0)

    private fun ended(id: Long, startedAtMs: Long, metres: Double) = Trip(
        id = id,
        startedAtMs = startedAtMs,
        endedAtMs = startedAtMs + 1_800_000,
        status = TripStatus.FINISHED,
        startedBy = TripStartCause.TRUCK,
        truckSeen = true,
        distanceMetres = metres,
        vehicleAddress = TRUCK,
    )

    private fun driving(metres: Double, truckSeen: Boolean = true, vehicle: String? = TRUCK) =
        TripSoFar(tripId = 9, tripStartedAt, metres, truckSeen, vehicle)

    private fun home(
        settings: MiloSettings,
        soFar: TripSoFar?,
        trips: List<Trip> = listOf(earlier),
    ): List<HomeOdometer> = homeOdometers(settings, trips, now, zone, soFar)

    @Test
    fun `the odometers are the ones Settings shows`() {
        val soFar = driving(12_340.0)

        assertEquals(
            vehicleOdometers(withVan, listOf(earlier), now, zone, soFar),
            home(withVan, soFar).map { it.odometer },
        )
        assertEquals(
            vehicleOdometers(withVan, listOf(earlier), now, zone, null),
            home(withVan, soFar = null).map { it.odometer },
        )
    }

    @Test
    fun `with no trip open no odometer is counting, and the wheels stand`() {
        val only = home(truckOnly, soFar = null).single()

        assertFalse(only.counting)
        // 123,456 and 40.0 km.
        assertEquals(1_234_960L, only.wheels?.tenths)
        assertNull(only.wheels?.turn)
    }

    @Test
    fun `the truck's trip moves the truck's odometer, and its last wheel turns`() {
        val only = home(truckOnly, driving(12_340.0)).single()

        assertTrue(only.counting)
        assertEquals(12_340.0, only.tripMetres)
        // 40.0 km and the 12.3 km this trip is printed as; the wheel is 40 m past its digit.
        assertEquals(1_235_083L, only.wheels?.tenths)
        assertEquals(0.4, checkNotNull(only.wheels?.turn), 1e-9)
    }

    @Test
    fun `it is counting from the trip's first metre, before the figure has moved`() {
        val only = home(truckOnly, driving(0.0)).single()

        assertTrue(only.counting)
        assertEquals(1_234_960L, only.wheels?.tenths)
        assertEquals(0.0, checkNotNull(only.wheels?.turn), 1e-9)
    }

    @Test
    fun `a trip no paired vehicle has been seen in moves no odometer`() {
        val byButton = driving(12_340.0, truckSeen = false, vehicle = null)

        val only = home(truckOnly, byButton).single()

        assertFalse(only.counting)
        assertEquals(1_234_960L, only.wheels?.tenths)
    }

    @Test
    fun `among several vehicles only the one that is driven is counting`() {
        val inTheVan = home(withVan, driving(15_000.0, vehicle = VAN))
        val inTheTruck = home(withVan, driving(15_000.0))
        // A trip that names no vehicle is the first vehicle's, as a finished one is.
        val unnamed = home(withVan, driving(15_000.0, vehicle = null))

        assertEquals(listOf(false, true), inTheVan.map { it.counting })
        assertEquals(listOf(1_234_960L, 500_150L), inTheVan.map { it.wheels?.tenths })
        assertEquals(listOf(true, false), inTheTruck.map { it.counting })
        assertEquals(listOf(true, false), unnamed.map { it.counting })
    }

    @Test
    fun `a reading typed during the trip already holds it, so the wheels stand at that reading`() {
        val typedOnTheWay = OdometerReading(now - 60_000, 123_470, DistanceUnit.KILOMETRES)
        val settings =
            truckOnly.copy(odometerReadings = truckOnly.odometerReadings + typedOnTheWay)

        val only = home(settings, driving(12_340.0)).single()

        assertFalse(only.counting)
        assertEquals(1_234_700L, only.wheels?.tenths)
    }

    @Test
    fun `in the moment storage already has the trip as finished, it is no longer being counted`() {
        val asFinished = ended(id = 9, startedAtMs = tripStartedAt, metres = 12_000.0)

        val only = home(truckOnly, driving(12_340.0), listOf(earlier, asFinished)).single()

        assertFalse(only.counting)
        // 40.0 km and the 12.0 km that were stored.
        assertEquals(1_235_080L, only.wheels?.tenths)
    }

    @Test
    fun `a vehicle without a reading has no wheels, and counts nothing`() {
        val noReading = truckOnly.copy(odometerReadings = emptyList())

        val only = home(noReading, driving(12_340.0)).single()

        assertNull(only.wheels)
        assertFalse(only.counting)
    }

    @Test
    fun `in miles the wheels are in miles, and count the trip in tenths of a mile`() {
        val inMiles =
            truckOnly.copy(
                distanceUnit = DistanceUnit.MILES,
                odometerReadings = listOf(OdometerReading(readAt, 76_543, DistanceUnit.MILES)),
            )

        // No finished trip. This one has come exactly three miles and a quarter of a tenth.
        val only = home(inMiles, driving(4_828.032 + 40.2336), trips = emptyList()).single()

        assertTrue(only.counting)
        assertEquals(DistanceUnit.MILES, only.wheels?.unit)
        assertEquals(765_460L, only.wheels?.tenths)
        assertEquals(0.25, checkNotNull(only.wheels?.turn), 1e-9)
    }
}

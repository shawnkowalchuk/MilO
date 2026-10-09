package com.shawnkowalchuk.milo.data.trip

import com.shawnkowalchuk.milo.core.odometer.OdometerReading
import com.shawnkowalchuk.milo.core.trip.TripStartCause
import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.core.util.DistanceUnit
import com.shawnkowalchuk.milo.data.settings.MiloSettings
import com.shawnkowalchuk.milo.data.settings.StoredVehicle
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

private const val TRUCK = "AA:BB:CC:DD:EE:FF"
private const val VAN = "22:33:44:55:66:77"

/**
 * The odometers while a trip is being recorded (Shawn's request of 2026-10-09: "can we have the
 * mileage on the home screen change as we drive"). Home's tile and Settings' both show what
 * [vehicleOdometers] gives; what it gives with no trip open is checked through Settings' tiles,
 * in `OdometerCardStateTest`.
 */
class VehicleOdometersTest {
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

    /** A trip of 40 km in the truck, an hour after the reading. */
    private val earlier = ended(id = 1, startedAtMs = readAt + 3_600_000, metres = 40_000.0)

    private fun ended(id: Long, startedAtMs: Long, metres: Double, vehicle: String = TRUCK) = Trip(
        id = id,
        startedAtMs = startedAtMs,
        endedAtMs = startedAtMs + 1_800_000,
        status = TripStatus.FINISHED,
        startedBy = TripStartCause.TRUCK,
        truckSeen = true,
        distanceMetres = metres,
        vehicleAddress = vehicle,
    )

    private fun driving(metres: Double, truckSeen: Boolean = true, vehicle: String? = TRUCK) =
        TripSoFar(tripId = 9, tripStartedAt, metres, truckSeen, vehicle)

    private fun shown(settings: MiloSettings, trips: List<Trip>, soFar: TripSoFar?): List<Long?> =
        vehicleOdometers(settings, trips, now, zone, soFar).map { it.figure?.value }

    @Test
    fun `the odometer counts up as the trip is driven, a whole kilometre at a time`() {
        assertEquals(listOf(123_496L), shown(truckOnly, listOf(earlier), soFar = null))
        // 40.0 km and 0.4 km: still 123,496. With 0.6 km it is 123,496.6, shown as 123,497.
        assertEquals(listOf(123_496L), shown(truckOnly, listOf(earlier), driving(400.0)))
        assertEquals(listOf(123_497L), shown(truckOnly, listOf(earlier), driving(600.0)))
        assertEquals(listOf(123_508L), shown(truckOnly, listOf(earlier), driving(12_300.0)))
    }

    @Test
    fun `a trip no paired vehicle has been seen in moves no odometer`() {
        val byButton = driving(12_300.0, truckSeen = false, vehicle = null)

        assertEquals(listOf(123_496L), shown(truckOnly, listOf(earlier), byButton))
    }

    @Test
    fun `among several vehicles only the one that is driven counts up`() {
        val both =
            truckOnly.copy(
                moreVehicles = listOf(StoredVehicle(VAN, "Van", 8, pairedAtMs = 5L)),
                odometerReadings =
                    truckOnly.odometerReadings +
                        OdometerReading(readAt, 50_000, DistanceUnit.KILOMETRES, vehicle = VAN),
            )

        val inTheVan = shown(both, listOf(earlier), driving(15_000.0, vehicle = VAN))
        val inTheTruck = shown(both, listOf(earlier), driving(15_000.0))
        // A trip that names no vehicle is the first vehicle's, as a finished one is.
        val unnamed = shown(both, listOf(earlier), driving(15_000.0, vehicle = null))

        assertEquals(listOf(123_496L, 50_015L), inTheVan)
        assertEquals(listOf(123_511L, 50_000L), inTheTruck)
        assertEquals(inTheTruck, unnamed)
    }

    @Test
    fun `a reading typed during the trip already holds it`() {
        // A trip counts by when it started: this one started before the reading was typed.
        val typedOnTheWay = OdometerReading(now - 60_000, 123_470, DistanceUnit.KILOMETRES)
        val settings =
            truckOnly.copy(odometerReadings = truckOnly.odometerReadings + typedOnTheWay)

        assertEquals(listOf(123_470L), shown(settings, listOf(earlier), driving(12_300.0)))
    }

    @Test
    fun `the trip is not counted twice in the moment storage already has it as finished`() {
        val asFinished = ended(id = 9, startedAtMs = tripStartedAt, metres = 12_000.0)

        // 40.0 km and the 12.0 km that were stored, and not the 12.3 km of a moment ago as well.
        val odometers = shown(truckOnly, listOf(earlier, asFinished), driving(12_300.0))

        assertEquals(listOf(123_508L), odometers)
    }
}

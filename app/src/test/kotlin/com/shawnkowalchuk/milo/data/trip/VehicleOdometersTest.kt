package com.shawnkowalchuk.milo.data.trip

import com.shawnkowalchuk.milo.core.odometer.OdometerReading
import com.shawnkowalchuk.milo.core.odometer.TripAtReading
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
    fun `a reading from before 2026-10-10, typed during the trip, holds all of it as it did`() {
        // It does not say how far the trip had gone, so the trip counts by when it started, as
        // every trip did until then: this one started before the reading was typed.
        val typedOnTheWay = OdometerReading(now - 60_000, 123_470, DistanceUnit.KILOMETRES)
        val settings =
            truckOnly.copy(odometerReadings = truckOnly.odometerReadings + typedOnTheWay)

        assertEquals(listOf(123_470L), shown(settings, listOf(earlier), driving(12_300.0)))
    }

    // ---- A reading typed while the trip is being recorded (2026-10-10) ---------------------------
    //
    // Shawn: "i typed the km to the accurate reading before driving today and the kms are now
    // 102729". MilO showed 102,720: the trip was open when he typed it, and was never added.

    /** A reading typed a minute ago with trip 9 open, [metres] into it. */
    private fun typedDuringTheTrip(value: Long, metres: Double, vehicle: String? = null) =
        OdometerReading(
            now - 60_000,
            value,
            DistanceUnit.KILOMETRES,
            vehicle,
            TripAtReading(tripId = 9, metres = metres),
        )

    private fun with(reading: OdometerReading, base: MiloSettings = truckOnly) =
        base.copy(odometerReadings = base.odometerReadings + reading)

    @Test
    fun `typed before driving off, the odometer counts the whole drive up from the reading`() {
        val settings = with(typedDuringTheTrip(102_720, metres = 0.0))

        assertEquals(listOf(102_720L), shown(settings, listOf(earlier), driving(0.0)))
        assertEquals(listOf(102_720L), shown(settings, listOf(earlier), driving(400.0)))
        assertEquals(listOf(102_721L), shown(settings, listOf(earlier), driving(600.0)))
        assertEquals(listOf(102_729L), shown(settings, listOf(earlier), driving(9_200.0)))
    }

    @Test
    fun `typed on the way, it counts up by what is driven after the reading`() {
        val settings = with(typedDuringTheTrip(102_724, metres = 4_000.0))

        assertEquals(listOf(102_724L), shown(settings, listOf(earlier), driving(4_000.0)))
        assertEquals(listOf(102_729L), shown(settings, listOf(earlier), driving(9_000.0)))
    }

    @Test
    fun `when the trip has ended, what it drove after the reading stays added`() {
        val settings = with(typedDuringTheTrip(102_720, metres = 0.0))
        val asFinished = ended(id = 9, startedAtMs = tripStartedAt, metres = 9_200.0)

        // With the controller still showing it, and after: once, by the stored distance.
        val inBetween = shown(settings, listOf(earlier, asFinished), driving(9_300.0))
        val afterwards = shown(settings, listOf(earlier, asFinished), soFar = null)

        assertEquals(listOf(102_729L), inBetween)
        assertEquals(listOf(102_729L), afterwards)
    }

    @Test
    fun `a trip that is discarded or deleted adds nothing, and one that is restored adds again`() {
        val settings = with(typedDuringTheTrip(102_720, metres = 0.0))
        val asFinished = ended(id = 9, startedAtMs = tripStartedAt, metres = 9_200.0)

        for (status in listOf(TripStatus.DISCARDED, TripStatus.DELETED)) {
            val gone = asFinished.copy(status = status)

            assertEquals(status.name, listOf(102_720L), shown(settings, listOf(gone), null))
        }
        // Restored on the Trips screen: the same row, counted again.
        assertEquals(listOf(102_729L), shown(settings, listOf(asFinished), null))
    }

    @Test
    fun `a trip by button counts from the reading once the truck has joined it`() {
        // Typed with the trip open and no paired vehicle seen in it yet.
        val settings = with(typedDuringTheTrip(102_720, metres = 1_500.0))
        val notJoined = driving(5_000.0, truckSeen = false, vehicle = null)

        assertEquals(listOf(102_720L), shown(settings, listOf(earlier), notJoined))
        // The truck joins: what was driven after the reading, not the 1.5 km before it.
        assertEquals(listOf(102_724L), shown(settings, listOf(earlier), driving(5_000.0)))
    }

    @Test
    fun `among several vehicles the reading cuts only its own vehicle's trip`() {
        val van = StoredVehicle(VAN, "Van", 8, pairedAtMs = 5L)
        val vanReading = OdometerReading(readAt, 50_000, DistanceUnit.KILOMETRES, vehicle = VAN)
        val both =
            truckOnly.copy(
                moreVehicles = listOf(van),
                odometerReadings = truckOnly.odometerReadings + vanReading,
            )

        // The truck's reading, typed in the truck 4.0 km into its trip.
        val inTheTruck = with(typedDuringTheTrip(102_724, 4_000.0, vehicle = TRUCK), both)
        assertEquals(
            listOf(102_729L, 50_000L),
            shown(inTheTruck, listOf(earlier), driving(9_000.0)),
        )

        // The van's reading typed while the truck's trip was open: that trip is not the van's.
        val forTheVan = with(typedDuringTheTrip(50_100, 4_000.0, vehicle = VAN), both)
        assertEquals(listOf(123_505L, 50_100L), shown(forTheVan, listOf(earlier), driving(9_000.0)))

        // And the van's reading typed in the van, on the way.
        val inTheVan = shown(forTheVan, listOf(earlier), driving(9_000.0, vehicle = VAN))
        assertEquals(listOf(123_496L, 50_105L), inTheVan)
    }

    @Test
    fun `in miles the rest of the trip is added as it is printed in miles`() {
        val inMiles =
            truckOnly.copy(
                distanceUnit = DistanceUnit.MILES,
                odometerReadings =
                    listOf(
                        OdometerReading(
                            now - 60_000,
                            63_827,
                            DistanceUnit.MILES,
                            duringTrip = TripAtReading(tripId = 9, metres = 4_000.0),
                        ),
                    ),
            )

        // 5.0 km after the reading are 3.1 mi: 63,830.1, which a dashboard shows as 63,830.
        assertEquals(listOf(63_830L), shown(inMiles, listOf(earlier), driving(9_000.0)))
    }

    @Test
    fun `the trip is not counted twice in the moment storage already has it as finished`() {
        val asFinished = ended(id = 9, startedAtMs = tripStartedAt, metres = 12_000.0)

        // 40.0 km and the 12.0 km that were stored, and not the 12.3 km of a moment ago as well.
        val odometers = shown(truckOnly, listOf(earlier, asFinished), driving(12_300.0))

        assertEquals(listOf(123_508L), odometers)
    }
}

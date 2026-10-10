package com.shawnkowalchuk.milo.data.trip

import com.shawnkowalchuk.milo.core.odometer.DrivenTrip
import com.shawnkowalchuk.milo.core.schedule.TripCategory
import com.shawnkowalchuk.milo.core.trip.TripStartCause
import com.shawnkowalchuk.milo.core.trip.TripStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which trips move the truck's odometer (Shawn's choice of 2026-10-07: "Every truck trip"). */
class TruckTripsTest {
    private fun trip(
        status: TripStatus = TripStatus.FINISHED,
        startedBy: TripStartCause = TripStartCause.TRUCK,
        truckSeen: Boolean = true,
        addedByHand: Boolean = false,
        category: TripCategory? = TripCategory.BUSINESS,
    ) = Trip(
        id = 1,
        startedAtMs = 1_791_028_800_000,
        endedAtMs = 1_791_030_000_000,
        status = status,
        startedBy = startedBy,
        truckSeen = truckSeen,
        distanceMetres = 12_345.0,
        category = category,
        addedByHand = addedByHand,
    )

    @Test
    fun `every counted trip in the truck moves it, whatever it is saved as`() {
        assertTrue(trip().movesOdometer)
        assertTrue(trip(category = TripCategory.PERSONAL).movesOdometer)
        assertTrue(trip(category = null).movesOdometer)
        // Started with the button, and the truck joined it.
        assertTrue(trip(startedBy = TripStartCause.MANUAL).movesOdometer)
    }

    @Test
    fun `a trip typed in by hand moves it too`() {
        val typed = trip(startedBy = TripStartCause.MANUAL, truckSeen = false, addedByHand = true)

        assertTrue(typed.movesOdometer)
    }

    @Test
    fun `a trip the truck was never part of does not`() {
        assertFalse(trip(startedBy = TripStartCause.MANUAL, truckSeen = false).movesOdometer)
    }

    @Test
    fun `nor does one that is not counted`() {
        for (status in listOf(TripStatus.OPEN, TripStatus.DISCARDED, TripStatus.DELETED)) {
            assertFalse(status.name, trip(status = status).movesOdometer)
        }
    }

    // Since 2026-10-10 each trip is handed over with its number, by which a reading typed
    // during it finds it again (`cutAtReadings`).
    @Test
    fun `the odometer is handed each trip's start, distance and number`() {
        val kept = trip()
        val left = trip(status = TripStatus.DELETED)

        assertEquals(
            listOf(DrivenTrip(kept.startedAtMs, kept.distanceMetres, id = kept.id)),
            drivenTrips(listOf(kept, left)),
        )
    }

    // The trip being recorded (Shawn's request of 2026-10-09: the odometer changes as he drives).

    private fun recording(tripId: Long, truckSeen: Boolean = true) = TripSoFar(
        tripId = tripId,
        startedAtMs = 1_791_031_000_000,
        metres = 800.0,
        truckSeen = truckSeen,
        vehicle = "AA:BB:CC:DD:EE:FF",
    )

    @Test
    fun `the trip being recorded is handed over with them, once the truck was seen in it`() {
        val kept = trip()
        val ended = DrivenTrip(kept.startedAtMs, kept.distanceMetres, id = kept.id)
        val soFar = recording(tripId = 2)

        assertEquals(
            listOf(ended, DrivenTrip(soFar.startedAtMs, soFar.metres, soFar.vehicle, id = 2)),
            drivenTrips(listOf(kept), soFar),
        )
        // Started with the button, and no paired vehicle has joined it yet.
        assertEquals(listOf(ended), drivenTrips(listOf(kept), recording(2, truckSeen = false)))
    }

    @Test
    fun `it is not counted a second time once storage holds it as ended`() {
        // The stored trip has id 1, and so has the one the controller still shows as recording.
        val soFar = recording(tripId = 1)
        val asEnded = trip()

        assertEquals(
            listOf(DrivenTrip(asEnded.startedAtMs, asEnded.distanceMetres, id = asEnded.id)),
            drivenTrips(listOf(asEnded), soFar),
        )
        for (status in listOf(TripStatus.DISCARDED, TripStatus.DELETED)) {
            val none = drivenTrips(listOf(trip(status)), soFar)

            assertEquals(status.name, emptyList<DrivenTrip>(), none)
        }
        // Its own row while it is still open hides nothing.
        assertEquals(
            listOf(DrivenTrip(soFar.startedAtMs, soFar.metres, soFar.vehicle, id = 1)),
            drivenTrips(listOf(trip(TripStatus.OPEN)), soFar),
        )
    }
}

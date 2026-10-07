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

    @Test
    fun `the odometer is handed each trip's start and distance`() {
        val kept = trip()
        val left = trip(status = TripStatus.DELETED)

        assertEquals(
            listOf(DrivenTrip(kept.startedAtMs, kept.distanceMetres)),
            drivenTrips(listOf(kept, left)),
        )
    }
}

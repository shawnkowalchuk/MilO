package com.shawnkowalchuk.milo.platform.trip

import com.shawnkowalchuk.milo.core.trip.ActiveTrip
import com.shawnkowalchuk.milo.core.trip.TripStartCause
import com.shawnkowalchuk.milo.core.trip.TripState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

private const val STARTED_AT_MS = 1_791_028_800_000L

/**
 * What the screens are told about the truck's connection. The Android Auto screen's Status row
 * rests on it: it must be what the trip rules believe, and nothing at all before they know.
 */
class TripActivityTest {
    private val open = OpenTrip(id = 3, startedAtMs = STARTED_AT_MS, TripStartCause.MANUAL)

    private fun rulesKnow(tripOpen: Boolean, truckConnected: Boolean) = TripState(
        trip =
            if (tripOpen) {
                ActiveTrip(
                    TripStartCause.MANUAL,
                    truckSeen = false,
                    lastMovementAtMs = STARTED_AT_MS,
                )
            } else {
                null
            },
        truckConnected = truckConnected,
    )

    @Test
    fun `nothing is said about the truck before the stored state has been picked up`() {
        val activity = tripActivityOf(open = null, known = null, startFailure = null)

        assertNull(activity.truckConnected)
        assertNull(activity.trip)
    }

    @Test
    fun `with no trip open the truck is reported as the trip rules believe it`() {
        val gone = tripActivityOf(null, rulesKnow(tripOpen = false, truckConnected = false), null)
        val there = tripActivityOf(null, rulesKnow(tripOpen = false, truckConnected = true), null)

        assertEquals(false, gone.truckConnected)
        assertEquals(true, there.truckConnected)
        assertNull(gone.trip)
    }

    @Test
    fun `with a trip open the truck is reported beside the trip`() {
        val without = tripActivityOf(open, rulesKnow(tripOpen = true, truckConnected = false), null)
        val with = tripActivityOf(open, rulesKnow(tripOpen = true, truckConnected = true), null)

        assertNotNull(without.trip)
        assertEquals(false, without.truckConnected)
        assertEquals(true, with.truckConnected)
    }
}

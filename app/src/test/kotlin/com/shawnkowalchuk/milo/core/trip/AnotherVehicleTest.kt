package com.shawnkowalchuk.milo.core.trip

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A trip that a parked truck's moving started, and that lost the truck before a kilometre, was
 * Shawn leaving in another vehicle with the phone still connected to the truck.
 */
class AnotherVehicleTest {
    /**
     * A drive north of [metres] at 72 km/h, a fix every 50 m, cut where the truck was found
     * gone: its last fix.
     */
    private fun cutAfter(metres: Double): ClosedTrip {
        val drive = driveNorth(fixCount = (metres / 50).toInt() + 1, metresPerFix = 50.0)
        return TripClosing.close(drive, drive.last().wallClockMs, minimumDistanceMetres = 300.0)
    }

    @Test
    fun `losing the truck a few hundred metres from where it was parked is another vehicle`() {
        assertTrue(leftInAnotherVehicle(true, TripEndReason.GRACE_EXPIRED, cutAfter(400.0)))
        // Under the minimum distance it would be discarded anyway; it is removed all the same.
        assertTrue(leftInAnotherVehicle(true, TripEndReason.GRACE_EXPIRED, cutAfter(150.0)))
    }

    @Test
    fun `a kilometre or more with the truck connected was the truck`() {
        assertFalse(leftInAnotherVehicle(true, TripEndReason.GRACE_EXPIRED, cutAfter(1_050.0)))
        assertFalse(leftInAnotherVehicle(true, TripEndReason.GRACE_EXPIRED, cutAfter(6_000.0)))
    }

    @Test
    fun `only a trip that driving off from the parked place started`() {
        // A trip a new link, a reading or a button started: something vouched for the truck.
        assertFalse(leftInAnotherVehicle(false, TripEndReason.GRACE_EXPIRED, cutAfter(400.0)))
    }

    @Test
    fun `only when the truck's connection was lost for good`() {
        val short = cutAfter(400.0)
        for (reason in TripEndReason.entries.filter { it != TripEndReason.GRACE_EXPIRED }) {
            // Parked again with the truck still connected, ended with End, and the rest: the
            // truck was there, or nothing says it was not.
            assertFalse(reason.name, leftInAnotherVehicle(true, reason, short))
        }
    }

    @Test
    fun `the distance is the one up to where the truck was found gone`() {
        // 600 m with the truck, then a kilometre more after the link was lost, recorded during
        // the grace period: the trip is cut at 600 m, and that is what counts.
        val withTruck = driveNorth(fixCount = 7, metresPerFix = 100.0)
        val afterwards =
            driveNorth(
                fixCount = 10,
                metresPerFix = 100.0,
                fromNorthMetres = 700.0,
                fromSecond = 35,
            )
        val closed =
            TripClosing.close(withTruck + afterwards, withTruck.last().wallClockMs, 300.0)

        assertTrue(leftInAnotherVehicle(true, TripEndReason.GRACE_EXPIRED, closed))
    }
}

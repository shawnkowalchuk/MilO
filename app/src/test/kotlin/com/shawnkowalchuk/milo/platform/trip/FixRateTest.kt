package com.shawnkowalchuk.milo.platform.trip

import com.shawnkowalchuk.milo.core.trip.MOVEMENT_BORNE_OUT_WITHIN_MS
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The three rates at which the phone is asked for a GPS fix. They are numbers in an enum, and a
 * slip in any of them goes unnoticed on a desk: a trip recorded at the watch's rate cuts every
 * corner, and a parked truck watched at the recording rate costs fifteen times the battery all
 * night.
 */
class FixRateTest {
    @Test
    fun `a trip is recorded with a fix every 2 seconds`() {
        // Shawn's choice of 2026-10-09 ("every 1 to 2 seconds"), over the brief's 5.
        assertEquals(2_000L, FixRate.RECORDING.intervalMs)
    }

    @Test
    fun `after a report of getting into a vehicle the parked truck is watched every 5 seconds`() {
        // Shawn's choice of 2026-10-07 ("Yes, every 5 s"). It does not follow the trip's rate.
        assertEquals(5_000L, FixRate.WATCHING_CLOSELY.intervalMs)
    }

    @Test
    fun `a parked truck is watched with a fix every 30 seconds`() {
        assertEquals(30_000L, FixRate.WATCHING_PARKED.intervalMs)
        // Two fixes of the watch must fit well inside the time a first fix waits to be borne
        // out, or a truck that drives off would never be seen to move.
        assertEquals(4, MOVEMENT_BORNE_OUT_WITHIN_MS / FixRate.WATCHING_PARKED.intervalMs)
    }
}

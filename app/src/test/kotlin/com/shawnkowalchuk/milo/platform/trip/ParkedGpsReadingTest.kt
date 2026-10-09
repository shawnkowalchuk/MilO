package com.shawnkowalchuk.milo.platform.trip

import com.shawnkowalchuk.milo.core.trip.ParkedGps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** What the trip service makes of the times it is given for a wait beside the parked truck. */
class ParkedGpsReadingTest {
    private val now = 1_791_419_562_000L

    @Test
    fun `with no times at all GPS is read every 30 seconds for the whole wait`() {
        val gps = ParkedGps()

        assertEquals(FixRate.WATCHING_PARKED, gps.rateAt(now))
        assertNull(gps.nextChangeAfter(now))
    }

    @Test
    fun `GPS is read every 5 seconds, then every 30, then not at all`() {
        val gps = ParkedGps(untilMs = now + 3_600_000, fastUntilMs = now + 600_000)

        assertEquals(FixRate.RECORDING, gps.rateAt(now))
        assertEquals(FixRate.RECORDING, gps.rateAt(now + 599_999))
        assertEquals(FixRate.WATCHING_PARKED, gps.rateAt(now + 600_000))
        assertEquals(FixRate.WATCHING_PARKED, gps.rateAt(now + 3_599_999))
        assertNull(gps.rateAt(now + 3_600_000))
    }

    @Test
    fun `the next change is the nearer of the two times that are still to come`() {
        val gps = ParkedGps(untilMs = now + 3_600_000, fastUntilMs = now + 600_000)

        assertEquals(now + 600_000, gps.nextChangeAfter(now))
        assertEquals(now + 3_600_000, gps.nextChangeAfter(now + 600_000))
        assertNull(gps.nextChangeAfter(now + 3_600_000))
    }

    @Test
    fun `a fast time that outlasts the hour keeps GPS on until it is over`() {
        // A report of getting into a vehicle 55 minutes into the wait.
        val gps = ParkedGps(untilMs = now + 600_000, fastUntilMs = now + 600_000)

        assertEquals(FixRate.RECORDING, gps.rateAt(now + 599_999))
        assertNull(gps.rateAt(now + 600_000))
    }
}

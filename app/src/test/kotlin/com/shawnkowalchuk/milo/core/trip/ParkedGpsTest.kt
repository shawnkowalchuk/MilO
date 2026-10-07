package com.shawnkowalchuk.milo.core.trip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** GPS for the first hour of a wait beside the parked truck, then the phone's motion sensor. */
class ParkedGpsTest {
    private val since = 1_791_028_800_000L
    private val minute = 60_000L

    @Test
    fun `a wait reads GPS for its first hour`() {
        assertEquals(since + 60 * minute, parkedGpsUntilMs(since, null, sensorWatching = true))
    }

    @Test
    fun `a report of entering a vehicle reads GPS for ten minutes from it`() {
        val reportAt = since + 5 * 60 * minute

        assertEquals(reportAt + 10 * minute, parkedGpsUntilMs(since, reportAt, true))
    }

    @Test
    fun `a report never cuts the first hour short`() {
        // Within the hour, and from before the wait began (the drive that led to the stop).
        assertEquals(since + 60 * minute, parkedGpsUntilMs(since, since + 20 * minute, true))
        assertEquals(since + 60 * minute, parkedGpsUntilMs(since, since - 3 * minute, true))
        // Ten minutes before the hour is up: the ten minutes reach past it.
        assertEquals(since + 65 * minute, parkedGpsUntilMs(since, since + 55 * minute, true))
    }

    @Test
    fun `without the phone's reports GPS runs for the whole wait`() {
        // Nothing else would turn it on again for the drive.
        assertNull(parkedGpsUntilMs(since, null, sensorWatching = false))
        assertNull(parkedGpsUntilMs(since, since + 5 * 60 * minute, sensorWatching = false))
    }
}

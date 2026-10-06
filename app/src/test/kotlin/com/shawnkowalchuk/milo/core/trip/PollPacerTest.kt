package com.shawnkowalchuk.milo.core.trip

import org.junit.Assert.assertEquals
import org.junit.Test

private const val SECOND = 1_000L

/** Any elapsed-realtime moment: recording began here. */
private const val START = 5_000_000L

/**
 * When the truck's connection is read again during a trip: by the service's minute timer, or by
 * a GPS fix when the phone slept through the timer. Either way the readings stay about a minute
 * apart, because two of them in a row are believed.
 */
class PollPacerTest {
    private val pacer = PollPacer().apply { start(START) }

    @Test
    fun `the timer reads the truck every minute while it is working`() {
        val verdicts = (1..3).map { minute -> pacer.timerFired(START + minute * MINUTE) }

        assertEquals(listOf(true, true, true), verdicts)
    }

    @Test
    fun `fixes do not prompt a reading while the timer is on time`() {
        // A fix every five seconds for a minute, then the timer, then fixes again.
        val before = (1..11).map { fix -> pacer.fixArrived(START + fix * 5 * SECOND) }
        val timer = pacer.timerFired(START + MINUTE)
        val after = (1..11).map { fix -> pacer.fixArrived(START + MINUTE + fix * 5 * SECOND) }

        assertEquals(List(11) { false }, before)
        assertEquals(true, timer)
        assertEquals(List(11) { false }, after)
    }

    @Test
    fun `a fix stands in once the timer is ten seconds late`() {
        // The phone slept between fixes and the timer did not count that time.
        assertEquals(false, pacer.fixArrived(START + MINUTE + POLL_LATE_AFTER_MS - 1))
        assertEquals(true, pacer.fixArrived(START + MINUTE + POLL_LATE_AFTER_MS))
    }

    @Test
    fun `the late timer does not add a second reading seconds after the fix stood in for it`() {
        // Two readings of "not connected" in a row are believed. Five seconds apart, they
        // would be worth no more than one.
        val stoodIn = START + MINUTE + 10 * SECOND
        pacer.fixArrived(stoodIn)

        assertEquals(false, pacer.timerFired(stoodIn + 5 * SECOND))
        // The next reading is due a minute after the one the fix prompted.
        assertEquals(false, pacer.fixArrived(stoodIn + MINUTE))
        assertEquals(true, pacer.fixArrived(stoodIn + MINUTE + 10 * SECOND))
    }

    @Test
    fun `while the timer stays late, fixes keep the readings seventy seconds apart`() {
        var readings = 0
        // Ten minutes of fixes, five seconds apart, and no timer at all.
        for (fix in 1..120) {
            if (pacer.fixArrived(START + fix * 5 * SECOND)) readings++
        }

        assertEquals(8, readings)
    }

    @Test
    fun `a new recording starts the minute again`() {
        pacer.timerFired(START + MINUTE)
        val nextTrip = START + 3 * 60 * MINUTE

        pacer.start(nextTrip)

        // Without the restart the first fix of the new trip would prompt a reading at once.
        assertEquals(false, pacer.fixArrived(nextTrip + 5 * SECOND))
        assertEquals(true, pacer.timerFired(nextTrip + MINUTE))
    }
}

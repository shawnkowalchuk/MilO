package com.shawnkowalchuk.milo.core.clock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val SECOND_MS = 1_000L
private const val HOUR_MS = 3_600 * SECOND_MS

/** Wednesday 7 October 2026, 18:32:42 in Edmonton. */
private const val EVENING_MS = 1_791_419_562_000L

/**
 * One reading of MilO's clock asks the phone twice: for the time since boot, and for the time
 * of day. A process that Android stops exactly between the two, or a phone that goes to sleep
 * there, would hold a fresh time of day against an old time since boot and see a change of the
 * clock that nobody made. The phone of these tests can be stopped at that very point.
 */
class TrustedClockReadingTest {
    /** Time since boot. The true time is [BOOTED_AT_MS] plus this: nobody sets this phone's clock. */
    private var elapsedMs = 1_000_000L

    /** How long the process is stopped the next time it has read the time since boot. */
    private var stopsForMs = 0L

    /** True for a phone that never holds still between the two readings. */
    private var stopsEveryTime = false

    private val news = mutableListOf<ClockNews>()

    private fun clock(): TrustedClock = TrustedClock(
        phoneNow = {
            // The stop falls here: after the time since boot was read, before the time of day is.
            elapsedMs += stopsForMs
            if (!stopsEveryTime) stopsForMs = 0L
            BOOTED_AT_MS + elapsedMs
        },
        elapsedNow = { elapsedMs },
        bootCount = 7,
        stored = null,
        save = {},
    ).also { it.tellNewsTo { told -> news += told } }

    @Test
    fun `a process stopped for hours between the two readings sees no change of the clock`() {
        val clock = clock()
        elapsedMs += SECOND_MS

        stopsForMs = 3 * HOUR_MS
        val answered = clock.now()

        // Before this was guarded: an answer three hours old, and a line in the log about a
        // phone's clock "set 3 h 0 min ahead" that nobody had touched.
        assertEquals(BOOTED_AT_MS + elapsedMs, answered)
        assertTrue(clock.agreesWithPhone())
        assertEquals(emptyList<ClockNews>(), news)
    }

    @Test
    fun `a stop of a second or less between the two is no reason to read again`() {
        val clock = clock()

        stopsForMs = SECOND_MS
        val answered = clock.now()

        assertEquals(BOOTED_AT_MS + elapsedMs, answered)
        assertEquals(emptyList<ClockNews>(), news)
    }

    @Test
    fun `a clock made while the process is stopped between the two starts from a true anchor`() {
        stopsForMs = 3 * HOUR_MS
        val clock = clock()

        assertEquals(BOOTED_AT_MS + elapsedMs, clock.now())
        elapsedMs += 5 * SECOND_MS
        assertEquals(BOOTED_AT_MS + elapsedMs, clock.now())
        // An anchor three hours out would have been "found ahead" at the next reading.
        assertEquals(emptyList<ClockNews>(), news)
    }

    @Test
    fun `a phone that never holds still between the two is still answered`() {
        stopsForMs = 5 * SECOND_MS
        stopsEveryTime = true
        val clock = clock()

        val answered = clock.now()

        // The pair is taken as it comes after three tries: the time of day as it was read.
        assertEquals(BOOTED_AT_MS + elapsedMs, answered)
    }

    private companion object {
        /** The true time at which this phone was switched on. */
        const val BOOTED_AT_MS = EVENING_MS - 1_000_000L
    }
}

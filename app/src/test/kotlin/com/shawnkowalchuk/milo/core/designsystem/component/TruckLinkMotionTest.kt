package com.shawnkowalchuk.milo.core.designsystem.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The movements of the truck's tile, held against the owner's design: how long each takes, and
 * where each begins, ends and turns. The design's own file gives them as "flow" (0.5 s),
 * "btFlash" (0.9 s, 1 to 0.2 and back), "ping" (1.2 s and 2.4 s, 0.85 to 1.9 while 0.8 fades to
 * nothing) and "glint" (1.8 s, from 30 percent before the line to 10 percent past it).
 */
class TruckLinkMotionTest {
    private val exact = 0.0001f

    @Test
    fun `each movement takes the time the design gives it`() {
        assertEquals(500, DASH_FLOW_MS)
        assertEquals(900, MARK_PULSE_MS)
        assertEquals(1_200, MARK_PING_MS)
        assertEquals(1_800, GLINT_MS)
        assertEquals(2_400, TRUCK_PING_MS)
    }

    @Test
    fun `the Bluetooth mark fades to a fifth at the half of its pulse, and comes back`() {
        assertEquals(1f, markPulseAlpha(0f), exact)
        assertEquals(0.2f, markPulseAlpha(0.5f), exact)
        assertEquals(1f, markPulseAlpha(1f), exact)
    }

    @Test
    fun `the mark fades and comes back alike, slow at each end`() {
        // A quarter of the way is the middle of the way down; three quarters, of the way back.
        assertEquals(0.6f, markPulseAlpha(0.25f), exact)
        assertEquals(markPulseAlpha(0.25f), markPulseAlpha(0.75f), exact)
        assertEquals(markPulseAlpha(0.1f), markPulseAlpha(0.9f), exact)
        // Eased in and out: early in the pulse it has hardly faded.
        assertTrue(markPulseAlpha(0.05f) > 0.97f)
    }

    @Test
    fun `a ring starts smaller than its patch and ends almost twice as large`() {
        assertEquals(0.85f, pingScale(0f), exact)
        assertEquals(1.9f, pingScale(1f), exact)
    }

    @Test
    fun `a ring fades from four fifths to nothing as it grows`() {
        assertEquals(0.8f, pingAlpha(0f), exact)
        assertEquals(0f, pingAlpha(1f), exact)
    }

    @Test
    fun `a ring slows down as it spreads`() {
        // Eased out: more than half of the way is done in the first half of the time.
        assertTrue(pingScale(0.5f) > (0.85f + 1.9f) / 2)
        assertTrue(pingAlpha(0.5f) < 0.4f)
        var before = pingScale(0f)
        for (step in 1..10) {
            val now = pingScale(step / 10f)
            assertTrue("the ring only ever grows", now > before)
            before = now
        }
    }

    @Test
    fun `the light runs from before the line's start to past its end`() {
        assertEquals(-0.3f, glintStart(0f), exact)
        assertEquals(1.1f, glintStart(1f), exact)
        // Half-way through its time it is half-way along its path.
        assertEquals(0.4f, glintStart(0.5f), exact)
        assertEquals(0.3f, GLINT_WIDTH, exact)
    }

    @Test
    fun `at the end of a turn nothing that moves can be seen, and the mark shows fully`() {
        // The end is where every movement is held while nothing may move: with the phone's
        // animations switched off, out of sight, or in the background.
        assertEquals(0f, pingAlpha(1f), exact)
        assertTrue("the light lies past the end of the line", glintStart(1f) >= 1f)
        assertEquals(1f, markPulseAlpha(1f), exact)
    }
}

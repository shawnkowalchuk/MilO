package com.shawnkowalchuk.milo.core.designsystem.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * The arithmetic of the two-handle slider: where a value stands on its line, which value a
 * place on the line is, how close the two handles may come, and one step of a screen reader.
 * The numbers are the design's own line for the work hours: minutes since midnight, from 4 AM
 * to 10 PM in quarters of an hour, with at least half an hour between the handles.
 */
class SpanScaleTest {
    private fun at(hour: Int, minute: Int = 0): Int = hour * 60 + minute

    private val line = SpanScale(from = at(4), to = at(22), step = 15, minimumSpan = 30)
    private val day = Span(start = at(8), end = at(16, 30))

    private fun fractionOf(value: Int): Float =
        (value - line.from).toFloat() / (line.to - line.from)

    @Test
    fun `a value stands at its share of the line, as the design's script places it`() {
        assertEquals(0f, line.fractionOf(at(4)), 0f)
        assertEquals(1f, line.fractionOf(at(22)), 0f)
        assertEquals(0.5f, line.fractionOf(at(13)), 0f)
        // The design's own start, 8:00 AM: 240 of the line's 1080 minutes.
        assertEquals(240f / 1080f, line.fractionOf(at(8)), 1e-6f)
    }

    @Test
    fun `a value off the line stands at the nearer end`() {
        assertEquals(0f, line.fractionOf(at(3)), 0f)
        assertEquals(1f, line.fractionOf(at(23, 59)), 0f)
    }

    @Test
    fun `a place on the line is the nearest quarter of an hour`() {
        assertEquals(at(4), line.valueAt(0f))
        assertEquals(at(22), line.valueAt(1f))
        assertEquals(at(13), line.valueAt(0.5f))
        // Seven minutes past a quarter goes back to it, eight minutes past goes on.
        assertEquals(at(9, 15), line.valueAt(fractionOf(at(9, 22))))
        assertEquals(at(9, 30), line.valueAt(fractionOf(at(9, 23))))
    }

    @Test
    fun `a place beyond an end of the line is that end`() {
        assertEquals(at(4), line.valueAt(-0.3f))
        assertEquals(at(22), line.valueAt(1.7f))
    }

    @Test
    fun `every quarter of an hour of the line can be set and is shown where it was set`() {
        for (value in line.from..line.to step line.step) {
            assertEquals(value, line.valueAt(line.fractionOf(value)))
        }
    }

    @Test
    fun `moving one handle leaves the other where it is`() {
        assertEquals(Span(at(6, 45), at(16, 30)), line.withStartAt(fractionOf(at(6, 45)), day))
        assertEquals(Span(at(8), at(18)), line.withEndAt(fractionOf(at(18)), day))
    }

    @Test
    fun `the handles cannot cross, and stay half an hour apart, as in the design's script`() {
        // The start dragged far past the end stops half an hour before it.
        assertEquals(Span(at(16), at(16, 30)), line.withStartAt(1f, day))
        // The end dragged far before the start stops half an hour after it.
        assertEquals(Span(at(8), at(8, 30)), line.withEndAt(0f, day))
        // Exactly half an hour apart is allowed; a quarter of an hour is not.
        assertEquals(at(16), line.withStartAt(fractionOf(at(16)), day).start)
        assertEquals(at(16), line.withStartAt(fractionOf(at(16, 15)), day).start)
    }

    @Test
    fun `a time set to the minute elsewhere stays until its own handle is moved`() {
        val picked = Span(start = at(8, 7), end = at(16, 37))

        // The other handle's move does not touch it.
        assertEquals(at(8, 7), line.withEndAt(fractionOf(at(18)), picked).start)
        assertEquals(at(16, 37), line.withStartAt(fractionOf(at(7)), picked).end)
        // Its own move puts it on a quarter of an hour.
        assertEquals(at(8, 15), line.withStartAt(fractionOf(at(8, 9)), picked).start)
    }

    @Test
    fun `beside a time between two quarters the other handle stops a quarter further off`() {
        // The end is 16:37: the latest start half an hour before it is 16:07, so 16:00.
        assertEquals(at(16), line.withStartAt(1f, Span(at(8), at(16, 37))).start)
        // The start is 8:07: the earliest end half an hour after it is 8:37, so 8:45.
        assertEquals(at(8, 45), line.withEndAt(0f, Span(at(8, 7), at(16, 30))).end)
    }

    @Test
    fun `a handle cannot leave the line`() {
        assertEquals(at(4), line.withStartAt(-1f, day).start)
        assertEquals(at(22), line.withEndAt(2f, day).end)
    }

    @Test
    fun `one step of a screen reader is one quarter of an hour`() {
        // Compose asks for the present value plus or minus one step of the line.
        assertEquals(at(8, 15), line.withStartSteppedTo(at(8, 15).toFloat(), day).start)
        assertEquals(at(7, 45), line.withStartSteppedTo(at(7, 45).toFloat(), day).start)
        assertEquals(at(16, 45), line.withEndSteppedTo(at(16, 45).toFloat(), day).end)
        assertEquals(at(16, 15), line.withEndSteppedTo(at(16, 15).toFloat(), day).end)
    }

    @Test
    fun `the line has 72 quarters of an hour, so 71 values between its two ends`() {
        assertEquals(71, line.stepsBetweenEnds)
    }

    @Test
    fun `from a time between two quarters a step goes to the next quarter that way, not past it`() {
        val picked = Span(start = at(8, 7), end = at(16, 37))

        assertEquals(at(8, 15), line.withStartSteppedTo(at(8, 22).toFloat(), picked).start)
        assertEquals(at(8), line.withStartSteppedTo(at(7, 52).toFloat(), picked).start)
        assertEquals(at(16, 45), line.withEndSteppedTo(at(16, 52).toFloat(), picked).end)
        assertEquals(at(16, 30), line.withEndSteppedTo(at(16, 22).toFloat(), picked).end)
    }

    @Test
    fun `a screen reader that asks for a value far away gets the nearest quarter to it`() {
        assertEquals(at(11), line.withStartSteppedTo(at(11, 4).toFloat(), day).start)
        assertEquals(at(20, 15), line.withEndSteppedTo(at(20, 10).toFloat(), day).end)
    }

    @Test
    fun `a screen reader's step keeps the handles apart and on the line too`() {
        val close = Span(start = at(8), end = at(8, 30))
        assertEquals(close, line.withStartSteppedTo(at(8, 15).toFloat(), close))
        assertEquals(close, line.withEndSteppedTo(at(8, 15).toFloat(), close))

        val whole = Span(start = at(4), end = at(22))
        assertEquals(whole, line.withStartSteppedTo(at(3, 45).toFloat(), whole))
        assertEquals(whole, line.withEndSteppedTo(at(22, 15).toFloat(), whole))
    }

    @Test
    fun `the end of a whole day's line can be reached and left again`() {
        val wholeDay = SpanScale(from = 0, to = at(24), step = 15, minimumSpan = 30)
        // 23:59 is the latest a day's hours can end: one minute short of the line's end.
        val late = Span(start = at(8), end = at(23, 59))

        assertEquals(at(24), wholeDay.withEndSteppedTo(at(24).toFloat(), late).end)
        assertEquals(at(23, 45), wholeDay.withEndSteppedTo(at(23, 44).toFloat(), late).end)
        assertEquals(at(23, 15), wholeDay.withStartAt(1f, late).start)
    }

    @Test
    fun `a line that cannot be stepped, or a mark that is not on it, is refused`() {
        assertThrows(IllegalArgumentException::class.java) { SpanScale(10, 10, 5, 5) }
        assertThrows(IllegalArgumentException::class.java) { SpanScale(0, 100, 15, 30) }
        assertThrows(IllegalArgumentException::class.java) { SpanScale(0, 60, 15, 5) }
        assertThrows(IllegalArgumentException::class.java) { SpanScale(0, 60, 15, 90) }
        assertThrows(IllegalArgumentException::class.java) {
            SpanScale(0, 60, 15, 30, marks = listOf(0, 75))
        }
    }

    @Test
    fun `a finger takes the nearer handle, and none when it is far from both`() {
        assertEquals(SpanGrab.START, grabbedHandle(105f, 100f, 300f, reach = 24f, closest = false))
        assertEquals(SpanGrab.END, grabbedHandle(290f, 100f, 300f, reach = 24f, closest = false))
        assertNull(grabbedHandle(200f, 100f, 300f, reach = 24f, closest = false))
        // Between two handles that are near each other but may still come closer: the nearer.
        assertEquals(SpanGrab.START, grabbedHandle(108f, 100f, 120f, reach = 24f, closest = false))
        assertEquals(SpanGrab.END, grabbedHandle(112f, 100f, 120f, reach = 24f, closest = false))
    }

    @Test
    fun `between two handles that stand as close as they may, the way the finger moves decides`() {
        assertEquals(SpanGrab.EITHER, grabbedHandle(104f, 100f, 108f, reach = 24f, closest = true))
        // Out of reach of one of them, it is the other, whichever way the finger then moves.
        assertEquals(SpanGrab.START, grabbedHandle(80f, 100f, 108f, reach = 24f, closest = true))
        assertEquals(SpanGrab.END, grabbedHandle(130f, 100f, 108f, reach = 24f, closest = true))
    }
}

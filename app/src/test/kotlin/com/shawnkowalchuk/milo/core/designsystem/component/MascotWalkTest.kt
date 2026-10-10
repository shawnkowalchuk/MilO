package com.shawnkowalchuk.milo.core.designsystem.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The mascot's walk into the screen, and a greeting's course from stage to stage: which frame
 * of the walk is due at each moment and where the mascot is in it, that its step and its place
 * cannot come apart, and that a greeting ends once.
 */
class MascotWalkTest {
    private val exact = 0.001f

    /** Every width from a small phone to a tablet on its side, in dp. */
    private val widths = (320..1280).map { it.toFloat() }

    /** How far one frame of the walk carries the mascot. */
    private val perFrame = MascotPicture.STRIDE / MascotPicture.WALK_FRAMES

    @Test
    fun `at the start nothing of the mascot is on the screen`() {
        for (width in widths) {
            val walk = MascotWalk(width)
            assertEquals(walk.start, walk.middleIn(0), exact)
            val leftmost = walk.start - MascotPicture.REACH
            assertTrue("$width dp: the mascot begins at $leftmost", leftmost >= width)
        }
    }

    @Test
    fun `it does not start further out than one cycle of its walk`() {
        for (width in widths) {
            val walk = MascotWalk(width)
            val beyondTheEdge = walk.start - MascotPicture.REACH - width
            assertTrue("$width dp: $beyondTheEdge", beyondTheEdge < MascotPicture.STRIDE)
        }
    }

    @Test
    fun `at the end it stands in the middle of the screen's width`() {
        for (width in widths) {
            val walk = MascotWalk(width)
            assertEquals(width / 2, walk.end, exact)
            assertEquals(width / 2, walk.middleIn(walk.frames), exact)
            assertEquals(walk.frames, walk.frameAt(walk.millis))
            // And stays there, however long the turn and the wave take.
            assertEquals(walk.frames, walk.frameAt(walk.millis + 5_000))
            assertEquals(width / 2, walk.middleIn(walk.frames + 100), exact)
        }
    }

    @Test
    fun `the walk is a whole number of cycles, so the turn follows its last frame`() {
        for (width in widths) {
            val walk = MascotWalk(width)
            assertTrue("$width dp: ${walk.cycles} cycles", walk.cycles >= 1)
            assertEquals(walk.cycles * MascotPicture.WALK_FRAMES, walk.frames)
            assertEquals(walk.frames.toLong() * MascotPicture.FRAME_MS, walk.millis)
            assertEquals(walk.cycles * MascotPicture.STRIDE, walk.start - walk.end, exact)
        }
    }

    @Test
    fun `the feet do not slide, every frame stands a twelfth of a stride left of the last`() {
        for (width in listOf(320f, 360f, 393f, 411f, 430f, 600f, 840f, 1280f)) {
            val walk = MascotWalk(width)
            for (frame in 0 until walk.frames) {
                val moved = walk.middleIn(frame) - walk.middleIn(frame + 1)
                assertEquals("$width dp, frame $frame", perFrame, moved, exact)
            }
            // The walk's last frame is one step short of the middle. The turn's first frame is
            // the frame that follows it, and stands in the middle.
            assertEquals(walk.end + perFrame, walk.middleIn(walk.frames - 1), exact)
        }
    }

    @Test
    fun `every frame is due for 42 milliseconds, the first from the start`() {
        val walk = MascotWalk(393f)
        assertEquals(0, walk.frameAt(0))
        assertEquals(0, walk.frameAt(41))
        assertEquals(1, walk.frameAt(42))
        assertEquals(11, walk.frameAt(503))
        // The second cycle: the app draws frame 12 as the sheet's first again.
        assertEquals(12, walk.frameAt(504))
        // The last frame has its 42 milliseconds too, and then the walk is over.
        assertEquals(walk.frames - 1, walk.frameAt(walk.millis - 42))
        assertEquals(walk.frames - 1, walk.frameAt(walk.millis - 1))
        assertEquals(walk.frames, walk.frameAt(walk.millis))
    }

    @Test
    fun `before the walk it waits at its start`() {
        val walk = MascotWalk(393f)
        assertEquals(0, walk.frameAt(-300))
        assertEquals(walk.start, walk.middleIn(0), exact)
        assertEquals(walk.start, walk.middleIn(-3), exact)
    }

    @Test
    fun `a phone that draws slowly leaves frames out, and the walk still ends on time`() {
        val walk = MascotWalk(393f)
        // The screen is drawn every 130 ms: three frames of the walk pass between two drawings.
        val drawn = (0..walk.millis + 130 step 130).map(walk::frameAt)
        assertEquals(listOf(0, 3, 6, 9, 12), drawn.take(5))
        assertTrue(drawn.zipWithNext().all { (earlier, later) -> later >= earlier })
        // Whatever is left out, the place is the frame's own: the two are one number.
        for (frame in drawn) {
            assertEquals(walk.start - frame * perFrame, walk.middleIn(frame), exact)
        }
        // The first drawing at or after the walk's time shows the turn, in the middle.
        assertEquals(walk.frames, drawn.last())
        assertEquals(walk.frames, walk.frameAt(walk.millis + 129))
    }

    @Test
    fun `a stride, a reach and a frame's time of one's own are used as given`() {
        // 100 to the middle and 20 of reach, in strides of 60: two cycles, from 220.
        val walk = MascotWalk(screenWidth = 200f, stride = 60f, reach = 20f, frameMillis = 50)
        assertEquals(2, walk.cycles)
        assertEquals(24, walk.frames)
        assertEquals(220f, walk.start, exact)
        assertEquals(1_200L, walk.millis)
        assertEquals(12, walk.frameAt(600))
        assertEquals(160f, walk.middleIn(12), exact)
        // A way that is not a whole number of strides is made one cycle longer.
        assertEquals(3, MascotWalk(200f, stride = 50f, reach = 20f).cycles)
    }

    @Test
    fun `on a phone the walk takes about four seconds, on a wide screen as long as it is wide`() {
        // 360 to 430 dp is every phone held upright. Shawn's is 393 dp wide.
        assertEquals(7, MascotWalk(360f).cycles)
        assertEquals(8, MascotWalk(393f).cycles)
        assertEquals(96, MascotWalk(393f).frames)
        assertEquals(4_032L, MascotWalk(393f).millis)
        assertEquals(8, MascotWalk(411f).cycles)
        assertEquals(8, MascotWalk(430f).cycles)
        assertEquals(20, MascotWalk(1280f).cycles)
    }

    // ------------------------------------------------------------ a greeting's course

    private class Told {
        val stages = mutableListOf<GreetingStage>()
        var done = 0
        val course = GreetingCourse(onStage = { stages += it }, onDone = { done++ })
    }

    private val inOrder =
        listOf(
            GreetingEvent.PICTURES_READ,
            GreetingEvent.WALK_OVER,
            GreetingEvent.WAVE_OVER,
            GreetingEvent.FADED,
        )

    @Test
    fun `a greeting goes from reading to walking, waving and leaving, and is over once`() {
        val told = Told()
        assertEquals(GreetingStage.READING, told.course.stage)

        inOrder.forEach(told.course::on)

        assertEquals(
            listOf(
                GreetingStage.WALKING,
                GreetingStage.WAVING,
                GreetingStage.LEAVING,
                GreetingStage.OVER,
            ),
            told.stages,
        )
        assertEquals(1, told.done)
    }

    @Test
    fun `cut short at any stage, it is over at once and once`() {
        for (before in 0..inOrder.size) {
            val told = Told()
            inOrder.take(before).forEach(told.course::on)

            told.course.on(GreetingEvent.CUT_SHORT)

            assertEquals(GreetingStage.OVER, told.course.stage)
            assertEquals("after $before steps", 1, told.done)
            assertEquals("after $before steps", 1, told.stages.count { it == GreetingStage.OVER })
        }
    }

    @Test
    fun `an event that does not belong to the stage changes nothing`() {
        val told = Told()
        told.course.on(GreetingEvent.WAVE_OVER)
        told.course.on(GreetingEvent.FADED)
        told.course.on(GreetingEvent.WALK_OVER)
        assertEquals(GreetingStage.READING, told.course.stage)
        assertEquals(emptyList<GreetingStage>(), told.stages)
        assertEquals(0, told.done)
    }

    @Test
    fun `whatever happens, in whatever order and however often, a greeting is done at most once`() {
        val events = GreetingEvent.entries
        // Every run of six events, of five kinds: 15,625 of them.
        val runs = generateSequence(listOf(emptyList<GreetingEvent>())) { shorter ->
            shorter.flatMap { run -> events.map { run + it } }
        }.elementAt(6)
        assertEquals(15_625, runs.size)
        for (run in runs) {
            val told = Told()
            run.forEach(told.course::on)
            val over = told.course.stage == GreetingStage.OVER
            assertEquals("$run", if (over) 1 else 0, told.done)
            // Once it is over, nothing is told again and no stage follows.
            assertEquals("$run", if (over) 1 else 0, told.stages.count { it == GreetingStage.OVER })
            if (over) assertEquals("$run", GreetingStage.OVER, told.stages.last())
            // A run that holds the cut, or the four steps in their order, is over.
            if (GreetingEvent.CUT_SHORT in run) assertTrue("$run", over)
        }
    }
}

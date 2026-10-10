package com.shawnkowalchuk.milo.core.designsystem.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The mascot's walk into the screen, and a greeting's course from stage to stage: where the
 * mascot is at each moment, that its feet cannot slide, and that a greeting ends once.
 */
class MascotWalkTest {
    private val exact = 0.001f

    /** Every width from a small phone to a tablet on its side, in dp. */
    private val widths = (320..1280).map { it.toFloat() }

    @Test
    fun `at the start nothing of the mascot is on the screen`() {
        for (width in widths) {
            val walk = MascotWalk(width)
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
    fun `the picture itself is on the screen from the start, or Android would not play it`() {
        // An animated picture only moves on while some part of it is drawn. The picture is
        // wider than the mascot in it, and that see-through margin is what is on the screen.
        for (width in widths) {
            val walk = MascotWalk(width)
            assertTrue("$width dp", walk.start - MascotPicture.WIDTH / 2 < width)
        }
    }

    @Test
    fun `at the end it stands in the middle of the screen's width`() {
        for (width in widths) {
            val walk = MascotWalk(width)
            assertEquals(width / 2, walk.end, exact)
            assertEquals(width / 2, walk.middleAt(walk.millis), exact)
            // And stays there, however long the turn is waited for.
            assertEquals(width / 2, walk.middleAt(walk.millis + 5_000), exact)
        }
    }

    @Test
    fun `the walk is a whole number of cycles of its picture`() {
        for (width in widths) {
            val walk = MascotWalk(width)
            assertTrue("$width dp: ${walk.cycles} cycles", walk.cycles >= 1)
            assertEquals(walk.cycles * MascotPicture.WALK_CYCLE_MS.toLong(), walk.millis)
            assertEquals(walk.cycles * MascotPicture.STRIDE, walk.start - walk.end, exact)
        }
    }

    @Test
    fun `the feet do not slide, every cycle moves the picture by one stride`() {
        for (width in listOf(320f, 360f, 393f, 411f, 430f, 600f, 840f, 1280f)) {
            val walk = MascotWalk(width)
            for (cycle in 0 until walk.cycles) {
                val from = walk.middleAt(cycle * MascotPicture.WALK_CYCLE_MS.toLong())
                val to = walk.middleAt((cycle + 1) * MascotPicture.WALK_CYCLE_MS.toLong())
                assertEquals("$width dp, cycle $cycle", MascotPicture.STRIDE, from - to, exact)
            }
        }
    }

    @Test
    fun `it walks at an even pace, a twelfth of a stride for each frame of the picture`() {
        val walk = MascotWalk(393f)
        val perFrame = MascotPicture.STRIDE / MascotPicture.WALK_FRAMES
        var at = 0L
        while (at + MascotPicture.FRAME_MS <= walk.millis) {
            val moved = walk.middleAt(at) - walk.middleAt(at + MascotPicture.FRAME_MS)
            assertEquals("at $at ms", perFrame, moved, exact)
            at += MascotPicture.FRAME_MS
        }
    }

    @Test
    fun `before the walk it waits at its start`() {
        val walk = MascotWalk(393f)
        assertEquals(walk.start, walk.middleAt(0), exact)
        assertEquals(walk.start, walk.middleAt(-300), exact)
    }

    @Test
    fun `a stride and a reach of one's own are used as given`() {
        // 100 to the middle and 20 of reach, in strides of 50: three cycles, from 250.
        val walk = MascotWalk(screenWidth = 200f, stride = 50f, reach = 20f, cycleMillis = 400)
        assertEquals(3, walk.cycles)
        assertEquals(250f, walk.start, exact)
        assertEquals(1_200L, walk.millis)
        assertEquals(175f, walk.middleAt(600), exact)
        // A way that is a whole number of strides is not made one cycle longer.
        assertEquals(2, MascotWalk(200f, stride = 60f, reach = 20f, cycleMillis = 400).cycles)
    }

    @Test
    fun `on a phone the walk takes about four seconds, on a wide screen as long as it is wide`() {
        // 360 to 430 dp is every phone held upright. Shawn's is 393 dp wide.
        assertEquals(7, MascotWalk(360f).cycles)
        assertEquals(8, MascotWalk(393f).cycles)
        assertEquals(4_032L, MascotWalk(393f).millis)
        assertEquals(8, MascotWalk(411f).cycles)
        assertEquals(8, MascotWalk(430f).cycles)
        assertEquals(20, MascotWalk(1280f).cycles)
    }

    @Test
    fun `the walk is over when its last frame has had its time and the mascot has arrived`() {
        val walk = MascotWalk(393f)
        val end = walk.millis
        // The picture shows its last frame one frame before the end.
        val lastFrameAt = end - MascotPicture.FRAME_MS
        assertFalse(walk.isOver(end - 1, lastFrameAt))
        assertTrue(walk.isOver(end, lastFrameAt))
        // The mascot is not turned before it has arrived, even if the picture were early.
        assertFalse(walk.isOver(end - 1, lastFrameAt - 500))
    }

    @Test
    fun `a picture that has fallen behind is waited for, and its last frame still gets its time`() {
        val walk = MascotWalk(393f)
        val end = walk.millis
        assertFalse(walk.isOver(end + 200, lastFrameAt = null))
        assertFalse(walk.isOver(end + 200, lastFrameAt = end + 180))
        assertTrue(walk.isOver(end + 180 + MascotPicture.FRAME_MS, lastFrameAt = end + 180))
    }

    @Test
    fun `a picture that never says it has ended is not waited for longer than a second`() {
        val walk = MascotWalk(393f)
        assertFalse(walk.isOver(walk.millis + PICTURE_PATIENCE_MS - 1, lastFrameAt = null))
        assertTrue(walk.isOver(walk.millis + PICTURE_PATIENCE_MS, lastFrameAt = null))
        assertEquals(1_000L, PICTURE_PATIENCE_MS)
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

package com.shawnkowalchuk.milo.core.designsystem.component

import kotlin.math.ceil
import kotlin.math.max

// The greeting's course, apart from its drawing (ADR-007): where the mascot is at any moment of
// its walk, when the walk is over, and which stage follows which. Plain Kotlin with unit tests;
// `MascotGreeting.kt` draws what this works out.

/**
 * What the greeting's two pictures hold, as `design/mascot/scripts/render_greeting.py` printed
 * it when it drew them. Lengths are in dp: the pictures are drawn at this size on every phone,
 * whatever its density. `MascotPicturesTest` holds the two files to the sizes and the frames.
 */
internal object MascotPicture {
    /** Both pictures are this wide and this high, and the mascot turns about their middle. */
    const val WIDTH = 240f
    const val HEIGHT = 168f

    /** The pictures have this many pixels to the dp. */
    const val PIXELS_PER_DP = 2.5f

    /** While it walks, the mascot's soles are this far above the picture's lower edge. */
    const val SOLES = 1.6f

    /** While it walks, the mascot reaches this far to the left of the picture's middle. */
    const val REACH = 66.4f

    /** One cycle of the walk, two steps, carries the mascot this far. */
    const val STRIDE = 36.89f

    /** Every frame of both pictures is shown for this long: 24 frames a second. */
    const val FRAME_MS = 42

    /** The walk's picture is one cycle, and starts again for ever. */
    const val WALK_FRAMES = 12
    const val WALK_CYCLE_MS = WALK_FRAMES * FRAME_MS

    /** The other picture, the turn and the wave, plays once and keeps its last frame. */
    const val TURN_AND_WAVE_FRAMES = 60
    const val TURN_AND_WAVE_MS = TURN_AND_WAVE_FRAMES * FRAME_MS
}

/**
 * How long a picture that does not say it has ended is waited for, past the time it should have
 * taken. Android moves the pictures on by itself, and only while they are being drawn.
 */
internal const val PICTURE_PATIENCE_MS = 1_000L

/**
 * The mascot's way in: from wholly beyond the right edge of the screen to the middle of its
 * width, at an even pace. Positions are where the middle of the picture is, in dp from the
 * screen's left edge; moments are milliseconds since the walk began.
 *
 * **The feet do not slide,** because the picture moves by exactly [stride] in the time one
 * cycle of the walk plays: that is how far the planted feet go back in the picture.
 *
 * **The walk is a whole number of cycles,** because the walk's picture can only be left where
 * it starts again: there the turn begins. So the mascot does not start at the screen's edge
 * but up to one cycle further out, and the first part of that cycle is not seen.
 *
 * @param screenWidth the width the mascot walks into, in dp.
 * @param stride how far one cycle of the walk carries the mascot, in dp.
 * @param reach how far the walking mascot reaches ahead of the picture's middle, in dp.
 * @param cycleMillis how long one cycle of the walk plays.
 */
internal class MascotWalk(
    screenWidth: Float,
    private val stride: Float = MascotPicture.STRIDE,
    reach: Float = MascotPicture.REACH,
    private val cycleMillis: Int = MascotPicture.WALK_CYCLE_MS,
) {
    /** Where the mascot stops: the middle of the screen's width. */
    val end: Float = screenWidth / 2

    /** How many times the walk's picture plays. */
    val cycles: Int = max(1, ceil((screenWidth - end + reach) / stride).toInt())

    /** Where the mascot starts: nothing of it is on the screen. */
    val start: Float = end + cycles * stride

    /** How long the walk takes. */
    val millis: Long = cycles.toLong() * cycleMillis

    /** Where the mascot is at [atMillis]: at [start] before the walk, at [end] after it. */
    fun middleAt(atMillis: Long): Float =
        start - stride * atMillis.coerceIn(0, millis) / cycleMillis

    /**
     * Whether the walk's picture may give way to the turn at [atMillis]. The picture says when
     * it shows the last frame of its last cycle ([lastFrameAt], null until it has); that frame
     * then gets its time on the screen, and the mascot the time to arrive. If the picture has
     * fallen behind, the mascot marks time in the middle until it has caught up; a picture that
     * says nothing is waited for [PICTURE_PATIENCE_MS] and no longer.
     */
    fun isOver(atMillis: Long, lastFrameAt: Long?): Boolean = if (lastFrameAt == null) {
        atMillis >= millis + PICTURE_PATIENCE_MS
    } else {
        atMillis >= max(millis, lastFrameAt + MascotPicture.FRAME_MS)
    }
}

/** How far a greeting has come. */
internal enum class GreetingStage {
    /** The pictures are being read. Nothing is drawn. */
    READING,

    /** The mascot walks in from the right. */
    WALKING,

    /** It stands in the middle, turns to the viewer and waves. */
    WAVING,

    /** It fades away. */
    LEAVING,

    /** The greeting has ended, in whichever way. Nothing is drawn, and nothing follows. */
    OVER,
}

/**
 * What can happen to a greeting.
 *
 * @property from the stage the event belongs to, or null for one that belongs to every stage.
 * @property to the stage it leads to.
 */
internal enum class GreetingEvent(val from: GreetingStage?, val to: GreetingStage) {
    PICTURES_READ(GreetingStage.READING, GreetingStage.WALKING),
    WALK_OVER(GreetingStage.WALKING, GreetingStage.WAVING),
    WAVE_OVER(GreetingStage.WAVING, GreetingStage.LEAVING),
    FADED(GreetingStage.LEAVING, GreetingStage.OVER),

    /**
     * The greeting ends where it stands: the phone's animations are off, a picture could not
     * be read, or MilO was put away in the middle of it.
     */
    CUT_SHORT(null, GreetingStage.OVER),
}

/**
 * One greeting's course from stage to stage. Each event moves it on from the one stage it
 * belongs to and is ignored in every other, so nothing that arrives late or twice can take a
 * greeting back, or end it a second time.
 *
 * @param onStage told each new stage, for the drawing.
 * @param onDone called when the greeting is over: **once, whatever happens and in whatever
 * order.**
 */
internal class GreetingCourse(
    private val onStage: (GreetingStage) -> Unit,
    private val onDone: () -> Unit,
) {
    var stage: GreetingStage = GreetingStage.READING
        private set

    fun on(event: GreetingEvent) {
        val belongs = event.from == null || event.from == stage
        if (stage == GreetingStage.OVER || !belongs) return
        stage = event.to
        onStage(event.to)
        if (event.to == GreetingStage.OVER) onDone()
    }
}

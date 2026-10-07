package com.shawnkowalchuk.milo.core.designsystem.component

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

// How a `SpanSlider` is moved: by a finger that drags a handle, and by a screen reader that
// steps one. In a file of its own to keep the slider's file under the size limit
// (ENGINEERING_STANDARDS, section 3).

/** Android's smallest target for a finger: each handle can be taken hold of within this. */
private val HandleReach = 48.dp

/**
 * Follows one finger: it takes hold of a handle, drags it along the line, and lets it go.
 *
 * **Which handle is meant is decided where the finger came down,** not where it is once it has
 * moved far enough to count as a drag. A finger that comes down at the edge of a handle's 48 dp
 * and moves away from it would otherwise be out of the handle's reach by then, and take hold of
 * nothing. The handle then moves exactly as far as the finger has moved since it came down, so
 * it does not jump under the finger.
 *
 * A finger that only taps, or that moves up or down, takes hold of nothing: the page under the
 * slider still scrolls from here.
 *
 * @param span and [scale] are asked at every move: the caller hands a new span in after each
 * change, and may hand in another line.
 * @param onHeld which handle is held, or null when it is let go.
 * @param onLetGo a handle that was held has been let go.
 */
internal suspend fun PointerInputScope.dragAHandle(
    mirrored: Boolean,
    span: () -> Span,
    scale: () -> SpanScale,
    onHeld: (SpanEnd?) -> Unit,
    onChange: (Span) -> Unit,
    onLetGo: () -> Unit,
) {
    fun along() = Along(size.width.toFloat(), SpanHandleSize.toPx() / 2, mirrored)

    fun placeOf(end: SpanEnd) = along().place(scale().fractionOf(span().of(end)))

    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val grab =
            grabbedHandle(
                touch = down.position.x,
                start = placeOf(SpanEnd.START),
                end = placeOf(SpanEnd.END),
                reach = HandleReach.toPx() / 2,
                closest = span().end - span().start <= scale().minimumSpan,
            ) ?: return@awaitEachGesture
        // Nothing is held until the finger has moved sideways far enough to mean it.
        val first =
            awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                ?: return@awaitEachGesture
        // Two handles as close as they may stand: back is the start's way, on is the end's.
        val back = (first.position.x < down.position.x) != mirrored
        val end = grab.end() ?: if (back) SpanEnd.START else SpanEnd.END
        // How far the middle of the handle was from the finger when it came down.
        val fromFinger = placeOf(end) - down.position.x

        fun follow(change: PointerInputChange) {
            change.consume()
            val fraction = along().fraction(change.position.x + fromFinger)
            val now = span()
            val next =
                when (end) {
                    SpanEnd.START -> scale().withStartAt(fraction, now)
                    SpanEnd.END -> scale().withEndAt(fraction, now)
                }
            if (next != now) onChange(next)
        }

        onHeld(end)
        follow(first)
        // Until the finger is lifted, or the drag is taken away from the slider.
        horizontalDrag(first.id) { follow(it) }
        onHeld(null)
        onLetGo()
    }
}

private fun Span.of(end: SpanEnd): Int = if (end == SpanEnd.START) start else this.end

private fun SpanGrab.end(): SpanEnd? = when (this) {
    SpanGrab.START -> SpanEnd.START
    SpanGrab.END -> SpanEnd.END
    SpanGrab.EITHER -> null
}

/**
 * What a handle is to a finger and to a screen reader: 48 dp around its middle, although it is
 * drawn 28 dp. It draws nothing. The drag itself is taken by the slider, which knows which of
 * two handles that stand close is meant.
 *
 * @param middle where the handle's middle is along the slider, in pixels.
 * @param onAsked a screen reader asked for this value.
 */
@Composable
internal fun HandleTarget(
    middle: Float,
    words: SpanHandleWords,
    value: Int,
    scale: SpanScale,
    onAsked: (Float) -> Unit,
) {
    Box(
        modifier =
            Modifier
                .offset { IntOffset((middle - HandleReach.toPx() / 2).roundToInt(), 0) }
                // Higher than the slider itself: it reaches 2 dp past it, above and below.
                .requiredSize(HandleReach)
                .semantics {
                    contentDescription = words.name
                    stateDescription = words.value
                    progressBarRangeInfo =
                        ProgressBarRangeInfo(
                            current = value.toFloat(),
                            range = scale.from.toFloat()..scale.to.toFloat(),
                            steps = scale.stepsBetweenEnds,
                        )
                    setProgress { target ->
                        onAsked(target)
                        true
                    }
                },
    )
}

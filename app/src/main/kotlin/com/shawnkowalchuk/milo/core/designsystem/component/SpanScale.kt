package com.shawnkowalchuk.milo.core.designsystem.component

import androidx.compose.runtime.Immutable
import kotlin.math.abs
import kotlin.math.roundToInt

// The arithmetic of the two-handle slider (SpanSlider.kt), apart from all drawing: where a value
// stands on the line, which value a place on the line is, and how far a handle may go. Plain
// whole numbers and pure functions, so it is tested without a phone. What the numbers mean is
// the caller's: the work schedule hands in minutes since midnight.

/** The two values a `SpanSlider` holds: where the chosen span starts and where it ends. */
@Immutable
data class Span(val start: Int, val end: Int)

/**
 * The line a `SpanSlider` chooses on.
 *
 * A handle that is moved lands on a step of the line, counted from [from]. A value that is
 * handed in may stand between two steps (a time set to the minute elsewhere): it is drawn where
 * it is, and goes to a step the first time its handle is moved.
 *
 * @param from the value at the start of the line, and [to] the value at its end.
 * @param step how far apart the values are that a handle can be moved to.
 * @param minimumSpan the least a moved handle leaves between the start and the end, so the two
 * handles never meet or cross.
 * @param marks the values that are named under the line.
 */
@Immutable
data class SpanScale(
    val from: Int,
    val to: Int,
    val step: Int,
    val minimumSpan: Int,
    val marks: List<Int> = listOf(from, to),
) {
    init {
        require(to > from) { "A line must end after it starts, but ran from $from to $to" }
        require(step > 0 && (to - from) % step == 0) {
            "The line from $from to $to cannot be cut into steps of $step"
        }
        require(minimumSpan in step..(to - from)) {
            "The shortest span must be at least one step and fit on the line, but was $minimumSpan"
        }
        require(marks.all { it in from..to }) { "A mark stands off the line: $marks" }
    }

    /** How many values a handle can be moved to, less the two at the ends. */
    val stepsBetweenEnds: Int get() = (to - from) / step - 1

    /**
     * How far along the line [value] stands: 0 at its start, 1 at its end. A value off the line
     * stands at the nearer end.
     */
    fun fractionOf(value: Int): Float = ((value - from).toFloat() / (to - from)).coerceIn(0f, 1f)

    /** The step of the line that is nearest to [fraction] of its length. */
    fun valueAt(fraction: Float): Int =
        from + (fraction.coerceIn(0f, 1f) * ((to - from) / step)).roundToInt() * step

    /**
     * [span] with its start handle moved to [fraction] of the line: on the nearest step, and no
     * closer to the end than [minimumSpan]. The end is left where it is.
     */
    fun withStartAt(fraction: Float, span: Span): Span =
        span.copy(start = startBefore(span.end, valueAt(fraction)))

    /** [span] with its end handle moved to [fraction] of the line, on the same terms. */
    fun withEndAt(fraction: Float, span: Span): Span =
        span.copy(end = endAfter(span.start, valueAt(fraction)))

    /**
     * [span] with its start moved the way a screen reader moves a slider: it asks for the value
     * [target]. One step up or down from where the handle stands goes to the next step of the
     * line in that direction, so a value between two steps moves to the nearer one on that
     * side and not past it; a larger jump goes to the step nearest to what was asked for.
     */
    fun withStartSteppedTo(target: Float, span: Span): Span =
        span.copy(start = startBefore(span.end, stepped(span.start, target)))

    /** [span] with its end moved the same way. */
    fun withEndSteppedTo(target: Float, span: Span): Span =
        span.copy(end = endAfter(span.start, stepped(span.end, target)))

    private fun stepped(current: Int, target: Float): Int = when {
        abs(target - current) > step -> valueAt((target - from) / (to - from))
        target > current -> stepAtOrBelow(current) + step
        target < current -> stepAtOrAbove(current) - step
        else -> current
    }.coerceIn(from, to)

    /** The latest start that leaves [minimumSpan] before [end], or [wanted] if that is earlier. */
    private fun startBefore(end: Int, wanted: Int): Int =
        wanted.coerceAtMost(stepAtOrBelow(end - minimumSpan)).coerceAtLeast(from)

    private fun endAfter(start: Int, wanted: Int): Int =
        wanted.coerceAtLeast(stepAtOrAbove(start + minimumSpan)).coerceAtMost(to)

    private fun stepAtOrBelow(value: Int): Int = from + Math.floorDiv(value - from, step) * step

    private fun stepAtOrAbove(value: Int): Int = from - Math.floorDiv(from - value, step) * step
}

/** One of the two handles of a `SpanSlider`. */
internal enum class SpanEnd { START, END }

/**
 * Which handle a finger that came down at [touch] takes hold of, or null if it is too far from
 * both. Positions are along the line, in any one unit.
 *
 * The nearer handle is taken. Two handles that stand as close as they may are a special case:
 * there the one at the start can only go back and the one at the end only on, so the way the
 * finger first moves decides, and the caller is told so with [SpanGrab.EITHER].
 *
 * @param reach how far from a handle's middle a finger still takes hold of it.
 * @param closest true if the two handles stand no further apart than they may.
 */
internal fun grabbedHandle(
    touch: Float,
    start: Float,
    end: Float,
    reach: Float,
    closest: Boolean,
): SpanGrab? {
    val toStart = abs(touch - start)
    val toEnd = abs(touch - end)
    return when {
        minOf(toStart, toEnd) > reach -> null
        closest && toStart <= reach && toEnd <= reach -> SpanGrab.EITHER
        toStart <= toEnd -> SpanGrab.START
        else -> SpanGrab.END
    }
}

/** What [grabbedHandle] answers. */
internal enum class SpanGrab { START, END, EITHER }

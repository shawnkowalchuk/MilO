package com.shawnkowalchuk.milo.core.designsystem.component

import kotlin.math.floor

// The arithmetic of a row of wheels (`DashboardWheels`), apart from all drawing: which digit
// stands on each wheel, and which wheels turn together, as on a truck's own odometer. Plain
// functions, so the carry from one wheel to the next is held by unit tests and not by the eye.
//
// A figure is counted in its smallest step, which the last wheel shows: a tenth of a kilometre
// on Home's odometer. Every wheel before it shows ten times as much as the one after it.

private const val DIGITS_ON_A_WHEEL = 10L

/** A whole part and its one decimal: the fewest wheels a figure is shown on. */
private const val FEWEST_WHEELS = 2

/**
 * One wheel at one moment.
 *
 * @param digit the digit that stands in its window, or is leaving it.
 * @param turning whether it is turning on to [next] together with the last wheel. The last
 * wheel always is, while the row rolls. A wheel before it turns only while every wheel after
 * it is passing from 9 to 0: that is the carry, and it is why a dashboard's wheels never jump.
 */
internal data class Wheel(val digit: Int, val turning: Boolean) {
    /** The digit that follows on the wheel: after 9 comes 0. */
    val next: Int get() = ((digit + 1) % DIGITS_ON_A_WHEEL).toInt()
}

/**
 * The wheels of [steps], the highest first and the last wheel last.
 *
 * There are as many wheels as the figure has digits, and never fewer than two: a figure under
 * one whole unit has a 0 before its tenth. While the row rolls and every digit is a 9, there is
 * one wheel more, a 0 in front: the carry is about to reach it, and it needs a wheel to turn.
 *
 * @param steps the figure in steps of its last wheel (tenths), not negative: the step the last
 * wheel last reached.
 * @param rolling whether the last wheel is turning on from that step. A row that stands
 * (`false`) has no wheel that turns.
 */
internal fun wheelsOf(steps: Long, rolling: Boolean): List<Wheel> {
    require(steps >= 0) { "A row of wheels cannot show a negative figure, but was given $steps" }
    val reached = if (rolling) steps + 1 else steps
    val count = maxOf(FEWEST_WHEELS, digitsOf(steps), digitsOf(reached))
    var place = 1L
    val lowestFirst =
        (0 until count).map {
            val wheel =
                Wheel(
                    digit = ((steps / place) % DIGITS_ON_A_WHEEL).toInt(),
                    // Everything after this wheel reads 9, 99, 999…: one more step turns it.
                    turning = rolling && steps % place == place - 1,
                )
            place *= DIGITS_ON_A_WHEEL
            wheel
        }
    return lowestFirst.reversed()
}

private fun digitsOf(number: Long): Int = number.toString().length

/**
 * Where a row stands that has rolled [offset] steps on from the step it started at: the whole
 * steps it has passed, and how far the last wheel has turned from the last of them to the
 * next, from 0 up to 1. An offset below nought is a row that has rolled back.
 */
internal data class RolledBy(val steps: Long, val part: Float)

/** Splits [offset] into the steps passed and the part of the next one. */
internal fun rolledBy(offset: Float): RolledBy {
    val whole = floor(offset)
    return RolledBy(whole.toLong(), offset - whole)
}

package com.shawnkowalchuk.milo.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.DpSize
import com.shawnkowalchuk.milo.core.designsystem.theme.FillAndText
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme
import kotlin.math.roundToInt

// A figure as a row of wheels, like a truck's own odometer: each digit in a cell of its own,
// the last cell for the tenth, and the last wheel turning as the figure grows. Added on
// 2026-10-10 for Home's odometer, as the owner chose it from three drawings ("Dashboard
// wheels"). What stands on which wheel, and which wheels turn together, is worked out in
// WheelPositions.kt.

/**
 * A cell's width and height, as multiples of the size its digit is written in, so that the
 * cells grow with the phone's font size as the digits do.
 */
private const val CELL_WIDTH_IN_DIGITS = 1.12f
private const val CELL_HEIGHT_IN_DIGITS = 1.5f

/**
 * How softly the wheels follow the figure: the stiffness of a spring without bounce.
 *
 * The figure arrives in steps of uneven size at an uneven pace. Home's odometer moves when the
 * trip's distance does, and a position that is not yet far enough from the last counted one
 * counts with the next: on an emulator driven at an even 50 km/h the steps came 2, 2 and 4
 * seconds apart, over and over (FINDINGS_LOG, 2026-10-10). A spring this soft turns that into
 * one movement that all but never stops while the truck drives, about two and a half seconds
 * behind the figure, and comes to rest some five seconds after the truck has. A stiffer one
 * lets every step show as a stop and a start; a softer one is a whole digit behind on the
 * highway.
 *
 * One value for every pace, on purpose: a spring tuned to the time since the last step was
 * tried, and made things worse, because a short wait follows a long one.
 */
private const val ROLL_STIFFNESS = 0.64f

/** A wheel is at rest when it is within a thousandth of a digit of where it belongs. */
private const val ROLL_AT_REST = 0.001f

/**
 * A figure that has moved on by more steps than this at once is shown where it now stands, and
 * not rolled there: a trip the truck joined late counts all its distance in one go, and wheels
 * that spin through a hundred digits say nothing.
 */
private const val MOST_STEPS_ROLLED = 5f

/**
 * A figure on a row of wheels: every digit of [steps] in a cell of its own, dark with a light
 * digit, the last one apart from the others for the tenth, and [unit] small and grey after it.
 *
 * **Standing** ([turn] null): the digits stand, and the last cell is grey with a dark digit:
 * the counting cell without its colour.
 *
 * **Counting** ([turn] given): the last cell is the accent with a dark digit, and the wheels
 * roll. The last wheel stands [turn] of a digit away from the last digit of [steps], and
 * follows every change of the two softly: it does not jump from 6 to 7, it rolls there, and
 * while it passes from 9 to 0 the wheel before it rolls on by one, as on a dashboard.
 * Nothing rolls while the row is out of view or its screen is not in front, and with the
 * phone's animations switched off the digits of [steps] just stand, in the same colours.
 *
 * It is as wide as its cells. Where that is wider than the room it is given (a very large font,
 * a figure of eight digits), the whole row is drawn smaller, and nothing is cut off.
 *
 * @param steps the figure in steps of its last wheel: for an odometer its tenths, so that
 * 1,234,606 is drawn as 123460 and a 6.
 * @param unit the unit alone: "km", "mi".
 * @param spoken what a screen reader says for the whole row: the figure with its unit's whole
 * word. The cells themselves are not read.
 * @param turn how far the last wheel stands from the last digit of [steps], from -0.5 (half-way
 * up from the digit before) to 0.5 (half-way on to the next), or null for a row that stands.
 */
@Composable
fun DashboardWheels(
    steps: Long,
    unit: String,
    spoken: String,
    modifier: Modifier = Modifier,
    turn: Float? = null,
) {
    // Read while composing, so the row is drawn again when the setting is changed.
    val scale = rememberCoroutineScope().coroutineContext[MotionDurationScale]?.scaleFactor
    val row = modifier.clearAndSetSemantics { contentDescription = spoken }.shrunkToFit()
    if (turn == null || scale == 0f) {
        val wheels = remember(steps) { wheelsOf(steps.coerceAtLeast(0), rolling = false) }
        WheelRow(wheels, unit, counting = turn != null, part = { 0f }, modifier = row)
    } else {
        RollingWheels(steps, turn, unit, row)
    }
}

/**
 * The row while it counts. What is animated is a small number, how far the row has rolled from
 * the figure it started at: a figure of millions of tenths does not fit a `Float` to the
 * hundredth of a digit, and a trip's worth of tenths does.
 *
 * **The wheels follow the figure on a soft spring** ([ROLL_STIFFNESS]), so the steps the
 * figure arrives in become one movement. They only ever roll forwards, and no further than
 * [MOST_STEPS_ROLLED] at once: any other change of the figure is shown at once.
 */
@Composable
private fun RollingWheels(steps: Long, turn: Float, unit: String, modifier: Modifier) {
    val start = remember { steps }
    val target = (steps - start).toFloat() + turn
    val rolled = remember { Animatable(target, ROLL_AT_REST) }
    val inFront = rememberInFront()
    LaunchedEffect(target, inFront.shown) {
        // Forwards only: wheels that roll back are no odometer. A figure that is lower than
        // where the wheels stand (a counted step taken back as one bad position) is set.
        val ahead = target - rolled.value
        if (inFront.shown && ahead >= 0f && ahead <= MOST_STEPS_ROLLED) {
            rolled.animateTo(
                target,
                spring(Spring.DampingRatioNoBouncy, ROLL_STIFFNESS, ROLL_AT_REST),
            )
        } else {
            rolled.snapTo(target)
        }
    }
    // Composed again only when a wheel reaches its next digit. The turning itself is read
    // while drawing, so a frame of it lays nothing out and composes nothing.
    val passed by remember { derivedStateOf { rolledBy(rolled.value).steps } }
    val standsAt = (start + passed).coerceAtLeast(0)
    val wheels = remember(standsAt) { wheelsOf(standsAt, rolling = true) }
    WheelRow(
        wheels = wheels,
        unit = unit,
        counting = true,
        part = { rolledBy(rolled.value).part },
        modifier = modifier.then(inFront.modifier),
    )
}

/**
 * The cells and the unit.
 *
 * @param part how far the last wheel has turned on to its next digit, from 0 up to 1. Read
 * while drawing.
 */
@Composable
private fun WheelRow(
    wheels: List<Wheel>,
    unit: String,
    counting: Boolean,
    part: () -> Float,
    modifier: Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val spacing = MiloTheme.spacing
    val style = MaterialTheme.typography.headlineSmall
    val digitSize = with(LocalDensity.current) { style.fontSize.toDp() }
    val cell = DpSize(digitSize * CELL_WIDTH_IN_DIGITS, digitSize * CELL_HEIGHT_IN_DIGITS)
    val colors = MiloTheme.colors.wheels
    val whole = colors.whole
    val tenth = if (counting) colors.tenthCounting else colors.tenth
    Row(modifier = modifier, verticalAlignment = Alignment.Bottom) {
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.extraSmall)) {
            for (index in 0 until wheels.lastIndex) {
                WheelCell(wheels[index], whole, cell, part)
            }
        }
        // The tenth stands apart: it is not one more digit of the kilometres.
        Spacer(Modifier.width(spacing.small))
        WheelCell(wheels.last(), tenth, cell, part)
        Spacer(Modifier.width(spacing.buttonGap))
        Text(
            text = unit,
            style = MaterialTheme.typography.labelLarge,
            color = scheme.onSurfaceVariant,
        )
    }
}

/**
 * One wheel in its window. A wheel that is turning shows the digit that is leaving at the top
 * and the one that is coming from below, each as far along as [part] says.
 */
@Composable
private fun WheelCell(wheel: Wheel, colors: FillAndText, size: DpSize, part: () -> Float) {
    val travel = with(LocalDensity.current) { size.height.toPx() }
    Box(
        modifier =
            Modifier
                .size(size)
                .clip(MiloTheme.shapes.wheel)
                .background(colors.fill),
        contentAlignment = Alignment.Center,
    ) {
        if (wheel.turning) {
            WheelDigit(
                wheel.digit,
                colors,
                Modifier.graphicsLayer {
                    translationY = -part() * travel
                },
            )
            WheelDigit(
                wheel.next,
                colors,
                Modifier.graphicsLayer { translationY = (1f - part()) * travel },
            )
        } else {
            WheelDigit(wheel.digit, colors, Modifier)
        }
    }
}

@Composable
private fun WheelDigit(digit: Int, colors: FillAndText, modifier: Modifier) {
    Text(
        text = digit.toString(),
        modifier = modifier,
        color = colors.text,
        style = MaterialTheme.typography.headlineSmall,
    )
}

/**
 * Draws what it is put on smaller where it is wider than the room it is given, so that all of
 * it shows; where it fits, it is left alone. The room it takes up is that of what is drawn.
 */
private fun Modifier.shrunkToFit(): Modifier = layout { measurable, constraints ->
    val natural = measurable.measure(
        constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity),
    )
    val fits = !constraints.hasBoundedWidth || natural.width <= constraints.maxWidth
    val scale = if (fits) 1f else constraints.maxWidth.toFloat() / natural.width
    layout((natural.width * scale).roundToInt(), (natural.height * scale).roundToInt()) {
        natural.placeWithLayer(0, 0) {
            scaleX = scale
            scaleY = scale
            transformOrigin = TransformOrigin(0f, 0f)
        }
    }
}

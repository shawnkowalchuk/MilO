package com.shawnkowalchuk.milo.core.designsystem.component

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.dp

// What the drawing in the truck's tile draws by hand: the line between the phone and the
// truck, the light on it, and the ring that spreads from a round patch. Every measure is the
// design's. Each function is handed how far a movement has come and draws that moment.

/** The line stops this far from each end's patch. */
private val LineInset = 6.dp

/** Not linked: a thin line of dashes, each as long as the gap after it. */
private val DashedLineHeight = 2.dp
private val DashLength = 6.dp

/** Linked: a thicker solid line with round ends. */
private val SolidLineHeight = 4.dp

/** A spreading ring's line, while the ring is as large as the patch it spreads from. */
private val RingWidth = 2.dp

/**
 * The line while the two are not linked: dashes, each as long as the gap after it. At [turn]
 * they have moved that share of one dash and one gap toward the truck. The dashes that have
 * moved past the line's end are cut off, and the next one moves in at its start.
 */
internal fun DrawScope.drawDashes(color: Color, turn: Float) {
    val dash = DashLength.toPx()
    val step = dash * 2
    val shift = step * turn
    val height = DashedLineHeight.toPx()
    val left = LineInset.toPx()
    val right = size.width - left
    val top = (size.height - height) / 2
    clipRect(left = left, top = top, right = right, bottom = top + height) {
        // From one step before the line's start, for the dash that is moving in.
        var from = left + shift % step - step
        while (from < right) {
            drawRect(color = color, topLeft = Offset(from, top), size = Size(dash, height))
            from += step
        }
    }
}

/**
 * The line while the two are linked, and the light on it: a soft patch that begins
 * [glintStart] of the way along the line and is only seen where it lies on the line.
 */
internal fun DrawScope.drawLitLine(color: Color, glint: Color, glintStart: Float) {
    val height = SolidLineHeight.toPx()
    val left = LineInset.toPx()
    val top = (size.height - height) / 2
    val length = size.width - left * 2
    drawRoundRect(
        color = color,
        topLeft = Offset(left, top),
        size = Size(length, height),
        cornerRadius = CornerRadius(height / 2),
    )
    clipRect(left = left, top = top, right = left + length, bottom = top + height) {
        val from = left + length * glintStart
        val width = length * GLINT_WIDTH
        val clear = glint.copy(alpha = 0f)
        drawRect(
            brush =
                Brush.horizontalGradient(
                    colors = listOf(clear, glint, clear),
                    startX = from,
                    endX = from + width,
                ),
            topLeft = Offset(from, top),
            size = Size(width, height),
        )
    }
}

/**
 * A ring that spreads from the round patch this is drawn behind, and fades. As in the design
 * the whole ring grows, its line with it.
 */
internal fun DrawScope.drawPing(color: Color, turn: Float) {
    val scale = pingScale(turn)
    val width = RingWidth.toPx() * scale
    drawCircle(
        color = color,
        radius = (size.minDimension * scale - width) / 2,
        alpha = pingAlpha(turn),
        style = Stroke(width),
    )
}

package com.shawnkowalchuk.milo.core.designsystem.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme
import kotlin.math.abs
import kotlin.math.roundToInt

// What a `SpanSlider` looks like: its line, its two handles, and the names under the line. In a
// file of its own to keep the slider's file under the size limit (ENGINEERING_STANDARDS,
// section 3).

/** A handle: a light knob inside a dark ring, with a hairline around both. */
internal val SpanHandleSize = 28.dp
private val HandleRing = 4.dp
private val HandleOutline = 1.dp

/** The accent ring around the handle that is held, as the design draws the one in focus. */
private val HeldRing = 3.dp

/** The line the handles stand on. */
private val TrackHeight = 6.dp

/** The least room between two names under the line. */
private val MarkGap = 4.dp

/**
 * Where a share of the line is on the screen, and back. The middle of a handle stays half a
 * handle inside each end of the line, so a handle at an end is drawn whole.
 *
 * @param mirrored true where a language is written from right to left: the line starts at
 * the right.
 */
internal class Along(
    private val width: Float,
    private val inset: Float,
    private val mirrored: Boolean,
) {
    private val travel: Float get() = (width - 2 * inset).coerceAtLeast(1f)

    fun place(fraction: Float): Float {
        val fromStart = inset + fraction * travel
        return if (mirrored) width - fromStart else fromStart
    }

    fun fraction(place: Float): Float {
        val fromStart = if (mirrored) width - place else place
        return ((fromStart - inset) / travel).coerceIn(0f, 1f)
    }
}

@Immutable
internal class SpanSliderColors(
    val track: Color,
    val chosen: Color,
    val knob: Color,
    val ring: Color,
    val outline: Color,
)

@Composable
internal fun spanSliderColors(): SpanSliderColors {
    val scheme = MaterialTheme.colorScheme
    return SpanSliderColors(
        track = MiloTheme.colors.control.fill,
        chosen = scheme.primary,
        knob = scheme.onSurface,
        // The page's dark, which is also what is written on the accent.
        ring = scheme.onPrimary,
        outline = MiloTheme.colors.idleOutline,
    )
}

/**
 * The line, the chosen part of it, and the two handles, the one at the end drawn last.
 *
 * @param start and [end] where the middles of the two handles are, in pixels.
 */
internal fun DrawScope.drawSpan(
    start: Float,
    end: Float,
    held: SpanEnd?,
    colors: SpanSliderColors,
) {
    val middle = size.height / 2
    val thick = TrackHeight.toPx()
    val round = CornerRadius(thick / 2)
    drawRoundRect(
        color = colors.track,
        topLeft = Offset(0f, middle - thick / 2),
        size = Size(size.width, thick),
        cornerRadius = round,
    )
    drawRoundRect(
        color = colors.chosen,
        topLeft = Offset(minOf(start, end), middle - thick / 2),
        size = Size(abs(end - start), thick),
        cornerRadius = round,
    )
    drawHandle(Offset(start, middle), held == SpanEnd.START, colors)
    drawHandle(Offset(end, middle), held == SpanEnd.END, colors)
}

private fun DrawScope.drawHandle(at: Offset, held: Boolean, colors: SpanSliderColors) {
    val radius = SpanHandleSize.toPx() / 2
    if (held) {
        drawCircle(color = colors.chosen, radius = radius + HeldRing.toPx(), center = at)
    } else {
        drawCircle(color = colors.outline, radius = radius + HandleOutline.toPx(), center = at)
    }
    drawCircle(color = colors.ring, radius = radius, center = at)
    drawCircle(color = colors.knob, radius = radius - HandleRing.toPx(), center = at)
}

/**
 * The names under the line, each with its middle under the place its value has on the line.
 * The first and the last are kept inside the slider's two ends, as the design draws them. A
 * name that would run into one that is already placed is left out: at a large font size there
 * is not room for all of them.
 *
 * A screen reader is told nothing: each handle says its own value.
 */
@Composable
internal fun SpanMarks(scale: SpanScale, label: (Int) -> String) {
    val marks = scale.marks
    Layout(
        content = {
            for (mark in marks) {
                Text(
                    text = label(mark),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        },
        modifier = Modifier.fillMaxWidth().clearAndSetSemantics {},
    ) { measurables, constraints ->
        val names = measurables.map { it.measure(Constraints()) }
        val width = constraints.maxWidth
        val along = Along(width.toFloat(), SpanHandleSize.toPx() / 2, mirrored = false)
        val gap = MarkGap.roundToPx()
        // The two ends first, so that they are the ones that stay when room runs out.
        val order = (listOf(0, names.lastIndex) + names.indices).distinct()
        val taken = mutableListOf<IntRange>()
        val places = arrayOfNulls<Int>(names.size)
        for (index in order) {
            val name = names.getOrNull(index) ?: continue
            val middle = along.place(scale.fractionOf(marks[index]))
            val x = (middle - name.width / 2f).roundToInt().coerceIn(
                0,
                maxOf(0, width - name.width),
            )
            val room = (x - gap)..(x + name.width + gap)
            if (taken.none { it.first < room.last && room.first < it.last }) {
                taken += x..(x + name.width)
                places[index] = x
            }
        }
        layout(width, names.maxOfOrNull { it.height } ?: 0) {
            names.forEachIndexed { index, name ->
                places[index]?.let { name.placeRelative(it, 0) }
            }
        }
    }
}

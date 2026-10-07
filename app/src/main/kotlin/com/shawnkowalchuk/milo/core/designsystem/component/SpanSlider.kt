package com.shawnkowalchuk.milo.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme

/** How high the design draws the slider: the room its two handles move in. */
private val SliderHeight = 44.dp

/**
 * What a screen reader says for one handle.
 *
 * @param name what the handle sets: "Start of the work day".
 * @param value where it stands, as it is to be read: "8:00 AM".
 */
@Immutable
data class SpanHandleWords(val name: String, val value: String)

/**
 * The design's slider with two handles: a span chosen on a line, such as the hours of a work
 * day between 4 AM and 10 PM. The line is grey, the chosen part of it is the accent, and each
 * end of that part is a round handle. Under the line stand the names of a few of its values.
 *
 * **It shows what it is told and keeps nothing.** A handle that is moved reports the new span
 * through [onChange], on a step of the line, and the caller hands that span back in. The two
 * handles cannot meet or cross: [SpanScale] says how close they may come.
 *
 * **Only a handle moves it.** A finger takes hold of a handle within 24 dp of its middle and
 * drags it sideways; a tap on the line does nothing, so a thumb that scrolls the screen over
 * the slider changes nothing. The page still scrolls up and down from here.
 *
 * **For a screen reader each handle is a slider of its own,** with its name and its value in
 * words, and one step of the screen reader is one step of the line.
 *
 * The slider is as wide as it is allowed to be and 44 dp high. Material's `RangeSlider` is not
 * used: its handles are bars, and its sizes are not the design's.
 *
 * @param onChangeFinished called when a handle is let go, and after each step a screen reader
 * takes.
 * @param markLabel the words under the line for one of the scale's marks: "4 AM".
 * @param showMarks false leaves the names out, for a slider that stands above another on the
 * same line.
 */
@Composable
fun SpanSlider(
    span: Span,
    scale: SpanScale,
    onChange: (Span) -> Unit,
    onChangeFinished: () -> Unit,
    startWords: SpanHandleWords,
    endWords: SpanHandleWords,
    markLabel: (Int) -> String,
    modifier: Modifier = Modifier,
    showMarks: Boolean = true,
) {
    // The drag below outlives many redraws, and reads these as they are at each move.
    val current by rememberUpdatedState(span)
    val line by rememberUpdatedState(scale)
    val changed by rememberUpdatedState(onChange)
    val finished by rememberUpdatedState(onChangeFinished)
    var held by remember { mutableStateOf<SpanEnd?>(null) }
    val mirrored = LocalLayoutDirection.current == LayoutDirection.Rtl
    val colors = spanSliderColors()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MiloTheme.spacing.small),
    ) {
        BoxWithConstraints(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(SliderHeight)
                    .pointerInput(mirrored) {
                        dragAHandle(
                            mirrored = mirrored,
                            span = { current },
                            scale = { line },
                            onHeld = { held = it },
                            onChange = { changed(it) },
                            onLetGo = { finished() },
                        )
                    }.drawBehind {
                        val along = Along(size.width, SpanHandleSize.toPx() / 2, mirrored)
                        drawSpan(
                            start = along.place(scale.fractionOf(span.start)),
                            end = along.place(scale.fractionOf(span.end)),
                            held = held,
                            colors = colors,
                        )
                    },
        ) {
            val along =
                with(LocalDensity.current) {
                    Along(constraints.maxWidth.toFloat(), SpanHandleSize.toPx() / 2, mirrored)
                }
            HandleTarget(
                middle = along.place(scale.fractionOf(span.start)),
                words = startWords,
                value = span.start,
                scale = scale,
                onAsked = { target ->
                    val next = scale.withStartSteppedTo(target, span)
                    if (next != span) {
                        onChange(next)
                        onChangeFinished()
                    }
                },
            )
            HandleTarget(
                middle = along.place(scale.fractionOf(span.end)),
                words = endWords,
                value = span.end,
                scale = scale,
                onAsked = { target ->
                    val next = scale.withEndSteppedTo(target, span)
                    if (next != span) {
                        onChange(next)
                        onChangeFinished()
                    }
                },
            )
        }
        if (showMarks) SpanMarks(scale = scale, label = markLabel)
    }
}

// Sample values are written inline because a preview is never shown to a user or shipped.
@Preview
@Composable
private fun SpanSliderPreview() {
    val hour = 60
    val scale =
        SpanScale(
            from = 4 * hour,
            to = 22 * hour,
            step = 15,
            minimumSpan = 30,
            marks = listOf(4, 9, 13, 18, 22).map { it * hour },
        )
    MiloTheme {
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            SpanSlider(
                span = Span(start = 8 * hour, end = 16 * hour + 30),
                scale = scale,
                onChange = {},
                onChangeFinished = {},
                startWords = SpanHandleWords("Start of the work day", "8:00 AM"),
                endWords = SpanHandleWords("End of the work day", "4:30 PM"),
                markLabel = { "${it / hour}:00" },
            )
        }
    }
}

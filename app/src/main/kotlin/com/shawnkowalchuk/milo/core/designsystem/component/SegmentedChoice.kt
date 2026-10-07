package com.shawnkowalchuk.milo.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme

/** How high the design draws one part. It grows when a large font needs more. */
private val SegmentHeight = 44.dp

/**
 * One part of a [SegmentedChoice].
 *
 * @param label a few words: every part is as wide as the others.
 */
@Immutable
data class Segment(val label: String, val selected: Boolean, val onSelect: () -> Unit)

/**
 * One choice out of two or three that each have a name of their own, drawn as the design's
 * segmented control: the parts side by side and equally wide inside a dark, rounded frame, the
 * one in force filled with the accent and written in dark words.
 *
 * A switch says "on or off". This is for a setting whose sides both need a name, such as "Save
 * as Personal" and "Ignore them", and whose names are short enough to stand side by side. A
 * choice whose sides each need a sentence is a column of `ChoiceRow`s; what a part means is
 * said under the control, by the screen.
 *
 * Each part is drawn 44 dp high. The frame keeps 4 dp around the parts, so the place a finger
 * can hit is Android's 48 dp. Exactly one part should be selected. A screen reader announces
 * the parts as one group, and each as one choice of it that is chosen or not.
 */
@Composable
fun SegmentedChoice(segments: List<Segment>, modifier: Modifier = Modifier) {
    // TODO(debt): Shape.kt still says that no component uses `wideButton` and `segment`. This
    // one does. The sentence was left alone while other screens were laid out on a branch of
    // their own (FINDINGS_LOG, 2026-10-07).
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(MiloTheme.colors.fieldFill, MiloTheme.shapes.wideButton)
                .padding(MiloTheme.spacing.extraSmall)
                // The row is as high as its highest part needs, and every part fills it.
                .height(IntrinsicSize.Min)
                .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(MiloTheme.spacing.buttonGap),
    ) {
        for (segment in segments) {
            val scheme = MaterialTheme.colorScheme
            val shape = MiloTheme.shapes.segment
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        // The clip keeps the ripple of a press inside the part.
                        .clip(shape)
                        .background(if (segment.selected) scheme.primary else Color.Transparent)
                        .selectable(
                            selected = segment.selected,
                            role = Role.RadioButton,
                            onClick = segment.onSelect,
                        ).heightIn(min = SegmentHeight)
                        .padding(
                            horizontal = MiloTheme.spacing.small,
                            vertical = MiloTheme.spacing.extraSmall,
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = segment.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (segment.selected) scheme.onPrimary else scheme.onSurface,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

// Sample text is written inline because a preview is never shown to a user or shipped.
@Preview
@Composable
private fun SegmentedChoicePreview() {
    MiloTheme {
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            SegmentedChoice(
                segments =
                    listOf(
                        Segment("Save as Personal", selected = true, onSelect = {}),
                        Segment("Ignore them", selected = false, onSelect = {}),
                    ),
            )
        }
    }
}

package com.shawnkowalchuk.milo.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme

/** How high the design draws a chip. Material keeps 48 dp for the finger around each one. */
private val ChipHeight = 36.dp

/**
 * One of the choices of a [ChipChoice].
 *
 * @param label a word or two: the chip is as wide as its label.
 */
@Immutable
data class ChipOption(val label: String, val selected: Boolean, val onSelect: () -> Unit)

/**
 * One choice out of many short ones, each a chip, wrapped onto as many lines as they need: the
 * categories the event log can be narrowed to.
 *
 * A column of `ChoiceRow`s is for two or three choices that each need a sentence. A dozen
 * one-word choices as rows would fill the screen before the list they narrow, so here every
 * choice is in view at once and takes a few lines. Nothing scrolls sideways: a choice that is
 * off the edge is a choice nobody finds.
 *
 * The chips are the design's pills: the one in force is light with dark words, every other one
 * is the colour of a tile. That is why they stand on the page and not inside a tile, where the
 * others would not be seen.
 *
 * Exactly one option should be selected. A screen reader announces the chips as one group.
 */
@Composable
fun ChipChoice(options: List<ChipOption>, modifier: Modifier = Modifier) {
    val selected = MiloTheme.colors.chipSelected
    val other = MiloTheme.colors.chip
    FlowRow(
        modifier = modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(MiloTheme.spacing.buttonGap),
    ) {
        for (option in options) {
            FilterChip(
                selected = option.selected,
                onClick = option.onSelect,
                label = { Text(text = option.label, style = MaterialTheme.typography.labelMedium) },
                modifier = Modifier.heightIn(min = ChipHeight),
                shape = MiloTheme.shapes.pill,
                colors =
                    FilterChipDefaults.filterChipColors(
                        containerColor = other.fill,
                        labelColor = other.text,
                        selectedContainerColor = selected.fill,
                        selectedLabelColor = selected.text,
                    ),
                // The fill alone says which chip is in force; the design draws no outline.
                border = null,
            )
        }
    }
}

// Sample text is written inline because a preview is never shown to a user or shipped.
@Preview
@Composable
private fun ChipChoicePreview() {
    val labels = listOf("All", "TRIP", "TRIGGER", "SERVICE", "ERROR", "LOCATION", "PAIRING")
    MiloTheme {
        Surface {
            ChipChoice(labels.map { ChipOption(it, selected = it == "TRIP", onSelect = {}) })
        }
    }
}

package com.shawnkowalchuk.milo.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme

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
 * Exactly one option should be selected. A screen reader announces the chips as one group.
 */
@Composable
fun ChipChoice(options: List<ChipOption>, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(MiloTheme.spacing.small),
    ) {
        for (option in options) {
            FilterChip(
                selected = option.selected,
                onClick = option.onSelect,
                label = { Text(text = option.label) },
            )
        }
    }
}

// Sample text is written inline because a preview is never shown to a user or shipped.
@PreviewLightDark
@Composable
private fun ChipChoicePreview() {
    val labels = listOf("All", "TRIP", "TRIGGER", "SERVICE", "ERROR", "LOCATION", "PAIRING")
    MiloTheme {
        Surface {
            ChipChoice(labels.map { ChipOption(it, selected = it == "TRIP", onSelect = {}) })
        }
    }
}

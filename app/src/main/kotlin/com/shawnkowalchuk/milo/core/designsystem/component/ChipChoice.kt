package com.shawnkowalchuk.milo.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme

/** How high the design draws a chip. It grows when a large font needs more. */
private val ChipHeight = 36.dp

/**
 * One of the choices of a [ChipChoice].
 *
 * @param label a word or two: the chip is as wide as its label.
 */
@Immutable
data class ChipOption(val label: String, val selected: Boolean, val onSelect: () -> Unit)

/**
 * One choice out of several short ones, each a chip, wrapped onto as many lines as they need:
 * the kinds of line the event log can be narrowed to.
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
 * **Each chip is drawn at the design's size,** 36 dp high with 14 dp beside its words, and two
 * chips stand 6 dp apart, side by side and line under line. Material's own chip could be given
 * neither measure: it keeps 16 dp beside its words, and 48 dp of height for the finger, which
 * put two lines of chips twice as far apart as drawn. The place a finger can hit is still
 * Android's 48 dp: Compose lets a press that lands within that distance of a smaller control
 * count as a press on it, and between two lines of chips the nearer chip gets it.
 *
 * Exactly one option should be selected. A screen reader announces the chips as one group, and
 * each as one choice of it that is chosen or not.
 */
@Composable
fun ChipChoice(options: List<ChipOption>, modifier: Modifier = Modifier) {
    val gap = MiloTheme.spacing.buttonGap
    FlowRow(
        modifier = modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalArrangement = Arrangement.spacedBy(gap),
    ) {
        for (option in options) Chip(option)
    }
}

/** One chip. The fill alone says whether it is the one in force; the design draws no outline. */
@Composable
private fun Chip(option: ChipOption) {
    val colors = if (option.selected) MiloTheme.colors.chipSelected else MiloTheme.colors.chip
    val round = MiloTheme.shapes.pill
    Box(
        modifier =
            Modifier
                // The clip keeps the ripple of a press inside the pill.
                .clip(round)
                .background(colors.fill, round)
                .selectable(
                    selected = option.selected,
                    role = Role.RadioButton,
                    onClick = option.onSelect,
                ).heightIn(min = ChipHeight)
                .padding(horizontal = MiloTheme.spacing.controlPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = option.label,
            style = MaterialTheme.typography.labelMedium,
            color = colors.text,
        )
    }
}

// Sample text is written inline because a preview is never shown to a user or shipped.
@Preview
@Composable
private fun ChipChoicePreview() {
    val labels = listOf("All", "Trips", "Bluetooth", "Android Auto", "Errors")
    MiloTheme {
        Surface {
            ChipChoice(labels.map { ChipOption(it, selected = it == "Trips", onSelect = {}) })
        }
    }
}

package com.shawnkowalchuk.milo.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme

// The two ways the design draws a switch inside a tile, beside the plain `SwitchRow`: a small
// tile that is one switch, and a tile's title with a switch at its end. Each is pressed as a
// whole, and a screen reader reads its words and its state as one item.

/** Android's smallest target for a finger. */
private val FingerTarget = 48.dp

/** How high the design draws a title with the line under it, beside its switch. */
private val TitleRowHeight = 36.dp

/**
 * The design's tile that is one switch, for the half of a line: a small grey label, a sentence
 * that says what the switch does, and the switch under it ("Driving alert"). **The whole tile
 * is what is pressed.**
 *
 * It is as wide and as high as it is told through [modifier]; two side by side go in a
 * `TilePair`. A screen reader reads the label, the sentence and the state as one switch, and
 * can jump to it as to a heading.
 *
 * @param label the small grey label: what the tile is.
 * @param text what switching it on does, in the words of the one who does it.
 */
@Composable
fun SwitchTile(
    label: String,
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MiloTheme.shapes.tile,
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        // The press is taken inside the panel, so that its ripple stops at the round corners.
        Box(
            modifier =
                Modifier
                    .toggleable(
                        value = checked,
                        role = Role.Switch,
                        onValueChange = onCheckedChange,
                    ).semantics { heading() },
            propagateMinConstraints = true,
        ) {
            Column(
                modifier = Modifier.padding(MiloTheme.spacing.medium),
                verticalArrangement = Arrangement.spacedBy(MiloTheme.spacing.tileGap),
            ) {
                TileLabel(text = label)
                Text(text = text, style = MiloTheme.textStyles.sentence)
                SwitchMark(checked = checked)
            }
        }
    }
}

/**
 * A tile's own title with a quieter line under it and a switch at the end, as the design draws
 * "Monthly reminder" and "Trip-start sound". **The whole row toggles the switch.**
 *
 * It stands first in a tile. It is drawn as high as its two lines and takes up no more; the
 * place a finger can hit is 48 dp high and reaches into the tile's own padding above it, and
 * into the room below it, where nothing that can be pressed may stand within 6 dp. A row whose
 * words need more than the 48 dp is left as high as it is.
 *
 * A screen reader reads the title, the line and the state as one switch, and can jump to it as
 * to a heading.
 *
 * @param line the quieter line: what the switch does, or which of its choices is in force.
 */
@Composable
fun TitledSwitchRow(
    title: String,
    line: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .takesUpOnly(TitleRowHeight)
                .heightIn(min = FingerTarget)
                .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
                .semantics { heading() },
        horizontalArrangement = Arrangement.spacedBy(MiloTheme.spacing.rowGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(MiloTheme.spacing.textGap),
        ) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = line,
                style = MiloTheme.textStyles.tileLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SwitchMark(checked = checked)
    }
}

// Sample text is written inline because a preview is never shown to a user or shipped.
@Preview
@Composable
private fun SwitchTilesPreview() {
    MiloTheme {
        Surface {
            Column(verticalArrangement = Arrangement.spacedBy(MiloTheme.spacing.tileGap)) {
                SwitchTile(
                    label = "Driving alert",
                    text = "Tell me when I drive without a trip",
                    checked = true,
                    onCheckedChange = {},
                )
                Tile(padding = TilePadding.EVEN) {
                    TitledSwitchRow(
                        title = "Monthly reminder",
                        line = "Daily from the 1st until the report is sent",
                        checked = false,
                        onCheckedChange = {},
                    )
                }
            }
        }
    }
}

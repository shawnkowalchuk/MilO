package com.shawnkowalchuk.milo.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme

// Material's minimum touch target. The whole row is the target, not only the small switch.
private val MinHeight = 48.dp

// The design's switch: a pill-shaped track with a round thumb that sits just inside it.
private val TrackWidth = 48.dp
private val TrackHeight = 28.dp
private val ThumbSize = 22.dp

/**
 * A labelled on/off switch. The whole row toggles it, and a screen reader reads the label and
 * the state as one item.
 *
 * @param quiet true for a switch that stands on the page between two tiles and only changes
 * what is listed, as the design draws it on Trips: its label is small and grey, and the row
 * takes up no more than the height of the switch. The place a finger can hit stays 48 dp high:
 * it reaches into the room the tiles above and below keep free.
 * @param quietOnTile true for a quiet switch that stands in a tile, as the design draws it on
 * the edit screen: the same small label and the same height, with the label in the tile's own
 * text colour. What stands above and below it in the tile has to leave 10 dp free.
 */
@Composable
fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    quiet: Boolean = false,
    quietOnTile: Boolean = false,
) {
    val small = quiet || quietOnTile
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .then(if (small) Modifier.takesUpOnly(TrackHeight) else Modifier)
                .heightIn(min = MinHeight)
                .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        horizontalArrangement = Arrangement.spacedBy(MiloTheme.spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style =
                if (small) {
                    MiloTheme.textStyles.quietLabel
                } else {
                    MaterialTheme.typography.bodyLarge
                },
            color =
                if (quiet) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    LocalContentColor.current
                },
            modifier = Modifier.weight(1f),
        )
        SwitchMark(checked = checked)
    }
}

/**
 * The switch as the design draws it. On: the track is the accent and the thumb is dark, at the
 * end. Off: the track is the quiet fill of a control and the thumb is grey, at the start. The
 * thumb's place says the state as well as the colours do.
 *
 * It only shows the state. It has no handler and says nothing to a screen reader: the row
 * around it is what is pressed and what is read out. The tiles of `SwitchTiles.kt` draw their
 * switch with it too.
 *
 * Material's own `Switch` is not used because its size is fixed and is not the design's.
 */
@Composable
internal fun SwitchMark(checked: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val track by animateColorAsState(
        targetValue = if (checked) scheme.primary else MiloTheme.colors.control.fill,
        label = "switch track",
    )
    val thumb by animateColorAsState(
        targetValue = if (checked) scheme.onPrimary else scheme.onSurfaceVariant,
        label = "switch thumb",
    )
    // 0 at the start of the track, 1 at its end.
    val place by animateFloatAsState(targetValue = if (checked) 1f else 0f, label = "switch place")
    Spacer(
        modifier =
            Modifier
                .size(width = TrackWidth, height = TrackHeight)
                // Drawn, not laid out: the three values above are read only while drawing, so
                // the slide of the thumb redraws this one mark and nothing around it.
                .drawBehind {
                    drawRoundRect(color = track, cornerRadius = CornerRadius(size.height / 2))
                    val radius = ThumbSize.toPx() / 2
                    val fromEdge = size.height / 2
                    val travel = size.width - size.height
                    // "The end" is the left where a language is written from right to left.
                    val along =
                        if (layoutDirection == LayoutDirection.Rtl) 1f - place else place
                    drawCircle(
                        color = thumb,
                        radius = radius,
                        center = Offset(x = fromEdge + travel * along, y = size.height / 2),
                    )
                },
    )
}

// Sample text is written inline because a preview is never shown to a user or shipped.
@Preview
@Composable
private fun SwitchRowPreview() {
    MiloTheme {
        Surface {
            SwitchRow(label = "Trip-start sound", checked = true, onCheckedChange = {})
        }
    }
}

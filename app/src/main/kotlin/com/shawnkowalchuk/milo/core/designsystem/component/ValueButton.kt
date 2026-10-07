package com.shawnkowalchuk.milo.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme

/** How high the design draws the button without a label, and with one over its value. */
private val PlainHeight = 48.dp
private val LabelledHeight = 56.dp

/**
 * A value that is chosen with a picker, drawn as the design draws a date or a time on the edit
 * screen: a quiet button on a tile that shows the value, with its words at the start. A press
 * opens the picker.
 *
 * Without a [label] it is 48 dp high and its value is set like the main line of a row: the
 * date. With one it is 56 dp high, with the small grey label over a larger value: "Start" over
 * "7:42 AM". It grows when a large font needs more. A screen reader reads the label and the
 * value as one button.
 *
 * @param value the value as it is to be read, or what to do while none is chosen.
 */
@Composable
fun ValueButton(
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    val control = MiloTheme.colors.control
    Surface(
        onClick = onClick,
        modifier = modifier.semantics { role = Role.Button },
        shape = MiloTheme.shapes.control,
        color = control.fill,
        contentColor = control.text,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = if (label == null) PlainHeight else LabelledHeight)
                    .padding(
                        horizontal = MiloTheme.spacing.controlPadding,
                        vertical = MiloTheme.spacing.extraSmall,
                    ),
            verticalArrangement =
                Arrangement.spacedBy(MiloTheme.spacing.textGap, Alignment.CenterVertically),
        ) {
            if (label != null) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = value,
                style =
                    if (label == null) {
                        MaterialTheme.typography.bodyLarge
                    } else {
                        MaterialTheme.typography.titleMedium
                    },
            )
        }
    }
}

// Sample text is written inline because a preview is never shown to a user or shipped.
@Preview
@Composable
private fun ValueButtonPreview() {
    MiloTheme {
        Surface {
            Tile(padding = TilePadding.EVEN, gap = MiloTheme.spacing.tileGap) {
                ValueButton(value = "Tuesday, October 6, 2026", onClick = {})
                ValueButton(value = "7:42 AM", onClick = {}, label = "Start")
            }
        }
    }
}

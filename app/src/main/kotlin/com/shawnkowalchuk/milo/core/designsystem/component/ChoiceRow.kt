package com.shawnkowalchuk.milo.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme

// Material's minimum touch target, as in SwitchRow: the whole row is the target.
private val MinHeight = 48.dp

/**
 * One of a few choices of which exactly one is in force: a radio button, its name, and a
 * quieter line saying what choosing it means. The whole row chooses it, and a screen reader
 * reads the row as one item.
 *
 * The radio button is Material's own, and the theme colours it: the accent for the choice in
 * force, the grey of secondary text for the others.
 *
 * The row keeps a little air of its own above and below its words, inside what is pressed. Two
 * rows that stand directly under each other are therefore always apart, and a choice with a
 * long second line is not read as one paragraph with the next. A column of them needs no gap
 * of its own. A row whose words fit in the 48 dp is not made taller by it.
 *
 * A switch says "on or off". This is for a setting whose two sides both need a name, such as
 * "Save as Personal" and "Ignore them". The rows of one choice are put in a column that carries
 * `Modifier.selectableGroup()`, so a screen reader announces them as one group.
 *
 * @param supportingText a second, quieter line: what the choice does.
 */
@Composable
fun ChoiceRow(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = MinHeight)
                .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
                // After the press handler, so the air belongs to the row and is pressed with it.
                .padding(vertical = MiloTheme.spacing.extraSmall),
        horizontalArrangement = Arrangement.spacedBy(MiloTheme.spacing.rowGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // No handler of its own: the row above is what is pressed.
        RadioButton(selected = selected, onClick = null)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(MiloTheme.spacing.textGap),
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            if (supportingText != null) {
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// Sample text is written inline because a preview is never shown to a user or shipped. The
// column adds no gap, to show the one the rows keep between themselves.
@Preview
@Composable
private fun ChoiceRowPreview() {
    MiloTheme {
        Surface {
            Column(modifier = Modifier.selectableGroup()) {
                ChoiceRow(
                    label = "A whole month",
                    selected = true,
                    onSelect = {},
                    supportingText = "The month is marked as submitted once its report is sent.",
                )
                ChoiceRow(
                    label = "A date range",
                    selected = false,
                    onSelect = {},
                    supportingText = "Any days you choose. It never marks a month as submitted.",
                )
            }
        }
    }
}

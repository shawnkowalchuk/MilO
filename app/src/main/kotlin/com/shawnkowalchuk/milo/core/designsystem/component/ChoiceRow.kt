package com.shawnkowalchuk.milo.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme

// Material's minimum touch target, as in SwitchRow: the whole row is the target.
private val MinHeight = 48.dp

/**
 * One of a few choices of which exactly one is in force: a radio button, its name, and a
 * quieter line saying what choosing it means. The whole row chooses it, and a screen reader
 * reads the row as one item.
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
                .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
        horizontalArrangement = Arrangement.spacedBy(MiloTheme.spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // No handler of its own: the row above is what is pressed.
        RadioButton(selected = selected, onClick = null)
        Column(modifier = Modifier.weight(1f)) {
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

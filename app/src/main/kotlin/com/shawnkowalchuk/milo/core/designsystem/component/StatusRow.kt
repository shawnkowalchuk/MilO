package com.shawnkowalchuk.milo.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme

/**
 * The button at the end of a [StatusRow]. The label and the click handler travel together so a
 * row can never show a button that does nothing, or carry a handler with no button.
 */
@Immutable
data class StatusRowAction(val label: String, val onClick: () -> Unit)

/**
 * One requirement and whether it is met: a green tick or a red warning, a label, and an optional
 * button that takes the user to the place where it can be fixed.
 *
 * It has two states only, which is not enough for the permission checklist: that screen also
 * needs "unknown" and "confirm you set this" for the settings the app cannot read.
 *
 * @param supportingText a second, quieter line: what the requirement is for, or how to fix it.
 * @param action shown at the end of the row; usually only passed while [isOk] is false.
 */
@Composable
fun StatusRow(
    label: String,
    // TODO(debt): replace this Boolean with a status type (ok, problem, unknown, needs
    // confirmation) when the permission checklist is built in phase 1. See FINDINGS_LOG.
    isOk: Boolean,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    action: StatusRowAction? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MiloTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            // Read the indicator and both lines as one sentence ("Needs attention, Location,
            // ...") instead of three separate stops. The button stays its own stop.
            modifier = Modifier.weight(1f).semantics(mergeDescendants = true) {},
            horizontalArrangement = Arrangement.spacedBy(MiloTheme.spacing.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (isOk) StatusIcons.Ok else StatusIcons.Problem,
                contentDescription =
                    stringResource(if (isOk) R.string.status_ok else R.string.status_problem),
                tint = if (isOk) MiloTheme.statusColors.ok else MiloTheme.statusColors.problem,
            )
            Column {
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
        if (action != null) {
            TextButton(onClick = action.onClick) {
                Text(text = action.label)
            }
        }
    }
}

// Sample text is written inline because a preview is never shown to a user or shipped in a
// screen; putting it in strings.xml would add resources the app does not use.
@PreviewLightDark
@Composable
private fun StatusRowPreview() {
    MiloTheme {
        Surface {
            Column {
                StatusRow(label = "Notifications", isOk = true)
                StatusRow(
                    label = "Background location",
                    isOk = false,
                    supportingText = "Set to \"Allow all the time\"",
                    action = StatusRowAction(label = "Fix", onClick = {}),
                )
            }
        }
    }
}

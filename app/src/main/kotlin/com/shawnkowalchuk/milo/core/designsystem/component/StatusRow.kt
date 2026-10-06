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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme

/**
 * What a [StatusRow] says about its requirement. Four states, because MilO cannot read every
 * setting it depends on and must not pretend to.
 */
enum class RowStatus {
    /** Met. A green tick. */
    OK,

    /** Not met. A red warning. */
    PROBLEM,

    /** MilO tried to find out and could not. A grey question mark. */
    UNKNOWN,

    /** MilO cannot read this at all: the user sets it and says so. An amber empty ring. */
    NEEDS_CONFIRMATION,
}

/**
 * A button of a [StatusRow]. The label and the click handler travel together so a row can never
 * show a button that does nothing, or carry a handler with no button.
 */
@Immutable
data class StatusRowAction(val label: String, val onClick: () -> Unit)

/**
 * One requirement and its state: an indicator, a label, an optional second line, and up to two
 * buttons.
 *
 * The buttons sit on a line of their own, under the text and at its end. The second line is
 * often an instruction a sentence or two long, and a button beside it would squeeze it into a
 * narrow column.
 *
 * @param supportingText a second, quieter line: what the requirement is for, or how to fix it.
 * @param action the main button: usually the one that takes the user to the place to fix it.
 * @param secondaryAction a second button, shown before [action]: for example "Open settings"
 * beside "I have set this".
 */
@Composable
fun StatusRow(
    label: String,
    status: RowStatus,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    action: StatusRowAction? = null,
    secondaryAction: StatusRowAction? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            // Read the indicator and both lines as one sentence ("Needs attention, Location,
            // ...") instead of three separate stops. Each button stays its own stop.
            modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
            horizontalArrangement = Arrangement.spacedBy(MiloTheme.spacing.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = status.icon(),
                contentDescription = stringResource(status.descriptionRes()),
                tint = status.tint(),
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
        if (action != null || secondaryAction != null) {
            Row(
                modifier = Modifier.align(Alignment.End),
                horizontalArrangement = Arrangement.spacedBy(MiloTheme.spacing.small),
            ) {
                for (button in listOfNotNull(secondaryAction, action)) {
                    TextButton(onClick = button.onClick) {
                        Text(text = button.label)
                    }
                }
            }
        }
    }
}

private fun RowStatus.icon(): ImageVector = when (this) {
    RowStatus.OK -> StatusIcons.Ok
    RowStatus.PROBLEM -> StatusIcons.Problem
    RowStatus.UNKNOWN -> StatusIcons.Unknown
    RowStatus.NEEDS_CONFIRMATION -> StatusIcons.NeedsConfirmation
}

private fun RowStatus.descriptionRes(): Int = when (this) {
    RowStatus.OK -> R.string.status_ok
    RowStatus.PROBLEM -> R.string.status_problem
    RowStatus.UNKNOWN -> R.string.status_unknown
    RowStatus.NEEDS_CONFIRMATION -> R.string.status_needs_confirmation
}

@Composable
private fun RowStatus.tint(): Color = when (this) {
    RowStatus.OK -> MiloTheme.statusColors.ok
    RowStatus.PROBLEM -> MiloTheme.statusColors.problem
    RowStatus.UNKNOWN -> MiloTheme.statusColors.unknown
    RowStatus.NEEDS_CONFIRMATION -> MiloTheme.statusColors.attention
}

// Sample text is written inline because a preview is never shown to a user or shipped in a
// screen; putting it in strings.xml would add resources the app does not use.
@PreviewLightDark
@Composable
private fun StatusRowPreview() {
    MiloTheme {
        Surface {
            Column {
                StatusRow(label = "Notifications", status = RowStatus.OK)
                StatusRow(
                    label = "Background location",
                    status = RowStatus.PROBLEM,
                    supportingText = "Set to \"Allow all the time\"",
                    action = StatusRowAction(label = "Fix", onClick = {}),
                )
                StatusRow(
                    label = "Background autostart",
                    status = RowStatus.UNKNOWN,
                    supportingText = "MilO could not read this setting.",
                    action = StatusRowAction(label = "Open", onClick = {}),
                )
                StatusRow(
                    label = "Battery saver",
                    status = RowStatus.NEEDS_CONFIRMATION,
                    supportingText = "Set it to \"No restrictions\", then confirm here.",
                    action = StatusRowAction(label = "I have set this", onClick = {}),
                    secondaryAction = StatusRowAction(label = "Open", onClick = {}),
                )
            }
        }
    }
}

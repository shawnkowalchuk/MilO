package com.shawnkowalchuk.milo.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme

// The design's status dot, the mark on it, and the line of the empty ring.
internal val StatusDotSize = 28.dp
private val MarkSize = 14.dp
private val RingWidth = 2.dp

/**
 * What a [StatusRow] says about its requirement. Four states, because MilO cannot read every
 * setting it depends on and must not pretend to. Each has a dot of its own, different in what
 * is drawn on it as well as in colour.
 */
enum class RowStatus {
    /** Met. A dot in the accent colour with a tick. */
    OK,

    /** Not met. A red dot with an exclamation mark. */
    PROBLEM,

    /** MilO tried to find out and could not. A grey dot with a question mark. */
    UNKNOWN,

    /** MilO cannot read this at all: the user sets it and says so. An empty ring. */
    NEEDS_CONFIRMATION,
}

/** Where the buttons of a [StatusRow] stand. */
enum class StatusRowButtons {
    /** On a line of their own, under the text and at its end. */
    BELOW,

    /**
     * As the design draws the rows of the checklist: one button stands at the end of the row,
     * beside the text. A row with two buttons, with a second line of more than a few lines, or
     * with too little room for its text beside the button keeps them under the text
     * (`buttonStandsBeside` is the rule). The row is also as high as drawn: a button takes up
     * the 40 dp it is drawn at, and a second line of one line is no higher than its letters.
     */
    AT_END,
}

/**
 * A button of a [StatusRow]. The label and the click handler travel together so a row can never
 * show a button that does nothing, or carry a handler with no button.
 */
@Immutable
data class StatusRowAction(val label: String, val onClick: () -> Unit)

/**
 * One requirement and its state: a status dot, a label, an optional second line, and up to two
 * buttons.
 *
 * Unless told otherwise the buttons sit on a line of their own, under the text and at its end.
 * The second line is often an instruction a sentence or two long, and a button beside it would
 * squeeze it into a narrow column. The checklist, which the design draws with the button at
 * the end of the row, asks for [StatusRowButtons.AT_END].
 *
 * The main button is drawn in the accent colour only while the row is a problem: that is the
 * one press that puts something right. In every other state both buttons are quiet.
 *
 * @param supportingText a second, quieter line: what the requirement is for, or how to fix it.
 * @param action the main button: usually the one that takes the user to the place to fix it.
 * @param secondaryAction a second button, shown before [action]: for example "Open settings"
 * beside "I have set this".
 * @param buttons where the buttons stand.
 */
@Composable
fun StatusRow(
    label: String,
    status: RowStatus,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    action: StatusRowAction? = null,
    secondaryAction: StatusRowAction? = null,
    buttons: StatusRowButtons = StatusRowButtons.BELOW,
) {
    if (buttons == StatusRowButtons.AT_END) {
        StatusRowWithEndButton(
            label = label,
            status = status,
            supportingText = supportingText,
            action = action,
            secondaryAction = secondaryAction,
            modifier = modifier.fillMaxWidth(),
        )
        return
    }
    Column(modifier = modifier.fillMaxWidth()) {
        StatusRowInfo(label, status, supportingText, MaterialTheme.typography.bodyMedium)
        if (action != null || secondaryAction != null) {
            Row(
                modifier = Modifier.align(Alignment.End),
                horizontalArrangement = Arrangement.spacedBy(MiloTheme.spacing.buttonGap),
            ) {
                if (secondaryAction != null) {
                    RowButton(text = secondaryAction.label, onClick = secondaryAction.onClick)
                }
                if (action != null) {
                    RowButton(
                        text = action.label,
                        onClick = action.onClick,
                        accent = status == RowStatus.PROBLEM,
                    )
                }
            }
        }
    }
}

/**
 * The dot and the row's one or two lines, in both layouts of a [StatusRow].
 *
 * @param noteStyle the text style of the second line.
 */
@Composable
internal fun StatusRowInfo(
    label: String,
    status: RowStatus,
    supportingText: String?,
    noteStyle: TextStyle,
) {
    Row(
        // Read the dot and both lines as one sentence ("Needs attention, Location, ...")
        // instead of three separate stops. Each button stays its own stop.
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(MiloTheme.spacing.rowGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(status)
        Column(verticalArrangement = Arrangement.spacedBy(MiloTheme.spacing.textGap)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            if (supportingText != null) {
                Text(
                    text = supportingText,
                    style = noteStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * The dot at the start of the row. It never changes size with the phone's font size: its mark
 * is a drawn shape, not a letter.
 */
@Composable
private fun StatusDot(status: RowStatus) {
    val colors = MiloTheme.statusColors
    val description = stringResource(status.descriptionRes())
    val dot =
        Modifier.size(StatusDotSize).semantics {
            contentDescription = description
            role = Role.Image
        }
    val round = MiloTheme.shapes.pill
    // Every state is named, with no "else": a fifth state would not compile until it has a dot.
    when (status) {
        RowStatus.OK -> FilledDot(dot, colors.ok, StatusIcons.Tick)
        RowStatus.PROBLEM -> FilledDot(dot, colors.problem, StatusIcons.Exclamation)
        RowStatus.UNKNOWN -> FilledDot(dot, colors.unknown, StatusIcons.Question)
        RowStatus.NEEDS_CONFIRMATION -> Box(dot.border(RingWidth, colors.toConfirm, round))
    }
}

/** A dot filled with [fill], with [mark] drawn on it. */
@Composable
private fun FilledDot(modifier: Modifier, fill: Color, mark: ImageVector) {
    Box(
        modifier = modifier.background(fill, MiloTheme.shapes.pill),
        contentAlignment = Alignment.Center,
    ) {
        // The dot itself already says what the state is.
        Icon(
            imageVector = mark,
            contentDescription = null,
            modifier = Modifier.size(MarkSize),
            tint = MiloTheme.statusColors.onDot,
        )
    }
}

private fun RowStatus.descriptionRes(): Int = when (this) {
    RowStatus.OK -> R.string.status_ok
    RowStatus.PROBLEM -> R.string.status_problem
    RowStatus.UNKNOWN -> R.string.status_unknown
    RowStatus.NEEDS_CONFIRMATION -> R.string.status_needs_confirmation
}

// Sample text is written inline because a preview is never shown to a user or shipped in a
// screen; putting it in strings.xml would add resources the app does not use.
@Preview
@Composable
private fun StatusRowPreview() {
    MiloTheme {
        // On a tile, where a status row stands in the app.
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
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

package com.shawnkowalchuk.milo.feature.trips

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.component.ChoiceButton
import com.shawnkowalchuk.milo.core.designsystem.component.RowButton
import com.shawnkowalchuk.milo.core.designsystem.component.TextEntry
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme
import com.shawnkowalchuk.milo.core.trip.MAX_LABEL_LENGTH
import com.shawnkowalchuk.milo.core.trip.cleanLabel

/**
 * The choice of a trip's label: a sentence, the labels used before as a column of choice
 * buttons, the one used where the trip ended first and already chosen, "No label" for a trip
 * that has one, and a field for a new label. Save keeps the label typed in, if there is one,
 * and otherwise the one chosen; Cancel keeps the trip as it is.
 *
 * It is the dialog of `ConfirmDialog` (a tile, its title, two buttons of a row) with the edit
 * screen's choice buttons and text field in it. The design's chips are not used: they are the
 * colour of a tile, and would not be seen on one.
 */
@Composable
internal fun LabelDialog(pick: LabelPick, onSave: (String?) -> Unit, onDismiss: () -> Unit) {
    var chosen by rememberSaveable(pick.tripId) { mutableStateOf(pick.initial) }
    var typed by rememberSaveable(pick.tripId) { mutableStateOf("") }
    // Counts the presses on a choice, each of which empties the field: the field keeps its own
    // text, and is begun again to empty it.
    var emptied by rememberSaveable(pick.tripId) { mutableIntStateOf(0) }
    val typedLabel = cleanLabel(typed)
    val saved = typedLabel ?: chosen
    val choose: (String?) -> Unit = { label ->
        chosen = label
        typed = ""
        emptied++
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.trips_label_title),
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(MiloTheme.spacing.small),
            ) {
                Text(text = pick.sentence(), style = MiloTheme.textStyles.sentence)
                LabelChoiceButtons(pick, chosen, typing = typedLabel != null, choose)
                key(emptied) {
                    TextEntry(
                        label = stringResource(R.string.trips_label_new),
                        initialText = typed,
                        onTextChange = { typed = it },
                        maxLength = MAX_LABEL_LENGTH,
                        lastField = true,
                    )
                }
            }
        },
        confirmButton = {
            RowButton(
                text = stringResource(R.string.trips_label_save),
                onClick = { onSave(saved) },
                enabled = saved != pick.current,
            )
        },
        dismissButton = {
            RowButton(text = stringResource(R.string.action_cancel), onClick = onDismiss)
        },
        // As in ConfirmDialog: the sentence is what the dialog is about.
        textContentColor = MaterialTheme.colorScheme.onSurface,
    )
}

/**
 * The labels used before, one choice button each, and "No label" for a trip that has one.
 *
 * @param chosen the one chosen, or null for "No label".
 * @param typing true while a label is typed in: then none of them is in force.
 */
@Composable
private fun LabelChoiceButtons(
    pick: LabelPick,
    chosen: String?,
    typing: Boolean,
    onChoose: (String?) -> Unit,
) {
    val used = pick.choices.used
    if (used.isEmpty() && pick.current == null) return
    Column(
        modifier = Modifier.fillMaxWidth().selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(MiloTheme.spacing.buttonGap),
    ) {
        for (label in used) {
            ChoiceButton(
                text = label,
                selected = !typing && label.equals(chosen, ignoreCase = true),
                onSelect = { onChoose(label) },
            )
        }
        if (pick.current != null) {
            ChoiceButton(
                text = stringResource(R.string.trips_label_none),
                selected = !typing && chosen == null,
                onSelect = { onChoose(null) },
            )
        }
    }
}

/** What the dialog says first: the label used where the trip ended, if there is one. */
@Composable
private fun LabelPick.sentence(): String {
    val suggested = choices.suggested
    return when {
        suggested != null -> stringResource(R.string.trips_label_used_here, suggested)
        choices.used.isNotEmpty() -> stringResource(R.string.trips_label_choose)
        else -> stringResource(R.string.trips_label_first)
    }
}

package com.shawnkowalchuk.milo.feature.settings

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.component.ActionButton
import com.shawnkowalchuk.milo.core.designsystem.component.ActionButtonRow
import com.shawnkowalchuk.milo.core.designsystem.component.ActionKind
import com.shawnkowalchuk.milo.core.designsystem.component.ConfirmDialog
import com.shawnkowalchuk.milo.core.designsystem.component.QuietExpander
import com.shawnkowalchuk.milo.core.designsystem.component.StatusRow
import com.shawnkowalchuk.milo.core.designsystem.component.SwitchRow
import com.shawnkowalchuk.milo.core.designsystem.component.Tile
import com.shawnkowalchuk.milo.core.designsystem.component.TilePadding
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme
import com.shawnkowalchuk.milo.data.settings.LastExport
import com.shawnkowalchuk.milo.platform.transfer.PointsTaken
import com.shawnkowalchuk.milo.platform.transfer.TransferRefusal

/** What the file picker is told an export is. */
private const val JSON_TYPE = "application/json"

/**
 * Every kind of file is offered for an import, and not only JSON: the apps that hold files
 * (Drive, Files) do not agree on what kind an export is, and a file that is greyed out in the
 * picker cannot be chosen at all. A file that is no export is refused by MilO's own check.
 */
private const val ANY_FILE = "*/*"

/** What the tile can ask for. */
internal class DataActions(
    val onIncludePoints: (Boolean) -> Unit,
    val onExport: () -> Unit,
    val onImport: () -> Unit,
    val onRestoreSafetyCopy: (writtenAtMs: Long) -> Unit,
    val onConfirmImport: () -> Unit,
    val onDeclineImport: () -> Unit,
)

/**
 * The tile for Android's backup and for the export and import of all data, with the two file
 * pickers and the question before an import.
 *
 * It stands on the Settings screen with a ViewModel of its own ([DataViewModel]).
 */
@Composable
internal fun DataTile(viewModel: DataViewModel) {
    val state by viewModel.state.collectAsState()

    // Android's own file pickers. Each answers with the file, or with nothing if Shawn closed
    // it, in which case nothing happens.
    val exportPicker =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument(JSON_TYPE),
        ) { made ->
            if (made != null) viewModel.onExportTo(made.toString())
        }
    val importPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { picked ->
            if (picked != null) viewModel.onImportFrom(picked.toString())
        }

    val actions =
        DataActions(
            onIncludePoints = viewModel::onIncludePoints,
            onExport = {
                try {
                    exportPicker.launch(viewModel.exportFileName())
                } catch (noPicker: ActivityNotFoundException) {
                    // A phone with its file picker removed or disabled. The tile says so.
                    viewModel.onNoFilePicker()
                }
            },
            onImport = {
                try {
                    importPicker.launch(arrayOf(ANY_FILE))
                } catch (noPicker: ActivityNotFoundException) {
                    viewModel.onNoFilePicker()
                }
            },
            onRestoreSafetyCopy = viewModel::onRestoreSafetyCopy,
            onConfirmImport = viewModel::onConfirmImport,
            onDeclineImport = viewModel::onDeclineImport,
        )
    // Nothing is drawn before the first real state: DataTileContent takes the state it is
    // first drawn with as what Shawn has seen already.
    state?.let { DataTileContent(it, actions) }
}

/**
 * The tile itself, from its state alone. As the design draws it: the title, one line, and
 * "Export" and "Import" side by side.
 *
 * Under the buttons stands what the design does not draw and MilO has: when the last export
 * was made, the switch for the GPS points, each safety copy with its "Put that data back", and
 * what is going on or what the last export or import came to. What Android's backup takes and
 * what an export and an import are is put away behind the line at the end of the tile.
 *
 * What an export or an import came to is said under the buttons. The tile is the last one of a
 * long screen, so a sentence that comes up there would be below the edge; the screen moves
 * until it can be read, once for each thing that changes there. A tile that is only drawn again
 * (the phone turned, Settings opened again with the last outcome still standing) leaves the
 * screen where it is. So the [state] this is first drawn with must be a real one, never a
 * placeholder: what differs from it later counts as a change.
 */
@Composable
internal fun DataTileContent(state: DataCardState, actions: DataActions) {
    val told = remember { BringIntoViewRequester() }
    var seen by remember { mutableStateOf(state.working to state.lines) }
    var aboutOpen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.working, state.lines) {
        if (seen != state.working to state.lines) {
            seen = state.working to state.lines
            // What is new is laid out in the frame that has just begun. One frame on it has
            // its place, and the screen can tell how far to move.
            withFrameNanos { }
            told.bringIntoView()
        }
    }
    Tile(
        modifier = Modifier.fillMaxWidth(),
        padding = TilePadding.EVEN,
        gap = MiloTheme.spacing.rowGap,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(MiloTheme.spacing.textGap)) {
            Text(
                text = stringResource(R.string.settings_data_title),
                style = MaterialTheme.typography.bodyLarge,
                // Lets a screen reader jump from tile to tile.
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.settings_data_line),
                style = MiloTheme.textStyles.tileLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // While something is under way, asked about or being recorded, the buttons wait.
        ActionButtonRow(
            actions =
                listOf(
                    ActionButton(
                        label = stringResource(R.string.settings_data_export_short),
                        kind = ActionKind.PLAIN,
                        onClick = actions.onExport,
                        spokenName = stringResource(R.string.settings_data_export),
                        enabled = state.canStart,
                    ),
                    ActionButton(
                        label = stringResource(R.string.settings_data_import_short),
                        kind = ActionKind.PLAIN,
                        onClick = actions.onImport,
                        spokenName = stringResource(R.string.settings_data_import),
                        enabled = state.canStart,
                    ),
                ),
        )
        // Only while there is something to say: an empty block would still take its place in
        // the tile, as a gap under the buttons.
        if (state.tripInProgress || state.working != null || state.lines.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().bringIntoViewRequester(told),
                verticalArrangement = Arrangement.spacedBy(MiloTheme.spacing.small),
            ) {
                if (state.tripInProgress) {
                    Note(stringResource(R.string.settings_data_trip_in_progress))
                }
                state.working?.let { Note(stringResource(it.wordsRes())) }
                for (line in state.lines) {
                    StatusRow(label = line.text(), status = line.tone.rowStatus())
                }
            }
        }
        SwitchRow(
            label = stringResource(R.string.settings_data_include_points),
            checked = state.includePoints,
            onCheckedChange = actions.onIncludePoints,
            quiet = true,
        )
        Note(lastExportText(state))
        for (atMs in state.safetyCopiesAtMs) {
            SafetyCopy(atMs, state, actions.onRestoreSafetyCopy)
        }
        QuietExpander(
            label = stringResource(R.string.settings_about_data),
            expanded = aboutOpen,
            onToggle = { aboutOpen = !aboutOpen },
        ) {
            Note(stringResource(R.string.settings_data_backup))
            Note(stringResource(R.string.settings_data_export_intro))
            NamedNote(
                stringResource(R.string.settings_data_include_points),
                stringResource(R.string.settings_data_include_points_detail),
            )
            Note(stringResource(R.string.settings_data_import_intro))
        }
    }
    state.offer?.let { offer ->
        ConfirmDialog(
            title = stringResource(R.string.settings_data_question_title),
            text = importQuestion(offer, state.zone),
            confirmLabel = stringResource(R.string.settings_data_question_confirm),
            dismissLabel = stringResource(R.string.action_cancel),
            onConfirm = actions.onConfirmImport,
            onDismiss = actions.onDeclineImport,
        )
    }
}

/** One safety copy: when MilO kept it, and under that the button that puts it back. */
@Composable
private fun SafetyCopy(atMs: Long, state: DataCardState, onRestore: (Long) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(MiloTheme.spacing.small)) {
        Note(
            stringResource(
                R.string.settings_data_safety_copy,
                day(atMs, state.zone),
                time(atMs, state.zone),
            ),
        )
        ActionButtonRow(
            actions =
                listOf(
                    ActionButton(
                        label = stringResource(R.string.settings_data_restore_safety_copy),
                        kind = ActionKind.PLAIN,
                        onClick = { onRestore(atMs) },
                        enabled = state.canStart,
                    ),
                ),
        )
    }
}

// Sample values are written inline because a preview is never shown to a user or shipped.
@Preview
@Composable
private fun DataTilePreview() {
    val state =
        DataCardState(
            lastExport = LastExport(atMs = 1_791_234_567_890, withPoints = true),
            safetyCopiesAtMs = listOf(1_791_234_000_000, 1_791_100_000_000),
            lines =
                listOf(
                    OutcomeLine.Imported(1_234, 12, 380_112, PointsTaken.TAKEN),
                    OutcomeLine.PairTruckAgain,
                    OutcomeLine.Refused(TransferRefusal.Damaged),
                ),
        )
    MiloTheme {
        Surface { DataTileContent(state, DataActions({}, {}, {}, {}, {}, {})) }
    }
}

package com.shawnkowalchuk.milo.feature.tripedit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.component.PrimaryButton
import com.shawnkowalchuk.milo.core.designsystem.component.RowStatus
import com.shawnkowalchuk.milo.core.designsystem.component.ScreenTitle
import com.shawnkowalchuk.milo.core.designsystem.component.StatusRow
import com.shawnkowalchuk.milo.core.designsystem.component.Tile
import com.shawnkowalchuk.milo.core.designsystem.component.TileColumn
import com.shawnkowalchuk.milo.core.designsystem.component.TilePair
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme
import com.shawnkowalchuk.milo.core.schedule.TripCategory
import java.time.LocalDate

/**
 * What the form can ask for.
 *
 * @param onStart and [onEnd] carry the time picker's answer: an hour from 0 to 23 and a minute.
 */
internal class TripEditActions(
    val onDate: (LocalDate) -> Unit,
    val onStart: (hour: Int, minute: Int) -> Unit,
    val onEnd: (hour: Int, minute: Int) -> Unit,
    val onEndsNextDay: (Boolean) -> Unit,
    val onFrom: (String) -> Unit,
    val onTo: (String) -> Unit,
    val onKilometres: (String) -> Unit,
    val onCategory: (TripCategory) -> Unit,
    val onSave: () -> Unit,
    val onRestore: () -> Unit,
)

/**
 * The edit screen: one form for a finished trip's date, start and end time, from and to
 * address, distance, and Business or Personal. Opened for a trip of the Trips screen it changes
 * that trip; opened empty it takes a trip MilO missed.
 *
 * Nothing is stored until Save is pressed, and the screen leaves when it has been. It is the
 * one screen of MilO with a Save button: a setting is stored the moment it is changed, but a
 * trip's figures only make sense together.
 *
 * Leaving it belongs to the app, like all navigation, and so does the question that is asked
 * before something typed is thrown away: Android's Back and the bottom bar lead out of this
 * screen too, and only the app sees all three ways.
 *
 * @param onBack the Back arrow was pressed.
 * @param onSaved the form has been stored and the screen is done. It is told when the trip
 * starts as it is stored now, so that Trips can show the month it is in.
 * @param onUnsavedWork told true while the form holds something picked or typed that has not
 * been saved, and false again once it does not, or the screen is gone.
 */
@Composable
fun TripEditScreen(
    viewModel: TripEditViewModel,
    onBack: () -> Unit,
    onSaved: (startedAtMs: Long?) -> Unit,
    onUnsavedWork: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val actions =
        TripEditActions(
            onDate = viewModel::onDate,
            onStart = viewModel::onStart,
            onEnd = viewModel::onEnd,
            onEndsNextDay = viewModel::onEndsNextDay,
            onFrom = viewModel::onFrom,
            onTo = viewModel::onTo,
            onKilometres = viewModel::onKilometres,
            onCategory = viewModel::onCategory,
            onSave = viewModel::onSave,
            onRestore = viewModel::onRestore,
        )

    val ready = state as? TripEditUiState.Ready

    // Stored: back to the Trips screen, which shows the trip as it now is.
    val closing = ready?.closing == true
    val savedStartMs = ready?.savedStartMs
    LaunchedEffect(closing) { if (closing) onSaved(savedStartMs) }

    val unsaved = ready?.unsaved == true
    DisposableEffect(unsaved) {
        onUnsavedWork(unsaved)
        // Whatever closed the screen, nothing of it is left to ask about.
        onDispose { onUnsavedWork(false) }
    }

    TripEditContent(state = state, actions = actions, onBack = onBack, modifier = modifier)
}

/**
 * The edit screen as the owner's design draws it: the title behind the square back button,
 * "When", "Where", and side by side "Distance, km" and "Saved as"; the line that says who
 * chose Business or Personal; the Save button; and, for a trip that was edited, what MilO
 * recorded.
 */
@Composable
internal fun TripEditContent(
    state: TripEditUiState,
    actions: TripEditActions,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MiloTheme.spacing
    val adding = (state as? TripEditUiState.Ready)?.adding == true
    TileColumn(
        modifier =
            modifier
                .fillMaxSize()
                // Large font settings or a small window must scroll rather than cut content off.
                .verticalScroll(rememberScrollState())
                .padding(top = spacing.tileGap, bottom = spacing.small),
    ) {
        ScreenTitle(
            text =
                stringResource(if (adding) R.string.trip_add_title else R.string.trip_edit_title),
            onBack = onBack,
            // With the gap between two tiles, the design's 16 under the title.
            modifier = Modifier.padding(bottom = spacing.buttonGap),
        )
        when (state) {
            TripEditUiState.Reading ->
                Tile(modifier = Modifier.fillMaxWidth()) {
                    Quiet(stringResource(R.string.trip_edit_reading))
                }

            TripEditUiState.NotEditable ->
                Tile(modifier = Modifier.fillMaxWidth()) {
                    StatusRow(
                        label = stringResource(R.string.trip_edit_not_editable),
                        status = RowStatus.PROBLEM,
                    )
                }

            is TripEditUiState.Ready -> Form(state, actions)
        }
    }
}

/** The tiles of the form, the Save button, and the way back to what MilO recorded. */
@Composable
private fun ColumnScope.Form(state: TripEditUiState.Ready, actions: TripEditActions) {
    val whenTile = remember { BringIntoViewRequester() }
    val distanceTile = remember { BringIntoViewRequester() }
    val saveArea = remember { BringIntoViewRequester() }
    // A press on Save that stored nothing is answered in red beside what is wrong, and that is
    // a screen or more above the button: without this, the press would seem to have done
    // nothing. The screen moves once for each such press, when its answer is there, to the
    // first thing to put right. It does not move when the screen is merely drawn again (the
    // phone turned), nor while the form is being put right and the lines go away one by one.
    val refusalsWhenDrawn = remember { state.refusals }
    LaunchedEffect(state.refusals) {
        if (state.refusals != refusalsWhenDrawn) {
            // The lines are laid out in the frame that has just begun. One frame on they have
            // their place, and the screen can tell how far to move.
            withFrameNanos { }
            when (state.firstProblemPart) {
                FormPart.TIMES -> whenTile

                FormPart.DISTANCE -> distanceTile

                // Nothing is wrong with the form: storage did not take it. Said at the button.
                null -> saveArea
            }.bringIntoView()
        }
    }

    // What sets a trip that is typed in apart from one MilO recorded, in from the edge like
    // the line under the tiles. A recorded trip has no line here, as drawn.
    state.introRes()?.let { Note(stringResource(it)) }
    WhenTile(state, actions, Modifier.bringIntoViewRequester(whenTile))
    WhereTile(state, actions)
    TilePair(
        first = { half ->
            DistanceTile(state, actions, half.bringIntoViewRequester(distanceTile))
        },
        second = { half -> SavedAsTile(state, actions, half) },
    )
    // Who chose Business or Personal, under the two tiles as drawn.
    Note(stringResource(state.kindSource.noteRes()))
    SaveArea(state, actions.onSave, Modifier.bringIntoViewRequester(saveArea))
    state.recorded?.let { RecordedTile(it, state, actions.onRestore) }
}

private fun TripEditUiState.Ready.introRes(): Int? = when {
    adding -> R.string.trip_add_intro
    addedByHand -> R.string.trip_edit_added_intro
    else -> null
}

/**
 * The Save button and, directly above it, that storage did not take the last press. What is
 * wrong with the form itself is said in the tiles, beside what is to be put right.
 */
@Composable
private fun SaveArea(state: TripEditUiState.Ready, onSave: () -> Unit, modifier: Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(MiloTheme.spacing.tileGap),
    ) {
        if (state.saveFailed) {
            Tile(modifier = Modifier.fillMaxWidth()) {
                StatusRow(
                    label = stringResource(R.string.trip_edit_save_failed),
                    status = RowStatus.PROBLEM,
                )
            }
        }
        PrimaryButton(
            text =
                stringResource(
                    if (state.adding) R.string.trip_add_save else R.string.trip_edit_save,
                ),
            onClick = onSave,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** A quieter line of explanation inside a tile. */
@Composable
internal fun Quiet(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** A quieter line that stands on the page, in from the edge like a label above a tile. */
@Composable
private fun Note(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(horizontal = MiloTheme.spacing.extraSmall),
        style = MiloTheme.textStyles.tileLabel,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

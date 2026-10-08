package com.shawnkowalchuk.milo.feature.tripedit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.component.ChoiceButton
import com.shawnkowalchuk.milo.core.designsystem.component.DateDialog
import com.shawnkowalchuk.milo.core.designsystem.component.RowStatus
import com.shawnkowalchuk.milo.core.designsystem.component.StatusRow
import com.shawnkowalchuk.milo.core.designsystem.component.SwitchRow
import com.shawnkowalchuk.milo.core.designsystem.component.TextEntry
import com.shawnkowalchuk.milo.core.designsystem.component.Tile
import com.shawnkowalchuk.milo.core.designsystem.component.TileLabel
import com.shawnkowalchuk.milo.core.designsystem.component.TilePadding
import com.shawnkowalchuk.milo.core.designsystem.component.TimeDialog
import com.shawnkowalchuk.milo.core.designsystem.component.ValueButton
import com.shawnkowalchuk.milo.core.designsystem.component.rememberTwentyFourHourClock
import com.shawnkowalchuk.milo.core.designsystem.text.unitShortRes
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme
import com.shawnkowalchuk.milo.core.schedule.TripCategory
import com.shawnkowalchuk.milo.core.util.DistanceUnit
import com.shawnkowalchuk.milo.core.util.formatClockTime
import com.shawnkowalchuk.milo.core.util.formatDay
import com.shawnkowalchuk.milo.core.util.formatDistance

// The tiles of the edit form, as the owner's drawing has them and in the order a trip is read
// on the Trips screen: when, where, and side by side how far and what it is saved as. (Under
// them, for a trip that was edited, stands what MilO recorded: `RecordedTile.kt`.)
// What a press on Save found wrong is said in the tile it is about, beside what is to be put
// right: the times in "When", the kilometres under their field.

/** Which of the form's three pickers is open. Saved, so turning the phone does not close it. */
private enum class Picking { DATE, START, END }

/**
 * "When": the day as a quiet button as wide as the tile, and under it the start and the end
 * side by side, each a button with its small label over the time. A press opens the calendar
 * or the clock dial, as a time of day is chosen everywhere in MilO. Under them the switch for
 * a trip that ran past midnight, and what the last press on Save found wrong with the times.
 */
@Composable
internal fun WhenTile(
    state: TripEditUiState.Ready,
    actions: TripEditActions,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    val spacing = MiloTheme.spacing
    // The two times and the dial go by the phone's own 24-hour switch, like every time of day.
    val twentyFourHour = rememberTwentyFourHourClock()
    var picking by rememberSaveable { mutableStateOf<Picking?>(null) }
    val notSet = stringResource(R.string.trip_edit_time_not_set)

    Tile(
        modifier = modifier.fillMaxWidth(),
        padding = TilePadding.EVEN,
        gap = spacing.tileGap,
    ) {
        // Lets a screen reader jump from tile to tile, as it could from card to card.
        TileLabel(
            stringResource(R.string.trip_edit_when_title),
            Modifier.semantics(mergeDescendants = true) {
                heading()
            },
        )
        ValueButton(
            value = formatDay(state.date, locale),
            onClick = { picking = Picking.DATE },
            modifier = Modifier.fillMaxWidth(),
        )
        // Each button has half the line, so that the two stay side by side at a large font size.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.small),
        ) {
            ValueButton(
                value = state.start?.let { formatClockTime(it, locale, twentyFourHour) } ?: notSet,
                onClick = { picking = Picking.START },
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.trip_edit_start_label),
            )
            ValueButton(
                value = state.end?.let { formatClockTime(it, locale, twentyFourHour) } ?: notSet,
                onClick = { picking = Picking.END },
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.trip_edit_end_label),
            )
        }
        // The form has one date, the day the trip started. A trip that ran past midnight is
        // said to have done so here, and never worked out from an end that is earlier than the
        // start: that is far more often a slip of the dial.
        SwitchRow(
            label = stringResource(R.string.trip_edit_ends_next_day),
            checked = state.endsNextDay,
            onCheckedChange = actions.onEndsNextDay,
            // The 2 dp the design has above the switch's line.
            modifier = Modifier.padding(top = spacing.textGap),
            quietOnTile = true,
        )
        state.laterEndDate?.let { endDate ->
            Quiet(stringResource(R.string.trip_edit_ends_on, formatDay(endDate, locale)))
        }
        for (problem in state.timeProblems) {
            StatusRow(label = problem.words(state.unit), status = RowStatus.PROBLEM)
        }
    }

    when (picking) {
        Picking.DATE ->
            DateDialog(
                date = state.date,
                latest = state.latestDate,
                confirmLabel = stringResource(R.string.action_ok),
                dismissLabel = stringResource(R.string.action_cancel),
                onConfirm = { date ->
                    picking = null
                    actions.onDate(date)
                },
                onDismiss = { picking = null },
            )

        Picking.START, Picking.END -> {
            val forEnd = picking == Picking.END
            val shown = if (forEnd) state.endDial else state.startDial
            TimeDialog(
                title =
                    stringResource(
                        if (forEnd) R.string.trip_edit_pick_end else R.string.trip_edit_pick_start,
                    ),
                hour = shown.hour,
                minute = shown.minute,
                twelveHourClock = !twentyFourHour,
                confirmLabel = stringResource(R.string.action_ok),
                dismissLabel = stringResource(R.string.action_cancel),
                onConfirm = { hour, minute ->
                    picking = null
                    if (forEnd) actions.onEnd(hour, minute) else actions.onStart(hour, minute)
                },
                onDismiss = { picking = null },
            )
        }

        null -> Unit
    }
}

/** "Where": the two addresses, typed, each under its small label. */
@Composable
internal fun WhereTile(state: TripEditUiState.Ready, actions: TripEditActions) {
    Tile(
        modifier = Modifier.fillMaxWidth(),
        padding = TilePadding.EVEN,
        gap = MiloTheme.spacing.tileGap,
    ) {
        TileLabel(
            stringResource(R.string.trip_edit_where_title),
            Modifier.semantics(mergeDescendants = true) {
                heading()
            },
        )
        TextEntry(
            label = stringResource(R.string.trip_edit_from),
            initialText = state.from,
            onTextChange = actions.onFrom,
            maxLength = MAX_ADDRESS_LENGTH,
        )
        TextEntry(
            label = stringResource(R.string.trip_edit_to),
            initialText = state.to,
            onTextChange = actions.onTo,
            maxLength = MAX_ADDRESS_LENGTH,
        )
    }
}

/**
 * "Distance, km" or "Distance, mi": the distance, typed in the unit chosen in Settings, in the
 * drawing's larger figure. The field's label is the tile's own, and names the unit. What Save
 * found wrong with the distance is said under the field.
 */
@Composable
internal fun DistanceTile(
    state: TripEditUiState.Ready,
    actions: TripEditActions,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    Tile(modifier = modifier, padding = TilePadding.EVEN) {
        TextEntry(
            label =
                stringResource(
                    R.string.trip_edit_distance_label,
                    stringResource(unitShortRes(state.unit)),
                ),
            // What was typed, or the stored distance as every screen writes it. A stored
            // distance that cannot be written (it should never be negative) starts empty.
            initialText =
                state.kilometres
                    ?: state.storedMetres
                        ?.takeIf { it.isFinite() && it >= 0.0 }
                        ?.let { formatDistance(it, state.unit, locale) }
                        .orEmpty(),
            onTextChange = actions.onKilometres,
            maxLength = MAX_DISTANCE_LENGTH,
            decimalNumber = true,
            lastField = true,
            error = state.distanceProblem?.words(state.unit),
            figure = true,
        )
    }
}

/** The words for one thing that is wrong with the form, with what its sentence quotes. */
@Composable
private fun FormProblem.words(unit: DistanceUnit): String {
    val sentence = sentence(unit)
    return when {
        sentence.number != null && sentence.numberWith != null ->
            stringResource(
                sentence.text,
                stringResource(sentence.numberWith, sentence.number.toString()),
            )

        sentence.number != null -> stringResource(sentence.text, sentence.number)

        sentence.names != null -> stringResource(sentence.text, stringResource(sentence.names))

        else -> stringResource(sentence.text)
    }
}

/**
 * "Saved as": Business and Personal as two buttons, one above the other, the one in force in
 * the accent colour. Neither is, while a trip that is being added has no start time to sort it
 * by. Who chose is said in the line under the two tiles.
 */
@Composable
internal fun SavedAsTile(
    state: TripEditUiState.Ready,
    actions: TripEditActions,
    modifier: Modifier = Modifier,
) {
    val spacing = MiloTheme.spacing
    Tile(modifier = modifier, padding = TilePadding.EVEN, gap = spacing.small) {
        TileLabel(
            stringResource(R.string.trip_edit_saved_as),
            Modifier.semantics(mergeDescendants = true) {
                heading()
            },
        )
        // One group for a screen reader: "1 of 2", "2 of 2".
        Column(
            modifier = Modifier.selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(spacing.buttonGap),
        ) {
            ChoiceButton(
                text = stringResource(R.string.trip_business),
                selected = state.category == TripCategory.BUSINESS,
                onSelect = { actions.onCategory(TripCategory.BUSINESS) },
            )
            ChoiceButton(
                text = stringResource(R.string.trip_personal),
                selected = state.category == TripCategory.PERSONAL,
                onSelect = { actions.onCategory(TripCategory.PERSONAL) },
            )
        }
    }
}

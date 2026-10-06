package com.shawnkowalchuk.milo.feature.tripedit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.component.ChoiceRow
import com.shawnkowalchuk.milo.core.designsystem.component.DateDialog
import com.shawnkowalchuk.milo.core.designsystem.component.RowStatus
import com.shawnkowalchuk.milo.core.designsystem.component.SectionCard
import com.shawnkowalchuk.milo.core.designsystem.component.StatusRow
import com.shawnkowalchuk.milo.core.designsystem.component.SwitchRow
import com.shawnkowalchuk.milo.core.designsystem.component.TextEntry
import com.shawnkowalchuk.milo.core.designsystem.component.TimeDialog
import com.shawnkowalchuk.milo.core.designsystem.component.rememberTwentyFourHourClock
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme
import com.shawnkowalchuk.milo.core.schedule.TripCategory
import com.shawnkowalchuk.milo.core.util.formatClockTime
import com.shawnkowalchuk.milo.core.util.formatDay
import com.shawnkowalchuk.milo.core.util.formatKilometres

// The cards of the edit form, in the order a trip is read on the Trips screen: when, where, how
// far, and what it is saved as. (Under them, for a trip that was edited, stands what MilO
// recorded: `RecordedCard.kt`.)
// What a press on Save found wrong is said in the card it is about, beside what is to be put
// right: the times in "When", the kilometres under their field.

/** Which of the form's three pickers is open. Saved, so turning the phone does not close it. */
private enum class Picking { DATE, START, END }

/**
 * The day and the two times. Each is a text button that shows the value and opens a picker,
 * as a time of day is chosen everywhere in MilO. Under them a switch for a trip that ran past
 * midnight, and what the last press on Save found wrong with the times.
 */
@Composable
internal fun WhenCard(
    state: TripEditUiState.Ready,
    actions: TripEditActions,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    // The two times and the dial go by the phone's own 24-hour switch, like every time of day.
    val twentyFourHour = rememberTwentyFourHourClock()
    var picking by rememberSaveable { mutableStateOf<Picking?>(null) }

    SectionCard(title = stringResource(R.string.trip_edit_when_title), modifier = modifier) {
        TextButton(onClick = { picking = Picking.DATE }) {
            Text(text = stringResource(R.string.trip_edit_date, formatDay(state.date, locale)))
        }
        // Each button has half the line, so that the two stay side by side at a large font size.
        Row(modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = { picking = Picking.START }, modifier = Modifier.weight(1f)) {
                Text(
                    text =
                        state.start?.let {
                            val time = formatClockTime(it, locale, twentyFourHour)
                            stringResource(R.string.trip_edit_start, time)
                        } ?: stringResource(R.string.trip_edit_start_not_set),
                )
            }
            TextButton(onClick = { picking = Picking.END }, modifier = Modifier.weight(1f)) {
                Text(
                    text =
                        state.end?.let {
                            val time = formatClockTime(it, locale, twentyFourHour)
                            stringResource(R.string.trip_edit_end, time)
                        } ?: stringResource(R.string.trip_edit_end_not_set),
                )
            }
        }
        // The form has one date, the day the trip started. A trip that ran past midnight is
        // said to have done so here, and never worked out from an end that is earlier than the
        // start: that is far more often a slip of the dial.
        SwitchRow(
            label = stringResource(R.string.trip_edit_ends_next_day),
            checked = state.endsNextDay,
            onCheckedChange = actions.onEndsNextDay,
        )
        state.laterEndDate?.let { endDate ->
            Quiet(stringResource(R.string.trip_edit_ends_on, formatDay(endDate, locale)))
        }
        for (problem in state.timeProblems) {
            StatusRow(label = problem.words(), status = RowStatus.PROBLEM)
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

/** The two addresses, typed. */
@Composable
internal fun WhereCard(state: TripEditUiState.Ready, actions: TripEditActions) {
    SectionCard(title = stringResource(R.string.trip_edit_where_title)) {
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
        Quiet(
            stringResource(
                if (state.adding) R.string.trip_add_where_note else R.string.trip_edit_where_note,
            ),
        )
    }
}

/** The distance, typed in kilometres. What Save found wrong with it is said under the field. */
@Composable
internal fun DistanceCard(
    state: TripEditUiState.Ready,
    actions: TripEditActions,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    SectionCard(title = stringResource(R.string.trip_edit_distance_title), modifier = modifier) {
        TextEntry(
            label = stringResource(R.string.trip_edit_kilometres),
            // What was typed, or the stored distance as every screen writes it. A stored
            // distance that cannot be written (it should never be negative) starts empty.
            initialText =
                state.kilometres
                    ?: state.storedMetres
                        ?.takeIf { it.isFinite() && it >= 0.0 }
                        ?.let { formatKilometres(it, locale) }
                        .orEmpty(),
            onTextChange = actions.onKilometres,
            maxLength = MAX_DISTANCE_LENGTH,
            decimalNumber = true,
            lastField = true,
            error = state.distanceProblem?.words(),
        )
    }
}

/** The words for one thing that is wrong with the form, with what its sentence quotes. */
@Composable
private fun FormProblem.words(): String {
    val sentence = sentence()
    return when {
        sentence.number != null -> stringResource(sentence.text, sentence.number)
        sentence.names != null -> stringResource(sentence.text, stringResource(sentence.names))
        else -> stringResource(sentence.text)
    }
}

/** Business or Personal: one of two, and under them who chose. */
@Composable
internal fun KindCard(state: TripEditUiState.Ready, actions: TripEditActions) {
    SectionCard(title = stringResource(R.string.trip_edit_kind_title)) {
        // One group for a screen reader: "1 of 2", "2 of 2".
        Column(
            modifier = Modifier.selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(MiloTheme.spacing.small),
        ) {
            ChoiceRow(
                label = stringResource(R.string.trip_business),
                selected = state.category == TripCategory.BUSINESS,
                onSelect = { actions.onCategory(TripCategory.BUSINESS) },
            )
            ChoiceRow(
                label = stringResource(R.string.trip_personal),
                selected = state.category == TripCategory.PERSONAL,
                onSelect = { actions.onCategory(TripCategory.PERSONAL) },
            )
        }
        Quiet(stringResource(state.kindSource.noteRes()))
    }
}

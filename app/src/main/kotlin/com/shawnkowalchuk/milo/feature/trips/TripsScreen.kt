package com.shawnkowalchuk.milo.feature.trips

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.component.CameToFrontEffect
import com.shawnkowalchuk.milo.core.designsystem.component.RowStatus
import com.shawnkowalchuk.milo.core.designsystem.component.ScreenTitle
import com.shawnkowalchuk.milo.core.designsystem.component.StatusRow
import com.shawnkowalchuk.milo.core.designsystem.component.SwitchRow
import com.shawnkowalchuk.milo.core.designsystem.component.rememberTwentyFourHourClock
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme
import com.shawnkowalchuk.milo.core.schedule.TripCategory
import com.shawnkowalchuk.milo.core.util.formatMonthAndYear
import com.shawnkowalchuk.milo.data.trip.TripCorrection
import java.time.YearMonth

/**
 * What the Trips screen can ask for.
 *
 * @param onEdit opens the edit screen for a finished trip, and [onAdd] opens it empty, for a
 * trip MilO missed. Both lead to another screen, so both are the app's to carry out.
 * @param onReport opens the Report screen for a month, on the same terms.
 */
internal class TripsActions(
    val onPreviousMonth: () -> Unit,
    val onNextMonth: () -> Unit,
    val onShowLeftOut: (Boolean) -> Unit,
    val onCorrect: (Long, TripCorrection) -> Unit,
    val onMark: (Long, TripCategory) -> Unit,
    val onEdit: (Long) -> Unit,
    val onAdd: () -> Unit,
    val onReport: (YearMonth) -> Unit,
)

/**
 * One month of trips: the month's name, its Business total and, apart from it, its Personal
 * total at the top, then the trips grouped by day, newest first. It opens on the current month
 * and steps back and forth one month at a time, never past the current one.
 *
 * A finished trip can be marked Business or Personal here, whatever the work schedule made of
 * it.
 *
 * A finished trip can be deleted here, after a question. It is not destroyed: with the switch
 * on it is listed again, beside the trips MilO discarded, and can be restored; a discarded trip
 * can be counted after all.
 *
 * A finished trip's times, addresses and distance are changed on a screen of their own, and a
 * trip MilO missed is typed in there too.
 *
 * @param savedTripStartMs when a trip that was just saved on that screen starts, or null. The
 * month it is in is then shown, and [onSavedTripShown] says that this has been done.
 * @param onEditTrip opens that screen for the trip with this id, and [onAddTrip] opens it
 * empty. Navigation belongs to the app, not the feature.
 * @param onOpenReport opens the Report screen, where the month's report for the accountant is
 * made and sent, for the month it is handed.
 */
@Composable
fun TripsScreen(
    viewModel: TripsViewModel,
    savedTripStartMs: Long?,
    onSavedTripShown: () -> Unit,
    onEditTrip: (Long) -> Unit,
    onAddTrip: () -> Unit,
    onOpenReport: (YearMonth) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()

    // The current month can change while MilO sits in the background over midnight.
    CameToFrontEffect(viewModel::onCameToFront)

    // Back from the edit screen after a save. Acted on once: the month stays Shawn's to step
    // away from afterwards.
    LaunchedEffect(savedTripStartMs) {
        if (savedTripStartMs != null) {
            viewModel.onTripSaved(savedTripStartMs)
            onSavedTripShown()
        }
    }

    TripsContent(
        state = state,
        actions =
            TripsActions(
                onPreviousMonth = viewModel::onPreviousMonth,
                onNextMonth = viewModel::onNextMonth,
                onShowLeftOut = viewModel::onShowLeftOut,
                onCorrect = viewModel::onCorrect,
                onMark = viewModel::onMark,
                onEdit = onEditTrip,
                onAdd = onAddTrip,
                onReport = onOpenReport,
            ),
        modifier = modifier,
    )
}

@Composable
internal fun TripsContent(
    state: TripsUiState,
    actions: TripsActions,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    val twentyFourHour = rememberTwentyFourHourClock()
    val monthName = formatMonthAndYear(state.month, locale)
    val summary = state.summary

    // Which finished trip shows its buttons, and which one the question is being asked about.
    // Both belong to the screen, not to the trips, so they are kept here; saved, so a rotation
    // does not close the question.
    var openTripId by rememberSaveable { mutableStateOf<Long?>(null) }
    var askedAboutId by rememberSaveable { mutableStateOf<Long?>(null) }
    val rows =
        TripRowContext(
            zone = state.zone,
            twentyFourHour = twentyFourHour,
            openTripId = openTripId,
            onToggle = { id -> openTripId = if (openTripId == id) null else id },
            onAsk = { trip, correction ->
                // Only a delete is asked about. Restoring a trip, or counting one after all,
                // takes nothing away, and is undone by deleting the trip.
                if (correction == TripCorrection.DELETE) {
                    askedAboutId = trip.id
                } else {
                    actions.onCorrect(trip.id, correction)
                }
            },
            onMark = { trip, category ->
                // The buttons are put away with the press. Left open, the same spot would at
                // once hold the opposite button, and a double tap would undo the first.
                openTripId = null
                actions.onMark(trip.id, category)
            },
            onEdit = { trip ->
                // Put away as well: back from the edit screen the list starts closed, and a
                // trip that was moved to another day is not left behind with open buttons.
                openTripId = null
                actions.onEdit(trip.id)
            },
        )

    // A month that comes on screen starts at its top, where its name and its totals say which
    // month it is. That matters after a save on the edit screen: the list comes back where the
    // trip used to stand, and may now show another month.
    val list = rememberLazyListState()
    var monthAtTop by remember { mutableStateOf(state.month) }
    LaunchedEffect(state.month) {
        if (state.month != monthAtTop) {
            monthAtTop = state.month
            list.scrollToItem(0)
        }
    }

    // A lazy list: a busy month has a hundred trips, and only the rows on screen are laid out.
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = list,
        contentPadding = PaddingValues(MiloTheme.spacing.medium),
        verticalArrangement = Arrangement.spacedBy(MiloTheme.spacing.medium),
    ) {
        item { ScreenTitle(text = stringResource(R.string.trips_title)) }
        item {
            MonthCard(
                monthName = monthName,
                summary = summary,
                submission = state.submission,
                changed = state.changedSinceSent,
                zone = state.zone,
                canStepForward = state.canStepForward,
                onPreviousMonth = actions.onPreviousMonth,
                onNextMonth = actions.onNextMonth,
                onOpenReport = { actions.onReport(state.month) },
            )
        }
        item { AddTripButton(actions.onAdd) }
        item {
            SwitchRow(
                label = stringResource(R.string.trips_show_left_out),
                checked = state.showLeftOut,
                onCheckedChange = actions.onShowLeftOut,
            )
        }
        if (state.changeFailed) {
            item {
                StatusRow(
                    label = stringResource(R.string.trips_change_failed),
                    status = RowStatus.PROBLEM,
                )
            }
        }
        if (summary == null) {
            item { Note(stringResource(R.string.trips_reading)) }
            return@LazyColumn
        }
        summary.inProgress?.let { trip ->
            item { InProgressCard(trip, state.zone, twentyFourHour) }
        }
        if (summary.isEmpty) {
            item { Note(stringResource(R.string.trips_empty, monthName)) }
        }
        if (summary.hiddenLeftOut > 0) {
            item {
                Note(
                    pluralStringResource(
                        R.plurals.trips_hidden_left_out,
                        summary.hiddenLeftOut,
                        summary.hiddenLeftOut,
                    ),
                )
            }
        }
        if (summary.tripCount > 0) item { Note(stringResource(R.string.trips_press_hint)) }
        items(items = summary.days, key = { it.date.toEpochDay() }) { day ->
            DayCard(day, rows)
        }
    }

    // Looked up again each time: if the trip is no longer a counted one (it was deleted a
    // moment ago, or the month changed), there is nothing left to ask about.
    val askedAbout = summary?.countedTrip(askedAboutId)
    if (askedAbout != null) {
        DeleteQuestion(
            trip = askedAbout,
            zone = state.zone,
            twentyFourHour = twentyFourHour,
            onDelete = {
                askedAboutId = null
                openTripId = null
                actions.onCorrect(askedAbout.id, TripCorrection.DELETE)
            },
            onKeep = { askedAboutId = null },
        )
    }
}

private fun MonthSummary.countedTrip(id: Long?): TripLine? = days.firstNotNullOfOrNull { day ->
    day.trips.firstOrNull { it.id == id && it.kind == TripKind.COUNTED }
}

/**
 * The way to type in a trip MilO missed. Words and not a plus sign: it is used rarely, and
 * must be found without knowing what an icon means. At the end of a line of its own, like every
 * secondary action.
 */
@Composable
private fun AddTripButton(onAdd: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = onAdd) { Text(text = stringResource(R.string.trips_action_add)) }
    }
}

@Composable
private fun Note(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

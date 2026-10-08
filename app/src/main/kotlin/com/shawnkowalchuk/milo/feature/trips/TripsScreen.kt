package com.shawnkowalchuk.milo.feature.trips

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.component.CameToFrontEffect
import com.shawnkowalchuk.milo.core.designsystem.component.rememberTwentyFourHourClock
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme
import com.shawnkowalchuk.milo.core.schedule.TripCategory
import com.shawnkowalchuk.milo.data.trip.TripCorrection
import java.time.LocalDate
import java.time.YearMonth

/**
 * What the Trips screen can ask for.
 *
 * @param onToggleDay a day's heading was pressed: its trips are shown, or put away again.
 * @param onEdit opens the edit screen for a finished trip, and [onAdd] opens it empty, for a
 * trip MilO missed. Both lead to another screen, so both are the app's to carry out.
 * @param onReport opens the Report screen for a month, on the same terms.
 */
internal class TripsActions(
    val onPreviousMonth: () -> Unit,
    val onNextMonth: () -> Unit,
    val onShowLeftOut: (Boolean) -> Unit,
    val onToggleDay: (LocalDate) -> Unit,
    val onCorrect: (Long, TripCorrection) -> Unit,
    val onMark: (Long, TripCategory) -> Unit,
    val onEdit: (Long) -> Unit,
    val onAdd: () -> Unit,
    val onReport: (YearMonth) -> Unit,
)

/**
 * One month of trips, laid out as the owner's design draws it: the title with the two buttons
 * that step to the month before and the month after; the accent tile with the month's Business
 * kilometres and the pill that leads to its report; "Personal" and "Add missed trip" side by
 * side; the switch that lists the deleted and the discarded trips; then one tile per day,
 * newest first. It opens on the current month and never steps past it.
 *
 * **Every day starts closed, today too.** A closed day shows what it adds up to; a press on
 * its heading shows its trips, and another puts them away (the owner's own addition to his
 * drawing, 2026-10-06).
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
 * month it is in is then shown and its day is opened, and [onSavedTripShown] says that this has
 * been done.
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
                onToggleDay = viewModel::onToggleDay,
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
    val twentyFourHour = rememberTwentyFourHourClock()
    val summary = state.summary

    // Which finished trip shows its buttons, and which one the question is being asked about.
    // Both belong to the screen, not to the trips, so they are kept here; saved, so a rotation
    // does not close the question. Which days are open is the ViewModel's to keep.
    var openTripId by rememberSaveable { mutableStateOf<Long?>(null) }
    var askedAboutId by rememberSaveable { mutableStateOf<Long?>(null) }
    val rows =
        TripRowContext(
            zone = state.zone,
            twentyFourHour = twentyFourHour,
            unit = state.unit,
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
                // Put away as well: back from the edit screen the trip's buttons are closed,
                // and a trip that was moved to another day is not left behind with open ones.
                openTripId = null
                actions.onEdit(trip.id)
            },
        )
    val onToggleDay = { day: TripDay ->
        // A day that is closed takes its trip's buttons with it: opened again, it shows its
        // trips as a day that was never touched does.
        if (day.trips.any { it.id == openTripId }) openTripId = null
        actions.onToggleDay(day.date)
    }

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

    // A lazy list: a busy month has a day's tile for every day, and only the ones on screen
    // are laid out. The tiles stand as on Home: 18 dp from the sides, 10 dp apart.
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = list,
        contentPadding =
            PaddingValues(
                horizontal = MiloTheme.spacing.gutter,
                vertical = MiloTheme.spacing.small,
            ),
        verticalArrangement = Arrangement.spacedBy(MiloTheme.spacing.tileGap),
    ) {
        monthItems(state, actions)
        if (summary == null) {
            item(key = "reading") { SentenceTile(stringResource(R.string.trips_reading)) }
            return@LazyColumn
        }
        summary.inProgress?.let { trip ->
            item(key = "in progress") {
                InProgressTile(trip, state.zone, twentyFourHour, state.unit)
            }
        }
        summary.emptyWordsRes()?.let { words ->
            item(key = "nothing to list") {
                SentenceTile(stringResource(words, monthName(state)))
            }
        }
        items(items = summary.days, key = { it.date.toEpochDay() }) { day ->
            DayTile(
                day = day,
                today = state.today,
                expanded = day.date in state.openDays,
                onToggle = { onToggleDay(day) },
                context = rows,
            )
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
            unit = state.unit,
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

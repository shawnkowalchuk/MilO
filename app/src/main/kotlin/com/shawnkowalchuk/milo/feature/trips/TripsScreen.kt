package com.shawnkowalchuk.milo.feature.trips

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.component.CameToFrontEffect
import com.shawnkowalchuk.milo.core.designsystem.component.RowStatus
import com.shawnkowalchuk.milo.core.designsystem.component.ScreenTitle
import com.shawnkowalchuk.milo.core.designsystem.component.StatusRow
import com.shawnkowalchuk.milo.core.designsystem.component.SwitchRow
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme
import com.shawnkowalchuk.milo.core.util.formatMonthAndYear
import com.shawnkowalchuk.milo.data.trip.TripCorrection
import com.shawnkowalchuk.milo.platform.address.TripPlace
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** What the Trips screen can ask for. */
private class TripsActions(
    val onPreviousMonth: () -> Unit,
    val onNextMonth: () -> Unit,
    val onShowLeftOut: (Boolean) -> Unit,
    val onCorrect: (Long, TripCorrection) -> Unit,
)

/**
 * One month of trips: the month's name, its total and its number of trips at the top, then the
 * trips grouped by day, newest first. It opens on the current month and steps back and forth
 * one month at a time, never past the current one.
 *
 * A finished trip can be deleted here, after a question. It is not destroyed: with the switch
 * on it is listed again, beside the trips MilO discarded, and can be restored; a discarded trip
 * can be counted after all.
 */
@Composable
fun TripsScreen(viewModel: TripsViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsState()

    // The current month can change while MilO sits in the background over midnight.
    CameToFrontEffect(viewModel::onCameToFront)

    TripsContent(
        state = state,
        actions =
            TripsActions(
                onPreviousMonth = viewModel::onPreviousMonth,
                onNextMonth = viewModel::onNextMonth,
                onShowLeftOut = viewModel::onShowLeftOut,
                onCorrect = viewModel::onCorrect,
            ),
        modifier = modifier,
    )
}

@Composable
private fun TripsContent(
    state: TripsUiState,
    actions: TripsActions,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    val monthName = formatMonthAndYear(state.month, locale)
    val summary = state.summary

    // Which finished trip shows its Delete button, and which one the question is being asked
    // about. Both belong to the screen, not to the trips, so they are kept here; saved, so a
    // rotation does not close the question.
    var openTripId by rememberSaveable { mutableStateOf<Long?>(null) }
    var askedAboutId by rememberSaveable { mutableStateOf<Long?>(null) }
    val rows =
        TripRowContext(
            zone = state.zone,
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
        )

    // A lazy list: a busy month has a hundred trips, and only the rows on screen are laid out.
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(MiloTheme.spacing.medium),
        verticalArrangement = Arrangement.spacedBy(MiloTheme.spacing.medium),
    ) {
        item { ScreenTitle(text = stringResource(R.string.trips_title)) }
        item {
            MonthCard(
                monthName = monthName,
                summary = summary,
                canStepForward = state.canStepForward,
                onPreviousMonth = actions.onPreviousMonth,
                onNextMonth = actions.onNextMonth,
            )
        }
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
        summary.inProgress?.let { trip -> item { InProgressCard(trip, state.zone) } }
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

@Composable
private fun Note(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// Sample values are written inline because a preview is never shown to a user or shipped.
@PreviewLightDark
@Composable
private fun TripsPreview() {
    val morning = 1_791_028_800_000
    val summary =
        MonthSummary(
            totalMetres = 48_300.0,
            tripCount = 2,
            inProgress =
                TripLine(
                    4,
                    morning + 9_000_000,
                    null,
                    3_200.0,
                    TripKind.IN_PROGRESS,
                    from = TripPlace.Known("12 Shop Rd, Edmonton"),
                ),
            days =
                listOf(
                    TripDay(
                        LocalDate.of(2026, 10, 3),
                        listOf(
                            TripLine(
                                3,
                                morning + 3_600_000,
                                morning + 5_400_000,
                                24_900.0,
                                TripKind.COUNTED,
                                from = TripPlace.Known("48 Main St, Leduc"),
                                to = TripPlace.LookingUp,
                            ),
                            TripLine(
                                2,
                                morning + 1_800_000,
                                morning + 1_860_000,
                                120.0,
                                TripKind.DISCARDED,
                            ),
                            TripLine(
                                5,
                                morning + 1_000_000,
                                morning + 1_400_000,
                                4_100.0,
                                TripKind.DELETED,
                                from = TripPlace.Known("12 Shop Rd, Edmonton"),
                            ),
                            TripLine(
                                1,
                                morning,
                                morning + 1_500_000,
                                23_400.0,
                                TripKind.COUNTED,
                                from = TripPlace.Known("12 Shop Rd, Edmonton"),
                                to = TripPlace.Known("48 Main St, Leduc"),
                            ),
                        ),
                    ),
                ),
            hiddenLeftOut = 0,
        )
    val state =
        TripsUiState(
            month = YearMonth.of(2026, 10),
            zone = ZoneId.of("UTC"),
            canStepForward = false,
            showLeftOut = true,
            changeFailed = false,
            summary = summary,
        )
    MiloTheme {
        Surface {
            TripsContent(state = state, actions = TripsActions({}, {}, {}, { _, _ -> }))
        }
    }
}

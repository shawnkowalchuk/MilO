package com.shawnkowalchuk.milo.feature.trips

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.component.CameToFrontEffect
import com.shawnkowalchuk.milo.core.designsystem.component.ScreenTitle
import com.shawnkowalchuk.milo.core.designsystem.component.SectionCard
import com.shawnkowalchuk.milo.core.designsystem.component.SwitchRow
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme
import com.shawnkowalchuk.milo.core.util.formatKilometres
import com.shawnkowalchuk.milo.core.util.formatMonthAndYear
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** What the Trips screen can ask for. */
private class TripsActions(
    val onPreviousMonth: () -> Unit,
    val onNextMonth: () -> Unit,
    val onShowDiscarded: (Boolean) -> Unit,
)

/**
 * One month of trips: the month's name, its total and its number of trips at the top, then the
 * trips grouped by day, newest first. It opens on the current month and steps back and forth
 * one month at a time, never past the current one.
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
                onShowDiscarded = viewModel::onShowDiscarded,
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

    // A lazy list: a busy month has a hundred trips, and only the rows on screen are laid out.
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(MiloTheme.spacing.medium),
        verticalArrangement = Arrangement.spacedBy(MiloTheme.spacing.medium),
    ) {
        item { ScreenTitle(text = stringResource(R.string.trips_title)) }
        item { MonthCard(monthName, summary, state.canStepForward, actions) }
        item {
            SwitchRow(
                label = stringResource(R.string.trips_show_discarded),
                checked = state.showDiscarded,
                onCheckedChange = actions.onShowDiscarded,
            )
        }
        if (summary == null) {
            item { Note(stringResource(R.string.trips_reading)) }
            return@LazyColumn
        }
        summary.inProgress?.let { trip -> item { InProgressCard(trip, state.zone) } }
        if (summary.isEmpty) {
            item { Note(stringResource(R.string.trips_empty, monthName)) }
        }
        if (summary.hiddenDiscarded > 0) {
            item {
                Note(
                    pluralStringResource(
                        R.plurals.trips_hidden_discarded,
                        summary.hiddenDiscarded,
                        summary.hiddenDiscarded,
                    ),
                )
            }
        }
        items(items = summary.days, key = { it.date.toEpochDay() }) { day ->
            DayCard(day, state.zone)
        }
    }
}

/** The top of the screen: which month, what it adds up to, and the way to the months beside it. */
@Composable
private fun MonthCard(
    monthName: String,
    summary: MonthSummary?,
    canStepForward: Boolean,
    actions: TripsActions,
) {
    val locale = LocalConfiguration.current.locales[0]
    SectionCard(title = monthName) {
        if (summary != null) {
            Text(
                text =
                    stringResource(
                        R.string.distance_km,
                        formatKilometres(summary.totalMetres, locale),
                    ),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text =
                    pluralStringResource(
                        R.plurals.trips_count,
                        summary.tripCount,
                        summary.tripCount,
                    ),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(onClick = actions.onPreviousMonth) {
                Text(text = stringResource(R.string.trips_previous_month))
            }
            // Greyed out on the current month: a month that has not begun has no trips.
            TextButton(onClick = actions.onNextMonth, enabled = canStepForward) {
                Text(text = stringResource(R.string.trips_next_month))
            }
        }
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

// Sample values are written inline because a preview is never shown to a user or shipped.
@PreviewLightDark
@Composable
private fun TripsPreview() {
    val morning = 1_791_028_800_000
    val summary =
        MonthSummary(
            totalMetres = 48_300.0,
            tripCount = 2,
            inProgress = TripLine(4, morning + 9_000_000, null, 3_200.0, TripKind.IN_PROGRESS),
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
                            ),
                            TripLine(
                                2,
                                morning + 1_800_000,
                                morning + 1_860_000,
                                120.0,
                                TripKind.DISCARDED,
                            ),
                            TripLine(1, morning, morning + 1_500_000, 23_400.0, TripKind.COUNTED),
                        ),
                    ),
                ),
            hiddenDiscarded = 0,
        )
    val state =
        TripsUiState(
            month = YearMonth.of(2026, 10),
            zone = ZoneId.of("UTC"),
            canStepForward = false,
            showDiscarded = true,
            summary = summary,
        )
    MiloTheme {
        Surface {
            TripsContent(state = state, actions = TripsActions({}, {}, {}))
        }
    }
}

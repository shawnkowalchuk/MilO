package com.shawnkowalchuk.milo.feature.trips

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.component.AppHeader
import com.shawnkowalchuk.milo.core.designsystem.component.RowStatus
import com.shawnkowalchuk.milo.core.designsystem.component.StatusRow
import com.shawnkowalchuk.milo.core.designsystem.component.SteppedTitle
import com.shawnkowalchuk.milo.core.designsystem.component.StepperButton
import com.shawnkowalchuk.milo.core.designsystem.component.SwitchRow
import com.shawnkowalchuk.milo.core.designsystem.component.Tile
import com.shawnkowalchuk.milo.core.designsystem.component.TilePair
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme
import com.shawnkowalchuk.milo.core.util.formatMonthAndYear

// The top of the Trips screen's list, above the trips: in a file of its own because the
// screen's file would be over the size limit with it (ENGINEERING_STANDARDS section 3). The
// tiles themselves are in MonthTiles.kt.

/** "October 2026", as the month's tile and an empty month both name it. */
@Composable
internal fun monthName(state: TripsUiState): String =
    formatMonthAndYear(state.month, LocalConfiguration.current.locales[0])

/**
 * What stands above the trips, whatever the month holds: the top line every screen has, the
 * month with the two steps, the month's tile, "Personal" and "Add missed trip", the warning
 * for a month that changed after its report was sent, the switch, and that a change to a trip
 * was not made.
 */
internal fun LazyListScope.monthItems(state: TripsUiState, actions: TripsActions) {
    val summary = state.summary
    // Each part has a name of its own, so that the list keeps its place when one of the
    // parts that are not always there (the warning, the failed change) comes or goes.
    item(key = "title") {
        AppHeader(
            title = stringResource(R.string.trips_title),
            // As on Home: with the gap between two tiles, the design's 16 under the top line.
            modifier = Modifier.padding(bottom = MiloTheme.spacing.extraSmall),
        )
    }
    item(key = "steps") {
        SteppedTitle(
            text = monthName(state),
            previous =
                StepperButton(
                    description = stringResource(R.string.trips_previous_month),
                    onClick = actions.onPreviousMonth,
                ),
            // Greyed out on the current month: a month that has not begun has no trips.
            next =
                StepperButton(
                    description = stringResource(R.string.trips_next_month),
                    onClick = actions.onNextMonth,
                    enabled = state.canStepForward,
                ),
            // With the gap between two tiles, and the room a square keeps free around itself
            // for a finger, the design's 16 under the month, as under the top line.
            modifier = Modifier.padding(bottom = MiloTheme.spacing.extraSmall),
        )
    }
    item(key = "month") {
        MonthTile(
            monthName = monthName(state),
            summary = summary,
            submission = state.submission,
            zone = state.zone,
            onOpenReport = { actions.onReport(state.month) },
        )
    }
    item(key = "personal and add") {
        val locale = LocalConfiguration.current.locales[0]
        TilePair(
            first = { half -> PersonalTile(summary?.totals, locale, half) },
            second = { half -> AddTripTile(actions.onAdd, half) },
        )
    }
    state.changedSinceSent?.let { changed ->
        item(key = "changed since sent") {
            ChangedSinceTile(
                changed = changed,
                locale = LocalConfiguration.current.locales[0],
                onOpenReport = { actions.onReport(state.month) },
            )
        }
    }
    item(key = "switch") {
        LeftOutSwitch(state.showLeftOut, summary?.hiddenLeftOut ?: 0, actions.onShowLeftOut)
    }
    if (state.changeFailed) {
        item(key = "change failed") {
            Tile(modifier = Modifier.fillMaxWidth()) {
                StatusRow(
                    label = stringResource(R.string.trips_change_failed),
                    status = RowStatus.PROBLEM,
                )
            }
        }
    }
}

/**
 * The switch that lists the deleted and the discarded trips, on the page between the tiles as
 * drawn, and under it, only while some are hidden, how many.
 */
@Composable
private fun LeftOutSwitch(shown: Boolean, hidden: Int, onShow: (Boolean) -> Unit) {
    val spacing = MiloTheme.spacing
    Column(
        // The design sets the row 4 dp in from the tiles' edges and 4 dp lower.
        modifier =
            Modifier.padding(
                start = spacing.extraSmall,
                top = spacing.extraSmall,
                end = spacing.extraSmall,
            ),
        verticalArrangement = Arrangement.spacedBy(spacing.small),
    ) {
        SwitchRow(
            label = stringResource(R.string.trips_show_left_out),
            checked = shown,
            onCheckedChange = onShow,
            quiet = true,
        )
        if (hidden > 0) {
            Text(
                text = pluralStringResource(R.plurals.trips_hidden_left_out, hidden, hidden),
                style = MiloTheme.textStyles.tileLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A tile that holds one sentence where the trips would stand: reading, or nothing to list. */
@Composable
internal fun SentenceTile(text: String) {
    Tile(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = text,
            style = MiloTheme.textStyles.sentence,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

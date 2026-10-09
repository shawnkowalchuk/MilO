package com.shawnkowalchuk.milo.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.component.FigureRow
import com.shawnkowalchuk.milo.core.designsystem.component.Tile
import com.shawnkowalchuk.milo.core.designsystem.component.TileLabel
import com.shawnkowalchuk.milo.core.designsystem.component.TilePress
import com.shawnkowalchuk.milo.core.designsystem.text.distanceSpokenRes
import com.shawnkowalchuk.milo.core.designsystem.text.placesWords
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme
import com.shawnkowalchuk.milo.core.schedule.TripCategory
import com.shawnkowalchuk.milo.core.util.formatDistance

// The list of today's finished trips, in both of Home's layouts.

/**
 * Today's finished trips, newest first: where each went, its times, what it is saved as and
 * its kilometres. The whole tile leads to the Trips screen.
 *
 * Since 2026-10-09 it stands in both layouts (Shawn: "just have todays trips sorted latest at
 * the top"): with no trip open in place of the last trip alone, and while one is recorded as
 * before.
 *
 * @param recording true while a trip is recorded: it is not among the rows yet, and the tile
 * says so under them.
 */
@Composable
internal fun TodayListTile(
    figures: HomeTrips?,
    format: HomeFormat,
    onOpenTrips: () -> Unit,
    recording: Boolean,
) {
    val rows = figures?.rows.orEmpty()
    Tile(
        modifier = Modifier.fillMaxWidth(),
        press = TilePress(stringResource(R.string.home_open_trips), onOpenTrips),
        gap = MiloTheme.spacing.extraSmall,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = MiloTheme.spacing.buttonGap),
            horizontalArrangement = Arrangement.spacedBy(MiloTheme.spacing.rowGap),
        ) {
            TileLabel(
                text = todayListLabel(figures),
                modifier = Modifier.weight(1f).alignByBaseline(),
            )
            Text(
                text = stringResource(R.string.nav_trips),
                modifier = Modifier.alignByBaseline(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        when {
            figures == null -> Sentence(stringResource(R.string.trips_reading))
            rows.isEmpty() -> Sentence(stringResource(R.string.home_today_none))
        }
        rows.forEachIndexed { index, row ->
            TodayRow(row, format)
            if (index < rows.lastIndex) HorizontalDivider()
        }
        if (recording) {
            Text(
                text = stringResource(R.string.home_today_in_progress_note),
                modifier = Modifier.padding(top = MiloTheme.spacing.small),
                style = MiloTheme.textStyles.tileLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** "Today · 3 trips · 1 h 5 min driving", or "Today" before the first finished trip. */
@Composable
private fun todayListLabel(figures: HomeTrips?): String {
    val today = figures?.today
    if (today == null || today.count == 0) return stringResource(R.string.home_today_title)
    return pluralStringResource(
        R.plurals.home_today_list,
        today.count,
        today.count,
        durationText(today.driveTimeMs),
    )
}

/**
 * One finished trip. Its figure is the number alone, as drawn, and a screen reader is read the
 * unit too. A trip that is not Business has its figure greyed: it is not in the Business
 * figure of the tile above, and the white figures are the ones that add up to it.
 */
@Composable
private fun TodayRow(trip: HomeTrip, format: HomeFormat) {
    val distance = formatDistance(trip.distanceMetres, format.unit, format.locale)
    FigureRow(
        figure = distance,
        modifier = Modifier.padding(vertical = MiloTheme.spacing.tileGap),
        counted = trip.category == TripCategory.BUSINESS,
        figureSpoken = stringResource(distanceSpokenRes(format.unit), distance),
    ) {
        Text(text = placesWords(trip.places), style = MaterialTheme.typography.bodyLarge)
        Text(
            text = trip.timeAndCategory(trip.timesText(format)),
            style = MiloTheme.textStyles.tileLabel,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

package com.shawnkowalchuk.milo.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.component.DotWord
import com.shawnkowalchuk.milo.core.designsystem.component.FigureRow
import com.shawnkowalchuk.milo.core.designsystem.component.FigureSize
import com.shawnkowalchuk.milo.core.designsystem.component.FigureText
import com.shawnkowalchuk.milo.core.designsystem.component.HeroButton
import com.shawnkowalchuk.milo.core.designsystem.component.MiloIcons
import com.shawnkowalchuk.milo.core.designsystem.component.Pill
import com.shawnkowalchuk.milo.core.designsystem.component.Tile
import com.shawnkowalchuk.milo.core.designsystem.component.TileKind
import com.shawnkowalchuk.milo.core.designsystem.component.TileLabel
import com.shawnkowalchuk.milo.core.designsystem.component.TilePadding
import com.shawnkowalchuk.milo.core.designsystem.component.TilePair
import com.shawnkowalchuk.milo.core.designsystem.component.TilePress
import com.shawnkowalchuk.milo.core.designsystem.component.TruckLinkLook
import com.shawnkowalchuk.milo.core.designsystem.text.placesWords
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme
import com.shawnkowalchuk.milo.core.schedule.TripCategory
import com.shawnkowalchuk.milo.core.util.formatKilometres
import com.shawnkowalchuk.milo.core.util.formatTimeOfDay
import com.shawnkowalchuk.milo.platform.trip.CurrentTrip

// Home while a trip is being recorded, tile by tile, in the order of the owner's drawing.

@Composable
internal fun RecordingTiles(
    ui: HomeUi,
    trip: CurrentTrip,
    actions: HomeActions,
    format: HomeFormat,
) {
    RecordingTile(ui, trip, format, actions.onEnd)
    TilePair(
        first = { half -> SmallTruckTile(ui.truck.state, half) },
        second = { half ->
            TodayTile(ui.figures?.today, FigureSize.MEDIUM, format.locale, half)
        },
    )
    TodayListTile(ui.figures, format, actions.onOpenTrips)
}

/**
 * The trip being recorded: its kilometres so far, how long it has been running and since when,
 * where it started if that is known, and the button that ends it.
 *
 * It does not say Business or Personal: a trip is sorted by the work hours when it closes, and
 * until then MilO has not decided.
 */
@Composable
private fun RecordingTile(ui: HomeUi, trip: CurrentTrip, format: HomeFormat, onEnd: () -> Unit) {
    val waiting = TruckState.WAITING_TO_RECONNECT.takeIf { it == ui.truck.state }?.sentence
    Tile(
        modifier = Modifier.fillMaxWidth(),
        kind = TileKind.ACCENT,
        padding = TilePadding.ROOMY,
        gap = MiloTheme.spacing.controlPadding,
    ) {
        Row { Pill(text = stringResource(R.string.home_recording), lit = true) }
        // Side by side, as drawn. With a large font the time moves under the kilometres
        // rather than cutting them off.
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(MiloTheme.spacing.small),
            itemVerticalAlignment = Alignment.Bottom,
        ) {
            FigureText(
                figure = formatKilometres(trip.distanceMetres, format.locale),
                unit = stringResource(R.string.unit_km),
                size = FigureSize.HERO,
            )
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = durationText(ui.nowMs - trip.startedAtMs),
                    style = MiloTheme.textStyles.sideFigure,
                )
                Text(
                    text =
                        stringResource(
                            R.string.home_recording_since,
                            formatTimeOfDay(
                                trip.startedAtMs,
                                format.zone,
                                format.locale,
                                format.twentyFourHour,
                            ),
                        ),
                    style = MiloTheme.textStyles.accentNote,
                )
            }
        }
        // The grace period: the truck has gone, and the trip ends unless it comes back.
        if (waiting != null) {
            Text(text = stringResource(waiting), style = MiloTheme.textStyles.sentence)
        }
        ui.startAddress?.let { address ->
            Text(
                text = stringResource(R.string.trips_from, address),
                style = MiloTheme.textStyles.sentence,
            )
        }
        HeroButton(
            text = stringResource(R.string.home_end_trip),
            icon = MiloIcons.Stop,
            onClick = onEnd,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** The truck's connection in one word, beside today's figure. */
@Composable
private fun SmallTruckTile(state: TruckState, modifier: Modifier) {
    Tile(modifier = modifier, padding = TilePadding.EVEN, gap = MiloTheme.spacing.small) {
        TileLabel(stringResource(R.string.home_truck_corner), icon = MiloIcons.Bluetooth)
        DotWord(text = stringResource(state.title), on = state.look == TruckLinkLook.CONNECTED)
    }
}

/**
 * Today's finished trips, newest first: where each went, its times, what it is saved as and
 * its kilometres. The trip being recorded is not among them, and the tile says so. The whole
 * tile leads to the Trips screen.
 */
@Composable
private fun TodayListTile(figures: HomeTrips?, format: HomeFormat, onOpenTrips: () -> Unit) {
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
        Text(
            text = stringResource(R.string.home_today_in_progress_note),
            modifier = Modifier.padding(top = MiloTheme.spacing.small),
            style = MiloTheme.textStyles.tileLabel,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
    val kilometres = formatKilometres(trip.distanceMetres, format.locale)
    FigureRow(
        figure = kilometres,
        modifier = Modifier.padding(vertical = MiloTheme.spacing.tileGap),
        counted = trip.category == TripCategory.BUSINESS,
        figureSpoken = stringResource(R.string.distance_km, kilometres),
    ) {
        Text(text = placesWords(trip.places), style = MaterialTheme.typography.bodyLarge)
        Text(
            text = trip.timeAndCategory(trip.timesText(format)),
            style = MiloTheme.textStyles.tileLabel,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

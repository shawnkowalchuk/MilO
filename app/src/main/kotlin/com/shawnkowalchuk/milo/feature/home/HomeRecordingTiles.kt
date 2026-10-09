package com.shawnkowalchuk.milo.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.component.DotWord
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
import com.shawnkowalchuk.milo.core.designsystem.component.TruckLinkLook
import com.shawnkowalchuk.milo.core.designsystem.text.distanceSpokenRes
import com.shawnkowalchuk.milo.core.designsystem.text.unitShortRes
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme
import com.shawnkowalchuk.milo.core.util.formatDistance
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
            TodayTile(ui.figures, FigureSize.MEDIUM, format, half)
        },
    )
    ui.odometers?.let { OdometerTile(it, format) }
    TodayListTile(ui.figures, format, actions.onOpenTrips, recording = true)
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
            val soFar = formatDistance(trip.distanceMetres, format.unit, format.locale)
            FigureText(
                figure = soFar,
                unit = stringResource(unitShortRes(format.unit)),
                size = FigureSize.HERO,
                spoken = stringResource(distanceSpokenRes(format.unit), soFar),
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

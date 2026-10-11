package com.shawnkowalchuk.milo.feature.home

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.component.AttentionTile
import com.shawnkowalchuk.milo.core.designsystem.component.FigureSize
import com.shawnkowalchuk.milo.core.designsystem.component.HeroActionTile
import com.shawnkowalchuk.milo.core.designsystem.component.MiloIcons
import com.shawnkowalchuk.milo.core.designsystem.component.Tile
import com.shawnkowalchuk.milo.core.designsystem.component.TilePadding
import com.shawnkowalchuk.milo.core.designsystem.component.TilePair
import com.shawnkowalchuk.milo.core.designsystem.component.TilePress
import com.shawnkowalchuk.milo.core.designsystem.component.TruckTile
import com.shawnkowalchuk.milo.core.designsystem.component.TruckTileWords
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme
import com.shawnkowalchuk.milo.core.util.formatMonthName
import java.time.YearMonth

// Home with no trip open, tile by tile, in the order of the owner's drawing.

@Composable
internal fun IdleTiles(shown: HomeShown, actions: HomeActions, format: HomeFormat) {
    val ui = shown.ui
    // The whole tile is the Start button. One button, because with no trip open only starting
    // one makes sense; the tile for the trip being recorded has the button that ends it.
    HeroActionTile(
        title = stringResource(R.string.home_start_trip),
        line = stringResource(shown.startLine),
        icon = MiloIcons.Play,
        onClick = actions.onStart,
    )
    TilePair(
        first = { half ->
            TodayTile(ui.figures, FigureSize.LARGE, format, half)
        },
        second = { half ->
            MonthTile(YearMonth.from(ui.date), ui.figures?.month, ui.centsPerKm, format, half)
        },
    )
    HomeTruckTile(shown.truckTile, ui.truck, actions.onOpenPairing)
    // Under the truck: it is the truck's (since 2026-10-09).
    ui.odometers?.let { OdometerTile(it, format) }
    ui.reportWaiting?.let { month ->
        AttentionTile(
            title =
                stringResource(R.string.home_report_title, formatMonthName(month, format.locale)),
            text = stringResource(R.string.home_report_text),
            actionLabel = stringResource(R.string.home_report_action),
            onAction = { actions.onOpenReport(month) },
        )
    }
    // Every finished trip of today, the newest first (since 2026-10-09; the last one alone
    // before), as the layout of a trip in progress has always listed them.
    TodayListTile(ui.figures, format, actions.onOpenTrips, recording = false)
}

/**
 * The month's tile: its Business kilometres, a line that is filled by their share of all the
 * month's kilometres, that share as a number, and what those kilometres come to in dollars
 * ([MonthLines], which is also the other side of today's card while a trip is recorded).
 */
@Composable
private fun MonthTile(
    month: YearMonth,
    figures: MonthFigures?,
    centsPerKm: Int?,
    format: HomeFormat,
    modifier: Modifier,
) {
    Tile(modifier = modifier, padding = TilePadding.EVEN) {
        MonthLines(month, figures, centsPerKm, format, bar = true)
    }
}

/**
 * The truck's connection, in the words and the look of the state it is really in, or, for a
 * moment after the truck has arrived, in the design's "Connecting…". The vehicle's name stands
 * at the end of the first line. While no truck is paired, the tile is the way to the pairing
 * screen, and its sentence says so.
 *
 * @param tile the look and the words to draw.
 * @param truck the state the truck is in, and its name.
 */
@Composable
private fun HomeTruckTile(tile: TruckTileShown, truck: TruckTileState, onOpenPairing: () -> Unit) {
    TruckTile(
        look = tile.look,
        words =
            TruckTileWords(
                title = stringResource(tile.title),
                sentence = tile.sentence?.let { stringResource(it) },
                vehicle = truck.vehicleShown(stringResource(R.string.truck_without_a_name)),
            ),
        press =
            if (truck.state == TruckState.NO_TRUCK) {
                TilePress(stringResource(R.string.home_truck_none_press), onOpenPairing)
            } else {
                null
            },
    )
}

/** A sentence that stands in a tile in place of what the tile would otherwise show. */
@Composable
internal fun Sentence(text: String) {
    Text(
        text = text,
        style = MiloTheme.textStyles.sentence,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

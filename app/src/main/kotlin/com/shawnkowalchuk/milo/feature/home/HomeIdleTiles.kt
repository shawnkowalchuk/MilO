package com.shawnkowalchuk.milo.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.component.AttentionTile
import com.shawnkowalchuk.milo.core.designsystem.component.FigureSize
import com.shawnkowalchuk.milo.core.designsystem.component.HeroActionTile
import com.shawnkowalchuk.milo.core.designsystem.component.MiloIcons
import com.shawnkowalchuk.milo.core.designsystem.component.ProgressLine
import com.shawnkowalchuk.milo.core.designsystem.component.Tile
import com.shawnkowalchuk.milo.core.designsystem.component.TileLabel
import com.shawnkowalchuk.milo.core.designsystem.component.TilePadding
import com.shawnkowalchuk.milo.core.designsystem.component.TilePair
import com.shawnkowalchuk.milo.core.designsystem.component.TilePress
import com.shawnkowalchuk.milo.core.designsystem.component.TruckTile
import com.shawnkowalchuk.milo.core.designsystem.component.TruckTileWords
import com.shawnkowalchuk.milo.core.designsystem.text.placesWords
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme
import com.shawnkowalchuk.milo.core.util.formatKilometres
import com.shawnkowalchuk.milo.core.util.formatMonthName
import java.time.YearMonth
import java.util.Locale

// Home with no trip open, tile by tile, in the order of the owner's drawing.

private const val WHOLE = 100f

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
            TodayTile(ui.figures?.today, FigureSize.LARGE, format.locale, half)
        },
        second = { half ->
            MonthTile(YearMonth.from(ui.date), ui.figures?.month, format.locale, half)
        },
    )
    HomeTruckTile(shown.truckTile, ui.truck, actions.onOpenPairing)
    ui.reportWaiting?.let { month ->
        AttentionTile(
            title =
                stringResource(R.string.home_report_title, formatMonthName(month, format.locale)),
            text = stringResource(R.string.home_report_text),
            actionLabel = stringResource(R.string.home_report_action),
            onAction = { actions.onOpenReport(month) },
        )
    }
    LastTripTile(ui.figures, format, actions.onOpenTrips)
}

/**
 * The month's Business kilometres, a line that is filled by their share of all the month's
 * kilometres, and that share as a number.
 *
 * @param figures null while the trips are being read. The month's name is known all the same.
 */
@Composable
private fun MonthTile(
    month: YearMonth,
    figures: MonthFigures?,
    locale: Locale,
    modifier: Modifier,
) {
    Tile(modifier = modifier, padding = TilePadding.EVEN) {
        TileLabel(formatMonthName(month, locale))
        KilometresFigure(figures?.businessTenths, FigureSize.MEDIUM, locale)
        val percent = figures?.businessPercent
        ProgressLine(fraction = (percent ?: 0) / WHOLE)
        Caption(
            when {
                figures == null -> stringResource(R.string.trips_reading)

                // A share of no kilometres is not a number, so it is said in words.
                percent == null -> stringResource(R.string.home_month_none)

                else -> stringResource(R.string.home_month_share, percent)
            },
        )
    }
}

/**
 * The truck's connection, in the words and the look of the state it is really in, or, for a
 * moment after the truck has arrived, in the design's "Connecting…". While no truck is paired,
 * the tile is the way to the pairing screen, and its sentence says so.
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
                corner = stringResource(R.string.home_truck_corner),
                phoneLabel = stringResource(R.string.home_truck_phone),
                truckLabel = truck.truckName ?: stringResource(R.string.truck_without_a_name),
            ),
        press =
            if (truck.state == TruckState.NO_TRUCK) {
                TilePress(stringResource(R.string.home_truck_none_press), onOpenPairing)
            } else {
                null
            },
    )
}

/**
 * The most recent finished trip of today: where it went, when it started, what it is saved as
 * and its kilometres. With a trip to show, the whole tile leads to the Trips screen, where
 * every trip of today is listed; "All 4 today" says so.
 */
@Composable
private fun LastTripTile(figures: HomeTrips?, format: HomeFormat, onOpenTrips: () -> Unit) {
    val last = figures?.lastTrip
    Tile(
        modifier = Modifier.fillMaxWidth(),
        press =
            if (last == null) {
                null
            } else {
                TilePress(stringResource(R.string.home_open_trips), onOpenTrips)
            },
        gap = MiloTheme.spacing.rowGap,
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TileLabel(stringResource(R.string.home_last_trip), Modifier.alignByBaseline())
            if (last != null) {
                Text(
                    text =
                        pluralStringResource(
                            R.plurals.home_all_today,
                            figures.today.count,
                            figures.today.count,
                        ),
                    modifier = Modifier.alignByBaseline(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        when {
            figures == null -> Sentence(stringResource(R.string.trips_reading))
            last == null -> Sentence(stringResource(R.string.home_today_none))
            else -> LastTripRow(last, format)
        }
    }
}

@Composable
private fun LastTripRow(trip: HomeTrip, format: HomeFormat) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MiloTheme.spacing.rowGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(MiloTheme.spacing.textGap),
        ) {
            Text(text = placesWords(trip.places), style = MaterialTheme.typography.titleSmall)
            Text(
                text = trip.timeAndCategory(trip.startText(format)),
                style = MiloTheme.textStyles.tileLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text =
                stringResource(
                    R.string.distance_km,
                    formatKilometres(trip.distanceMetres, format.locale),
                ),
            style = MiloTheme.textStyles.rowFigure,
        )
    }
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

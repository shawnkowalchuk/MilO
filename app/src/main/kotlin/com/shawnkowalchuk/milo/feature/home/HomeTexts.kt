package com.shawnkowalchuk.milo.feature.home

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.component.FigureSize
import com.shawnkowalchuk.milo.core.designsystem.component.FigureText
import com.shawnkowalchuk.milo.core.designsystem.component.Tile
import com.shawnkowalchuk.milo.core.designsystem.component.TileLabel
import com.shawnkowalchuk.milo.core.designsystem.component.TilePadding
import com.shawnkowalchuk.milo.core.designsystem.text.categoryWordsRes
import com.shawnkowalchuk.milo.core.util.formatTenths
import com.shawnkowalchuk.milo.core.util.formatTimeOfDay
import com.shawnkowalchuk.milo.core.util.wholeHoursAndMinutes
import com.shawnkowalchuk.milo.data.trip.Tally
import com.shawnkowalchuk.milo.data.trip.TodayTrips
import java.util.Locale

// The words and the one tile that both of Home's layouts use.

/**
 * A total in kilometres as a large figure with its small unit, or a dash while the trips are
 * being read: a dash, because a zero would be a figure.
 *
 * @param tenths the total in tenths of a kilometre, as the rule for totals gives it
 * (`sumOfTenths`), or null while it is not known.
 */
@Composable
internal fun KilometresFigure(tenths: Long?, size: FigureSize, locale: Locale) {
    val unit = stringResource(R.string.unit_km)
    if (tenths == null) {
        val reading = stringResource(R.string.trips_reading)
        FigureText(
            figure = stringResource(R.string.home_figure_reading),
            unit = unit,
            modifier = Modifier.semantics { contentDescription = reading },
            size = size,
        )
    } else {
        FigureText(figure = formatTenths(tenths, locale), unit = unit, size = size)
    }
}

/**
 * Today's Business kilometres. Personal kilometres are never added to them: while today has a
 * Personal trip they stand in a quieter line of their own, as they always have on Home.
 *
 * @param today null while the trips are being read.
 * @param size the figure is larger when the tile holds nothing else.
 */
@Composable
internal fun TodayTile(
    today: TodayTrips?,
    size: FigureSize,
    locale: Locale,
    modifier: Modifier = Modifier,
) {
    Tile(modifier = modifier, padding = TilePadding.EVEN) {
        TileLabel(stringResource(R.string.home_today_business))
        KilometresFigure(today?.totals?.business?.tenths, size, locale)
        val totals = today?.totals ?: return@Tile
        if (totals.personal.count > 0) {
            Apart(R.plurals.trips_personal_total, totals.personal, locale)
        }
        // Only while there is such a trip, which in ordinary use is never.
        if (totals.unsorted.count > 0) {
            Apart(R.plurals.trips_unsorted_total, totals.unsorted, locale)
        }
    }
}

/** A total that is not Business, in a quieter line: "Personal: 5.0 km · 1 trip". */
@Composable
private fun Apart(plural: Int, tally: Tally, locale: Locale) {
    val kilometres = stringResource(R.string.distance_km, formatTenths(tally.tenths, locale))
    Caption(pluralStringResource(plural, tally.count, tally.count, kilometres))
}

/** The smallest grey line of a tile: "89% business". */
@Composable
internal fun Caption(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** "23 min" or "1 h 5 min": whole minutes, rounded down. */
@Composable
internal fun durationText(durationMs: Long): String {
    val time = wholeHoursAndMinutes(durationMs)
    return if (time.hours == 0L) {
        stringResource(R.string.duration_minutes, time.minutes)
    } else {
        stringResource(R.string.duration_hours_minutes, time.hours, time.minutes)
    }
}

/** "12:40 PM", the time a trip started. */
internal fun HomeTrip.startText(format: HomeFormat): String =
    formatTimeOfDay(startedAtMs, format.zone, format.locale, format.twentyFourHour)

/** "12:40 PM – 1:07 PM", as the Trips screen writes a trip's times. */
@Composable
internal fun HomeTrip.timesText(format: HomeFormat): String {
    val start = startText(format)
    // A finished trip has an end; the start alone is the fallback for a row that storage should
    // never produce.
    val end = endedAtMs ?: return start
    return stringResource(
        R.string.trips_time_range,
        start,
        formatTimeOfDay(end, format.zone, format.locale, format.twentyFourHour),
    )
}

/** A trip's time or times with what it is saved as: "12:40 PM · Business". */
@Composable
internal fun HomeTrip.timeAndCategory(time: String): String = stringResource(
    R.string.home_time_and_category,
    time,
    stringResource(categoryWordsRes(category, ranPastSchedule)),
)

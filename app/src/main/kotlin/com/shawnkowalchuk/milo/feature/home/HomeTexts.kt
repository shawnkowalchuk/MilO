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
import com.shawnkowalchuk.milo.core.allowance.allowanceCents
import com.shawnkowalchuk.milo.core.allowance.formatWholeDollars
import com.shawnkowalchuk.milo.core.designsystem.component.FigureSize
import com.shawnkowalchuk.milo.core.designsystem.component.FigureText
import com.shawnkowalchuk.milo.core.designsystem.component.ProgressLine
import com.shawnkowalchuk.milo.core.designsystem.component.Tile
import com.shawnkowalchuk.milo.core.designsystem.component.TileLabel
import com.shawnkowalchuk.milo.core.designsystem.component.TilePadding
import com.shawnkowalchuk.milo.core.designsystem.text.categoryWordsRes
import com.shawnkowalchuk.milo.core.designsystem.text.distanceRes
import com.shawnkowalchuk.milo.core.designsystem.text.distanceSpokenRes
import com.shawnkowalchuk.milo.core.designsystem.text.unitNameRes
import com.shawnkowalchuk.milo.core.designsystem.text.unitShortRes
import com.shawnkowalchuk.milo.core.util.DistanceUnit
import com.shawnkowalchuk.milo.core.util.formatMonthName
import com.shawnkowalchuk.milo.core.util.formatTenths
import com.shawnkowalchuk.milo.core.util.formatTimeOfDay
import com.shawnkowalchuk.milo.core.util.wholeHoursAndMinutes
import com.shawnkowalchuk.milo.data.trip.Tally
import java.time.YearMonth
import java.util.Locale

// The words and the figures that both of Home's layouts use.

private const val WHOLE = 100f

/**
 * A total as a large figure with its small unit, or a dash while the trips are being read: a
 * dash, because a zero would be a figure.
 *
 * @param tenths the total in tenths of [unit], as the rule for totals gives it
 * (`sumOfTenths`), or null while it is not known.
 * @param unit the unit the frame is drawn in. It is written after the figure, and said whole
 * to a screen reader.
 */
@Composable
internal fun DistanceFigure(tenths: Long?, size: FigureSize, locale: Locale, unit: DistanceUnit) {
    val short = stringResource(unitShortRes(unit))
    if (tenths == null) {
        val reading = stringResource(R.string.trips_reading)
        FigureText(
            figure = stringResource(R.string.home_figure_reading),
            unit = short,
            modifier = Modifier.semantics { contentDescription = reading },
            size = size,
        )
    } else {
        val figure = formatTenths(tenths, locale)
        FigureText(
            figure = figure,
            unit = short,
            size = size,
            spoken = stringResource(distanceSpokenRes(unit), figure),
        )
    }
}

/**
 * Today's Business distance, in a tile of its own. Personal trips are never added to it: while
 * today has a Personal trip they stand in a quieter line of their own, as they always have on
 * Home.
 *
 * @param figures null while the trips are being read.
 * @param size the figure is larger when the tile holds nothing else.
 */
@Composable
internal fun TodayTile(
    figures: HomeTrips?,
    size: FigureSize,
    format: HomeFormat,
    modifier: Modifier = Modifier,
) {
    Tile(modifier = modifier, padding = TilePadding.EVEN) { TodayLines(figures, size, format) }
}

/** What that tile holds: its label, the figure, and what is not Business apart from it. */
@Composable
internal fun TodayLines(figures: HomeTrips?, size: FigureSize, format: HomeFormat) {
    val totals = figures?.todayTotals
    TileLabel(stringResource(R.string.home_today_business))
    DistanceFigure(totals?.business?.tenths, size, format.locale, format.unit)
    if (totals == null) return
    if (totals.personal.count > 0) {
        Apart(R.plurals.trips_personal_total, totals.personal, format)
    }
    // Only while there is such a trip, which in ordinary use is never.
    if (totals.unsorted.count > 0) {
        Apart(R.plurals.trips_unsorted_total, totals.unsorted, format)
    }
}

/**
 * The month: its name, its Business kilometres, and under them their share of all the month's
 * kilometres with what they come to in dollars (Shawn, 2026-10-09: "the business dollar value
 * that the person's getting back"). The dollars are the widget's: the month's Business
 * kilometres at the one rate set in Settings, in whole dollars.
 *
 * It is what the month's tile holds with no trip open, and the other side of today's card
 * while one is recorded.
 *
 * @param figures null while the trips are being read. The month's name is known all the same.
 * @param centsPerKm null while the rate has not been read: the share then stands alone.
 * @param bar whether the thin line stands between the figure and the words, filled by the
 * share. The tile has it; the card's other side is as high as today's and has no room for it.
 */
@Composable
internal fun MonthLines(
    month: YearMonth,
    figures: MonthFigures?,
    centsPerKm: Int?,
    format: HomeFormat,
    bar: Boolean,
) {
    TileLabel(formatMonthName(month, format.locale))
    DistanceFigure(figures?.businessTenths, FigureSize.MEDIUM, format.locale, format.unit)
    val percent = figures?.businessPercent
    if (bar) ProgressLine(fraction = (percent ?: 0) / WHOLE)
    Caption(
        when {
            figures == null -> stringResource(R.string.trips_reading)

            // A share of no kilometres is not a number, so it is said in words.
            percent == null ->
                stringResource(R.string.home_month_none, stringResource(unitNameRes(format.unit)))

            centsPerKm == null -> stringResource(R.string.home_month_share, percent)

            else ->
                stringResource(
                    R.string.home_month_share_dollars,
                    percent,
                    formatWholeDollars(
                        allowanceCents(figures.businessKmTenths, centsPerKm),
                        format.locale,
                    ),
                )
        },
    )
}

/** A total that is not Business, in a quieter line: "Personal: 5.0 km · 1 trip". */
@Composable
private fun Apart(plural: Int, tally: Tally, format: HomeFormat) {
    val distance =
        stringResource(distanceRes(format.unit), formatTenths(tally.tenths, format.locale))
    Caption(pluralStringResource(plural, tally.count, tally.count, distance))
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

package com.shawnkowalchuk.milo.feature.trips

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.component.ExpandableHeading
import com.shawnkowalchuk.milo.core.designsystem.component.ExpandableTile
import com.shawnkowalchuk.milo.core.designsystem.component.FigureRow
import com.shawnkowalchuk.milo.core.designsystem.component.Tile
import com.shawnkowalchuk.milo.core.designsystem.text.placesLine
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme
import com.shawnkowalchuk.milo.core.util.formatDay
import com.shawnkowalchuk.milo.core.util.formatKilometres
import com.shawnkowalchuk.milo.core.util.formatShortDay
import com.shawnkowalchuk.milo.core.util.formatTenths
import com.shawnkowalchuk.milo.core.util.formatTimeOfDay
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

// The tiles the trips of a month stand in: the trip in progress, and one tile per day, which
// is closed until its heading is pressed. A trip's own row is in TripRows.kt.

/**
 * The trip being recorded, set apart from the finished ones and marked as not yet counted. It
 * has no button: it cannot be changed here until it has ended, and its last line says so.
 */
@Composable
internal fun InProgressTile(trip: TripLine, zone: ZoneId, twentyFourHour: Boolean) {
    val locale = LocalConfiguration.current.locales[0]
    Tile(modifier = Modifier.fillMaxWidth(), gap = MiloTheme.spacing.rowGap) {
        Text(
            text = stringResource(R.string.trips_in_progress_title),
            // Lets a screen reader jump to it, as to a day.
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleSmall,
        )
        // No figure rather than a wrong one, if the running distance is not known. Grey: it
        // is in no total yet.
        val kilometres = trip.distanceMetres?.let { formatKilometres(it, locale) }
        FigureRow(
            figure = kilometres,
            counted = false,
            figureSpoken = kilometres?.let { stringResource(R.string.distance_km, it) },
        ) {
            trip.placesText()?.let { PlacesLine(placesLine(it)) }
            GreyLine(
                stringResource(
                    R.string.trip_started_at,
                    formatTimeOfDay(trip.startedAtMs, zone, locale, twentyFourHour),
                ),
            )
        }
        Text(
            text = stringResource(R.string.trips_in_progress_note),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * One day: a tile whose heading says the day and what it adds up to, and which shows the
 * day's trips once the heading is pressed. Every day starts closed, today too.
 *
 * @param expanded whether the day's trips are showing. Kept by the ViewModel, so a day that
 * was opened is still open after a turn of the phone or a visit to the edit screen.
 */
@Composable
internal fun DayTile(
    day: TripDay,
    today: LocalDate,
    expanded: Boolean,
    onToggle: () -> Unit,
    context: TripRowContext,
) {
    val locale = LocalConfiguration.current.locales[0]
    val heading = day.heading(today)
    ExpandableTile(
        heading =
            ExpandableHeading(
                title = dayTitle(heading.isToday, formatShortDay(day.date, locale)),
                line = dayLine(heading, locale),
                openLabel = stringResource(R.string.trips_day_press_open),
                closeLabel = stringResource(R.string.trips_day_press_close),
                // The drawn date is short ("Mon, Oct 5"). A screen reader is read the whole.
                titleSpoken = dayTitle(heading.isToday, formatDay(day.date, locale)),
            ),
        expanded = expanded,
        onToggle = onToggle,
    ) {
        day.trips.forEachIndexed { index, trip ->
            val last = index == day.trips.lastIndex
            TripRow(trip, context, last)
            if (!last) HorizontalDivider()
        }
    }
}

/** "Today · Tue, Oct 6", or the day alone. */
@Composable
private fun dayTitle(isToday: Boolean, day: String): String =
    if (isToday) stringResource(R.string.trips_day_today, day) else day

/**
 * "4 trips · 48.2 km business": the day's counted trips, and what its Business ones add up
 * to. While the day lists deleted or discarded trips, how many: closed, the tile would
 * otherwise hide where they are.
 */
@Composable
private fun dayLine(heading: DayHeading, locale: Locale): String {
    val totals =
        pluralStringResource(
            R.plurals.trips_day_summary,
            heading.sessionCount,
            heading.sessionCount,
            stringResource(R.string.distance_km, formatTenths(heading.businessTenths, locale)),
        )
    if (heading.notCounted == 0) return totals
    val notCounted =
        pluralStringResource(
            R.plurals.trips_day_not_counted,
            heading.notCounted,
            heading.notCounted,
        )
    return joined(listOf(totals, notCounted))
}

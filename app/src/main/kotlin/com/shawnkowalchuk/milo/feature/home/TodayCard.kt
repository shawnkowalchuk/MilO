package com.shawnkowalchuk.milo.feature.home

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.component.FigureRow
import com.shawnkowalchuk.milo.core.designsystem.component.SectionCard
import com.shawnkowalchuk.milo.core.util.formatKilometres
import com.shawnkowalchuk.milo.core.util.formatTimeOfDay
import com.shawnkowalchuk.milo.core.util.wholeHoursAndMinutes
import com.shawnkowalchuk.milo.data.trip.TodaySession
import com.shawnkowalchuk.milo.data.trip.TodayTrips
import java.time.ZoneId
import java.util.Locale

/**
 * Today's finished trips: how far and how long in all, how many, and each one with its times
 * and its kilometres, newest first. The trip in progress is not among them: it has the card
 * above, and is added here when it ends.
 *
 * @param today null while the trips are being read.
 * @param tripInProgress adds a line saying that the trip being recorded is not counted yet.
 */
@Composable
internal fun TodayCard(today: TodayTrips?, tripInProgress: Boolean, zone: ZoneId) {
    val locale = LocalConfiguration.current.locales[0]
    SectionCard(title = stringResource(R.string.home_today_title)) {
        when {
            today == null ->
                Text(
                    text = stringResource(R.string.trips_reading),
                    style = MaterialTheme.typography.bodyLarge,
                )

            today.count == 0 ->
                Text(
                    text = stringResource(R.string.home_today_none),
                    style = MaterialTheme.typography.bodyLarge,
                )

            else -> {
                Text(
                    text = kilometres(today.totalMetres, locale),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text =
                        pluralStringResource(
                            R.plurals.home_today_summary,
                            today.count,
                            today.count,
                            durationText(today.driveTimeMs),
                        ),
                    style = MaterialTheme.typography.bodyLarge,
                )
                for (session in today.sessions) SessionRow(session, zone, locale)
            }
        }
        if (tripInProgress) {
            Text(
                text = stringResource(R.string.home_today_in_progress_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** One finished trip: "08:14 – 08:39" and its kilometres, as the Trips screen writes them. */
@Composable
private fun SessionRow(session: TodaySession, zone: ZoneId, locale: Locale) {
    val start = formatTimeOfDay(session.startedAtMs, zone, locale)
    // A finished trip has an end; the start alone is the fallback for a row that storage should
    // never produce.
    val end = session.endedAtMs?.let { formatTimeOfDay(it, zone, locale) }
    FigureRow(figure = kilometres(session.distanceMetres, locale)) {
        Text(
            text = if (end ==
                null
            ) {
                start
            } else {
                stringResource(R.string.trips_time_range, start, end)
            },
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun kilometres(metres: Double, locale: Locale): String =
    stringResource(R.string.distance_km, formatKilometres(metres, locale))

/** "23 min" or "1 h 5 min": whole minutes, rounded down. */
@Composable
private fun durationText(durationMs: Long): String {
    val time = wholeHoursAndMinutes(durationMs)
    return if (time.hours == 0L) {
        stringResource(R.string.duration_minutes, time.minutes)
    } else {
        stringResource(R.string.duration_hours_minutes, time.hours, time.minutes)
    }
}

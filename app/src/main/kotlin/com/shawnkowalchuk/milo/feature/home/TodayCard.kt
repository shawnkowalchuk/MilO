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
import com.shawnkowalchuk.milo.core.designsystem.text.categoryWordsRes
import com.shawnkowalchuk.milo.core.util.formatKilometres
import com.shawnkowalchuk.milo.core.util.formatTenths
import com.shawnkowalchuk.milo.core.util.formatTimeOfDay
import com.shawnkowalchuk.milo.core.util.wholeHoursAndMinutes
import com.shawnkowalchuk.milo.data.trip.Tally
import com.shawnkowalchuk.milo.data.trip.TodaySession
import com.shawnkowalchuk.milo.data.trip.TodayTrips
import java.time.ZoneId
import java.util.Locale

/**
 * Today's finished trips. The Business figures come first and large: how far, how many trips
 * and how long. Personal follows in a quieter line of its own, never added to Business. Then
 * each trip with its times, what it is saved as and its kilometres, newest first. The trip in
 * progress is not among them: it has the card above, and is added here when it ends.
 *
 * @param today null while the trips are being read.
 * @param tripInProgress adds a line saying that the trip being recorded is not counted yet.
 * @param twentyFourHour whether the phone is set to write times with 24 hours.
 */
@Composable
internal fun TodayCard(
    today: TodayTrips?,
    tripInProgress: Boolean,
    zone: ZoneId,
    twentyFourHour: Boolean,
) {
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
                val totals = today.totals
                Text(
                    text = stringResource(R.string.trip_business),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = total(totals.business, locale),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text =
                        pluralStringResource(
                            R.plurals.home_today_summary,
                            totals.business.count,
                            totals.business.count,
                            durationText(today.businessDriveTimeMs),
                        ),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Apart(R.plurals.trips_personal_total, totals.personal, locale)
                // Only while there is such a trip, which in ordinary use is never.
                if (totals.unsorted.count > 0) {
                    Apart(R.plurals.trips_unsorted_total, totals.unsorted, locale)
                }
                for (session in today.sessions) {
                    SessionRow(session, zone, locale, twentyFourHour)
                }
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

/** A total that is not Business, in a quieter line: "Personal: 5.0 km · 1 trip". */
@Composable
private fun Apart(plural: Int, tally: Tally, locale: Locale) {
    Text(
        text =
            pluralStringResource(
                plural,
                tally.count,
                tally.count,
                total(tally, locale),
            ),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * One finished trip: "08:14 – 08:39", what it is saved as, and its kilometres, as the Trips
 * screen writes them.
 */
@Composable
private fun SessionRow(
    session: TodaySession,
    zone: ZoneId,
    locale: Locale,
    twentyFourHour: Boolean,
) {
    val start = formatTimeOfDay(session.startedAtMs, zone, locale, twentyFourHour)
    // A finished trip has an end; the start alone is the fallback for a row that storage should
    // never produce.
    val end = session.endedAtMs?.let { formatTimeOfDay(it, zone, locale, twentyFourHour) }
    FigureRow(figure = kilometres(session.distanceMetres, locale)) {
        Text(
            text =
                if (end == null) {
                    start
                } else {
                    stringResource(R.string.trips_time_range, start, end)
                },
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = stringResource(categoryWordsRes(session.category, session.ranPastSchedule)),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun kilometres(metres: Double, locale: Locale): String =
    stringResource(R.string.distance_km, formatKilometres(metres, locale))

/** A total as it is printed: the sum of the figures the rows show, never of their metres. */
@Composable
private fun total(tally: Tally, locale: Locale): String =
    stringResource(R.string.distance_km, formatTenths(tally.tenths, locale))

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

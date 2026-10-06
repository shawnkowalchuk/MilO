package com.shawnkowalchuk.milo.feature.trips

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.component.SectionCard
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme
import com.shawnkowalchuk.milo.core.util.formatDay
import com.shawnkowalchuk.milo.core.util.formatKilometres
import com.shawnkowalchuk.milo.core.util.formatTimeOfDay
import java.time.ZoneId

// The rows of the Trips screen: the trip in progress, and one card per day.

/** The trip being recorded, set apart from the finished ones and marked as not yet counted. */
@Composable
internal fun InProgressCard(trip: TripLine, zone: ZoneId) {
    val locale = LocalConfiguration.current.locales[0]
    SectionCard(title = stringResource(R.string.trips_in_progress_title)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MiloTheme.spacing.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text =
                    stringResource(
                        R.string.trip_started_at,
                        formatTimeOfDay(trip.startedAtMs, zone, locale),
                    ),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            // No figure rather than a wrong one, if the running distance is not known.
            trip.distanceMetres?.let { metres ->
                Text(
                    text = stringResource(R.string.distance_km, formatKilometres(metres, locale)),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
        trip.placesText()?.let { PlacesLine(it) }
        Text(
            text = stringResource(R.string.trips_in_progress_note),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** One day's trips under the day's date. */
@Composable
internal fun DayCard(day: TripDay, zone: ZoneId) {
    val locale = LocalConfiguration.current.locales[0]
    SectionCard(title = formatDay(day.date, locale)) {
        for (trip in day.trips) TripRow(trip, zone)
    }
}

/**
 * A trip's start and end time, where it went, and its distance. A discarded trip says that it is
 * not counted.
 */
@Composable
private fun TripRow(trip: TripLine, zone: ZoneId) {
    val locale = LocalConfiguration.current.locales[0]
    val discarded = trip.kind == TripKind.DISCARDED
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MiloTheme.spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            val start = formatTimeOfDay(trip.startedAtMs, zone, locale)
            // Every listed trip has ended; the start alone is the fallback for a row that
            // storage should never produce.
            val end = trip.endedAtMs?.let { formatTimeOfDay(it, zone, locale) }
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
            trip.placesText()?.let { PlacesLine(it) }
            if (discarded) {
                Text(
                    text = stringResource(R.string.trips_discarded_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        trip.distanceMetres?.let { metres ->
            Text(
                text = stringResource(R.string.distance_km, formatKilometres(metres, locale)),
                style = MaterialTheme.typography.bodyLarge,
                // Greyed, so a figure that is not in the total does not read as one that is.
                color =
                    if (discarded) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
            )
        }
    }
}

/**
 * Where a trip went, under its times: "from → to", or in plain words that an address is still
 * being looked up or that none was found. Never coordinates, and never a gap.
 */
@Composable
private fun PlacesLine(places: PlacesText) {
    Text(
        text =
            when (places) {
                is PlacesText.Sentence -> stringResource(places.text)

                is PlacesText.From -> stringResource(R.string.trips_from, places.address)

                is PlacesText.FromTo ->
                    stringResource(R.string.trips_from_to, places.from.text(), places.to.text())
            },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun PlaceSide.text(): String = when (this) {
    is PlaceSide.Address -> line
    is PlaceSide.Words -> stringResource(text)
}

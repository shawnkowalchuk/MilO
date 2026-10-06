package com.shawnkowalchuk.milo.feature.trips

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.component.FigureRow
import com.shawnkowalchuk.milo.core.designsystem.component.SectionCard
import com.shawnkowalchuk.milo.core.util.formatDay
import com.shawnkowalchuk.milo.core.util.formatKilometres
import com.shawnkowalchuk.milo.core.util.formatTimeOfDay
import com.shawnkowalchuk.milo.data.trip.TripCorrection
import java.time.ZoneId
import java.util.Locale

// The rows of the Trips screen: the trip in progress, and one card per day.

/**
 * What a row needs besides its trip.
 *
 * @param openTripId the finished trip whose Delete button is showing, if one is.
 * @param onToggle a finished trip was pressed: its button is shown, or put away again.
 * @param onAsk the button of a row was pressed. Delete is asked about first; the other two are
 * made at once.
 */
internal class TripRowContext(
    val zone: ZoneId,
    val openTripId: Long?,
    val onToggle: (Long) -> Unit,
    val onAsk: (TripLine, TripCorrection) -> Unit,
)

/** The trip being recorded, set apart from the finished ones and marked as not yet counted. */
@Composable
internal fun InProgressCard(trip: TripLine, zone: ZoneId) {
    val locale = LocalConfiguration.current.locales[0]
    SectionCard(title = stringResource(R.string.trips_in_progress_title)) {
        // No figure rather than a wrong one, if the running distance is not known.
        FigureRow(figure = trip.kilometres(locale)) {
            Text(
                text =
                    stringResource(
                        R.string.trip_started_at,
                        formatTimeOfDay(trip.startedAtMs, zone, locale),
                    ),
                style = MaterialTheme.typography.bodyLarge,
            )
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
internal fun DayCard(day: TripDay, context: TripRowContext) {
    val locale = LocalConfiguration.current.locales[0]
    SectionCard(title = formatDay(day.date, locale)) {
        for (trip in day.trips) TripRow(trip, context)
    }
}

/**
 * A trip's start and end time, where it went, and its distance, with the one thing that can be
 * done about it.
 *
 * A finished trip is pressed to bring up its Delete button, so that a list of counted trips is
 * not a list of buttons. A deleted or a discarded trip says that it is not counted and shows
 * its button (Restore, Count this trip) straight away: those rows are only listed on request,
 * and the button is what they are looked at for.
 */
@Composable
private fun TripRow(trip: TripLine, context: TripRowContext) {
    val locale = LocalConfiguration.current.locales[0]
    val counted = trip.kind == TripKind.COUNTED
    val correction = trip.kind.correction
    val deleteShown = counted && trip.id == context.openTripId
    val rowAndButton = remember { BringIntoViewRequester() }
    // True from the tap that brings the Delete button up until the list has moved to show it.
    var openedByTap by remember { mutableStateOf(false) }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .bringIntoViewRequester(rowAndButton)
                .then(
                    if (counted) {
                        Modifier.clickable(
                            onClickLabel = stringResource(R.string.trips_row_press),
                            onClick = {
                                openedByTap = !deleteShown
                                context.onToggle(trip.id)
                            },
                        )
                    } else {
                        // Not something to press, so not offered as one: as a button that is
                        // switched off, a screen reader called a deleted trip "disabled",
                        // right above a Restore that works. Still read as one item, like a
                        // row that can be pressed.
                        Modifier.semantics(mergeDescendants = true) {}
                    },
                ),
    ) {
        FigureRow(
            figure = trip.kilometres(locale),
            // A row that is pressed is never lower than Material's smallest target for a
            // finger; with its addresses on one line it would be.
            modifier = if (counted) Modifier.minimumInteractiveComponentSize() else Modifier,
            counted = counted,
        ) {
            Text(
                text = trip.timesText(context.zone, locale),
                style = MaterialTheme.typography.bodyLarge,
            )
            trip.placesText()?.let { PlacesLine(it) }
            trip.kind.leftOutNoteRes()?.let { note ->
                Text(
                    text = stringResource(note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        if (deleteShown) {
            // The button comes up under the row, which for the lowest row on the screen is
            // below the edge: the tap then looked as if it had done nothing. Only a tap moves
            // the list. A button that comes back because the list was scrolled, or the phone
            // turned, stays where it is.
            LaunchedEffect(Unit) {
                if (openedByTap) {
                    openedByTap = false
                    // The button is laid out in the frame that has just begun. One frame on,
                    // the row has its new height, and the list can tell how far to move.
                    withFrameNanos { }
                    rowAndButton.bringIntoView()
                }
            }
        }
        if (correction != null && (!counted || deleteShown)) {
            TextButton(
                onClick = { context.onAsk(trip, correction) },
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(text = stringResource(correction.labelRes()))
            }
        }
    }
}

/** "08:14 – 08:39", as a row and the question before a delete both say it. */
@Composable
internal fun TripLine.timesText(zone: ZoneId, locale: Locale): String {
    val start = formatTimeOfDay(startedAtMs, zone, locale)
    // Every listed trip has ended; the start alone is the fallback for a row that storage
    // should never produce.
    val end = endedAtMs?.let { formatTimeOfDay(it, zone, locale) } ?: return start
    return stringResource(R.string.trips_time_range, start, end)
}

/** "12.4 km", or null when the distance is not known. */
@Composable
internal fun TripLine.kilometres(locale: Locale): String? =
    distanceMetres?.let { stringResource(R.string.distance_km, formatKilometres(it, locale)) }

/** The words that say a listed trip is not in the total, or null for one that is. */
private fun TripKind.leftOutNoteRes(): Int? = when (this) {
    TripKind.DISCARDED -> R.string.trips_discarded_note
    TripKind.DELETED -> R.string.trips_deleted_note
    TripKind.COUNTED, TripKind.IN_PROGRESS -> null
}

private fun TripCorrection.labelRes(): Int = when (this) {
    TripCorrection.DELETE -> R.string.trips_action_delete
    TripCorrection.RESTORE -> R.string.trips_action_restore
    TripCorrection.COUNT -> R.string.trips_action_count
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

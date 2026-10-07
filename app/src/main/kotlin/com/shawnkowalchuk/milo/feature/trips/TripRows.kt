package com.shawnkowalchuk.milo.feature.trips

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.component.ActionButton
import com.shawnkowalchuk.milo.core.designsystem.component.ActionButtonRow
import com.shawnkowalchuk.milo.core.designsystem.component.ActionKind
import com.shawnkowalchuk.milo.core.designsystem.component.FigureRow
import com.shawnkowalchuk.milo.core.designsystem.text.placesLine
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme
import com.shawnkowalchuk.milo.core.schedule.TripCategory
import com.shawnkowalchuk.milo.core.util.formatKilometres
import com.shawnkowalchuk.milo.core.util.formatTimeOfDay
import com.shawnkowalchuk.milo.core.util.formatTimeSpan
import com.shawnkowalchuk.milo.data.trip.TripCorrection
import java.time.ZoneId
import java.util.Locale

// A trip's row on the Trips screen, and the buttons under it. The tiles the rows stand in (a
// day, and the trip in progress) are in DayTiles.kt.

/**
 * What a row needs besides its trip.
 *
 * @param twentyFourHour whether the phone is set to write times with 24 hours.
 * @param openTripId the finished trip whose buttons are showing, if one is.
 * @param onToggle a finished trip was pressed: its buttons are shown, or put away again.
 * @param onAsk Delete, Restore or Count this trip was pressed. Delete is asked about first; the
 * other two are made at once.
 * @param onMark "Personal" or "Business" was pressed. Made at once: the other button undoes it.
 * @param onEdit "Edit" was pressed: the edit screen is opened for the trip.
 */
internal class TripRowContext(
    val zone: ZoneId,
    val twentyFourHour: Boolean,
    val openTripId: Long?,
    val onToggle: (Long) -> Unit,
    val onAsk: (TripLine, TripCorrection) -> Unit,
    val onMark: (TripLine, TripCategory) -> Unit,
    val onEdit: (TripLine) -> Unit,
)

/**
 * One trip of an open day, as drawn: where it went, under that its times and what it is saved
 * as, and its kilometres at the end as a bare figure.
 *
 * The grey line carries every note the trip has, each after a middle dot: "edited" or "added
 * by hand" for figures that are Shawn's own, and for a trip that is listed on request, why it
 * is not counted.
 *
 * The figure is white only for a trip that is in the day's Business figure, which is the one
 * its heading shows. A Personal trip, and one that is not counted at all, has it greyed.
 *
 * A finished trip is pressed to bring up its buttons, so that a day is not a list of buttons.
 * A deleted or a discarded trip shows its one button straight away (`actions`).
 *
 * @param last whether it is the day's last row, which has the tile's edge under it and not a
 * divider.
 */
@Composable
internal fun TripRow(trip: TripLine, context: TripRowContext, last: Boolean) {
    val locale = LocalConfiguration.current.locales[0]
    val spacing = MiloTheme.spacing
    val counted = trip.kind == TripKind.COUNTED
    val pressed = counted && trip.id == context.openTripId
    val buttons = trip.actions(pressed).map { it.asButton(trip, context) }
    val rowAndButtons = remember { BringIntoViewRequester() }
    // True from the tap that brings the buttons up until the list has moved to show them.
    var openedByTap by remember { mutableStateOf(false) }
    val kilometres = trip.distanceMetres?.let { formatKilometres(it, locale) }
    Column(modifier = Modifier.fillMaxWidth().bringIntoViewRequester(rowAndButtons)) {
        FigureRow(
            figure = kilometres,
            modifier =
                Modifier
                    .then(
                        if (counted) {
                            Modifier.clickable(
                                onClickLabel = stringResource(R.string.trips_row_press),
                                onClick = {
                                    openedByTap = !pressed
                                    context.onToggle(trip.id)
                                },
                            )
                        } else {
                            // Not something to press, so not offered as one: as a button that
                            // is switched off, a screen reader called a deleted trip
                            // "disabled", right above a Restore that works. Still read as
                            // one item, like a row that can be pressed.
                            Modifier.semantics(mergeDescendants = true) {}
                        },
                    )
                    // Inside what is pressed, so that a row is pressed over its whole height:
                    // with its two lines that is more than Android's smallest target.
                    .padding(vertical = spacing.rowGap),
            counted = counted && trip.category == TripCategory.BUSINESS,
            figureSpoken = kilometres?.let { stringResource(R.string.distance_km, it) },
        ) {
            val times = trip.timesText(context.zone, locale, context.twentyFourHour)
            val places = trip.placesText()
            if (places == null) {
                // A discarded trip is never looked up, so its times are what names it.
                Text(text = times, style = MaterialTheme.typography.bodyLarge)
                GreyLine(joined(trip.notesRes().map { stringResource(it) }))
            } else {
                PlacesLine(placesLine(places))
                GreyLine(joined(listOf(times) + trip.notesRes().map { stringResource(it) }))
            }
        }
        if (buttons.isNotEmpty()) {
            if (pressed) {
                // The buttons come up under the row, which for the lowest row on the screen
                // is below the edge: the tap then looked as if it had done nothing. Only a
                // tap moves the list. Buttons that come back because the list was scrolled,
                // or the phone turned, stay where they are.
                LaunchedEffect(Unit) {
                    if (openedByTap) {
                        openedByTap = false
                        // The buttons are laid out in the frame that has just begun. One
                        // frame on, the row has its new height, and the list can tell how
                        // far to move.
                        withFrameNanos { }
                        rowAndButtons.bringIntoView()
                    }
                }
            }
            // The row above keeps 12 dp under its words, which is the design's gap to the
            // buttons. Under them the design has 14 dp to the divider; under the day's last
            // row there is the tile's own edge, as far off as from a row's words.
            ActionButtonRow(
                actions = buttons,
                modifier =
                    Modifier.padding(
                        bottom = if (last) spacing.rowGap else spacing.controlPadding,
                    ),
            )
        }
    }
}

/** The button for one thing that can be done with [trip], as the row carries it out. */
@Composable
private fun TripAction.asButton(trip: TripLine, context: TripRowContext): ActionButton =
    ActionButton(
        label = stringResource(wordsRes()),
        kind =
            when (this) {
                TripAction.Edit -> ActionKind.ACCENT

                is TripAction.Mark -> ActionKind.PLAIN

                is TripAction.Correct ->
                    if (correction == TripCorrection.DELETE) {
                        ActionKind.DANGER
                    } else {
                        ActionKind.PLAIN
                    }
            },
        onClick =
            when (this) {
                TripAction.Edit -> ({ context.onEdit(trip) })
                is TripAction.Mark -> ({ context.onMark(trip, category) })
                is TripAction.Correct -> ({ context.onAsk(trip, correction) })
            },
        spokenName = stringResource(spokenRes()),
    )

/**
 * "5:30 – 5:41 PM", as a row and the question before a delete both say it. The half of the
 * day is said once where both times share it, as the design writes a trip's times.
 */
@Composable
internal fun TripLine.timesText(zone: ZoneId, locale: Locale, twentyFourHour: Boolean): String {
    // Every listed trip has ended; the start alone is the fallback for a row that storage
    // should never produce.
    val endedAt =
        endedAtMs ?: return formatTimeOfDay(startedAtMs, zone, locale, twentyFourHour)
    val (start, end) = formatTimeSpan(startedAtMs, endedAt, zone, locale, twentyFourHour)
    return stringResource(R.string.trips_time_range, start, end)
}

/** "12.4 km", or null when the distance is not known. */
@Composable
internal fun TripLine.kilometres(locale: Locale): String? =
    distanceMetres?.let { stringResource(R.string.distance_km, formatKilometres(it, locale)) }

/** The parts of a grey line, each after a middle dot: "5:30 – 5:41 PM · Personal". */
@Composable
internal fun joined(parts: List<String>): String =
    parts.joinToString(separator = stringResource(R.string.trips_note_separator))

/**
 * Where a trip went: "from → to", or in plain words that an address is still being looked up
 * or that none was found. Never coordinates, and never a gap. The words that stand in for a
 * missing address are already set apart in [places].
 */
@Composable
internal fun PlacesLine(places: AnnotatedString) {
    Text(text = places, style = MaterialTheme.typography.bodyLarge)
}

/** The quieter line under it: a trip's times and its notes. */
@Composable
internal fun GreyLine(text: String) {
    Text(
        text = text,
        style = MiloTheme.textStyles.tileLabel,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

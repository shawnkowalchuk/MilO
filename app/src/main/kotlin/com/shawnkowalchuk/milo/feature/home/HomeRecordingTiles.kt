package com.shawnkowalchuk.milo.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.component.DotWord
import com.shawnkowalchuk.milo.core.designsystem.component.FigureSize
import com.shawnkowalchuk.milo.core.designsystem.component.FigureText
import com.shawnkowalchuk.milo.core.designsystem.component.FlipTile
import com.shawnkowalchuk.milo.core.designsystem.component.HeroIconButton
import com.shawnkowalchuk.milo.core.designsystem.component.MiloIcons
import com.shawnkowalchuk.milo.core.designsystem.component.Pill
import com.shawnkowalchuk.milo.core.designsystem.component.Tile
import com.shawnkowalchuk.milo.core.designsystem.component.TileKind
import com.shawnkowalchuk.milo.core.designsystem.component.TileLabel
import com.shawnkowalchuk.milo.core.designsystem.component.TilePadding
import com.shawnkowalchuk.milo.core.designsystem.component.TilePair
import com.shawnkowalchuk.milo.core.designsystem.component.TilePress
import com.shawnkowalchuk.milo.core.designsystem.component.TruckLinkLook
import com.shawnkowalchuk.milo.core.designsystem.text.distanceSpokenRes
import com.shawnkowalchuk.milo.core.designsystem.text.unitShortRes
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme
import com.shawnkowalchuk.milo.core.util.formatDistance
import com.shawnkowalchuk.milo.core.util.formatTimeOfDay
import com.shawnkowalchuk.milo.platform.trip.CurrentTrip
import java.time.YearMonth
import kotlin.math.max

// Home while a trip is being recorded, tile by tile. Since 2026-10-10 the first tile is the
// low one the owner chose from three drawings ("Slim"), and today's card has a second side.

@Composable
internal fun RecordingTiles(
    ui: HomeUi,
    trip: CurrentTrip,
    actions: HomeActions,
    format: HomeFormat,
) {
    // Which side of today's card is up (`HomeCardSide.kt`). Kept through a turn of the phone.
    var monthChosenIn by rememberSaveable { mutableStateOf<Long?>(null) }
    RecordingTile(ui, trip, format, actions.onEnd)
    TilePair(
        first = { half -> SmallTruckTile(ui.truck.state, half) },
        second = { half ->
            TodayOrMonthTile(
                ui = ui,
                side = cardSide(monthChosenIn, trip.tripId),
                format = format,
                onTurn = { monthChosenIn = afterPress(monthChosenIn, trip.tripId) },
                modifier = half,
            )
        },
    )
    ui.odometers?.let { OdometerTile(it, format) }
    TodayListTile(ui.figures, format, actions.onOpenTrips, recording = true)
}

/**
 * The trip being recorded, in two rows (Shawn, 2026-10-10: "can we reduce the size of the
 * start trip green button at the top"; of three drawings he chose the low one, "but with the
 * square stop no text on that button. but also the text for minute for trip under the since
 * time"). First "Recording", with when the trip started and how long it has run at the end of
 * the line; then its kilometres so far and the button that ends it.
 *
 * While the trip waits for the truck to reconnect, the sentence that says so stands under the
 * two rows, so that the button does not move when it appears.
 *
 * It does not say Business or Personal: a trip is sorted by the work hours when it closes, and
 * until then MilO has not decided. Where it started is no longer written here; the Trips
 * screen's tile for the trip in progress says it.
 */
@Composable
private fun RecordingTile(ui: HomeUi, trip: CurrentTrip, format: HomeFormat, onEnd: () -> Unit) {
    val spacing = MiloTheme.spacing
    Tile(
        modifier = Modifier.fillMaxWidth(),
        kind = TileKind.ACCENT,
        padding = TilePadding.ROOMY,
        gap = spacing.tileGap,
    ) {
        PillAndTimes(
            since =
                stringResource(
                    R.string.home_recording_since,
                    formatTimeOfDay(
                        trip.startedAtMs,
                        format.zone,
                        format.locale,
                        format.twentyFourHour,
                    ),
                ),
            running = durationText(ui.nowMs - trip.startedAtMs),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.rowGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val soFar = formatDistance(trip.distanceMetres, format.unit, format.locale)
            FigureText(
                figure = soFar,
                unit = stringResource(unitShortRes(format.unit)),
                modifier = Modifier.weight(1f),
                size = FigureSize.TOTAL,
                spoken = stringResource(distanceSpokenRes(format.unit), soFar),
            )
            // The square of "stop" and no words. A screen reader is told what it does.
            HeroIconButton(
                icon = MiloIcons.Stop,
                description = stringResource(R.string.home_end_trip),
                onClick = onEnd,
            )
        }
        // The grace period: the truck has gone, and the trip ends unless it comes back.
        recordingNoteRes(ui.truck.state)?.let { note ->
            Text(text = stringResource(note), style = MiloTheme.textStyles.sentence)
        }
    }
}

/**
 * The tile's first row: "Recording", and at the end of the line when the trip started, over how
 * long it has run. Where the two do not fit side by side (a large font), the times stand under
 * the pill, from the start of the line, rather than being cut off or squeezed.
 */
@Composable
private fun PillAndTimes(since: String, running: String) {
    val gap = MiloTheme.spacing.small
    Layout(
        content = {
            Pill(text = stringResource(R.string.home_recording), lit = true)
            Text(text = since, style = MiloTheme.textStyles.accentNote)
            Text(text = running, style = MaterialTheme.typography.labelMedium)
        },
        modifier = Modifier.fillMaxWidth(),
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val (pill, start, time) = measurables.map { it.measure(constraints.copy(minWidth = 0)) }
        val times = max(start.width, time.width)
        val timesHeight = start.height + time.height
        val below = pill.height + gap.roundToPx()
        if (pill.width + gap.roundToPx() + times <= width) {
            val height = max(pill.height, timesHeight)
            val top = (height - timesHeight) / 2
            layout(width, height) {
                pill.placeRelative(0, (height - pill.height) / 2)
                start.placeRelative(width - start.width, top)
                time.placeRelative(width - time.width, top + start.height)
            }
        } else {
            layout(width, below + timesHeight) {
                pill.placeRelative(0, 0)
                start.placeRelative(0, below)
                time.placeRelative(0, below + start.height)
            }
        }
    }
}

/** The truck's connection in one word, beside today's figure. */
@Composable
private fun SmallTruckTile(state: TruckState, modifier: Modifier) {
    Tile(modifier = modifier, padding = TilePadding.EVEN, gap = MiloTheme.spacing.small) {
        TileLabel(stringResource(R.string.home_truck_corner), icon = MiloIcons.Bluetooth)
        DotWord(text = stringResource(state.title), on = state.look == TruckLinkLook.CONNECTED)
    }
}

/**
 * Today's Business kilometres, and on the card's other side the month's (Shawn, 2026-10-10:
 * "the one card that shows months mileage changes to today business mileage. can we have it
 * when you click it it toggles between days and month"). The whole card is the button. The
 * month's side holds what the month's tile holds with no trip open, without its thin line.
 *
 * @param side the side that is up.
 * @param onTurn a press on the card.
 */
@Composable
private fun TodayOrMonthTile(
    ui: HomeUi,
    side: CardSide,
    format: HomeFormat,
    onTurn: () -> Unit,
    modifier: Modifier,
) {
    val press =
        when (side) {
            CardSide.TODAY -> R.string.home_card_show_month
            CardSide.MONTH -> R.string.home_card_show_today
        }
    FlipTile(
        secondUp = side == CardSide.MONTH,
        press = TilePress(stringResource(press), onTurn),
        modifier = modifier,
        first = { TodayLines(ui.figures, FigureSize.MEDIUM, format) },
        second = {
            MonthLines(
                month = YearMonth.from(ui.date),
                figures = ui.figures?.month,
                centsPerKm = ui.centsPerKm,
                format = format,
                bar = false,
            )
        },
    )
}

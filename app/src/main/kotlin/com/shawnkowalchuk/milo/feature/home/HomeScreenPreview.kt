package com.shawnkowalchuk.milo.feature.home

import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.shawnkowalchuk.milo.core.designsystem.text.PlaceSide
import com.shawnkowalchuk.milo.core.designsystem.text.PlacesText
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme
import com.shawnkowalchuk.milo.core.schedule.TripCategory
import com.shawnkowalchuk.milo.core.trip.TripStartCause
import com.shawnkowalchuk.milo.data.trip.TodaySession
import com.shawnkowalchuk.milo.data.trip.TodayTrips
import com.shawnkowalchuk.milo.platform.system.PreflightProblem
import com.shawnkowalchuk.milo.platform.trip.CurrentTrip
import com.shawnkowalchuk.milo.platform.trip.ParkedTruckWatch
import com.shawnkowalchuk.milo.platform.trip.StartFailure
import com.shawnkowalchuk.milo.platform.trip.TripActivity
import java.time.LocalDate
import java.time.YearMonth
import kotlin.time.TimeSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

// The home screen as Android Studio draws it: the two layouts of the owner's design, every
// state of the truck, and the truck's arrival, which plays in the interactive preview. In a
// file of its own so that the screen's file stays within the size limit
// (ENGINEERING_STANDARDS section 3).
//
// An emulator has no truck, so these previews are also how the truck's tile is looked at on
// one: the debug build can show a preview by its name ("Run preview" in Android Studio).

// Sample values are written inline because a preview is never shown to a user or shipped. They
// are the design's own: a Tuesday in October with three Business trips behind it.
private const val MORNING = 1_791_028_800_000
private const val MINUTE = 60_000L

private fun sampleTrip(id: Long, from: String, to: String, startMinute: Long, metres: Double) =
    HomeTrip(
        tripId = id,
        places = PlacesText.FromTo(PlaceSide.Address(from), PlaceSide.Address(to)),
        startedAtMs = MORNING + startMinute * MINUTE,
        endedAtMs = MORNING + (startMinute + 25) * MINUTE,
        distanceMetres = metres,
        category = TripCategory.BUSINESS,
        ranPastSchedule = false,
    )

private val sampleRows =
    listOf(
        sampleTrip(3, "Supplier", "Shop", 300, 20_300.0),
        sampleTrip(2, "Windermere", "Supplier", 150, 9_300.0),
        sampleTrip(1, "Shop", "Windermere site", 0, 18_600.0),
    )

private val sampleFigures =
    HomeTrips(
        date = LocalDate.of(2026, 10, 6),
        today =
            TodayTrips(
                sampleRows.map {
                    TodaySession(
                        tripId = it.tripId,
                        startedAtMs = it.startedAtMs,
                        endedAtMs = it.endedAtMs,
                        distanceMetres = it.distanceMetres,
                        category = it.category,
                    )
                },
            ),
        rows = sampleRows,
        month = MonthFigures(YearMonth.of(2026, 10), businessTenths = 2_140, businessPercent = 89),
    )

private val sampleIdle =
    HomeUi(
        date = sampleFigures.date,
        activity = TripActivity(truckConnected = false),
        setupNeedsAttention = false,
        figures = sampleFigures,
        truck = TruckTileState(TruckState.NOT_CONNECTED, truckName = null),
        reportWaiting = YearMonth.of(2026, 9),
        startAddress = null,
        nowMs = MORNING + 400 * MINUTE,
    )

private val sampleTrip =
    CurrentTrip(
        tripId = 4,
        startedAtMs = MORNING + 382 * MINUTE,
        startedBy = TripStartCause.TRUCK,
        distanceMetres = 12_400.0,
        waitingForTruck = false,
    )

private val sampleRecording =
    sampleIdle.copy(
        activity = TripActivity(sampleTrip, truckConnected = true),
        truck = TruckTileState(TruckState.CONNECTED_RECORDING, truckName = null),
        startAddress = "Shop, 63 Ave NW",
    )

private val noActions = HomeActions({}, {}, {}, {}, {}, {}, {})

/**
 * Home as a preview draws it. Android Studio's own picture has no system bars; shown on a
 * device, the preview stands clear of them, as the screen does inside the app.
 */
@Composable
private fun HomePreview(shown: HomeShown) {
    MiloTheme { Surface { HomeContent(shown, noActions, Modifier.safeDrawingPadding()) } }
}

@Preview
@Composable
private fun HomeIdlePreview() {
    HomePreview(HomeShown(sampleIdle))
}

@Preview
@Composable
private fun HomeRecordingPreview() {
    val named = TruckTileState(TruckState.CONNECTED_RECORDING, "F-150")
    HomePreview(HomeShown(sampleRecording.copy(truck = named)))
}

/** What can stand in the way of a trip, the truck after End was pressed, and an empty day. */
@Preview
@Composable
private fun HomeTroublePreview() {
    val failure = StartFailure(listOf(PreflightProblem.BACKGROUND_LOCATION_MISSING))
    HomePreview(
        HomeShown(
            sampleIdle.copy(
                activity = TripActivity(startFailure = failure, truckConnected = true),
                setupNeedsAttention = true,
                figures = null,
                truck = TruckTileState(TruckState.CONNECTED_HELD_OFF, "F-150"),
                reportWaiting = null,
            ),
        ),
    )
}

private class TruckStates : PreviewParameterProvider<TruckState> {
    override val values = TruckState.entries.asSequence()
}

/** Home in each state of the truck, the two beside a parked truck among them. */
@Preview
@Composable
private fun HomeTruckStatePreview(@PreviewParameter(TruckStates::class) state: TruckState) {
    val recording =
        state in
            setOf(
                TruckState.CONNECTED_RECORDING,
                TruckState.RECORDING_WITHOUT_TRUCK,
                TruckState.WAITING_TO_RECONNECT,
            )
    val trip = sampleTrip.copy(waitingForTruck = state == TruckState.WAITING_TO_RECONNECT)
    val parked =
        when (state) {
            TruckState.PARKED_WAITING -> ParkedTruckWatch.WAITING_TO_MOVE
            TruckState.PARKED_NOT_WATCHED -> ParkedTruckWatch.NO_LONGER_WATCHED
            else -> null
        }
    val setupOpen = state == TruckState.NO_TRUCK || state == TruckState.NOT_CONNECTED_SETUP_OPEN
    HomePreview(
        HomeShown(
            sampleIdle.copy(
                activity = TripActivity(trip.takeIf { recording }, null, state.connected, parked),
                setupNeedsAttention = setupOpen,
                truck = TruckTileState(state, "F-150".takeIf { state != TruckState.NO_TRUCK }),
            ),
        ),
    )
}

/** The design's "Connecting…", held still in time so that its movement can be looked at. */
@Preview
@Composable
private fun HomeConnectingPreview() {
    HomePreview(HomeShown(sampleIdle, ArrivalStage.CONNECTING, TruckState.NOT_CONNECTED))
}

/** How long the script below shows Home before the truck arrives, and the trip after it. */
private const val SCRIPT_PAUSE_MS = 3_000L

/** The script's moment between the truck's connection and its trip, as on the phone. */
private const val SCRIPT_TRIP_OPENS_MS = 300L

/**
 * The truck arrives while Home is open, over and over: not connected, connected, a trip. What
 * is drawn of it is decided by the same [homeShown] the screen uses.
 */
private fun truckArrives(): Flow<HomeShown> {
    val started = TimeSource.Monotonic.markNow()
    val connected = TruckTileState(TruckState.CONNECTED_NOT_RECORDING, truckName = null)
    val seen =
        flow {
            while (true) {
                emit(sampleIdle)
                delay(SCRIPT_PAUSE_MS)
                emit(
                    sampleIdle.copy(
                        activity = TripActivity(truckConnected = true),
                        truck = connected,
                    ),
                )
                delay(SCRIPT_TRIP_OPENS_MS)
                emit(sampleRecording)
                delay(CONNECTING_MS + CONNECTED_MS + SCRIPT_PAUSE_MS)
            }
        }
    return homeShown(seen, onScreen = flowOf(true), startPresses = emptyFlow()) {
        started.elapsedNow().inWholeMilliseconds
    }
}

/** The whole arrival, as the design plays it. It moves in the interactive preview only. */
@Preview
@Composable
private fun HomeTruckArrivesPreview() {
    val shown by remember { truckArrives() }.collectAsState(HomeShown(sampleIdle))
    HomePreview(shown)
}

package com.shawnkowalchuk.milo.feature.home

import android.annotation.SuppressLint
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.component.AppHeader
import com.shawnkowalchuk.milo.core.designsystem.component.AttentionTile
import com.shawnkowalchuk.milo.core.designsystem.component.CameToFrontEffect
import com.shawnkowalchuk.milo.core.designsystem.component.RowStatus
import com.shawnkowalchuk.milo.core.designsystem.component.SectionCard
import com.shawnkowalchuk.milo.core.designsystem.component.StatusRow
import com.shawnkowalchuk.milo.core.designsystem.component.TileColumn
import com.shawnkowalchuk.milo.core.designsystem.component.rememberTwentyFourHourClock
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme
import com.shawnkowalchuk.milo.core.util.DistanceUnit
import com.shawnkowalchuk.milo.platform.system.PreflightProblem
import com.shawnkowalchuk.milo.platform.trip.StartFailure
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale
import kotlin.time.TimeSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

/** How long the trip that the truck's arrival started takes to fade in. */
private const val HAND_OVER_FADE_MS = 300

/** What the home screen can ask for. */
internal class HomeActions(
    val onOpenSetup: () -> Unit,
    val onOpenPairing: () -> Unit,
    val onOpenTrips: () -> Unit,
    val onOpenReport: (YearMonth) -> Unit,
    val onStart: () -> Unit,
    val onEnd: () -> Unit,
)

/**
 * How this phone writes a time and a number, and the unit a distance is written in.
 *
 * @param twentyFourHour whether the phone is set to write times with 24 hours.
 * @param unit the unit chosen in Settings, which the frame being drawn is in ([HomeUi.unit]).
 */
internal class HomeFormat(
    val locale: Locale,
    val zone: ZoneId,
    val twentyFourHour: Boolean,
    val unit: DistanceUnit,
)

/**
 * The home screen, laid out as the owner's design draws it: the top line every screen has
 * (the app's mark and today's date), then tiles. With no trip open: the accent tile that
 * starts one, today's and the month's Business kilometres side by side, the truck's
 * connection, last month's report while it has not been sent, and the last trip of today. While a trip is being recorded: the accent tile
 * with its kilometres and the button that ends it, the truck and today side by side, and
 * today's finished trips. A warning while the setup checklist needs attention, and why the
 * last start failed if it did, stand directly under the top line in both.
 *
 * When the truck arrives while the screen is open, the truck's tile plays the design's
 * "Connecting…" and "Connected" before the screen changes to the trip ([homeShown]).
 *
 * Navigation belongs to the app, not the feature, so each way out is a plain function.
 *
 * @param onOpenSetup the setup warning's button.
 * @param onOpenPairing the truck's tile, while no truck is paired.
 * @param onOpenTrips the tile of the last trip, and the list of today's trips.
 * @param onOpenReport the report tile's button, with the month the report is for.
 */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenSetup: () -> Unit,
    onOpenPairing: () -> Unit,
    onOpenTrips: () -> Unit,
    onOpenReport: (YearMonth) -> Unit,
    modifier: Modifier = Modifier,
) {
    // A press of Start is told to what is shown as well as to the trip controller, so that the
    // screen changes to the trip at once even in the middle of the truck's arrival.
    val startPresses = remember { MutableSharedFlow<Unit>(extraBufferCapacity = 1) }
    val shown by homeShownState(viewModel.ui, startPresses)

    // The warning follows the phone's settings, which change outside MilO without a word, and
    // "today" becomes another day while MilO sits in the background.
    CameToFrontEffect(viewModel::onCameToFront)

    HomeContent(
        shown = shown,
        actions =
            HomeActions(
                onOpenSetup = onOpenSetup,
                onOpenPairing = onOpenPairing,
                onOpenTrips = onOpenTrips,
                onOpenReport = onOpenReport,
                onStart = {
                    startPresses.tryEmit(Unit)
                    viewModel.onStartPressed()
                },
                onEnd = viewModel::onEndPressed,
            ),
        modifier = modifier,
    )
}

/**
 * What the screen draws, kept up from the view model's [ui] for as long as the screen is in
 * the composition. "On screen" is the screen being resumed: what the truck does while MilO is
 * in the background, or behind another app, is not played when MilO comes back.
 */
// Lint warns that reading a StateFlow's value while composing does not follow its changes. Here
// the value is only what the state starts from, so that the first frame is not empty; the flow
// itself is collected two lines further down, as `collectAsState` does it.
@SuppressLint("StateFlowValueCalledInComposition")
@Composable
private fun homeShownState(ui: StateFlow<HomeUi>, startPresses: Flow<Unit>): State<HomeShown> {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    return produceState(HomeShown(ui.value), ui, startPresses, lifecycle) {
        val started = TimeSource.Monotonic.markNow()
        val resumed = lifecycle.currentStateFlow.map { it.isAtLeast(Lifecycle.State.RESUMED) }
        homeShown(ui, resumed, startPresses) { started.elapsedNow().inWholeMilliseconds }
            .collect { value = it }
    }
}

@Composable
internal fun HomeContent(shown: HomeShown, actions: HomeActions, modifier: Modifier = Modifier) {
    val ui = shown.ui
    val locale = LocalConfiguration.current.locales[0]
    val format =
        HomeFormat(locale, ZoneId.systemDefault(), rememberTwentyFourHourClock(), ui.unit)
    TileColumn(
        modifier =
            modifier
                .fillMaxSize()
                // Large font settings or a small window must scroll rather than cut content off.
                .verticalScroll(rememberScrollState())
                .padding(vertical = MiloTheme.spacing.small),
    ) {
        AppHeader(
            title = stringResource(R.string.nav_home),
            // With the gap between two tiles, and the room the top line keeps free for a
            // finger, the design's 16 under the top line.
            modifier = Modifier.padding(bottom = MiloTheme.spacing.extraSmall),
        )

        // The two things that can stop a trip from being recorded come before everything else.
        if (ui.setupNeedsAttention) SetupWarningTile(actions.onOpenSetup)
        ui.activity.startFailure?.let { StartFailureTile(it) }

        TripOrNoTrip(shown, actions, format)
    }
}

/**
 * The tiles of the one layout or the other. A change between the two is made in one frame, as
 * it always was, with one exception: the trip that the truck's arrival started fades in over
 * the tiles that were showing, once the arrival has been played.
 */
@Composable
private fun TripOrNoTrip(shown: HomeShown, actions: HomeActions, format: HomeFormat) {
    val trip = shown.ui.activity.trip.takeIf { shown.recordingLayout }
    val layout = updateTransition(targetState = trip != null, label = "home layout")
    // How far the trip's tiles have come in: 0 before they show, 1 when they are there.
    val arrived =
        layout.animateFloat(
            transitionSpec = { if (targetState) tween(HAND_OVER_FADE_MS) else snap() },
            label = "trip fades in",
        ) { recording -> if (recording) 1f else 0f }
    val fading = shown.fadesToRecording && layout.targetState && !layout.currentState
    Box {
        // Each layout has its own place here, so the tiles that fade out are the ones that
        // were showing, with their movement, and not a second copy of them.
        if (trip == null || fading) {
            TileRows(alpha = { if (fading) 1f - arrived.value else 1f }) {
                IdleTiles(shown, actions, format)
            }
        }
        if (trip != null) {
            TileRows(alpha = { if (fading) arrived.value else 1f }) {
                RecordingTiles(shown.ui, trip, actions, format)
            }
        }
    }
}

/**
 * The tiles of one layout, one under the other.
 *
 * @param alpha how clearly they show. Read while drawing, so a fade composes nothing again.
 */
@Composable
private fun TileRows(alpha: () -> Float, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.graphicsLayer { this.alpha = alpha() },
        verticalArrangement = Arrangement.spacedBy(MiloTheme.spacing.tileGap),
        content = content,
    )
}

/**
 * Shown while a required row of the setup checklist is not in order, or no truck is paired: in
 * that state a trip may not start by itself. The checklist says what, so this tile only points
 * to it.
 */
@Composable
private fun SetupWarningTile(onOpenSetup: () -> Unit) {
    AttentionTile(
        title = stringResource(R.string.home_setup_warning_title),
        text = stringResource(R.string.home_setup_warning_text),
        actionLabel = stringResource(R.string.home_setup_warning_action),
        onAction = onOpenSetup,
    )
}

/**
 * Why recording could not start, one row per thing the preflight found. The same failure is
 * posted as a notification, but notifications may be switched off.
 */
@Composable
private fun StartFailureTile(failure: StartFailure) {
    SectionCard(title = stringResource(R.string.start_problem_title)) {
        if (failure.problems.isEmpty()) {
            StatusRow(
                label = stringResource(R.string.start_problem_refused),
                status = RowStatus.PROBLEM,
            )
        }
        for (problem in failure.problems) {
            StatusRow(label = stringResource(problem.textRes()), status = RowStatus.PROBLEM)
        }
    }
}

private fun PreflightProblem.textRes(): Int = when (this) {
    PreflightProblem.LOCATION_PERMISSION_MISSING -> R.string.start_problem_location_permission
    PreflightProblem.BACKGROUND_LOCATION_MISSING -> R.string.start_problem_background_location
    PreflightProblem.LOCATION_SWITCHED_OFF -> R.string.start_problem_location_off
    PreflightProblem.BACKGROUND_RESTRICTED -> R.string.start_problem_background_restricted
    PreflightProblem.BLUETOOTH_PERMISSION_MISSING -> R.string.start_problem_bluetooth_permission
}

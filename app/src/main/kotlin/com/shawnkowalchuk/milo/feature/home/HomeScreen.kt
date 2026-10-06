package com.shawnkowalchuk.milo.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.component.CameToFrontEffect
import com.shawnkowalchuk.milo.core.designsystem.component.PrimaryButton
import com.shawnkowalchuk.milo.core.designsystem.component.RowStatus
import com.shawnkowalchuk.milo.core.designsystem.component.ScreenTitle
import com.shawnkowalchuk.milo.core.designsystem.component.SectionCard
import com.shawnkowalchuk.milo.core.designsystem.component.StatusRow
import com.shawnkowalchuk.milo.core.designsystem.component.StatusRowAction
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme
import com.shawnkowalchuk.milo.core.trip.TripStartCause
import com.shawnkowalchuk.milo.core.util.formatKilometres
import com.shawnkowalchuk.milo.core.util.formatTimeOfDay
import com.shawnkowalchuk.milo.platform.system.PreflightProblem
import com.shawnkowalchuk.milo.platform.trip.CurrentTrip
import com.shawnkowalchuk.milo.platform.trip.StartFailure
import com.shawnkowalchuk.milo.platform.trip.TripActivity
import java.time.ZoneId

/**
 * The home screen as far as trip recording needs it: a warning while the setup checklist needs
 * attention, the trip in progress, why the last start failed if it did, and one button that
 * starts or ends a trip by hand. Today's sessions arrive in a later package.
 *
 * @param onOpenSetup the warning's button. Navigation belongs to the app, not the feature.
 */
@Composable
fun HomeScreen(viewModel: HomeViewModel, onOpenSetup: () -> Unit, modifier: Modifier = Modifier) {
    val activity by viewModel.activity.collectAsState()
    val setupNeedsAttention by viewModel.setupNeedsAttention.collectAsState()

    // The warning follows the phone's settings, which change outside MilO without a word.
    CameToFrontEffect(viewModel::onCameToFront)

    HomeContent(
        activity = activity,
        setupNeedsAttention = setupNeedsAttention,
        onOpenSetup = onOpenSetup,
        onStart = viewModel::onStartPressed,
        onEnd = viewModel::onEndPressed,
        modifier = modifier,
    )
}

@Composable
private fun HomeContent(
    activity: TripActivity,
    setupNeedsAttention: Boolean,
    onOpenSetup: () -> Unit,
    onStart: () -> Unit,
    onEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val trip = activity.trip
    Column(
        modifier =
            modifier
                .fillMaxSize()
                // Large font settings or a small window must scroll rather than cut content off.
                .verticalScroll(rememberScrollState())
                .padding(MiloTheme.spacing.medium),
        verticalArrangement = Arrangement.spacedBy(MiloTheme.spacing.medium),
    ) {
        ScreenTitle(text = stringResource(R.string.app_name))

        if (setupNeedsAttention) SetupWarningCard(onOpenSetup)

        SectionCard(title = stringResource(R.string.home_trip_title)) {
            if (trip == null) IdleTrip() else TripInProgress(trip)
        }

        activity.startFailure?.let { StartFailureCard(it) }

        // One button, because exactly one of the two actions makes sense at any moment.
        PrimaryButton(
            text = stringResource(
                if (trip ==
                    null
                ) {
                    R.string.home_start_trip
                } else {
                    R.string.home_end_trip
                },
            ),
            onClick = if (trip == null) onStart else onEnd,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun IdleTrip() {
    Text(
        text = stringResource(R.string.trip_status_idle),
        style = MaterialTheme.typography.bodyLarge,
    )
    Text(
        text = stringResource(R.string.home_trip_idle_detail),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun TripInProgress(trip: CurrentTrip) {
    val locale = LocalConfiguration.current.locales[0]
    Text(
        text = stringResource(R.string.distance_km, formatKilometres(trip.distanceMetres, locale)),
        style = MaterialTheme.typography.headlineSmall,
    )
    Text(
        text =
            stringResource(
                if (trip.waitingForTruck) {
                    R.string.trip_status_waiting_for_truck
                } else {
                    R.string.trip_status_in_progress
                },
            ),
        style = MaterialTheme.typography.bodyLarge,
    )
    Text(
        text =
            stringResource(
                R.string.trip_started_at,
                formatTimeOfDay(trip.startedAtMs, ZoneId.systemDefault(), locale),
            ),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * Shown while a required row of the setup checklist is not in order, or no truck is paired: in
 * that state a trip may not start by itself. The checklist says what, so this card only points
 * to it.
 */
@Composable
private fun SetupWarningCard(onOpenSetup: () -> Unit) {
    SectionCard(title = stringResource(R.string.home_setup_warning_title)) {
        StatusRow(
            label = stringResource(R.string.home_setup_warning_text),
            status = RowStatus.PROBLEM,
            action = StatusRowAction(
                stringResource(R.string.home_setup_warning_action),
                onOpenSetup,
            ),
        )
    }
}

/**
 * Why recording could not start, one row per thing the preflight found. The same failure is
 * posted as a notification, but notifications may be switched off.
 */
@Composable
private fun StartFailureCard(failure: StartFailure) {
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

// Sample values are written inline because a preview is never shown to a user or shipped.
@PreviewLightDark
@Composable
private fun HomeIdlePreview() {
    MiloTheme {
        Surface {
            HomeContent(
                activity =
                    TripActivity(
                        startFailure = StartFailure(
                            listOf(PreflightProblem.BACKGROUND_LOCATION_MISSING),
                        ),
                    ),
                setupNeedsAttention = true,
                onOpenSetup = {},
                onStart = {},
                onEnd = {},
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun HomeRecordingPreview() {
    MiloTheme {
        Surface {
            HomeContent(
                activity =
                    TripActivity(
                        trip =
                            CurrentTrip(
                                tripId = 1,
                                startedAtMs = 1_791_028_800_000,
                                startedBy = TripStartCause.MANUAL,
                                distanceMetres = 12_340.0,
                                waitingForTruck = false,
                            ),
                    ),
                setupNeedsAttention = false,
                onOpenSetup = {},
                onStart = {},
                onEnd = {},
            )
        }
    }
}

package com.shawnkowalchuk.milo.feature.tripedit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.component.ConfirmDialog
import com.shawnkowalchuk.milo.core.designsystem.component.SectionCard
import com.shawnkowalchuk.milo.core.designsystem.component.rememberTwentyFourHourClock
import com.shawnkowalchuk.milo.core.util.formatDay
import com.shawnkowalchuk.milo.core.util.formatKilometres
import com.shawnkowalchuk.milo.core.util.formatTimeOfDay
import com.shawnkowalchuk.milo.core.util.localDateOf
import com.shawnkowalchuk.milo.data.trip.RecordedValues

/**
 * What MilO recorded of a trip that was edited since, and the button that puts it back. The
 * button asks first: it drops what Shawn typed in place of the recorded figures.
 */
@Composable
internal fun RecordedCard(
    recorded: RecordedValues,
    state: TripEditUiState.Ready,
    onRestore: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val twentyFourHour = rememberTwentyFourHourClock()
    var asking by rememberSaveable { mutableStateOf(false) }
    val trip =
        stringResource(
            R.string.trip_edit_recorded_trip,
            formatDay(localDateOf(recorded.startedAtMs, state.zone), locale),
            stringResource(
                R.string.trips_time_range,
                formatTimeOfDay(recorded.startedAtMs, state.zone, locale, twentyFourHour),
                formatTimeOfDay(recorded.endedAtMs, state.zone, locale, twentyFourHour),
            ),
            stringResource(
                R.string.distance_km,
                formatKilometres(recorded.distanceMetres, locale),
            ),
        )

    SectionCard(title = stringResource(R.string.trip_edit_recorded_title)) {
        Text(text = trip, style = MaterialTheme.typography.bodyLarge)
        Quiet(stringResource(R.string.trip_edit_recorded_note))
        // The row takes the card's whole width, so that the button sits at its end.
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { asking = true }) {
                Text(text = stringResource(R.string.trip_edit_restore))
            }
        }
    }

    if (asking) {
        ConfirmDialog(
            title = stringResource(R.string.trip_edit_restore_title),
            text = stringResource(R.string.trip_edit_restore_text, trip),
            confirmLabel = stringResource(R.string.trip_edit_restore_confirm),
            dismissLabel = stringResource(R.string.action_cancel),
            onConfirm = {
                asking = false
                onRestore()
            },
            onDismiss = { asking = false },
        )
    }
}

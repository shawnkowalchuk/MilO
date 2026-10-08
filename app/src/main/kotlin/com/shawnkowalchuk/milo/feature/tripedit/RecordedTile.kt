package com.shawnkowalchuk.milo.feature.tripedit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.component.ConfirmDialog
import com.shawnkowalchuk.milo.core.designsystem.component.DashedTile
import com.shawnkowalchuk.milo.core.designsystem.component.rememberTwentyFourHourClock
import com.shawnkowalchuk.milo.core.designsystem.text.distanceRes
import com.shawnkowalchuk.milo.core.util.formatDay
import com.shawnkowalchuk.milo.core.util.formatDistance
import com.shawnkowalchuk.milo.core.util.formatShortDay
import com.shawnkowalchuk.milo.core.util.formatTimeOfDay
import com.shawnkowalchuk.milo.core.util.formatTimeSpan
import com.shawnkowalchuk.milo.core.util.localDateOf
import com.shawnkowalchuk.milo.data.trip.RecordedValues

/**
 * "What MilO recorded", for a trip that was edited since: its recorded times and distance in
 * the drawing's tile with a dashed outline, and the quiet button that puts them back. The
 * button asks first: it drops what Shawn typed in place of the recorded figures.
 *
 * The line names the recorded day only when the form shows another one, which it does once the
 * date was changed: otherwise the day stands on the date button above.
 */
@Composable
internal fun RecordedTile(
    recorded: RecordedValues,
    state: TripEditUiState.Ready,
    onRestore: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val twentyFourHour = rememberTwentyFourHourClock()
    var asking by rememberSaveable { mutableStateOf(false) }
    val zone = state.zone
    val day = localDateOf(recorded.startedAtMs, zone)
    val km =
        stringResource(
            distanceRes(state.unit),
            formatDistance(recorded.distanceMetres, state.unit, locale),
        )
    // The two times the short way, as a row of the Trips screen writes them.
    val (from, until) =
        formatTimeSpan(recorded.startedAtMs, recorded.endedAtMs, zone, locale, twentyFourHour)
    val times = stringResource(R.string.trips_time_range, from, until)

    DashedTile(
        title = stringResource(R.string.trip_edit_recorded_title),
        line =
            if (day == state.date) {
                stringResource(R.string.trip_edit_recorded_line, times, km)
            } else {
                stringResource(
                    R.string.trip_edit_recorded_line_with_day,
                    formatShortDay(day, locale),
                    times,
                    km,
                )
            },
        actionLabel = stringResource(R.string.trip_edit_restore_confirm),
        onAction = { asking = true },
    )

    if (asking) {
        // The question names what comes back in full: the day, both times and the distance.
        val trip =
            stringResource(
                R.string.trip_edit_recorded_trip,
                formatDay(day, locale),
                stringResource(
                    R.string.trips_time_range,
                    formatTimeOfDay(recorded.startedAtMs, zone, locale, twentyFourHour),
                    formatTimeOfDay(recorded.endedAtMs, zone, locale, twentyFourHour),
                ),
                km,
            )
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

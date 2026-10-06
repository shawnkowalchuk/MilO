package com.shawnkowalchuk.milo.feature.trips

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.component.ConfirmDialog
import java.time.ZoneId

/** The question before a trip is deleted. It names the trip and says how to get it back. */
@Composable
internal fun DeleteQuestion(
    trip: TripLine,
    zone: ZoneId,
    onDelete: () -> Unit,
    onKeep: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val times = trip.timesText(zone, locale)
    val kilometres = trip.kilometres(locale)
    ConfirmDialog(
        title = stringResource(R.string.trips_delete_title),
        text =
            stringResource(
                R.string.trips_delete_text,
                // A counted trip always has a distance; the times alone are the fallback.
                if (kilometres == null) {
                    times
                } else {
                    stringResource(R.string.trips_delete_trip, times, kilometres)
                },
                stringResource(R.string.trips_show_left_out),
            ),
        confirmLabel = stringResource(R.string.trips_delete_confirm),
        dismissLabel = stringResource(R.string.action_cancel),
        onConfirm = onDelete,
        onDismiss = onKeep,
    )
}

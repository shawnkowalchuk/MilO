package com.shawnkowalchuk.milo.core.designsystem.component

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme

/**
 * Asks for a time of day with Android's usual clock dial: the hour, then the minute, and two
 * buttons. Nothing is chosen until the confirming button is pressed; pressing outside the
 * dialog, or Back, is the same as [onDismiss].
 *
 * The dial is Material's own, in the theme's colours: the dialog is a tile, the dial is the
 * quiet fill of a control, and whatever is chosen (the hour or the minute, AM or PM, the number
 * on the dial) is the accent with dark figures. Its two buttons are the small buttons of a row,
 * both quiet, as in [ConfirmDialog].
 *
 * @param title what the time is for, such as "Monday: start".
 * @param hour the hour the dial opens on, 0 to 23, with [minute].
 * @param twelveHourClock true shows the dial with AM and PM, false with 24 hours. The screen
 * passes whatever its own times are written in, so the dial and the screen agree.
 * @param onConfirm the chosen time, as an hour from 0 to 23 and a minute.
 */
// Material 3's time picker is still marked experimental in the release the Compose BOM pins
// (1.4.0), and Material has no other. The opt-in is kept to this one component, so that a change
// to the picker in a later release is one file's repair.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeDialog(
    title: String,
    hour: Int,
    minute: Int,
    twelveHourClock: Boolean,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: (hour: Int, minute: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val state =
        rememberTimePickerState(
            initialHour = hour,
            initialMinute = minute,
            is24Hour = !twelveHourClock,
        )
    // Material draws its other dialogs as a tile by themselves. This one it draws on the scheme's
    // "surface", which in MilO is the page, and the colour the dialog can be handed does not
    // change that in the pinned release (seen on an emulator, 2026-10-06). So, for this dialog
    // alone, "surface" is the tile. The copy is remembered: a new scheme each time this is drawn
    // would count as a changed theme and draw everything in the dialog again.
    val scheme = MaterialTheme.colorScheme
    val dialogScheme = remember(scheme) { scheme.copy(surface = scheme.surfaceContainerHigh) }
    MaterialTheme(colorScheme = dialogScheme) {
        TimePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                RowButton(text = confirmLabel, onClick = { onConfirm(state.hour, state.minute) })
            },
            title = {
                // Material gives this title neither a style nor room under it. It is set like
                // the calendar's own "Select date", so that the two dialogs open alike.
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = MiloTheme.spacing.rowGap),
                )
            },
            dismissButton = {
                // Material sets this dialog's two buttons side by side with nothing between
                // them, which suits its own text buttons and makes two filled ones touch. The
                // gap is the one Material itself leaves in the other two dialogs.
                RowButton(
                    text = dismissLabel,
                    onClick = onDismiss,
                    modifier = Modifier.padding(end = MiloTheme.spacing.small),
                )
            },
        ) {
            TimePicker(
                state = state,
                colors =
                    TimePickerDefaults.colors(
                        // Material would draw the chosen one of AM and PM in its third colour,
                        // which in this theme is the amber of a tile that asks for attention.
                        // Whatever is chosen is the accent in this design, here as in the hour
                        // beside it.
                        periodSelectorSelectedContainerColor = MaterialTheme.colorScheme.primary,
                        periodSelectorSelectedContentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
            )
        }
    }
}

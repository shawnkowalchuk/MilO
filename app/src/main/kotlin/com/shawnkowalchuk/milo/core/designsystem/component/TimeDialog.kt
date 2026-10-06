package com.shawnkowalchuk.milo.core.designsystem.component

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable

/**
 * Asks for a time of day with Android's usual clock dial: the hour, then the minute, and two
 * buttons. Nothing is chosen until the confirming button is pressed; pressing outside the
 * dialog, or Back, is the same as [onDismiss].
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
    TimePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour, state.minute) }) {
                Text(text = confirmLabel)
            }
        },
        title = { Text(text = title) },
        dismissButton = { TextButton(onClick = onDismiss) { Text(text = dismissLabel) } },
    ) {
        TimePicker(state = state)
    }
}

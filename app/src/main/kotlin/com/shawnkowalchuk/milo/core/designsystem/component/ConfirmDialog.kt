package com.shawnkowalchuk.milo.core.designsystem.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/**
 * The question asked before something is taken away, such as a trip from the list: what is
 * about to happen, a button that does it and a button that does not.
 *
 * Pressing outside the dialog, or Back, is the same as [onDismiss]: nothing happens.
 *
 * @param text what the action does and, where it can be, how it is undone.
 * @param confirmLabel names the action itself ("Delete"), never "OK" or "Yes".
 * @param onPutOff what a press outside the dialog, or Back, does, where that is not the same
 * as the button that does not do it: a question whose "no" is an answer too ("Not sent") is
 * only put off by a stray tap, and asked again.
 */
@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onPutOff: () -> Unit = onDismiss,
) {
    AlertDialog(
        onDismissRequest = onPutOff,
        title = { Text(text = title) },
        text = { Text(text = text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(text = confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(text = dismissLabel) } },
    )
}

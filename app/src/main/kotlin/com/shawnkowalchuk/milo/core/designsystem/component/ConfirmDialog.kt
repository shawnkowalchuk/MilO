package com.shawnkowalchuk.milo.core.designsystem.component

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme

/**
 * The question asked before something is taken away, such as a trip from the list: what is
 * about to happen, a button that does it and a button that does not.
 *
 * Pressing outside the dialog, or Back, is the same as [onDismiss]: nothing happens.
 *
 * The text scrolls when the dialog cannot show all of it (a long question, the phone on its
 * side, large type). The dialog cuts off what does not fit, and the end of a question is
 * where it says what is kept and how the action is undone.
 *
 * The design the owner drew has no dialog in it, so this one is made of the design's parts: it
 * is a tile, its title is set like the title of a screen opened from another, and its two
 * buttons are the small buttons of a row. Both buttons are the same quiet colour on purpose.
 * The dialog cannot know whether its action takes something away, and an accent on "Delete"
 * would invite the press this question is here to slow down.
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
        title = { Text(text = title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Text(
                text = text,
                style = MiloTheme.textStyles.sentence,
                modifier = Modifier.verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = { RowButton(text = confirmLabel, onClick = onConfirm) },
        dismissButton = { RowButton(text = dismissLabel, onClick = onDismiss) },
        // Material would set the text in the grey of secondary text. It is what the question
        // is about, so it is set in the main text colour.
        textContentColor = MaterialTheme.colorScheme.onSurface,
    )
}

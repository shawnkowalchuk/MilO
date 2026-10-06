package com.shawnkowalchuk.milo.core.designsystem.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType

/**
 * One line of text to type in, under a label that stays visible while it is filled: an address,
 * or a distance.
 *
 * **The field keeps what is typed itself.** [initialText] is only where it starts, and every
 * change is reported through [onTextChange]. A text field has to show a keystroke in the frame
 * it is typed in. Text that travels to a ViewModel and comes back through a flow arrives a
 * frame later, and the cursor jumps or a letter is lost. So the screen is told about the text,
 * and never told to show it.
 *
 * @param maxLength typing stops at this many characters, so a field never holds more than what
 * is stored of it.
 * @param decimalNumber true brings up the keyboard with digits and a decimal separator, false
 * the one with letters, starting each word with a capital as addresses are written.
 * @param lastField true if no field follows on the screen: the keyboard's action key then puts
 * the keyboard away where it otherwise moves on to the next field.
 * @param error what is wrong with what the field holds, or null while nothing is. It is said
 * in red directly under the field, the field is outlined in red, and a screen reader is told
 * that the field is in error: what is wrong is said where it is put right.
 */
@Composable
fun TextEntry(
    label: String,
    initialText: String,
    onTextChange: (String) -> Unit,
    maxLength: Int,
    modifier: Modifier = Modifier,
    decimalNumber: Boolean = false,
    lastField: Boolean = false,
    error: String? = null,
) {
    var text by remember { mutableStateOf(initialText) }
    OutlinedTextField(
        value = text,
        onValueChange = { typed ->
            // One line: a pasted line break would otherwise be stored inside an address.
            val kept = typed.replace('\n', ' ').take(maxLength)
            text = kept
            onTextChange(kept)
        },
        modifier = modifier.fillMaxWidth(),
        label = { Text(text = label) },
        supportingText = error?.let { { Text(text = it) } },
        isError = error != null,
        singleLine = true,
        keyboardOptions =
            KeyboardOptions(
                capitalization =
                    if (decimalNumber) {
                        KeyboardCapitalization.None
                    } else {
                        KeyboardCapitalization.Words
                    },
                keyboardType = if (decimalNumber) KeyboardType.Decimal else KeyboardType.Text,
                imeAction = if (lastField) ImeAction.Done else ImeAction.Next,
            ),
    )
}

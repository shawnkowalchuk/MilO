package com.shawnkowalchuk.milo.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme

/** How high the design draws the field. Also Android's smallest target for a finger. */
private val FieldHeight = 48.dp

/** The hairline around the field, as drawn. */
private val Border = 1.dp

/** The border of the field that is being typed in, or that holds something wrong. */
private val StrongBorder = 2.dp

/**
 * One line of text to type in, under a label that stays visible while it is filled: an address,
 * or a distance.
 *
 * It is the design's field: the label small and grey above it, and the field itself a dark,
 * rounded well with a hairline around it. The hairline is a lighter grey than the design's, so
 * that an empty field can be found on its tile (see `fieldBorder`), and it turns into the accent
 * while the field is typed in. Material's own text field is not used because it puts the label
 * inside the field.
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
 * @param email true brings up the keyboard for an email address: with "@" on it, and without
 * the capitals and the corrections that would spoil one. Not together with [decimalNumber].
 * @param lastField true if no field follows on the screen: the keyboard's action key then puts
 * the keyboard away where it otherwise moves on to the next field.
 * @param error what is wrong with what the field holds, or null while nothing is. It is said
 * in red directly under the field, the field is outlined in red, and a screen reader is told
 * that the field is in error: what is wrong is said where it is put right.
 * @param placeholder grey words inside the field while it is empty, as the design draws them:
 * an example of what goes in ("name@example.com"). They go with the first letter typed. A
 * screen reader is not told them: the label says what the field is.
 * @param figure true for a field that is the one thing in a tile and holds a figure, as the
 * design draws the distance on the edit screen: what is typed is set large and firm, and the
 * label above it is the tile's own label, with the room a tile keeps under its label.
 */
@Composable
fun TextEntry(
    label: String,
    initialText: String,
    onTextChange: (String) -> Unit,
    maxLength: Int,
    modifier: Modifier = Modifier,
    decimalNumber: Boolean = false,
    email: Boolean = false,
    lastField: Boolean = false,
    error: String? = null,
    placeholder: String? = null,
    figure: Boolean = false,
) {
    var text by remember { mutableStateOf(initialText) }
    val interactions = remember { MutableInteractionSource() }
    val typedIn by interactions.collectIsFocusedAsState()
    val scheme = MaterialTheme.colorScheme
    val colors = MiloTheme.colors
    val shape = MiloTheme.shapes.control
    BasicTextField(
        value = text,
        onValueChange = { typed ->
            // One line: a pasted line break would otherwise be stored inside an address.
            val kept = typed.replace('\n', ' ').take(maxLength)
            text = kept
            onTextChange(kept)
        },
        // The field tells a screen reader what is wrong itself, so that it is said with the
        // field and not as a line somewhere after it. "this." is not decoration: without it,
        // and without the import above, the same words would call Kotlin's own error(), which
        // stops the app.
        modifier = modifier.fillMaxWidth().semantics { if (error != null) this.error(error) },
        textStyle =
            (if (figure) MiloTheme.textStyles.fieldFigure else MiloTheme.textStyles.fieldText)
                .copy(color = scheme.onSurface),
        keyboardOptions =
            KeyboardOptions(
                capitalization =
                    if (decimalNumber || email) {
                        KeyboardCapitalization.None
                    } else {
                        KeyboardCapitalization.Words
                    },
                // An address is typed letter for letter: a keyboard that "corrects" it to a
                // word it knows stores an address that does not exist. Every other field
                // leaves the choice to the keyboard, as before.
                autoCorrectEnabled = if (email) false else null,
                keyboardType =
                    when {
                        decimalNumber -> KeyboardType.Decimal
                        email -> KeyboardType.Email
                        else -> KeyboardType.Text
                    },
                imeAction = if (lastField) ImeAction.Done else ImeAction.Next,
            ),
        singleLine = true,
        interactionSource = interactions,
        cursorBrush = SolidColor(scheme.primary),
        // The label and the error line are drawn as parts of the field, not beside it. A
        // screen reader then reads the label with the field, and a tap on the label puts the
        // cursor in the field.
        decorationBox = { typedText ->
            val spacing = MiloTheme.spacing
            Column(
                verticalArrangement =
                    Arrangement.spacedBy(if (figure) spacing.small else spacing.extraSmall),
            ) {
                Text(
                    text = label,
                    style =
                        if (figure) {
                            MiloTheme.textStyles.tileLabel
                        } else {
                            MaterialTheme.typography.bodySmall
                        },
                    color = scheme.onSurfaceVariant,
                )
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = FieldHeight)
                            .background(colors.fieldFill, shape)
                            .border(
                                width = if (typedIn || error != null) StrongBorder else Border,
                                color =
                                    when {
                                        error != null -> scheme.error
                                        typedIn -> scheme.primary
                                        else -> colors.fieldBorder
                                    },
                                shape = shape,
                            ).padding(horizontal = MiloTheme.spacing.controlPadding),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (placeholder != null && text.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MiloTheme.textStyles.fieldText,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                            modifier = Modifier.clearAndSetSemantics {},
                        )
                    }
                    typedText()
                }
                if (error != null) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.error,
                        // Said once, by the field itself (above), not a second time here.
                        modifier = Modifier.clearAndSetSemantics {},
                    )
                }
            }
        },
    )
}

package com.shawnkowalchuk.milo.core.designsystem.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme

// Taller than Material's 40 dp button, as the design draws it. The main action on a screen
// (Start trip, Send report) is often pressed with the phone in a dashboard mount, where a small
// target is easy to miss.
private val MinHeight = 56.dp

/**
 * The one main action on a screen: the accent colour with dark words, the only large patch of
 * the accent a screen has. Use it at most once per screen; anything secondary is quieter, so
 * the main action stays obvious.
 *
 * The button is as wide as its text unless the caller passes a width through [modifier]. It
 * grows taller when its words need a second line.
 *
 * @param enabled false greys the button: the quiet fill of a control with the grey of secondary
 * text, which can still be read. A button that has to wait is greyed, never hidden.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = MinHeight),
        enabled = enabled,
        shape = MiloTheme.shapes.mainButton,
        colors =
            ButtonDefaults.buttonColors(
                disabledContainerColor = MiloTheme.colors.control.fill,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        contentPadding =
            PaddingValues(
                horizontal = MiloTheme.spacing.gutter,
                vertical = MiloTheme.spacing.small,
            ),
    ) {
        Text(
            text = text,
            style = MiloTheme.textStyles.mainButton,
            textAlign = TextAlign.Center,
        )
    }
}

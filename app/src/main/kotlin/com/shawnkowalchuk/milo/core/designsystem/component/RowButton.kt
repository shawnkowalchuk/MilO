package com.shawnkowalchuk.milo.core.designsystem.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme

/**
 * The design's small button: the one at the end of a status row, and the two under a dialog.
 *
 * It is Material's `Button` in the design's shape and colours. Material draws it 40 dp high, as
 * the design does, and keeps 48 dp for the finger around it; it grows when its words need more
 * than one line.
 *
 * @param accent true for the one button that puts a problem right: the accent with dark words.
 * Every other one is the quiet fill of a control on a tile.
 * @param enabled false greys the button while it has to wait: the quiet fill of a control with
 * the grey of secondary text, as on a main button that is switched off. It is never hidden.
 */
@Composable
internal fun RowButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    enabled: Boolean = true,
) {
    val control = MiloTheme.colors.control
    val greyed = MaterialTheme.colorScheme.onSurfaceVariant
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = MiloTheme.shapes.control,
        colors =
            if (accent) {
                ButtonDefaults.buttonColors(
                    disabledContainerColor = control.fill,
                    disabledContentColor = greyed,
                )
            } else {
                ButtonDefaults.buttonColors(
                    containerColor = control.fill,
                    contentColor = control.text,
                    disabledContainerColor = control.fill,
                    disabledContentColor = greyed,
                )
            },
        contentPadding =
            PaddingValues(
                horizontal = MiloTheme.spacing.controlPadding,
                vertical = MiloTheme.spacing.small,
            ),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
        )
    }
}

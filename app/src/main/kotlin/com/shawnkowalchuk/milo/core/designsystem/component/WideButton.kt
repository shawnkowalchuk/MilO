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

/** How high the design draws the button. It grows when its words need a second line. */
private val MinHeight = 48.dp

/**
 * The design's secondary button that stands on the page, under a screen's main button: the
 * colour of a tile with light words, as the design draws "Save PDF and CSV" and "Mark as
 * sent" on the Report screen. It is quieter than the main button on purpose.
 *
 * It is as wide as it is told through [modifier]. Two of them side by side each get half the
 * width from the row they stand in.
 *
 * @param enabled false greys the words while the button has to wait. It is never hidden.
 */
@Composable
fun WideButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = MinHeight),
        enabled = enabled,
        shape = MiloTheme.shapes.wideButton,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = scheme.surfaceContainer,
                contentColor = scheme.onSurface,
                disabledContainerColor = scheme.surfaceContainer,
                disabledContentColor = scheme.onSurfaceVariant,
            ),
        contentPadding =
            PaddingValues(
                horizontal = MiloTheme.spacing.small,
                vertical = MiloTheme.spacing.small,
            ),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
        )
    }
}

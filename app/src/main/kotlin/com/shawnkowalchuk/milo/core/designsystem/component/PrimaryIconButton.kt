package com.shawnkowalchuk.milo.core.designsystem.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme

/** How high the design draws the main button where it carries an icon. */
private val MinHeight = 58.dp

/** The icon before the words. */
private val IconSize = 18.dp

/**
 * The one main action of a screen, with a small icon before its words, as the design draws
 * "Email the report": the accent with dark words, 58 dp high. It is `PrimaryButton` with an
 * icon, and the same rule holds: at most one main button on a screen.
 *
 * @param icon drawn before the words, in their colour. The words say what the button does.
 * @param enabled false greys the button, as on `PrimaryButton`. It is never hidden.
 */
@Composable
fun PrimaryIconButton(
    text: String,
    icon: ImageVector,
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
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(IconSize))
        Spacer(Modifier.width(MiloTheme.spacing.tileGap))
        Text(
            text = text,
            style = MiloTheme.textStyles.mainButton,
            textAlign = TextAlign.Center,
        )
    }
}

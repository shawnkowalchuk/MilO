package com.shawnkowalchuk.milo.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme

/** How high the design draws the tile's button. */
private val ButtonHeight = 44.dp

/**
 * The amber tile for something that is waiting for the user, with the one button that deals
 * with it: a report that has not been sent, a setup that needs attention. A title, a quieter
 * line that says what is the matter, and the button at the end.
 *
 * It is amber and not red: it says "this is yours to do", not "something broke".
 *
 * @param text the quieter line. It may run over several lines; the button stays at the end.
 * @param actionLabel the button's words: what it does, or where it leads ("Send", "Open Setup").
 */
@Composable
fun AttentionTile(
    title: String,
    text: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MiloTheme.colors
    TileSurface(kind = TileKind.ATTENTION, press = null, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier.padding(
                    horizontal = MiloTheme.spacing.gutter,
                    vertical = MiloTheme.spacing.medium,
                ),
            horizontalArrangement = Arrangement.spacedBy(MiloTheme.spacing.rowGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(MiloTheme.spacing.textGap),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    // Lets a screen reader jump to what is waiting.
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = text,
                    style = MiloTheme.textStyles.tileLabel,
                    color = colors.attentionSecondaryText,
                )
            }
            Button(
                onClick = onAction,
                // Material keeps 48 dp for a finger around the lower button. The tile is as
                // high as the design draws it all the same: the 2 dp above and below reach into
                // the room the tile keeps free.
                modifier = Modifier.heightIn(min = ButtonHeight).takesUpOnly(ButtonHeight),
                shape = MiloTheme.shapes.pill,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = colors.attentionButton.fill,
                        contentColor = colors.attentionButton.text,
                    ),
                contentPadding =
                    PaddingValues(
                        horizontal = MiloTheme.spacing.gutter,
                        vertical = MiloTheme.spacing.small,
                    ),
            ) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

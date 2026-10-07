package com.shawnkowalchuk.milo.core.designsystem.component

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme

/**
 * A [Pill] that is a button: the dark patch on an accent tile that says a state and leads to
 * where that state is dealt with ("Not submitted", which opens the month's report).
 *
 * It is drawn as the pill is, 30 dp high, and takes up no more room than that. The place a
 * finger can hit is 48 dp high: it reaches 9 dp past the pill, into the tile's own padding.
 *
 * @param text the words on the pill: the state.
 * @param spokenName what a screen reader says the button is. The words alone say the state
 * and not where the press leads, so this says both.
 * @param pressLabel what a screen reader says the press does, after "double tap to".
 */
@Composable
fun PillButton(
    text: String,
    spokenName: String,
    pressLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val presses = remember { MutableInteractionSource() }
    Box(modifier = modifier) {
        // The ripple of a press takes the colour of the words around it. On an accent tile
        // those are dark, like the pill, so it is told the pill's own light words.
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            Box(
                modifier =
                    Modifier
                        .clip(MiloTheme.shapes.pill)
                        .indication(presses, LocalIndication.current)
                        // The button over it is what is read out.
                        .clearAndSetSemantics {},
            ) {
                Pill(text = text)
            }
        }
        PressArea(
            spokenName = spokenName,
            onClick = onClick,
            interactionSource = presses,
            pressLabel = pressLabel,
        )
    }
}

// Sample text is written inline because a preview is never shown to a user or shipped.
@Preview
@Composable
private fun PillButtonPreview() {
    MiloTheme {
        Surface {
            Tile(kind = TileKind.ACCENT, padding = TilePadding.ROOMY) {
                PillButton(
                    text = "Not submitted",
                    spokenName = "Not submitted. Report for the accountant",
                    pressLabel = "open the report",
                    onClick = {},
                )
            }
        }
    }
}

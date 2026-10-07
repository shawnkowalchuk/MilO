package com.shawnkowalchuk.milo.core.designsystem.component

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.shawnkowalchuk.milo.core.designsystem.theme.FillAndText
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme

/** How high the design draws the button. It grows when a large font needs more. */
private val DrawnHeight = 44.dp

/** Android's smallest target for a finger. */
private val FingerTarget = 48.dp

/**
 * One of two or three choices that are each a button, as the design draws "Business" and
 * "Personal" on the edit screen: the one in force is the accent with dark words, every other
 * one the quiet fill of a control on a tile.
 *
 * A `ChoiceRow` is for a choice that needs a sentence; this is for one that a word or two
 * says. The buttons of one choice stand under each other or side by side, 6 dp apart, in a
 * column or a row that carries `Modifier.selectableGroup()`, so that a screen reader announces
 * them as one group and each as chosen or not. None of them may be in force, while nothing is
 * chosen yet.
 *
 * It is drawn 44 dp high and takes up no more. The place a finger can hit is 48 dp high: it
 * reaches 2 dp past the button, above and below.
 */
@Composable
fun ChoiceButton(
    text: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val colors =
        if (selected) FillAndText(scheme.primary, scheme.onPrimary) else MiloTheme.colors.control
    val presses = remember { MutableInteractionSource() }
    Box(
        modifier =
            modifier
                .takesUpOnly(DrawnHeight)
                .heightIn(min = FingerTarget)
                .selectable(
                    selected = selected,
                    interactionSource = presses,
                    // Drawn by the button itself, below, so that it stops at its corners.
                    indication = null,
                    role = Role.RadioButton,
                    onClick = onSelect,
                ),
        contentAlignment = Alignment.Center,
    ) {
        // The ripple of a press takes the colour of the button's own words.
        CompositionLocalProvider(LocalContentColor provides colors.text) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(MiloTheme.shapes.segment)
                        .background(colors.fill)
                        .indication(presses, LocalIndication.current)
                        .heightIn(min = DrawnHeight)
                        .padding(
                            horizontal = MiloTheme.spacing.small,
                            vertical = MiloTheme.spacing.extraSmall,
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.text,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

// Sample text is written inline because a preview is never shown to a user or shipped.
@Preview
@Composable
private fun ChoiceButtonPreview() {
    MiloTheme {
        Surface {
            Tile(padding = TilePadding.EVEN) {
                Column(modifier = Modifier.selectableGroup()) {
                    ChoiceButton(text = "Business", selected = true, onSelect = {})
                    ChoiceButton(text = "Personal", selected = false, onSelect = {})
                }
            }
        }
    }
}

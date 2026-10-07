package com.shawnkowalchuk.milo.core.designsystem.component

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.shawnkowalchuk.milo.core.designsystem.theme.FillAndText
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme

/** How high the design draws one of these buttons. It grows when a large font needs more. */
private val ButtonHeight = 44.dp

/** The design draws three side by side. A fourth starts a second line. */
private const val MOST_IN_A_LINE = 3

/** What one of the buttons under a row does, which decides its colours and nothing else. */
enum class ActionKind {
    /** The one that leads on: the accent with dark words ("Edit"). */
    ACCENT,

    /** One that changes something and is undone as easily: the quiet fill of a control. */
    PLAIN,

    /** One that takes something away: the design's dark red with light red words ("Delete"). */
    DANGER,
}

/**
 * One of the buttons of an [ActionButtonRow]. The words and the handler travel together, so a
 * button can never be shown without doing something.
 *
 * @param label the word on the button, as short as the design has it: "Edit", "Personal".
 * @param spokenName what a screen reader says the button is, where the short word alone would
 * not say enough: "Mark as Personal".
 */
@Immutable
data class ActionButton(
    val label: String,
    val kind: ActionKind,
    val onClick: () -> Unit,
    val spokenName: String = label,
)

/**
 * The buttons that come up under a row of a list, as the design draws them under a trip: side
 * by side, equally wide and equally high, 6 dp apart. One button is as wide as the row, two
 * are halves, three are thirds; more than three go on to a second line.
 *
 * Each is drawn 44 dp high and takes up no more. The place a finger can hit is 48 dp high: it
 * reaches 2 dp past the button, above and below, so what stands over and under the buttons has
 * to leave that free.
 */
@Composable
fun ActionButtonRow(actions: List<ActionButton>, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MiloTheme.spacing.buttonGap),
    ) {
        for (line in actions.chunked(MOST_IN_A_LINE)) {
            Row(
                // The line is as high as its highest button needs, and every button fills it.
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(MiloTheme.spacing.buttonGap),
            ) {
                for (action in line) {
                    OneButton(action, Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
    }
}

@Composable
private fun OneButton(action: ActionButton, modifier: Modifier) {
    val colors = action.kind.colors()
    val presses = remember { MutableInteractionSource() }
    // The drawn button is handed the box's own height, so that all of a line are equally high.
    Box(modifier = modifier, propagateMinConstraints = true) {
        // The ripple of a press takes the colour of the button's own words.
        CompositionLocalProvider(LocalContentColor provides colors.text) {
            Box(
                modifier =
                    Modifier
                        .clip(MiloTheme.shapes.control)
                        .background(colors.fill)
                        .indication(presses, LocalIndication.current)
                        .heightIn(min = ButtonHeight)
                        .padding(
                            horizontal = MiloTheme.spacing.small,
                            vertical = MiloTheme.spacing.extraSmall,
                        )
                        // The button over it is what is read out.
                        .clearAndSetSemantics {},
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = action.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.text,
                    textAlign = TextAlign.Center,
                )
            }
        }
        PressArea(
            spokenName = action.spokenName,
            onClick = action.onClick,
            interactionSource = presses,
        )
    }
}

@Composable
private fun ActionKind.colors(): FillAndText = when (this) {
    ActionKind.ACCENT ->
        FillAndText(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary)

    ActionKind.PLAIN -> MiloTheme.colors.control

    ActionKind.DANGER -> MiloTheme.colors.danger
}

// Sample text is written inline because a preview is never shown to a user or shipped.
@Preview
@Composable
private fun ActionButtonRowPreview() {
    MiloTheme {
        Surface {
            Tile {
                ActionButtonRow(
                    actions =
                        listOf(
                            ActionButton("Edit", ActionKind.ACCENT, {}),
                            ActionButton("Personal", ActionKind.PLAIN, {}),
                            ActionButton("Delete", ActionKind.DANGER, {}),
                        ),
                )
            }
        }
    }
}

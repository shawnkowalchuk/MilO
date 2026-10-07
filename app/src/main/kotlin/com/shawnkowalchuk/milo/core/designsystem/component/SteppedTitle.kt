package com.shawnkowalchuk.milo.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme

/**
 * Each square is drawn 44 dp and takes up 48, for the finger. Moved out by the 2 dp the last
 * one keeps free at its end, its drawn edge lines up with the tiles under it, as on `AppHeader`.
 */
private val ButtonOverhang = 2.dp

/**
 * The heading of a bottom-bar screen that is stepped through, as the design draws it on Trips:
 * the large title, and at the end of its line two square buttons, one step back and one step on
 * (the month before, the month after).
 *
 * A step that cannot be taken keeps its square, with the arrow in the design's idle grey, so
 * that nothing moves. A screen reader is told that the button is switched off.
 *
 * The two squares are drawn 6 dp apart, as in the design: each keeps 2 dp free around itself
 * for the finger, and 2 dp stand between them.
 *
 * @param previous the step back, with what a screen reader says for it.
 * @param next the step on.
 */
@Composable
fun SteppedTitle(
    text: String,
    previous: StepperButton,
    next: StepperButton,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MiloTheme.spacing.rowGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.headlineMedium,
            // Lets a screen reader announce where the user has landed. The weight pushes the
            // two buttons to the end of the line.
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        Row(
            modifier = Modifier.offset(x = ButtonOverhang),
            horizontalArrangement = Arrangement.spacedBy(MiloTheme.spacing.textGap),
        ) {
            StepSquare(MiloIcons.Back, previous)
            StepSquare(ChevronIcons.Forward, next)
        }
    }
}

@Composable
private fun StepSquare(icon: ImageVector, step: StepperButton) {
    SquareIconButton(
        icon = icon,
        description = step.description,
        onClick = step.onClick,
        enabled = step.enabled,
        greyedIcon = MiloTheme.colors.idleIcon,
    )
}

// Sample text is written inline because a preview is never shown to a user or shipped.
@Preview
@Composable
private fun SteppedTitlePreview() {
    MiloTheme {
        Surface {
            SteppedTitle(
                text = "Trips",
                previous = StepperButton("Previous month", {}),
                next = StepperButton("Next month", {}, enabled = false),
            )
        }
    }
}

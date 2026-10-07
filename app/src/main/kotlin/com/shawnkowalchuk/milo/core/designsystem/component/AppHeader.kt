package com.shawnkowalchuk.milo.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme

/** How large the design draws the app's mark. */
private val MarkSize = 36.dp

/**
 * The square button is drawn 44 dp and takes up 48, for the finger. Moved out by the 2 dp it
 * keeps free at its end, its drawn edge lines up with the tiles under it.
 */
private val ButtonOverhang = 2.dp

/**
 * The top of the app's first screen, as the design draws it on Home: the app's mark (its
 * initial on a square of the accent colour), its name with a quieter line under it, and one
 * square icon button at the end. The other screens start with `ScreenTitle`.
 *
 * @param mark the app's initial. It is decoration: a screen reader is read the name.
 * @param line the quieter line under the name, such as today's date.
 * @param action the button at the end: the way to a screen that has no place in the bottom bar.
 */
@Composable
fun AppHeader(
    mark: String,
    name: String,
    line: String,
    action: ScreenTitleAction,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MiloTheme.spacing.tileGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(MarkSize)
                    .background(scheme.primary, MiloTheme.shapes.appMark)
                    .clearAndSetSemantics {},
            contentAlignment = Alignment.Center,
        ) {
            Text(text = mark, style = MiloTheme.textStyles.markLetter, color = scheme.onPrimary)
        }
        // The weight pushes the button to the end of the line.
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MiloTheme.textStyles.appName,
                // Lets a screen reader announce where the user has landed.
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = line,
                style = MiloTheme.textStyles.tileLabel,
                color = scheme.onSurfaceVariant,
            )
        }
        SquareIconButton(
            icon = action.icon,
            description = action.description,
            onClick = action.onClick,
            modifier = Modifier.offset(x = ButtonOverhang),
        )
    }
}

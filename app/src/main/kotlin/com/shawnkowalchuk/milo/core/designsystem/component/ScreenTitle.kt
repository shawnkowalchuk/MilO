package com.shawnkowalchuk.milo.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme

/**
 * The square button is drawn 44 dp and takes up 48, for the finger. Moved out by the 2 dp it
 * keeps free at its end, its drawn edge lines up with the tiles under it.
 */
private val ActionOverhang = 2.dp

/**
 * An icon button at the end of a [ScreenTitle]: the way from a screen to one that is opened from
 * it and has no place in the bottom bar, or the one thing that is done with the screen's
 * content as a whole, where the design draws it there (sharing the log).
 *
 * @param description what a screen reader says for the icon, and so what the button does.
 * @param enabled false greys the icon and takes the press away while the button has to wait.
 */
@Immutable
data class ScreenTitleAction(
    val icon: ImageVector,
    val description: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
)

/**
 * The heading at the top of every screen, so all of them start the same way.
 *
 * The four screens of the bottom bar have the large title. A screen opened from another one has
 * a smaller title behind a square back button, as the design draws the two kinds, so the size
 * of the title alone says how deep in the app one is.
 *
 * @param onBack pass it on a screen that was opened from another one (the pairing screen, from
 * Setup; Settings, from Home): a back button is then shown before the title. The four screens of
 * the bottom bar have none, because the bar is how they are left.
 * @param action one square icon button at the end of the line, such as the way to Settings on
 * Home, or "Share the log" on the Log screen.
 */
@Composable
fun ScreenTitle(
    text: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    action: ScreenTitleAction? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MiloTheme.spacing.rowGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            SquareIconButton(
                icon = MiloIcons.Back,
                description = stringResource(R.string.navigate_back),
                onClick = onBack,
            )
        }
        Text(
            text = text,
            style =
                if (onBack == null) {
                    MaterialTheme.typography.headlineMedium
                } else {
                    MaterialTheme.typography.titleLarge
                },
            // Lets a screen reader announce where the user has landed. The weight pushes the
            // action, if there is one, to the end of the line.
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        if (action != null) {
            SquareIconButton(
                icon = action.icon,
                description = action.description,
                onClick = action.onClick,
                // As on Home's top line: the square's drawn edge lines up with the tiles.
                modifier = Modifier.offset(x = ActionOverhang),
                enabled = action.enabled,
            )
        }
    }
}

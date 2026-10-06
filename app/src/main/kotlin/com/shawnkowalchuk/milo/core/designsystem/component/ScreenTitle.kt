package com.shawnkowalchuk.milo.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme

/**
 * An icon button at the end of a [ScreenTitle]: the way from a screen to one that is opened from
 * it and has no place in the bottom bar.
 *
 * @param description what a screen reader says for the icon, and so what the button does.
 */
@Immutable
data class ScreenTitleAction(
    val icon: ImageVector,
    val description: String,
    val onClick: () -> Unit,
)

/**
 * The heading at the top of every screen, so all of them start the same way.
 *
 * @param onBack pass it on a screen that was opened from another one (the pairing screen, from
 * Setup; Settings, from Home): a back arrow is then shown before the title. The four screens of
 * the bottom bar have no arrow, because the bar is how they are left.
 * @param action one icon button at the end of the line, such as the way to Settings on Home.
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
        horizontalArrangement = Arrangement.spacedBy(MiloTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = MiloIcons.Back,
                    contentDescription = stringResource(R.string.navigate_back),
                )
            }
        }
        Text(
            text = text,
            style = MaterialTheme.typography.headlineSmall,
            // Lets a screen reader announce where the user has landed. The weight pushes the
            // action, if there is one, to the end of the line.
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        if (action != null) {
            IconButton(onClick = action.onClick) {
                Icon(imageVector = action.icon, contentDescription = action.description)
            }
        }
    }
}

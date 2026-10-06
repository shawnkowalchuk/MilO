package com.shawnkowalchuk.milo.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme

/**
 * The heading at the top of every screen, so all of them start the same way.
 *
 * @param onBack pass it on a screen that was opened from another one (the pairing screen, from
 * Setup): a back arrow is then shown before the title. The four screens of the bottom bar have
 * no arrow, because the bar is how they are left.
 */
@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier, onBack: (() -> Unit)? = null) {
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
            // Lets a screen reader announce where the user has landed.
            modifier = Modifier.semantics { heading() },
        )
    }
}

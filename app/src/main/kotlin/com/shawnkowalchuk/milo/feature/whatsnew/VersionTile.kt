package com.shawnkowalchuk.milo.feature.whatsnew

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.component.Tile
import com.shawnkowalchuk.milo.core.designsystem.component.TileLinkLabel
import com.shawnkowalchuk.milo.core.designsystem.component.TilePress
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme
import com.shawnkowalchuk.milo.platform.system.InstalledVersion

/**
 * The last tile of Settings (2026-10-08): the version of MilO on this phone, "0.1.0 · build 1",
 * and the way into the What's new screen, as GopherForms' Settings has "Version 1.4.2 →". It is
 * drawn as Settings' Setup tile is, the label with its arrowhead and the whole tile one button.
 * The app hands it to Settings, because it is this feature's: a feature never imports another.
 *
 * @param onOpen opens the What's new screen.
 */
@Composable
fun VersionTile(installed: InstalledVersion, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    Tile(
        modifier = modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        press = TilePress(
            label = stringResource(R.string.whats_new_version_open),
            onClick = onOpen,
        ),
        gap = MiloTheme.spacing.extraSmall,
    ) {
        TileLinkLabel(stringResource(R.string.whats_new_version_label))
        Text(
            text = stringResource(R.string.whats_new_version_value, installed.name, installed.code),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.whats_new_version_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

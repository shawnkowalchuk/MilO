package com.shawnkowalchuk.milo.feature.settings

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.component.RowStatus
import com.shawnkowalchuk.milo.core.designsystem.component.StatusRow
import com.shawnkowalchuk.milo.core.designsystem.component.Tile
import com.shawnkowalchuk.milo.core.designsystem.component.TileLinkLabel
import com.shawnkowalchuk.milo.core.designsystem.component.TilePress
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme

/** Shawn's page on Buy Me a Coffee (his request of 2026-10-08), the one the website links to. */
internal const val COFFEE_PAGE = "https://www.buymeacoffee.com/SeaWingman"

/**
 * The tile above Version that opens [COFFEE_PAGE] in the phone's browser (2026-10-08). It is
 * drawn as the Version tile is: the label with its arrowhead, and the whole tile one button.
 *
 * The page is opened, never shown inside MilO: the app needs no permission to use the internet
 * for it, and nothing about the phone or its trips goes with the press. A phone with no browser
 * says so on the tile, with the address to type in elsewhere.
 */
@Composable
internal fun CoffeeTile(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var noBrowser by rememberSaveable { mutableStateOf(false) }
    Tile(
        modifier = modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        press =
            TilePress(
                label = stringResource(R.string.settings_coffee_open),
                onClick = { noBrowser = !context.openPage(COFFEE_PAGE) },
            ),
        gap = MiloTheme.spacing.extraSmall,
    ) {
        TileLinkLabel(stringResource(R.string.settings_coffee_label))
        Text(
            text = stringResource(R.string.settings_coffee_value),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.settings_coffee_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (noBrowser) {
            StatusRow(
                label = stringResource(R.string.settings_coffee_no_browser),
                status = RowStatus.PROBLEM,
            )
        }
    }
}

/**
 * Opens [address] in the browser. False if the phone has nothing that opens a web page.
 *
 * Lint offers String.toUri() from core-ktx, a library MilO does not declare (it is only on the
 * classpath through other libraries), as `SystemScreens` says. One call does not justify it.
 */
@SuppressLint("UseKtx")
private fun Context.openPage(address: String): Boolean = try {
    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(address)))
    true
} catch (noBrowser: ActivityNotFoundException) {
    false
}

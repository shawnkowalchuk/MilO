package com.shawnkowalchuk.milo.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme

/**
 * A screen of tiles that scrolls above its main button, which stays in view at the bottom of the
 * screen however far the tiles are scrolled. **Added on 2026-10-09** for the first start (Shawn:
 * "pin the bottom where it says ... continue so it shows all the time"): the welcome page's OK
 * and Setup's "Done, go to Settings".
 *
 * The tiles are laid out as in [TileColumn] and scroll, with the gap the other screens keep at
 * their top; the button stands under them as wide as a tile, the gap between two tiles above it
 * and the same small gap under it. Nothing is drawn behind the button: the tiles end above it.
 *
 * The column is as high as [modifier] makes it, which should be the whole screen
 * (`fillMaxSize`), or the button is not at the bottom.
 *
 * @param button the main button, usually a [PrimaryButton] with `fillMaxWidth`.
 */
@Composable
fun PinnedButtonColumn(
    button: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val spacing = MiloTheme.spacing
    Column(modifier = modifier) {
        TileColumn(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    // A large font or a small window scrolls the tiles; the button stays.
                    .verticalScroll(rememberScrollState())
                    .padding(top = spacing.small),
            content = content,
        )
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.gutter)
                    .padding(top = spacing.tileGap, bottom = spacing.small),
        ) {
            button()
        }
    }
}

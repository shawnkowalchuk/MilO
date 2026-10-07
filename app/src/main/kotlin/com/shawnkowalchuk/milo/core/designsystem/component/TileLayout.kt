package com.shawnkowalchuk.milo.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme

/**
 * The column a screen of tiles is laid out in, as the design draws it: 18 dp from each side of
 * the screen, with 10 dp between two tiles.
 *
 * Whether it scrolls and how high it is are the screen's to say, through [modifier].
 */
@Composable
fun TileColumn(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier.padding(horizontal = MiloTheme.spacing.gutter),
        verticalArrangement = Arrangement.spacedBy(MiloTheme.spacing.tileGap),
        content = content,
    )
}

/**
 * Two tiles side by side, each half the width, 10 dp apart and equally high: the lower one is
 * stretched to the height of the other, as in the design.
 *
 * Each tile is handed the modifier that gives it its half and its height, and has to pass it
 * on to its `Tile`.
 */
@Composable
fun TilePair(
    first: @Composable (Modifier) -> Unit,
    second: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        // The row is as high as its higher tile needs, and both fill it.
        modifier = modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(MiloTheme.spacing.tileGap),
    ) {
        first(Modifier.weight(1f).fillMaxHeight())
        second(Modifier.weight(1f).fillMaxHeight())
    }
}

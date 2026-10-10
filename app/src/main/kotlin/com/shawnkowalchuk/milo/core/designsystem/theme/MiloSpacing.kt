package com.shawnkowalchuk.milo.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The spacing scale. Every gap and padding in the app is one of these steps, reached through
 * `MiloTheme.spacing`, so screens line up with each other without anyone measuring.
 *
 * The five steps named by size are the ones the screens were first built on. The steps named
 * after what they separate came with the "Bento" design, which spaces things by 2, 6, 10, 12,
 * 14 and 18 as well. Add a step here if a design truly needs one; do not write a dp value in a
 * screen.
 *
 * **Only Home is spaced as the design draws it.** Its tiles stand [gutter] (18) from the edge of
 * the screen with [tileGap] (10) between them, through `TileColumn` and `TilePair`. The other
 * screens still stand [medium] (16) from the edge, with [small] (8) or [medium] between two
 * tiles; they get the design's two with their own layouts. Below, each step is described by
 * what uses it today, and what the design also uses it for is said as that.
 *
 * @param textGap between a row's main line and the quieter line under it.
 * @param extraSmall between a label and the thing it names, and above and below the words of
 * a choice row, so that two such rows under each other stand two of these apart.
 * @param buttonGap between two buttons or two chips that stand side by side, and inside the
 * bottom bar at its two ends. In the design also between a tile's label and its figure.
 * @param small between things that belong together, and above and below the words of a button.
 * @param tileGap between the rows inside a tile, and on Home between two tiles.
 * @param rowGap between a row's dot or button and its text, and above a bar that floats.
 * @param controlPadding inside a small button or a text field, left and right of its words.
 * Also between the rows of a tile that the design gives more air (the tile of the trip being
 * recorded), and above and below what a low tile holds: the small tile, and since 2026-10-09
 * the truck's tile.
 * @param medium inside a tile, from its edge to its content. Today also from the edge of the
 * screen to the tiles.
 * @param gutter the 18 of the design: beside the bottom bar, left and right of the main button's
 * words, the wider padding of a tile (a dialog's buttons from its edge), and on Home from the
 * edge of the screen to the tiles.
 * @param large between groups that do not belong together. Nothing uses it today.
 * @param extraLarge the widest gap. Nothing uses it today.
 */
@Immutable
data class MiloSpacing(
    val textGap: Dp = 2.dp,
    val extraSmall: Dp = 4.dp,
    val buttonGap: Dp = 6.dp,
    val small: Dp = 8.dp,
    val tileGap: Dp = 10.dp,
    val rowGap: Dp = 12.dp,
    val controlPadding: Dp = 14.dp,
    val medium: Dp = 16.dp,
    val gutter: Dp = 18.dp,
    val large: Dp = 24.dp,
    val extraLarge: Dp = 32.dp,
)

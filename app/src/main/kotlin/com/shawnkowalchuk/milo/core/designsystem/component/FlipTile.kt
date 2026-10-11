package com.shawnkowalchuk.milo.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme

/** How long one side takes to give way to the other. Short: it is a figure, not a show. */
private const val TURN_MS = 150

/** The dots under the tile's content that say which side is up. */
private val SideDotSize = 6.dp

/**
 * A tile with two sides, of which one is up: the whole tile is a button, and a press turns it
 * over. Two small dots under what it holds say that there are two sides and which one is up:
 * that one's dot is the accent, the other's a grey that can still be seen. Added on 2026-10-10
 * for Home's card of today and the month.
 *
 * **It is always as high as its higher side,** so turning it moves nothing else on the screen.
 * The side that is down is still laid out, and is neither seen nor read: a screen reader is
 * read the side that is up, and what a press does. The change is a short fade, and none with
 * the phone's animations switched off.
 *
 * Which side is up is the screen's to keep and to say.
 *
 * @param secondUp whether the second side is up.
 * @param press what a press does, in the words a screen reader says after "double tap to":
 * "show the month" while the first side is up.
 * @param first the side that is up to begin with, laid out in a column like a `Tile`.
 */
@Composable
fun FlipTile(
    secondUp: Boolean,
    press: TilePress,
    modifier: Modifier = Modifier,
    first: @Composable ColumnScope.() -> Unit,
    second: @Composable ColumnScope.() -> Unit,
) {
    val turned by animateFloatAsState(
        targetValue = if (secondUp) 1f else 0f,
        animationSpec = tween(TURN_MS),
        label = "tile turns over",
    )
    Tile(
        modifier = modifier,
        padding = TilePadding.EVEN,
        press = press,
        gap = MiloTheme.spacing.small,
    ) {
        // In a pair the tile is as high as its neighbour: the sides take what room there is,
        // and the dots stay at the tile's foot.
        Box(modifier = Modifier.weight(1f)) {
            Side(up = !secondUp, alpha = { 1f - turned }, content = first)
            Side(up = secondUp, alpha = { turned }, content = second)
        }
        SideDots(
            lit = if (secondUp) 1 else 0,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}

/**
 * One side.
 *
 * @param alpha how clearly it shows. Read while drawing, so the fade composes nothing again.
 */
@Composable
private fun Side(up: Boolean, alpha: () -> Float, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier =
            Modifier
                .graphicsLayer { this.alpha = alpha() }
                .then(if (up) Modifier else Modifier.clearAndSetSemantics {}),
        verticalArrangement = Arrangement.spacedBy(MiloTheme.spacing.buttonGap),
        content = content,
    )
}

/** Two dots, the one of the side that is up lit. Decoration for a screen reader. */
@Composable
private fun SideDots(lit: Int, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier.clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(MiloTheme.spacing.buttonGap),
    ) {
        repeat(2) { side ->
            Box(
                modifier =
                    Modifier
                        .size(SideDotSize)
                        .background(
                            // The grey of an outline that has to be seen: 3 to 1 on a tile.
                            if (side == lit) scheme.primary else scheme.outline,
                            MiloTheme.shapes.pill,
                        ),
            )
        }
    }
}

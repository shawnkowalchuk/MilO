package com.shawnkowalchuk.milo.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme

/** Where a [TileRow] stands among the rows that make up one tile. */
enum class TileRowPlace {
    /** The tile's one row: all four corners are round. */
    ONLY,

    /** The top of the tile. */
    FIRST,

    /** Between two others: no round corner. */
    MIDDLE,

    /** The bottom of the tile. */
    LAST,
}

/**
 * The place of the row at [index] among [count] rows of one tile.
 *
 * @param index from 0, top to bottom.
 */
fun tileRowPlace(index: Int, count: Int): TileRowPlace {
    require(index in 0 until count) { "Row $index is not one of $count rows" }
    return when {
        count == 1 -> TileRowPlace.ONLY
        index == 0 -> TileRowPlace.FIRST
        index == count - 1 -> TileRowPlace.LAST
        else -> TileRowPlace.MIDDLE
    }
}

/**
 * How much room the design gives the rows of a tile.
 *
 * @param row above and below what each row holds.
 * @param end more room above the first row and below the last, inside the tile's round ends.
 */
enum class TileRowSpacing(internal val row: Dp, internal val end: Dp) {
    /** 12 for a row and 4 at the tile's ends: the rows of a checklist. */
    ROOMY(row = 12.dp, end = 4.dp),

    /** 11 for a row and 6 at the tile's ends: the lines of a long list, such as the event log. */
    DENSE(row = 11.dp, end = 6.dp),
}

/**
 * One row of a tile that is a list, as the design draws the checklist and the event log: the
 * rows stand directly under each other in one panel, with a hairline between two of them.
 *
 * **Each row draws its own slice of the panel.** Stacked with nothing between them, the slices
 * are one tile: the first has the tile's round corners at its top, the last at its bottom. A
 * long list can therefore be a lazy one, which lays out only the rows on screen; a `Tile`
 * around the whole list could not be. The rows of a short list go in a plain `Column`.
 *
 * The row is as wide as it is allowed to be and stands 16 dp in from the tile's sides.
 *
 * @param place where the row stands among the tile's rows ([tileRowPlace]).
 * @param press makes the whole row one button, from side to side of the tile.
 */
@Composable
fun TileRow(
    place: TileRowPlace,
    modifier: Modifier = Modifier,
    spacing: TileRowSpacing = TileRowSpacing.ROOMY,
    press: TilePress? = null,
    content: @Composable () -> Unit,
) {
    val isFirst = place == TileRowPlace.FIRST || place == TileRowPlace.ONLY
    val isLast = place == TileRowPlace.LAST || place == TileRowPlace.ONLY
    val sides = MiloTheme.spacing.medium
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = place.sliceOf(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier =
                if (press == null) {
                    Modifier
                } else {
                    // Inside the panel, so that the ripple stops at its round corners.
                    Modifier.clickable(
                        onClickLabel = press.label,
                        role = Role.Button,
                        onClick = press.onClick,
                    )
                },
        ) {
            Box(
                modifier =
                    Modifier.fillMaxWidth().padding(
                        PaddingValues(
                            start = sides,
                            end = sides,
                            top = spacing.row + if (isFirst) spacing.end else 0.dp,
                            bottom = spacing.row + if (isLast) spacing.end else 0.dp,
                        ),
                    ),
            ) {
                content()
            }
            if (!isLast) HorizontalDivider(modifier = Modifier.padding(horizontal = sides))
        }
    }
}

/** The part of a tile's outline that a row in this place draws. */
@Composable
private fun TileRowPlace.sliceOf(): Shape {
    val tile = MiloTheme.shapes.tile
    return when (this) {
        TileRowPlace.ONLY -> tile
        TileRowPlace.FIRST -> tile.copy(bottomStart = ZeroCornerSize, bottomEnd = ZeroCornerSize)
        TileRowPlace.MIDDLE -> RectangleShape
        TileRowPlace.LAST -> tile.copy(topStart = ZeroCornerSize, topEnd = ZeroCornerSize)
    }
}

package com.shawnkowalchuk.milo.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme

/** The small icon the design draws before some tile labels. */
private val LabelIconSize = 14.dp

/** Which of the design's tiles a [Tile] is. They differ in their colours and in nothing else. */
enum class TileKind {
    /** A flat panel a shade lighter than the page. Almost every tile. */
    PLAIN,

    /** The one tile of a screen that is the accent colour, with dark words: its "hero". */
    ACCENT,

    /** The amber tile for something that is waiting for the user. */
    ATTENTION,
}

/** How far a tile's content stands from the tile's edge, as the design draws its tiles. */
enum class TilePadding {
    /** 16 all round: a tile that shares a line with another, and a `SectionCard`. */
    EVEN,

    /** 16 above and below, 18 at the sides: a tile as wide as the screen. */
    WIDE,

    /** 18 all round: a hero tile that holds more than a title. */
    ROOMY,
}

/**
 * What pressing a tile does. The words and the handler travel together, so a tile can never be
 * pressable without saying what the press is for.
 *
 * @param label what a screen reader says the press does, after "double tap to": "open Trips".
 */
@Immutable
data class TilePress(val label: String, val onClick: () -> Unit)

/**
 * One of the design's tiles: a flat, rounded panel with no shadow, which every screen is built
 * from. What it holds is laid out in a column.
 *
 * It is as wide as it is told through [modifier] and as high as its content. Two tiles side by
 * side go in a [TilePair], which makes them equally high.
 *
 * @param press makes the whole tile one button. A tile that is pressed is never smaller than
 * Android's smallest target for a finger, because a tile is never that small.
 * @param gap the room between the things the tile holds, one of `MiloTheme.spacing`'s steps.
 */
@Composable
fun Tile(
    modifier: Modifier = Modifier,
    kind: TileKind = TileKind.PLAIN,
    padding: TilePadding = TilePadding.WIDE,
    press: TilePress? = null,
    gap: Dp = MiloTheme.spacing.buttonGap,
    content: @Composable ColumnScope.() -> Unit,
) {
    TileSurface(kind = kind, press = press, modifier = modifier) {
        Column(
            modifier = Modifier.padding(padding.values()),
            verticalArrangement = Arrangement.spacedBy(gap),
            content = content,
        )
    }
}

/**
 * The panel itself, for the components of the design system that lay a tile out in their own
 * way. Screens use [Tile].
 */
@Composable
internal fun TileSurface(
    kind: TileKind,
    press: TilePress?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val attention = MiloTheme.colors.attentionTile
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        shape = MiloTheme.shapes.tile,
        color =
            when (kind) {
                TileKind.PLAIN -> scheme.surfaceContainer
                TileKind.ACCENT -> scheme.primary
                TileKind.ATTENTION -> attention.fill
            },
        contentColor =
            when (kind) {
                TileKind.PLAIN -> scheme.onSurface
                TileKind.ACCENT -> scheme.onPrimary
                TileKind.ATTENTION -> attention.text
            },
    ) {
        if (press == null) {
            content()
        } else {
            // The press is taken inside the panel, so that its ripple stops at the round
            // corners. What the tile holds is then read as one button.
            Box(
                modifier =
                    Modifier.clickable(
                        onClickLabel = press.label,
                        role = Role.Button,
                        onClick = press.onClick,
                    ),
                propagateMinConstraints = true,
            ) {
                content()
            }
        }
    }
}

@Composable
private fun TilePadding.values(): PaddingValues {
    val spacing = MiloTheme.spacing
    return when (this) {
        TilePadding.EVEN -> PaddingValues(spacing.medium)
        TilePadding.WIDE -> PaddingValues(horizontal = spacing.gutter, vertical = spacing.medium)
        TilePadding.ROOMY -> PaddingValues(spacing.gutter)
    }
}

/**
 * The small grey label that says what a tile holds: "Today · business", "Last trip". It stands
 * first in a tile, above what is to be read.
 *
 * @param icon a small icon before the words, where the design draws one.
 */
@Composable
fun TileLabel(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(MiloTheme.spacing.buttonGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            // The words beside it say what the tile is.
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(LabelIconSize),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = text,
            style = MiloTheme.textStyles.tileLabel,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

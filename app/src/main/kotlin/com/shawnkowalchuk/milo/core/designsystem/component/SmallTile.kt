package com.shawnkowalchuk.milo.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme

/** The icon before the words of a small tile that is a link. */
private val LinkIconSize = 18.dp

/**
 * The design's small tile: a plain tile with the smaller corner (22) and less room inside (14
 * above and below, 16 at the sides), for a label over one short value, or for a few words that
 * are a button. The design draws two of them side by side under a screen's accent tile; put
 * them in a `TilePair`, which makes them equally high.
 *
 * It is a file of its own, and does not go through `Tile`, because `Tile` rounds every tile
 * with the larger corner.
 *
 * @param press makes the whole tile one button, as for a `Tile`.
 * @param centred true to stand what it holds in the middle of the tile's height, as the design
 * does with a tile that is a button; otherwise it starts at the top.
 */
@Composable
fun SmallTile(
    modifier: Modifier = Modifier,
    press: TilePress? = null,
    centred: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val spacing = MiloTheme.spacing
    Surface(
        modifier = modifier,
        shape = MiloTheme.shapes.smallTile,
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Box(
            // The press is taken inside the panel, so that its ripple stops at the round
            // corners. What the tile holds is then read as one button.
            modifier =
                if (press == null) {
                    Modifier
                } else {
                    Modifier.clickable(
                        onClickLabel = press.label,
                        role = Role.Button,
                        onClick = press.onClick,
                    )
                },
            propagateMinConstraints = true,
        ) {
            Column(
                modifier =
                    Modifier.padding(
                        horizontal = spacing.medium,
                        vertical = spacing.controlPadding,
                    ),
                verticalArrangement =
                    Arrangement.spacedBy(
                        spacing.extraSmall,
                        if (centred) Alignment.CenterVertically else Alignment.Top,
                    ),
                content = content,
            )
        }
    }
}

/**
 * A small tile that is the way to another screen, as the design draws "Add missed trip": an
 * icon and a few words, both in the accent colour, which is how the design marks a link. The
 * whole tile is the button, and its words say what it does.
 */
@Composable
fun SmallLinkTile(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    SmallTile(
        modifier = modifier,
        // No words of its own for the press: the tile's words already name what it does.
        press = TilePress(label = text, onClick = onClick),
        centred = true,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(MiloTheme.spacing.tileGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The words beside it say what the tile does.
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(LinkIconSize),
                tint = accent,
            )
            Text(text = text, style = MaterialTheme.typography.bodyLarge, color = accent)
        }
    }
}

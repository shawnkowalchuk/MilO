package com.shawnkowalchuk.milo.core.designsystem.component

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme

/**
 * The small grey label that stands on the page above a tile and names the group the tile
 * holds: "Android" above the checklist's rows, a day above that day's lines of the log.
 *
 * It is `TileLabel`'s counterpart outside a tile. As the design draws it, it stands a little
 * in from the tile's edge and a little further from the tile above it than two tiles stand
 * from each other, so it is read with the tile under it. A screen reader can jump from one to
 * the next.
 */
@Composable
fun GroupLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier =
            modifier
                .padding(start = MiloTheme.spacing.extraSmall, top = MiloTheme.spacing.small)
                .semantics { heading() },
        style = MiloTheme.textStyles.tileLabel,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

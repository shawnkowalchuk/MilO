package com.shawnkowalchuk.milo.core.designsystem.component

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme

/**
 * A titled group of related content, drawn as one of the design's tiles ([Tile]): a flat,
 * rounded panel a shade lighter than the page, with no shadow. It is the standard way to divide
 * a screen into sections.
 *
 * The title is the tile's label: small and in the colour of secondary text, because what the
 * tile holds is what is to be read first, and the label only says what it is.
 *
 * It always fills the available width so that stacked tiles share the same edges. [content] is
 * laid out in a column with the standard gap between items, so callers do not add their own
 * spacing between rows.
 */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Tile(
        modifier = modifier.fillMaxWidth(),
        padding = TilePadding.EVEN,
        gap = MiloTheme.spacing.tileGap,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            // Lets a screen reader jump from section to section instead of reading every row.
            modifier = Modifier.semantics { heading() },
        )
        content()
    }
}

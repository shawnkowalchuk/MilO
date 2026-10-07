package com.shawnkowalchuk.milo.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme

// The outline as the design draws it: a hairline, broken into short dashes.
private val OutlineWidth = 1.dp
private val DashLength = 4.dp
private val DashGap = 3.dp

/** How high the design draws the tile's button. */
private val ButtonHeight = 44.dp

/**
 * A tile that is only an outline of dashes, with the page showing through, as the design draws
 * "What MilO recorded" under the edit form: something that is kept beside what the screen is
 * for and is not part of it, with the one quiet button that brings it back. It has a tile's
 * corners and a tile's 16 dp inside.
 *
 * The button is drawn 44 dp high and takes up no more, so the tile is as high as drawn. The
 * place a finger can hit is 48 dp high: it reaches 2 dp past the button, into the tile's own
 * padding.
 *
 * @param title what is kept, a little firmer than the line under it.
 * @param line the quieter line: the kept values themselves.
 * @param actionLabel the button's word: what it does ("Restore").
 */
@Composable
fun DashedTile(
    title: String,
    line: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val outline = MiloTheme.colors.idleOutline
    val corner = MiloTheme.shapes.tile.topStart
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .drawBehind {
                    val width = OutlineWidth.toPx()
                    // The line is drawn half inside and half outside its path, so the path
                    // stands half a line in from the tile's edge.
                    val radius = corner.toPx(size, this) - width / 2
                    drawRoundRect(
                        color = outline,
                        topLeft = Offset(width / 2, width / 2),
                        size = Size(size.width - width, size.height - width),
                        cornerRadius = CornerRadius(radius, radius),
                        style =
                            Stroke(
                                width = width,
                                pathEffect =
                                    PathEffect.dashPathEffect(
                                        floatArrayOf(DashLength.toPx(), DashGap.toPx()),
                                    ),
                            ),
                    )
                }.padding(MiloTheme.spacing.medium),
        horizontalArrangement = Arrangement.spacedBy(MiloTheme.spacing.rowGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(MiloTheme.spacing.textGap),
        ) {
            Text(
                text = title,
                // Lets a screen reader jump to what is kept beside the form.
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                text = line,
                style = MiloTheme.textStyles.tileLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        RowButton(
            text = actionLabel,
            onClick = onAction,
            modifier = Modifier.takesUpOnly(ButtonHeight).heightIn(min = ButtonHeight),
        )
    }
}

// Sample text is written inline because a preview is never shown to a user or shipped.
@Preview
@Composable
private fun DashedTilePreview() {
    MiloTheme {
        Surface {
            DashedTile(
                title = "What MilO recorded",
                line = "7:42 – 8:06 AM · 18.4 km",
                actionLabel = "Restore",
                onAction = {},
            )
        }
    }
}

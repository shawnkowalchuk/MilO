package com.shawnkowalchuk.milo.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme

// The design's padding of the hero tile that is one button: 20 above and below, 22 before its
// words, 18 after its round mark.
private val HeroPadding = PaddingValues(start = 22.dp, top = 20.dp, end = 18.dp, bottom = 20.dp)

/** The round dark mark at the end of that tile, and the icon in it. */
private val RoundMarkSize = 64.dp
private val RoundMarkIconSize = 24.dp

/** How high the design draws the dark button on an accent tile, and the icon before its words. */
private val HeroButtonHeight = 52.dp
private val HeroButtonIconSize = 14.dp

/**
 * The accent tile that is the one main action of a screen, as the design draws it on Home: a
 * few large words, a quieter line under them, and a round dark mark with an icon at the end.
 * **The whole tile is the button.** A screen reader reads the words and the line as one button.
 *
 * At most one per screen, like `PrimaryButton`, and never both.
 *
 * @param line the quieter line under the title: what else there is to know before pressing.
 * @param icon drawn in the round mark, in the accent colour.
 */
@Composable
fun HeroActionTile(
    title: String,
    line: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    TileSurface(
        kind = TileKind.ACCENT,
        // No words of its own for the press: the title already names what it does.
        press = TilePress(label = title, onClick = onClick),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(HeroPadding),
            horizontalArrangement = Arrangement.spacedBy(MiloTheme.spacing.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(MiloTheme.spacing.extraSmall),
            ) {
                Text(text = title, style = MaterialTheme.typography.headlineMedium)
                Text(text = line, style = MiloTheme.textStyles.accentNote)
            }
            Box(
                modifier =
                    Modifier.size(
                        RoundMarkSize,
                    ).background(scheme.onPrimary, MiloTheme.shapes.pill),
                contentAlignment = Alignment.Center,
            ) {
                // The tile's words say what the press does.
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(RoundMarkIconSize),
                    tint = scheme.primary,
                )
            }
        }
    }
}

/**
 * The dark button that stands on an accent tile, as wide as it is told: "End trip" on the tile
 * of the trip being recorded. Light words on the dark that the tile's own words are written in,
 * with a small icon before them.
 *
 * It is drawn 52 dp high, so it is never smaller than Android's smallest target for a finger,
 * and it grows when its words need more than one line.
 */
@Composable
fun HeroButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = HeroButtonHeight),
        shape = MiloTheme.shapes.mainButton,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = scheme.onPrimary,
                contentColor = scheme.onSurface,
            ),
        contentPadding =
            PaddingValues(
                horizontal = MiloTheme.spacing.gutter,
                vertical = MiloTheme.spacing.small,
            ),
    ) {
        // The words beside it say what the button does.
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(HeroButtonIconSize),
        )
        Spacer(Modifier.width(MiloTheme.spacing.tileGap))
        Text(text = text, style = MaterialTheme.typography.titleSmall)
    }
}

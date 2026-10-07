package com.shawnkowalchuk.milo.core.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme
import kotlinx.coroutines.delay

/** The arrowhead at the end of the heading. */
private val ChevronSize = 18.dp

/** Half a turn: the arrowhead points down while the tile is closed, and up while it is open. */
private const val OPEN_TURN = 180f

/** How long the opening and the closing take. Short: it is a list being shown, not a show. */
private const val OPEN_MS = 180

/** How many frames after the opening the tile is given before its full height is asked for. */
private const val FRAMES_TO_SETTLE = 2

/**
 * What the heading of an [ExpandableTile] says.
 *
 * @param title the tile's own title: "Mon, Oct 5".
 * @param line the quieter line under it, which says what the tile holds while it is closed:
 * "2 trips · 31.7 km business".
 * @param titleSpoken what a screen reader says for the title, where the drawn one is
 * shortened: "Monday, October 5, 2026".
 * @param openLabel what a screen reader says a press does while the tile is closed, after
 * "double tap to": "show this day's trips".
 * @param closeLabel the same while it is open: "hide this day's trips".
 */
@Immutable
data class ExpandableHeading(
    val title: String,
    val line: String,
    val openLabel: String,
    val closeLabel: String,
    val titleSpoken: String = title,
)

/**
 * A tile whose rows are put away under its heading until the heading is pressed: a day of
 * trips. Closed, it shows its title, a quieter line and an arrowhead that points down. Open,
 * the arrowhead points up and the rows stand under the heading.
 *
 * **The heading is one button,** as wide as the tile and with the tile's own padding inside
 * it, so the whole of a closed tile is what is pressed. A screen reader reads it as a heading
 * and a button, and is told "Expanded" or "Collapsed".
 *
 * **Whether it is open is the caller's to keep:** the tile is told, and says when its heading
 * was pressed.
 *
 * **The opening is animated, modestly:** the rows unfold and fade in, the arrowhead turns.
 * Compose finishes all three at once when the phone's animations are switched off. When a
 * press opens a tile whose rows would stand below the edge of a list, the list moves to show
 * them, once the tile has its full height. Only a press moves the list.
 *
 * **What it holds are rows.** They stand 18 dp from the sides. Each brings its own 12 dp above
 * and below, so that a row can be pressed over its whole height; the tile adds 4 dp under the
 * last, which makes the 16 dp every tile keeps at its lower edge.
 */
@Composable
fun ExpandableTile(
    heading: ExpandableHeading,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val spacing = MiloTheme.spacing
    val whole = remember { BringIntoViewRequester() }
    // True from the press that opens the tile until the list has moved to show it.
    var openedByPress by remember { mutableStateOf(false) }
    LaunchedEffect(expanded) {
        if (expanded && openedByPress) {
            openedByPress = false
            // The tile has its full height when it has finished opening, which is at once
            // while the phone's animations are switched off. Even then the rows are only laid
            // out in the frame that has just begun, and the opening itself ends a frame later:
            // two frames on, the list can tell how far to move.
            val scale = coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
            delay((OPEN_MS * scale).toLong())
            repeat(FRAMES_TO_SETTLE) { withFrameNanos { } }
            whole.bringIntoView()
        }
    }
    TileSurface(
        kind = TileKind.PLAIN,
        press = null,
        modifier = modifier.fillMaxWidth().bringIntoViewRequester(whole),
    ) {
        Column {
            HeadingButton(
                words = heading,
                expanded = expanded,
                onClick = {
                    openedByPress = !expanded
                    onToggle()
                },
            )
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(tween(OPEN_MS)) + fadeIn(tween(OPEN_MS)),
                exit = shrinkVertically(tween(OPEN_MS)) + fadeOut(tween(OPEN_MS)),
            ) {
                Column(
                    modifier =
                        Modifier.padding(
                            start = spacing.gutter,
                            end = spacing.gutter,
                            bottom = spacing.extraSmall,
                        ),
                    content = content,
                )
            }
        }
    }
}

@Composable
private fun HeadingButton(words: ExpandableHeading, expanded: Boolean, onClick: () -> Unit) {
    val spacing = MiloTheme.spacing
    val scheme = MaterialTheme.colorScheme
    val state =
        stringResource(
            if (expanded) R.string.tile_state_expanded else R.string.tile_state_collapsed,
        )
    // Closed, the heading is all the tile holds and keeps the tile's 16 dp under it. Open,
    // the design has 8 dp between the heading and the first row's own room.
    val under by animateDpAsState(
        targetValue = if (expanded) spacing.small else spacing.medium,
        animationSpec = tween(OPEN_MS),
        label = "room under the heading",
    )
    val turn by animateFloatAsState(
        targetValue = if (expanded) OPEN_TURN else 0f,
        animationSpec = tween(OPEN_MS),
        label = "arrowhead",
    )
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(
                    onClickLabel = if (expanded) words.closeLabel else words.openLabel,
                    role = Role.Button,
                    onClick = onClick,
                )
                // Lets a screen reader jump from tile to tile, and says which way this one is.
                .semantics {
                    heading()
                    stateDescription = state
                }.padding(
                    start = spacing.gutter,
                    top = spacing.medium,
                    end = spacing.gutter,
                    bottom = under,
                ),
        horizontalArrangement = Arrangement.spacedBy(spacing.rowGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(spacing.textGap),
        ) {
            Text(
                text = words.title,
                modifier = Modifier.semantics { contentDescription = words.titleSpoken },
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = words.line,
                style = MiloTheme.textStyles.tileLabel,
                color = scheme.onSurfaceVariant,
            )
        }
        // The state is said in words to a screen reader; the arrowhead is for the eye.
        Icon(
            imageVector = ChevronIcons.Down,
            contentDescription = null,
            modifier = Modifier.size(ChevronSize).rotate(turn),
            tint = scheme.onSurfaceVariant,
        )
    }
}

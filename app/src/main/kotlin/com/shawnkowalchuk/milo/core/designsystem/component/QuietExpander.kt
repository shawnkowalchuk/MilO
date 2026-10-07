package com.shawnkowalchuk.milo.core.designsystem.component

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme

/** How high the line is drawn. The place a finger can hit is 48 dp, as everywhere. */
private val LineHeight = 28.dp

/** Android's smallest target for a finger. */
private val FingerTarget = 48.dp

/** The arrowhead after the words. */
private val ChevronSize = 14.dp

/** Half a turn: the arrowhead points down while the line is closed, and up while it is open. */
private const val OPEN_TURN = 180f

/** How long the opening and the closing take. Short: it is a few sentences being shown. */
private const val OPEN_MS = 180

/**
 * A quiet line that opens what is put away under it: one small grey line with an arrowhead,
 * inside a tile or on the page. Pressed, it shows what it holds; pressed again, it puts it
 * away. It keeps a tile as calm as the design draws it while nothing is lost.
 *
 * Two things are put away behind one. **The sentences that say what a tile's settings do,**
 * which are read once and are then in the way ("About the work schedule"). And **a part of a
 * tile that is seldom needed,** such as the hours of each work day by itself.
 *
 * **A state, a refusal or anything that has happened is never put away.** It stands in the
 * tile, in sight.
 *
 * The line is drawn 28 dp high and takes up no more. The place a finger can hit is 48 dp high:
 * it reaches 10 dp past the line, above and below, so nothing that can be pressed may stand
 * within that distance. A screen reader reads the line as a button, and is told "Expanded" or
 * "Collapsed".
 *
 * **Whether it is open is the caller's to keep:** the line is told, and says when it was
 * pressed.
 *
 * @param label what is put away: "About the work schedule", "Set each day by itself".
 * @param pressable false while what it holds cannot be put away, because it is the only place
 * something is shown. The line is then a plain caption above it: no arrowhead, nothing to
 * press, and it stays where it is, so nothing under it moves when this changes.
 * @param content what is shown while it is open, laid out in a column with a small gap
 * between its parts.
 */
@Composable
fun QuietExpander(
    label: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    pressable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val grey = MaterialTheme.colorScheme.onSurfaceVariant
    val state =
        stringResource(
            if (expanded) R.string.tile_state_expanded else R.string.tile_state_collapsed,
        )
    val turn by animateFloatAsState(
        targetValue = if (expanded) OPEN_TURN else 0f,
        animationSpec = tween(OPEN_MS),
        label = "quiet expander arrowhead",
    )
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .takesUpOnly(LineHeight)
                    .heightIn(min = FingerTarget)
                    .then(
                        if (pressable) {
                            Modifier
                                .clickable(role = Role.Button, onClick = onToggle)
                                .semantics { stateDescription = state }
                        } else {
                            Modifier
                        },
                    ),
            horizontalArrangement = Arrangement.spacedBy(MiloTheme.spacing.extraSmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = label, style = MiloTheme.textStyles.tileLabel, color = grey)
            if (pressable) {
                // The state is said in words to a screen reader; the arrowhead is for the eye.
                Icon(
                    imageVector = ChevronIcons.Down,
                    contentDescription = null,
                    modifier = Modifier.size(ChevronSize).rotate(turn),
                    tint = grey,
                )
            }
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(tween(OPEN_MS)) + fadeIn(tween(OPEN_MS)),
            exit = shrinkVertically(tween(OPEN_MS)) + fadeOut(tween(OPEN_MS)),
        ) {
            Column(
                modifier = Modifier.padding(top = MiloTheme.spacing.extraSmall),
                verticalArrangement = Arrangement.spacedBy(MiloTheme.spacing.small),
                content = content,
            )
        }
    }
}

// Sample text is written inline because a preview is never shown to a user or shipped.
@Preview
@Composable
private fun QuietExpanderPreview() {
    MiloTheme {
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            QuietExpander(label = "About the work schedule", expanded = true, onToggle = {}) {
                Text(
                    text = "A trip that starts inside these hours is saved as Business.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

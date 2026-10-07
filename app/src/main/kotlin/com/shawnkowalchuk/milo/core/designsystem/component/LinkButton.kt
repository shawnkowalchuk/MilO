package com.shawnkowalchuk.milo.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme

/** Android's smallest target for a finger. */
private val FingerTarget = 48.dp

/**
 * A few words in the accent colour that are a button: how the design marks a link ("Preview
 * PDF" under a sentence, "Change" at the end of a tile's label line). It has no fill and no
 * outline. While it is pressed the words are the design's lighter accent.
 *
 * It takes up the room its words are drawn in and no more, so that it stands in a line of
 * text without pushing the lines apart: the words alone at the end of a label's line
 * ([atLineEnd]), or with the design's 8 dp above and below them under a sentence. The place a
 * finger can hit is 48 dp high and at least as wide: it reaches past the words, into the room
 * around them, so nothing else that can be pressed may stand that close.
 *
 * @param atLineEnd true for the smaller link at the end of a label's line: its words end
 * where the line ends.
 * @param enabled false greys the words and takes the press away while the link has to wait.
 * @param pressLabel what a screen reader says the press does, after "double tap to", where
 * the words alone do not say where they lead ("Change": "open Settings").
 */
@Composable
fun LinkButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    atLineEnd: Boolean = false,
    enabled: Boolean = true,
    pressLabel: String? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val style =
        if (atLineEnd) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge
    val roomAround = if (atLineEnd) 0.dp else MiloTheme.spacing.small
    // One line of the words, as high as the style says. Both of its values are the theme's,
    // in sp and in em, so the height grows with the phone's font size.
    val drawn =
        with(LocalDensity.current) { (style.fontSize.toPx() * style.lineHeight.value).toDp() } +
            roomAround * 2
    val presses = remember { MutableInteractionSource() }
    val pressed by presses.collectIsPressedAsState()
    Box(
        modifier =
            modifier
                .takesUpOnly(drawn)
                .sizeIn(minWidth = FingerTarget, minHeight = FingerTarget)
                .clickable(
                    interactionSource = presses,
                    // The words change colour instead: a ripple would show the whole of the
                    // place a finger can hit, which is larger than the link is drawn.
                    indication = null,
                    enabled = enabled,
                    onClickLabel = pressLabel,
                    role = Role.Button,
                    onClick = onClick,
                ),
        contentAlignment = if (atLineEnd) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Text(
            text = text,
            style = style,
            color =
                when {
                    !enabled -> scheme.onSurfaceVariant
                    pressed -> MiloTheme.colors.accentPressed
                    else -> scheme.primary
                },
        )
    }
}

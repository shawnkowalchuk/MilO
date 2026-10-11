package com.shawnkowalchuk.milo.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme

/** How high the design draws a pill. It grows when a large font needs more. */
private val PillHeight = 30.dp

/** The small round dot before a word that says a state. */
private val DotSize = 8.dp

/**
 * The design's pill: a few words on a dark, fully round patch, standing on an accent tile. It
 * says a state ("Recording", "Not submitted") and cannot be pressed.
 *
 * @param lit true for a state that is going on right now: the words are the accent colour,
 * with a dot of the same colour before them. Otherwise the words are the main text colour.
 */
@Composable
fun Pill(text: String, modifier: Modifier = Modifier, lit: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    val words = if (lit) scheme.primary else scheme.onSurface
    Row(
        modifier =
            modifier
                // The dark that words on the accent are written in is also the pill's fill.
                .background(scheme.onPrimary, MiloTheme.shapes.pill)
                .heightIn(min = PillHeight)
                .padding(horizontal = MiloTheme.spacing.rowGap),
        horizontalArrangement = Arrangement.spacedBy(MiloTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (lit) Dot(words)
        Text(text = text, style = MaterialTheme.typography.labelMedium, color = words)
    }
}

/**
 * A word that says a state, behind a small dot: "Connected". The dot is the accent while the
 * state is on, and grey while it is not; the word says which, so the colour is never the only
 * sign.
 *
 * @param on whether the dot is lit.
 */
@Composable
fun DotWord(text: String, on: Boolean, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(MiloTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Dot(if (on) scheme.primary else scheme.onSurfaceVariant)
        Text(text = text, style = MaterialTheme.typography.titleSmall)
    }
}

/** The dot itself. Also the dot that pulses while something is counted (`LiveDot`). */
@Composable
internal fun Dot(color: Color, modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(DotSize).background(color, MiloTheme.shapes.pill))
}

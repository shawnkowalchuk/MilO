package com.shawnkowalchuk.milo.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme

/** How thick the design draws the line. */
private val LineHeight = 5.dp

/**
 * The design's thin bar: a share of a whole, drawn as a line that is filled with the accent
 * from its start. It stands between a figure and the words that say the same share as a
 * number ("89% business"), so a screen reader is told nothing about it: the words say it.
 *
 * @param fraction the filled share, from 0 to 1. A value outside is drawn as the nearer end.
 */
@Composable
fun ProgressLine(fraction: Float, modifier: Modifier = Modifier) {
    val round = MiloTheme.shapes.pill
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(LineHeight)
                .background(MiloTheme.colors.control.fill, round)
                .clearAndSetSemantics {},
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.primary, round),
        )
    }
}

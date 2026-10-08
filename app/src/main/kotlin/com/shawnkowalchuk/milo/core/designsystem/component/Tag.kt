package com.shawnkowalchuk.milo.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shawnkowalchuk.milo.core.designsystem.theme.FillAndText
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme

/** How high the design draws a tag. It grows when a large font needs more. */
private val TagHeight = 20.dp

/**
 * The design's small tag: one short word in capitals, in monospace, on a fully round patch of
 * its own colour. It names the kind of a line of the event log ("TRIP", "ERROR"), and since
 * 2026-10-08 the kind of a change on the What's new screen ("NEW", "FIXED"), so that a column
 * of lines can be scanned by colour. It cannot be pressed.
 *
 * The colour is never the only sign: the word says the same.
 *
 * @param colors the patch and its word, one of `MiloTheme.colors.logTags` or
 * `MiloTheme.colors.changeTags`.
 */
@Composable
fun Tag(text: String, colors: FillAndText, modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .background(colors.fill, MiloTheme.shapes.pill)
                .heightIn(min = TagHeight)
                .padding(horizontal = MiloTheme.spacing.small),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = MiloTheme.textStyles.logTag, color = colors.text)
    }
}

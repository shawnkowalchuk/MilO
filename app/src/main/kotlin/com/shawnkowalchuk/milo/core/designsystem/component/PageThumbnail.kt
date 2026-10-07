package com.shawnkowalchuk.milo.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// The small picture of a printed page, to the design's sizes.
private val PageWidth = 104.dp
private val PageHeight = 136.dp
private val PageCorner = 8.dp
private val PagePaddingSides = 10.dp
private val PagePaddingEnds = 12.dp
private val LineGap = 5.dp

private val TitleHeight = 6.dp
private val LineHeight = 3.dp
private val BandHeight = 14.dp
private val BandRoom = 4.dp
private val LineCorner = 2.dp
private val BandCorner = 3.dp

private const val TITLE_SHARE = 0.6f
private const val UNDER_TITLE_SHARE = 0.4f
private const val SHORT_LINE_SHARE = 0.8f

/** Which of the page's lines of text ends early, as the last line of a paragraph does. */
private const val SHORT_LINE = 4
private const val TEXT_LINES = 6

/**
 * How strongly the grey of secondary text is laid on the light page for its lines of text. It
 * makes the pale grey the design draws them in (C9CCD2) out of two colours the theme has, so
 * that the picture needs no colour of its own.
 */
private const val TEXT_LINE_STRENGTH = 0.45f

/**
 * A small picture of a printed page, as the design draws it beside the name of the report's
 * PDF: a light sheet with a dark title, a grey line under it, a band in the accent colour and
 * a few pale lines of text. It is made of plain shapes and shows nothing of the real file.
 *
 * It is decoration: the words beside it say what the file is, so a screen reader is told
 * nothing. Its size is the design's and does not grow with the phone's font size.
 */
@Composable
fun PageThumbnail(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val textLine = scheme.onSurfaceVariant.copy(alpha = TEXT_LINE_STRENGTH)
    Column(
        modifier =
            modifier
                .size(width = PageWidth, height = PageHeight)
                // The light of the main text is the sheet, and the page's dark its title.
                .background(scheme.inverseSurface, RoundedCornerShape(PageCorner))
                .padding(horizontal = PagePaddingSides, vertical = PagePaddingEnds)
                .clearAndSetSemantics {},
        verticalArrangement = Arrangement.spacedBy(LineGap),
    ) {
        Bar(TitleHeight, scheme.inverseOnSurface, share = TITLE_SHARE)
        Bar(LineHeight, scheme.onSurfaceVariant, share = UNDER_TITLE_SHARE)
        Bar(
            height = BandHeight,
            color = scheme.primary,
            corner = BandCorner,
            modifier = Modifier.padding(vertical = BandRoom),
        )
        repeat(TEXT_LINES) { line ->
            Bar(LineHeight, textLine, share = if (line == SHORT_LINE) SHORT_LINE_SHARE else 1f)
        }
    }
}

/** One line of the picture, [share] of the sheet's width. */
@Composable
private fun Bar(
    height: Dp,
    color: Color,
    modifier: Modifier = Modifier,
    share: Float = 1f,
    corner: Dp = LineCorner,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth(share)
                .height(height)
                .background(color, RoundedCornerShape(corner)),
    )
}

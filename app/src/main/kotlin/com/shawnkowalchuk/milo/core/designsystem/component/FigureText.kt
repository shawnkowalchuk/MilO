package com.shawnkowalchuk.milo.core.designsystem.component

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme
import kotlin.math.roundToInt

/** How large a [FigureText] is. Each is one of the design's pairs of a figure and its unit. */
enum class FigureSize {
    /** 30 with a unit of 14: the one figure of a tile that holds nothing else. */
    LARGE,

    /** 26 with a unit of 13: a figure in a tile that holds more. */
    MEDIUM,

    /** 28 with a unit of 13: the figure of a tile with a line under it, as on the Report screen. */
    TILE,

    /**
     * 36 with words of 16 after it, a little lighter than a unit: a count and what it is a
     * count of ("11 of 14 ready"), in the tile at the top of Setup.
     */
    COUNT,

    /**
     * 44 with a unit of 16: a figure on the accent tile, where the unit is the figure's own
     * dark: a month's total on Trips, and since 2026-10-10 the kilometres of the trip being
     * recorded on Home. It takes up exactly the height of its figure, as the design draws it
     * (see [FigureText]).
     */
    TOTAL,
}

/**
 * A figure with its unit right after it, as the design writes a distance: the figure large,
 * the unit small on the same line ("48.2 km"). On a plain tile the unit is grey. On the accent
 * tile it is the colour of the figure, because grey cannot be read there.
 *
 * It is one piece of text, so the unit stands on the figure's own line whatever the font size
 * is, and a screen reader reads the two together: as written ("48.2 km"), or as [spoken] says
 * them.
 *
 * **How high it is.** The design sets its largest figures on a line exactly as high as the
 * figure (44 for 44), and the theme's styles say the same. Compose does not go by that for a
 * single line: it never makes a text lower than its typeface asks for, which for Sora is 1.26
 * times the size. The figure on an accent tile ([FigureSize.TOTAL]) is therefore told to take
 * up the figure's own height, with the text standing in the middle of it; a digit is lower
 * than that, so nothing is cut off. A figure that a very large font sends onto a second line
 * is left as high as it is. (Until 2026-10-10 there was a sixth size, 56 with 18, for the
 * trip being recorded on Home's old, higher tile; it was as high as Compose made it.)
 *
 * @param figure the number as it is to be read, already rounded and written for the language.
 * @param unit the unit alone: "km", "mi".
 * @param spoken what a screen reader says in place of the figure and its unit, or null to have
 * them read as they are written. A distance passes its figure with the unit's whole word
 * ("48.2 miles"), because a short word such as "mi" is not said as the unit it stands for.
 */
@Composable
fun FigureText(
    figure: String,
    unit: String,
    modifier: Modifier = Modifier,
    size: FigureSize = FigureSize.MEDIUM,
    spoken: String? = null,
) {
    val typography = MaterialTheme.typography
    val figureStyle: TextStyle
    val unitStyle: TextStyle
    when (size) {
        FigureSize.LARGE -> {
            figureStyle = typography.headlineLarge
            unitStyle = typography.bodyLarge
        }

        FigureSize.MEDIUM -> {
            figureStyle = typography.headlineSmall
            unitStyle = typography.labelLarge
        }

        FigureSize.TILE -> {
            figureStyle = MiloTheme.textStyles.tileFigure
            unitStyle = typography.labelLarge
        }

        FigureSize.COUNT -> {
            figureStyle = typography.displaySmall
            unitStyle = MiloTheme.textStyles.countWords
        }

        FigureSize.TOTAL -> {
            figureStyle = typography.displayMedium
            unitStyle = typography.titleMedium
        }
    }
    val unitColor =
        if (size == FigureSize.TOTAL) {
            LocalContentColor.current
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
    // The design pulls the unit's letters together by as much as the figure's: the same length,
    // not the same share of a smaller size. Both of the figure's values are of the theme, in
    // sp and in em, so the product is in sp and grows with the phone's font size.
    val unitTracking = figureStyle.fontSize * figureStyle.letterSpacing.value
    Text(
        text =
            buildAnnotatedString {
                append(figure)
                // The space belongs to the unit, so it is as narrow as the unit is small.
                withStyle(
                    unitStyle.toSpanStyle().copy(color = unitColor, letterSpacing = unitTracking),
                ) {
                    append(" $unit")
                }
            },
        modifier =
            (if (size == FigureSize.TOTAL) modifier.asHighAs(figureStyle) else modifier)
                .then(
                    if (spoken == null) {
                        Modifier
                    } else {
                        Modifier.semantics { contentDescription = spoken }
                    },
                ),
        style = figureStyle,
    )
}

/**
 * Takes up the height [style] names for a line, and stands the text, which Compose measures
 * higher, in the middle of it. Both of the style's values are of the theme, in sp and in em,
 * so the height grows with the phone's font size.
 */
private fun Modifier.asHighAs(style: TextStyle): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val line = (style.fontSize.toPx() * style.lineHeight.value).roundToInt()
    // A figure that runs onto a second line, as a very large font can make it, is left as high
    // as it is: held to one line's height it would be drawn over what stands above and below.
    val oneLine = placeable.height < line * 2
    val height = if (oneLine) minOf(line, placeable.height) else placeable.height
    layout(placeable.width, height) { placeable.place(0, (height - placeable.height) / 2) }
}

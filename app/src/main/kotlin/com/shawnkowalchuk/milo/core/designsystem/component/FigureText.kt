package com.shawnkowalchuk.milo.core.designsystem.component

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme

/** How large a [FigureText] is. Each is one of the design's pairs of a figure and its unit. */
enum class FigureSize {
    /** 30 with a unit of 14: the one figure of a tile that holds nothing else. */
    LARGE,

    /** 26 with a unit of 13: a figure in a tile that holds more. */
    MEDIUM,

    /** 56 with a unit of 18: the kilometres of the trip being recorded, on the accent tile. */
    HERO,
}

/**
 * A figure with its unit right after it, as the design writes a distance: the figure large,
 * the unit small on the same line ("48.2 km"). On a plain tile the unit is grey. On the accent
 * tile it is the colour of the figure, because grey cannot be read there.
 *
 * It is one piece of text, so a screen reader reads "48.2 km" and the unit stands on the
 * figure's own line whatever the font size is.
 *
 * @param figure the number as it is to be read, already rounded and written for the language.
 * @param unit the unit alone: "km".
 */
@Composable
fun FigureText(
    figure: String,
    unit: String,
    modifier: Modifier = Modifier,
    size: FigureSize = FigureSize.MEDIUM,
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

        FigureSize.HERO -> {
            figureStyle = typography.displayLarge
            unitStyle = MiloTheme.textStyles.rowFigure
        }
    }
    val unitColor =
        if (size == FigureSize.HERO) {
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
        modifier = modifier,
        style = figureStyle,
    )
}

package com.shawnkowalchuk.milo.core.designsystem.text

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme

// Where a trip went, as the design writes it in a list: the addresses in the line's own firm
// style, and the words that stand in for a missing address ("looking up the address…")
// lighter and grey beside them, so that they are not taken for a place.
//
// The words themselves are the ones of `TripPlaceWords.kt`, which this file adds nothing to:
// it only says which part of the line is quiet. It came with the Trips screen's layout. Home
// still writes the same line in one style (`placesWords`), and can be given this one.

// Two characters that are in no address and no sentence: they stand in the sentence from
// strings.xml where the two sides belong, so that the sentence keeps the order and the arrow
// its language gives it. They are of Unicode's private use area, which no keyboard types.
internal const val FROM_MARK = '\uE000'
internal const val TO_MARK = '\uE001'

/**
 * One side of "from → to", ready to be written.
 *
 * @param standIn true for words that stand where the address is missing.
 */
internal data class SideWords(val text: String, val standIn: Boolean)

/**
 * The line that says where a trip went, with its stand-in words set apart.
 *
 * A sentence that stands for both addresses ("Looking up the addresses…") is quiet as a
 * whole. "From (address)", the start of a trip in progress, is not: it names a place.
 */
@Composable
fun placesLine(places: PlacesText): AnnotatedString {
    val quiet =
        MiloTheme.textStyles.standInWords
            .toSpanStyle()
            .copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    return when (places) {
        is PlacesText.Sentence -> AnnotatedString(stringResource(places.text), quiet)

        is PlacesText.From -> AnnotatedString(stringResource(R.string.trips_from, places.address))

        is PlacesText.FromTo ->
            fromToLine(
                sentence =
                    stringResource(
                        R.string.trips_from_to,
                        FROM_MARK.toString(),
                        TO_MARK.toString(),
                    ),
                from = places.from.sideWords(),
                to = places.to.sideWords(),
                quiet = quiet,
            )
    }
}

/**
 * Puts the two sides where [sentence] has its two marks, each stand-in in the [quiet] style.
 * Everything else of the sentence (the arrow, the spaces) is written as it stands.
 */
internal fun fromToLine(
    sentence: String,
    from: SideWords,
    to: SideWords,
    quiet: SpanStyle,
): AnnotatedString = buildAnnotatedString {
    for (character in sentence) {
        val side =
            when (character) {
                FROM_MARK -> from
                TO_MARK -> to
                else -> null
            }
        when {
            side == null -> append(character)
            side.standIn -> withStyle(quiet) { append(side.text) }
            else -> append(side.text)
        }
    }
}

@Composable
private fun PlaceSide.sideWords(): SideWords = when (this) {
    is PlaceSide.Address -> SideWords(line, standIn = false)
    is PlaceSide.Words -> SideWords(stringResource(text), standIn = true)
}

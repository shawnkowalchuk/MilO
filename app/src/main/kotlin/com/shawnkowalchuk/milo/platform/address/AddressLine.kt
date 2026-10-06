package com.shawnkowalchuk.milo.platform.address

// Turns a geocoder's answer into the one short line a trip shows, such as "12 Shop Rd, Edmonton".
// Pure functions with no Android in them, so they are tested on the JVM with made-up answers.

/** At most this many parts of the geocoder's own line are shown when nothing better exists. */
private const val FALLBACK_PARTS = 2

/** How much a line says, best last, so that the best of several candidates can be picked. */
private enum class LineKind { GEOCODER_LINE, TOWN, STREET, STREET_AND_TOWN }

private data class Line(val kind: LineKind, val text: String)

/**
 * The line for a position, or null if the answer holds nothing a person would recognise.
 *
 * In order of preference: the street (with its house number when there is one) and the town;
 * the street alone; the town alone; and last, the geocoder's own line with the country and the
 * postal code taken out. A country or a postal code is never shown.
 *
 * The house number is written before the street, as addresses are written in Canada. A geocoder
 * returns several candidates for one position, usually from the most exact to the widest; the
 * one that says the most is used, and the earlier one when two say as much.
 */
fun addressLine(candidates: List<PlaceParts>): String? =
    // maxByOrNull keeps the first of equals, which is the geocoder's own preference.
    candidates.mapNotNull { it.asLine() }.maxByOrNull { it.kind }?.text

private fun PlaceParts.asLine(): Line? {
    val streetText = streetText()
    val townText = town.clean() ?: district.clean() ?: county.clean() ?: region.clean()
    return when {
        streetText != null && townText != null ->
            Line(LineKind.STREET_AND_TOWN, "$streetText, $townText")

        streetText != null -> Line(LineKind.STREET, streetText)

        townText != null -> Line(LineKind.TOWN, townText)

        else -> geocoderLine()?.let { Line(LineKind.GEOCODER_LINE, it) }
    }
}

/** "12 Shop Rd", or "Shop Rd" without a number. A number without a street says nothing. */
private fun PlaceParts.streetText(): String? {
    val name = street.clean() ?: return null
    val number = houseNumber.clean() ?: return name
    return "$number $name"
}

/**
 * The geocoder's own line, cut down: its parts are separated by commas, the postal code is taken
 * out of whichever part holds it, the part that is the country is dropped, and the first
 * [FALLBACK_PARTS] of what is left are kept.
 */
private fun PlaceParts.geocoderLine(): String? {
    val line = firstLine.clean() ?: return null
    val postal = postalCode.clean()
    val countryNames = listOfNotNull(country.clean(), countryCode.clean())
    val parts =
        line
            .split(',')
            .mapNotNull { part ->
                val withoutPostal =
                    if (postal == null) part else part.replace(postal, "", ignoreCase = true)
                withoutPostal.clean()
            }.filter { part -> countryNames.none { it.equals(part, ignoreCase = true) } }
    return parts.take(FALLBACK_PARTS).joinToString(", ").clean()
}

/** Trimmed, with runs of spaces closed up; null when nothing is left. */
private fun String?.clean(): String? =
    this?.trim()?.replace(Regex("\\s+"), " ")?.takeIf { it.isNotEmpty() }

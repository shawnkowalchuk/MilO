package com.shawnkowalchuk.milo.feature.trips

import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.platform.address.TripPlace

// Which words the line under a trip's times uses for where the trip went. Only the choice is
// made here, so that it is tested without a phone: the words themselves are in strings.xml.
// A row never shows coordinates and never leaves the line out for a trip that has places.

/** The line under a trip's times. */
internal sealed interface PlacesText {
    /** One sentence from strings.xml. */
    data class Sentence(val text: Int) : PlacesText

    /** "From (address)": the start of a trip in progress. */
    data class From(val address: String) : PlacesText

    /** "(from) → (to)": a finished trip with at least one address known. */
    data class FromTo(val from: PlaceSide, val to: PlaceSide) : PlacesText
}

/** One side of "from → to". */
internal sealed interface PlaceSide {
    data class Address(val line: String) : PlaceSide

    /** Words from strings.xml that stand where the address is missing. */
    data class Words(val text: Int) : PlaceSide
}

/**
 * What the row says about where the trip went, or null for a discarded trip, which has no such
 * line: its addresses are never looked up, and its own note says why it is not counted.
 */
internal fun TripLine.placesText(): PlacesText? = when (kind) {
    TripKind.DISCARDED -> null

    TripKind.IN_PROGRESS -> startText(from)

    // A finished trip always carries both; "not found" is the honest reading of a missing one.
    TripKind.COUNTED -> routeText(from ?: TripPlace.NotFound, to ?: TripPlace.NotFound)
}

/** A finished trip. One plain sentence when neither end is known, otherwise "from → to". */
internal fun routeText(from: TripPlace, to: TripPlace): PlacesText = when {
    from == TripPlace.LookingUp && to == TripPlace.LookingUp ->
        PlacesText.Sentence(R.string.trips_addresses_looking_up)

    from == TripPlace.NotFound && to == TripPlace.NotFound ->
        PlacesText.Sentence(R.string.trips_addresses_not_found)

    else -> PlacesText.FromTo(from.asSide(), to.asSide())
}

/**
 * A trip in progress: only where it started can be known.
 *
 * @param from null while the trip has no usable GPS fix, so there is nothing to look up yet.
 */
internal fun startText(from: TripPlace?): PlacesText = when (from) {
    null -> PlacesText.Sentence(R.string.trips_start_not_known_yet)
    is TripPlace.Known -> PlacesText.From(from.address)
    TripPlace.LookingUp -> PlacesText.Sentence(R.string.trips_start_looking_up)
    TripPlace.NotFound -> PlacesText.Sentence(R.string.trips_start_not_found)
}

private fun TripPlace.asSide(): PlaceSide = when (this) {
    is TripPlace.Known -> PlaceSide.Address(address)
    TripPlace.LookingUp -> PlaceSide.Words(R.string.trips_place_looking_up)
    TripPlace.NotFound -> PlaceSide.Words(R.string.trips_place_not_found)
}

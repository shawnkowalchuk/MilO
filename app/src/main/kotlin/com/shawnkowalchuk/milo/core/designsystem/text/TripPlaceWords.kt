package com.shawnkowalchuk.milo.core.designsystem.text

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.platform.address.TripPlace

// Which words say where a trip went: "from → to", or in plain words that an address is still
// being looked up, was not found, or was left empty. Only the choice is made here, so that it
// is tested without a phone: the words themselves are in strings.xml. A trip is never written
// as coordinates, and never as a gap.
//
// In one place because the home screen and the Trips screen both write trips, and a trip must
// read the same on both (moved here from the Trips screen on 2026-10-06, when Home began to
// write where a trip went). It is the second file of the design system that knows a type from
// outside it: `TripPlace`, what the address lookup knows about one end of a trip.

/** Where a trip went, as it is written. */
sealed interface PlacesText {
    /** One sentence from strings.xml. */
    data class Sentence(val text: Int) : PlacesText

    /** "From (address)": the start of a trip in progress. */
    data class From(val address: String) : PlacesText

    /** "(from) → (to)": a finished trip with at least one address known. */
    data class FromTo(val from: PlaceSide, val to: PlaceSide) : PlacesText
}

/** One side of "from → to". */
sealed interface PlaceSide {
    data class Address(val line: String) : PlaceSide

    /** Words from strings.xml that stand where the address is missing. */
    data class Words(val text: Int) : PlaceSide
}

/** A finished trip. One plain sentence when neither end is known, otherwise "from → to". */
fun routeText(from: TripPlace, to: TripPlace): PlacesText = when {
    from == TripPlace.LookingUp && to == TripPlace.LookingUp ->
        PlacesText.Sentence(R.string.trips_addresses_looking_up)

    from == TripPlace.NotFound && to == TripPlace.NotFound ->
        PlacesText.Sentence(R.string.trips_addresses_not_found)

    from == TripPlace.LeftBlank && to == TripPlace.LeftBlank ->
        PlacesText.Sentence(R.string.trips_addresses_left_blank)

    else -> PlacesText.FromTo(from.asSide(), to.asSide())
}

/**
 * A trip in progress: only where it started can be known.
 *
 * @param from null while the trip has no usable GPS fix, so there is nothing to look up yet.
 */
fun startText(from: TripPlace?): PlacesText = when (from) {
    null -> PlacesText.Sentence(R.string.trips_start_not_known_yet)

    is TripPlace.Known -> PlacesText.From(from.address)

    TripPlace.LookingUp -> PlacesText.Sentence(R.string.trips_start_looking_up)

    // A trip in progress has no address of Shawn's own; the two read alike if it ever did.
    TripPlace.NotFound, TripPlace.LeftBlank -> PlacesText.Sentence(R.string.trips_start_not_found)
}

private fun TripPlace.asSide(): PlaceSide = when (this) {
    is TripPlace.Known -> PlaceSide.Address(address)
    TripPlace.LookingUp -> PlaceSide.Words(R.string.trips_place_looking_up)
    TripPlace.NotFound -> PlaceSide.Words(R.string.trips_place_not_found)
    TripPlace.LeftBlank -> PlaceSide.Words(R.string.trips_place_left_blank)
}

/** The words themselves, as a screen writes them. */
@Composable
fun placesWords(places: PlacesText): String = when (places) {
    is PlacesText.Sentence -> stringResource(places.text)

    is PlacesText.From -> stringResource(R.string.trips_from, places.address)

    is PlacesText.FromTo ->
        stringResource(R.string.trips_from_to, places.from.words(), places.to.words())
}

@Composable
private fun PlaceSide.words(): String = when (this) {
    is PlaceSide.Address -> line
    is PlaceSide.Words -> stringResource(text)
}

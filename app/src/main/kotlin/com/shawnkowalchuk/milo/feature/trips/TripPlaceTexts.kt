package com.shawnkowalchuk.milo.feature.trips

import com.shawnkowalchuk.milo.core.designsystem.text.PlacesText
import com.shawnkowalchuk.milo.core.designsystem.text.routeText
import com.shawnkowalchuk.milo.core.designsystem.text.startText
import com.shawnkowalchuk.milo.platform.address.TripPlace

// Which line a row of the Trips screen has for where its trip went. The words for a route and
// for the start of a trip in progress are chosen in `core/designsystem/text/TripPlaceWords.kt`,
// which the home screen uses too; here it is only decided which kind of row says what.

/**
 * What the row says about where the trip went, or null for a row that has no such line: a
 * discarded trip, whose addresses are never looked up and whose own note says why it is not
 * counted, and a deleted trip that had no address yet when it was deleted.
 */
internal fun TripLine.placesText(): PlacesText? = when (kind) {
    TripKind.DISCARDED -> null

    // Only what was already stored: nothing is looked up for a trip while it is deleted.
    TripKind.DELETED ->
        if (from == null && to == null) {
            null
        } else {
            routeText(from ?: TripPlace.NotFound, to ?: TripPlace.NotFound)
        }

    TripKind.IN_PROGRESS -> startText(from)

    // A finished trip always carries both; "not found" is the honest reading of a missing one.
    TripKind.COUNTED -> routeText(from ?: TripPlace.NotFound, to ?: TripPlace.NotFound)
}

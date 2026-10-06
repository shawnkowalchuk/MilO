package com.shawnkowalchuk.milo.feature.trips

import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.platform.address.TripPlace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Which words the line under a trip's times uses, for every state an address can be in. The
 * words themselves are in `strings.xml`.
 */
class TripPlaceTextsTest {
    private val shop = TripPlace.Known("12 Shop Rd, Edmonton")
    private val leduc = TripPlace.Known("48 Main St, Leduc")

    private fun line(kind: TripKind, from: TripPlace? = null, to: TripPlace? = null) = TripLine(
        id = 1,
        startedAtMs = 0,
        endedAtMs = 1,
        distanceMetres = 1.0,
        kind = kind,
        from = from,
        to = to,
    )

    // ---- A finished trip --------------------------------------------------------------------------

    @Test
    fun `a finished trip with both addresses shows from and to`() {
        assertEquals(
            PlacesText.FromTo(
                PlaceSide.Address("12 Shop Rd, Edmonton"),
                PlaceSide.Address("48 Main St, Leduc"),
            ),
            routeText(shop, leduc),
        )
    }

    @Test
    fun `while both addresses are being looked up, one sentence says so`() {
        assertEquals(
            PlacesText.Sentence(R.string.trips_addresses_looking_up),
            routeText(TripPlace.LookingUp, TripPlace.LookingUp),
        )
    }

    @Test
    fun `when no address was found for either end, one sentence says so`() {
        assertEquals(
            PlacesText.Sentence(R.string.trips_addresses_not_found),
            routeText(TripPlace.NotFound, TripPlace.NotFound),
        )
    }

    @Test
    fun `a missing side says in words why it is missing, beside the address that is known`() {
        assertEquals(
            PlacesText.FromTo(
                PlaceSide.Address("12 Shop Rd, Edmonton"),
                PlaceSide.Words(R.string.trips_place_looking_up),
            ),
            routeText(shop, TripPlace.LookingUp),
        )
        assertEquals(
            PlacesText.FromTo(
                PlaceSide.Words(R.string.trips_place_not_found),
                PlaceSide.Address("48 Main St, Leduc"),
            ),
            routeText(TripPlace.NotFound, leduc),
        )
    }

    @Test
    fun `one side looked up and the other not found are each said`() {
        assertEquals(
            PlacesText.FromTo(
                PlaceSide.Words(R.string.trips_place_looking_up),
                PlaceSide.Words(R.string.trips_place_not_found),
            ),
            routeText(TripPlace.LookingUp, TripPlace.NotFound),
        )
    }

    // ---- A trip in progress -----------------------------------------------------------------------

    @Test
    fun `a trip in progress shows where it started once that is known`() {
        assertEquals(PlacesText.From("12 Shop Rd, Edmonton"), startText(shop))
    }

    @Test
    fun `a trip in progress says so while its start is unknown, looked up or not found`() {
        assertEquals(PlacesText.Sentence(R.string.trips_start_not_known_yet), startText(null))
        assertEquals(
            PlacesText.Sentence(R.string.trips_start_looking_up),
            startText(TripPlace.LookingUp),
        )
        assertEquals(
            PlacesText.Sentence(R.string.trips_start_not_found),
            startText(TripPlace.NotFound),
        )
    }

    // ---- By the kind of row -----------------------------------------------------------------------

    @Test
    fun `each kind of row gets the line that belongs to it`() {
        assertEquals(routeText(shop, leduc), line(TripKind.COUNTED, shop, leduc).placesText())
        assertEquals(startText(shop), line(TripKind.IN_PROGRESS, from = shop).placesText())
        assertEquals(startText(null), line(TripKind.IN_PROGRESS).placesText())
    }

    @Test
    fun `a discarded trip has no line about places`() {
        assertNull(line(TripKind.DISCARDED).placesText())
        assertNull(line(TripKind.DISCARDED, shop, leduc).placesText())
    }

    @Test
    fun `a deleted trip shows the addresses it had, and no line when it had none`() {
        assertEquals(routeText(shop, leduc), line(TripKind.DELETED, shop, leduc).placesText())
        // The side that was never stored is "no address found", never "looking up".
        assertEquals(
            PlacesText.FromTo(
                PlaceSide.Address("12 Shop Rd, Edmonton"),
                PlaceSide.Words(R.string.trips_place_not_found),
            ),
            line(TripKind.DELETED, from = shop).placesText(),
        )
        assertNull(line(TripKind.DELETED).placesText())
    }

    @Test
    fun `no state leaves the line of a finished trip or a trip in progress out`() {
        val states = listOf(shop, TripPlace.LookingUp, TripPlace.NotFound)

        for (from in states) {
            for (to in states) {
                assertEquals(routeText(from, to), line(TripKind.COUNTED, from, to).placesText())
            }
        }
    }
}

package com.shawnkowalchuk.milo.platform.address

/**
 * One place the phone's geocoder offered for a position, reduced to the parts MilO reads. Every
 * part can be missing: what a geocoder knows about a spot varies from a full street address to
 * nothing at all.
 *
 * A plain value, so that turning an answer into the line a trip shows ([addressLine]) is tested
 * without a phone.
 *
 * @param houseNumber the number on the street, such as "12".
 * @param street the street's name, such as "Shop Rd".
 * @param town the city, town or village.
 * @param district a part of a town; stands in for [town] where the geocoder names no town.
 * @param county the county or district around it; the next stand-in.
 * @param region the province or state; the last stand-in.
 * @param firstLine the geocoder's own one-line form of the address. It usually ends with the
 * postal code and the country, which MilO never shows.
 * @param country and [countryCode] and [postalCode] are read only to be taken out of
 * [firstLine].
 */
data class PlaceParts(
    val houseNumber: String? = null,
    val street: String? = null,
    val town: String? = null,
    val district: String? = null,
    val county: String? = null,
    val region: String? = null,
    val firstLine: String? = null,
    val country: String? = null,
    val countryCode: String? = null,
    val postalCode: String? = null,
)

/** What came back from asking for the address of one position. */
sealed interface LookupAnswer {
    /**
     * The geocoder answered. [candidates] are in its own order, the best match first, and the
     * list can be empty: a spot in a field has no address.
     */
    data class Places(val candidates: List<PlaceParts>) : LookupAnswer

    /**
     * No answer: the geocoder reported an error, did not reply in time, or the phone has none.
     *
     * @param reason in words, for the event log.
     */
    data class Failed(val reason: String) : LookupAnswer
}

/**
 * Asks for the address of a position. In the app this is Android's own geocoder
 * ([GeocoderAddressLookup]); an interface so that everything built on it is tested with a
 * stand-in.
 *
 * It never throws for a lookup that went wrong: that is a [LookupAnswer.Failed].
 */
fun interface AddressLookup {
    suspend fun lookUp(latitude: Double, longitude: Double): LookupAnswer
}

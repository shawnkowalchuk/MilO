package com.shawnkowalchuk.milo.platform.address

import android.content.Context
import android.location.Address
import android.location.Geocoder
import java.util.Locale
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * How many candidates are asked for. The first is usually the street address; the ones after it
 * are wider (the street, the district, the town) and are what [addressLine] falls back on.
 */
private const val MAX_CANDIDATES = 5

/**
 * How long an answer is waited for. Android promises no time at all for the listener form, and
 * a lookup that never answers must not hold up the trips waiting behind it.
 */
private const val ANSWER_TIMEOUT_MS = 20_000L

/**
 * Looks an address up with Android's own geocoder (`android.location.Geocoder`). It needs a
 * network connection and no key, and comes with no promise that it is available or right: on
 * this phone it is Google Play services answering.
 *
 * The position of a trip's start or end leaves the phone here, and nowhere else in MilO
 * (ENGINEERING_STANDARDS section 12).
 */
class GeocoderAddressLookup(context: Context) : AddressLookup {
    private val appContext = context.applicationContext

    override suspend fun lookUp(latitude: Double, longitude: Double): LookupAnswer {
        if (!Geocoder.isPresent()) return LookupAnswer.Failed("this phone has no geocoder")
        // Made for each lookup, so a change of the phone's language applies from the next one.
        val geocoder = Geocoder(appContext, Locale.getDefault())
        val answer =
            withTimeoutOrNull(ANSWER_TIMEOUT_MS) {
                try {
                    // The answer arrives on a listener, on one of Android's own threads, and
                    // exactly one of its two functions is called. If the wait has already
                    // given up, the late answer is dropped: resuming a cancelled continuation
                    // does nothing.
                    suspendCancellableCoroutine<LookupAnswer> { continuation ->
                        val listener =
                            object : Geocoder.GeocodeListener {
                                override fun onGeocode(addresses: MutableList<Address>) {
                                    continuation.resume(addresses.toAnswer())
                                }

                                // Not left to its default, which does nothing: an error would
                                // then look like an answer that never came.
                                override fun onError(errorMessage: String?) {
                                    val reason = errorMessage ?: "the geocoder gave no reason"
                                    continuation.resume(LookupAnswer.Failed(reason))
                                }
                            }
                        geocoder.getFromLocation(latitude, longitude, MAX_CANDIDATES, listener)
                    }
                } catch (refused: IllegalArgumentException) {
                    // Thrown for a latitude or longitude that is not a place on Earth.
                    LookupAnswer.Failed("the geocoder refused the position: ${refused.message}")
                }
            }
        return answer ?: LookupAnswer.Failed("no answer within ${ANSWER_TIMEOUT_MS / 1000} s")
    }
}

private fun List<Address>.toAnswer(): LookupAnswer = LookupAnswer.Places(map { it.toPlaceParts() })

private fun Address.toPlaceParts(): PlaceParts = PlaceParts(
    houseNumber = subThoroughfare,
    street = thoroughfare,
    town = locality,
    district = subLocality,
    county = subAdminArea,
    region = adminArea,
    firstLine = if (maxAddressLineIndex >= 0) getAddressLine(0) else null,
    country = countryName,
    countryCode = countryCode,
    postalCode = postalCode,
)

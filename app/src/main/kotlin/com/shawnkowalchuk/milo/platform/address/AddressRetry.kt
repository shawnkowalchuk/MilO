package com.shawnkowalchuk.milo.platform.address

import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.data.trip.Trip

// Which trips are due for an address lookup, when MilO stops asking, and what a trip's row says
// about each of its two ends meanwhile. Pure functions: the same rule decides what is looked up
// (TripAddresses) and what the Trips screen says, so the two cannot disagree.

/**
 * How many lookups may leave a trip without an address before MilO stops asking for it. Some
 * places have no address, and the geocoder is never asked about them for ever.
 */
// TODO(debt): there is no scheduled retry: a failed lookup waits for the next trip, process
//  start or visit to the Trips screen. A trip that has used up its attempts is not asked about
//  again; its address can be typed in on the edit screen. See docs/FINDINGS_LOG.md
//  (2026-10-05, and 2026-10-06 for the edit screen).
const val MAX_ADDRESS_ATTEMPTS = 4

private const val MINUTE_MS = 60_000L
private const val HOUR_MS = 60 * MINUTE_MS

/**
 * How long a trip waits after each failed attempt before the next: two minutes after the first,
 * an hour after the second, a day after the third. Short at first, because most failures are a
 * moment without signal; long at the end, so that the last attempt is made on another day.
 */
private val WAIT_AFTER_ATTEMPT_MS = listOf(2 * MINUTE_MS, HOUR_MS, 24 * HOUR_MS)

/** What is known about one end of a trip. */
sealed interface TripPlace {
    data class Known(val address: String) : TripPlace

    /** Not known yet. A lookup is under way, or will be tried again. */
    data object LookingUp : TripPlace

    /**
     * There is none to show: the lookups found nothing and MilO has stopped asking, or the trip
     * has no position for this end.
     */
    data object NotFound : TripPlace

    /**
     * Shawn left this address empty himself, in a trip he typed in or in the edit form. It is
     * his to fill in: MilO never looks it up.
     */
    data object LeftBlank : TripPlace
}

/** Where the trip started, as far as it is known. */
fun Trip.startPlace(): TripPlace =
    placeOf(startAddress, startAddressByHand, startLatitude, startLongitude)

/** Where the trip ended, as far as it is known. */
fun Trip.endPlace(): TripPlace = placeOf(endAddress, endAddressByHand, endLatitude, endLongitude)

private fun Trip.placeOf(
    address: String?,
    byHand: Boolean,
    latitude: Double?,
    longitude: Double?,
): TripPlace = when {
    address != null -> TripPlace.Known(address)

    // Before the positions are asked about: an address that is Shawn's own is never "still
    // being looked up", whatever the trip has for a position. This is what keeps the lookup
    // away from it ([isDueForLookup]); the update in storage has the same condition.
    byHand -> TripPlace.LeftBlank

    latitude == null || longitude == null -> TripPlace.NotFound

    addressAttempts >= MAX_ADDRESS_ATTEMPTS -> TripPlace.NotFound

    else -> TripPlace.LookingUp
}

/**
 * How long a trip that has failed [failedAttempts] times waits before its next lookup, or null
 * if it gets no further one.
 */
fun waitAfterAttemptMs(failedAttempts: Int): Long? = when {
    failedAttempts <= 0 -> 0L
    failedAttempts >= MAX_ADDRESS_ATTEMPTS -> null
    else -> WAIT_AFTER_ATTEMPT_MS[failedAttempts - 1]
}

/**
 * Whether [trip]'s addresses should be looked up at [nowMs].
 *
 * Only a finished trip is looked up: an open one has no stored position yet, and a discarded
 * or a deleted one is not counted. (A deleted trip that is restored, or a discarded one that is
 * counted after all, is a finished trip again and is due like any other.) It must lack an
 * address for an end whose position is known, must not have used up its attempts, and its last
 * attempt must be long enough ago. An end whose address is Shawn's own, typed or left empty, is
 * never lacking, so a trip that was added by hand is never due.
 */
fun isDueForLookup(trip: Trip, nowMs: Long): Boolean {
    if (trip.status != TripStatus.FINISHED) return false
    val lacksStart = trip.startPlace() == TripPlace.LookingUp
    val lacksEnd = trip.endPlace() == TripPlace.LookingUp
    if (!lacksStart && !lacksEnd) return false
    val waitMs = waitAfterAttemptMs(trip.addressAttempts) ?: return false
    val lastAttemptAtMs = trip.addressLastAttemptAtMs ?: return true
    // A last attempt in the future means the phone's clock has been set back since. Waiting for
    // the clock to catch up could take for ever; one attempt too early costs nothing, and the
    // number of attempts is still limited.
    return nowMs < lastAttemptAtMs || nowMs - lastAttemptAtMs >= waitMs
}

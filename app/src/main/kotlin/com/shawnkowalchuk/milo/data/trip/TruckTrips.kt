package com.shawnkowalchuk.milo.data.trip

import com.shawnkowalchuk.milo.core.odometer.DrivenTrip
import com.shawnkowalchuk.milo.core.trip.TripStatus

/**
 * Whether this trip moved the truck's odometer (Shawn's choice of 2026-10-07: "Every truck
 * trip"): a counted trip ([isCounted]: finished, so not one in progress, discarded or deleted),
 * Business, Personal or not sorted yet, that was recorded with the truck connected or typed in
 * by hand. A trip started with the button that the truck never joined was in some other
 * vehicle, and a drive in another vehicle that the parked truck's watch started is gone from the
 * table altogether.
 */
val Trip.movesOdometer: Boolean get() = isCounted && (truckSeen || addedByHand)

/**
 * The trip being recorded, as far as an odometer needs it (Shawn's request of 2026-10-09: "can we
 * have the mileage on the home screen change as we drive"; he chose the odometer, on Home and in
 * Settings alike). Plain values, so that the data layer need not know the trip controller.
 *
 * @param metres the distance counted so far.
 * @param truckSeen whether a paired vehicle has been seen connected in it. Until then it moves
 * no odometer, as the finished trip would not ([movesOdometer]).
 * @param vehicle the paired vehicle it is in, once known.
 */
data class TripSoFar(
    val tripId: Long,
    val startedAtMs: Long,
    val metres: Double,
    val truckSeen: Boolean,
    val vehicle: String? = null,
)

/**
 * The trips among [trips] that moved an odometer, as the odometer counts them, each with the
 * vehicle it was in (since 2026-10-08): each vehicle's odometer takes its own (`drivenIn`).
 * Each also carries its number (since 2026-10-10): a reading typed while the trip was being
 * recorded names it, and the odometer cuts the trip at that reading (`cutAtReadings`).
 *
 * @param soFar the trip being recorded, which is counted with them while it is driven (since
 * 2026-10-09), once a paired vehicle has been seen in it. It is left out as soon as [trips] holds
 * the same trip as ended: storage says that a trip has ended a moment before the trip controller
 * does, and the trip must not be counted twice in between.
 */
fun drivenTrips(trips: List<Trip>, soFar: TripSoFar? = null): List<DrivenTrip> {
    val ended = trips
        .filter { it.movesOdometer }
        .map { DrivenTrip(it.startedAtMs, it.distanceMetres, it.vehicleAddress, it.id) }
    val hasEnded = trips.any { it.id == soFar?.tripId && it.status != TripStatus.OPEN }
    if (soFar == null || !soFar.truckSeen || hasEnded) return ended
    return ended + DrivenTrip(soFar.startedAtMs, soFar.metres, soFar.vehicle, soFar.tripId)
}

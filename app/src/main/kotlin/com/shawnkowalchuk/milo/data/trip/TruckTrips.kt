package com.shawnkowalchuk.milo.data.trip

import com.shawnkowalchuk.milo.core.odometer.DrivenTrip

/**
 * Whether this trip moved the truck's odometer (Shawn's choice of 2026-10-07: "Every truck
 * trip"): a counted trip ([isCounted]: finished, so not one in progress, discarded or deleted),
 * Business, Personal or not sorted yet, that was recorded with the truck connected or typed in
 * by hand. A trip started with the button that the truck never joined was in some other
 * vehicle, and a drive in another vehicle that the parked truck's watch started is gone from the
 * table altogether.
 */
val Trip.movesOdometer: Boolean get() = isCounted && (truckSeen || addedByHand)

/** The trips among [trips] that moved the odometer, as the odometer counts them. */
fun drivenTrips(trips: List<Trip>): List<DrivenTrip> =
    trips.filter { it.movesOdometer }.map { DrivenTrip(it.startedAtMs, it.distanceMetres) }

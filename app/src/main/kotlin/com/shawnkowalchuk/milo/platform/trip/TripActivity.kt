package com.shawnkowalchuk.milo.platform.trip

import com.shawnkowalchuk.milo.core.trip.TripStartCause
import com.shawnkowalchuk.milo.core.trip.TripState
import com.shawnkowalchuk.milo.platform.system.PreflightProblem

/**
 * What the screens show about trip recording at this moment. The phone's home screen and the
 * Android Auto screen both read it from [TripController.activity]; neither keeps a copy.
 *
 * @param trip the trip being recorded, or null when idle.
 * @param startFailure why the last attempt to start recording failed, or null. It is cleared
 * when a trip starts. The same failure is shown as a notification, but notifications can be
 * switched off, so the screen has to be able to say it too.
 * @param truckConnected what the trip rules believe about the truck's Bluetooth connection, or
 * null before the stored state has been picked up. The Android Auto screen shows it. It is a
 * belief, not a fresh reading: while no trip is open nothing reads the truck unless an event
 * arrives or MilO is opened on the phone.
 */
data class TripActivity(
    val trip: CurrentTrip? = null,
    val startFailure: StartFailure? = null,
    val truckConnected: Boolean? = null,
)

/**
 * The trip in progress.
 *
 * @param distanceMetres the distance counted so far. The final figure is worked out again from
 * the stored points when the trip closes, and can be a little lower: what was recorded after the
 * truck disconnected is cut off.
 * @param waitingForTruck true while the grace period runs: the truck has gone and the trip ends
 * unless it comes back.
 * @param startLatitude where the trip started: its first usable GPS fix, with [startLongitude].
 * Null until there is one. The stored row has no position until the trip closes, so this is
 * the only place the start of a trip in progress can be read (the address lookup does).
 */
data class CurrentTrip(
    val tripId: Long,
    val startedAtMs: Long,
    val startedBy: TripStartCause,
    val distanceMetres: Double,
    val waitingForTruck: Boolean,
    val startLatitude: Double? = null,
    val startLongitude: Double? = null,
)

/**
 * Why recording could not start.
 *
 * @param problems what the preflight found. Empty when the preflight passed and Android still
 * refused the foreground service.
 * @param detail the exception Android threw, for the event log. Null for a preflight failure.
 */
data class StartFailure(val problems: List<PreflightProblem>, val detail: String? = null)

/**
 * What to show, from what the controller holds: the open trip row with its running distance,
 * and what the trip rules know. A trip is shown only when both agree that one is open.
 */
internal fun tripActivityOf(
    open: OpenTrip?,
    known: TripState?,
    startFailure: StartFailure?,
): TripActivity {
    val trip = known?.trip
    val truckConnected = known?.truckConnected
    if (open == null || trip == null) {
        return TripActivity(startFailure = startFailure, truckConnected = truckConnected)
    }
    return TripActivity(
        trip =
            CurrentTrip(
                tripId = open.id,
                startedAtMs = open.startedAtMs,
                startedBy = open.startedBy,
                distanceMetres = open.progress.distance.metres,
                waitingForTruck = trip.grace != null,
                startLatitude = open.progress.distance.firstAccepted?.latitude,
                startLongitude = open.progress.distance.firstAccepted?.longitude,
            ),
        startFailure = startFailure,
        truckConnected = truckConnected,
    )
}

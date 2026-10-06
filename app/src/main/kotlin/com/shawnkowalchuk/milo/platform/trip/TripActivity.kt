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
 */
data class TripActivity(val trip: CurrentTrip? = null, val startFailure: StartFailure? = null)

/**
 * The trip in progress.
 *
 * @param distanceMetres the distance counted so far. The final figure is worked out again from
 * the stored points when the trip closes, and can be a little lower: what was recorded after the
 * truck disconnected is cut off.
 * @param waitingForTruck true while the grace period runs: the truck has gone and the trip ends
 * unless it comes back.
 */
data class CurrentTrip(
    val tripId: Long,
    val startedAtMs: Long,
    val startedBy: TripStartCause,
    val distanceMetres: Double,
    val waitingForTruck: Boolean,
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
    if (open == null || trip == null) return TripActivity(startFailure = startFailure)
    return TripActivity(
        trip =
            CurrentTrip(
                tripId = open.id,
                startedAtMs = open.startedAtMs,
                startedBy = open.startedBy,
                distanceMetres = open.progress.distance.metres,
                waitingForTruck = trip.grace != null,
            ),
        startFailure = startFailure,
    )
}

package com.shawnkowalchuk.milo.data.trip

import com.shawnkowalchuk.milo.core.trip.TripStatus

// Which stored trips count, and what one day of them adds up to. Pure functions, shared by the
// Trips screen, the home screen and the Android Auto screen, so that the three can never count
// a trip differently.

/**
 * Whether this trip is in a total: a finished trip, and nothing else. A trip in progress is not
 * counted until it ends, and a discarded or deleted one never is. A deleted trip that is
 * restored, or a discarded one that Shawn counts after all, is finished again and so counts.
 */
val Trip.isCounted: Boolean get() = status == TripStatus.FINISHED

/**
 * One of today's finished trips, as the home screen lists it.
 *
 * @param endedAtMs null only for a row that storage should never produce: a finished trip
 * without an end.
 */
data class TodaySession(
    val tripId: Long,
    val startedAtMs: Long,
    val endedAtMs: Long?,
    val distanceMetres: Double,
) {
    /**
     * How long the trip lasted. Never negative: a trip's stored start can be later than its end
     * when the phone corrected its clock during the trip.
     */
    val driveTimeMs: Long get() = ((endedAtMs ?: startedAtMs) - startedAtMs).coerceAtLeast(0)
}

/**
 * Today's finished trips, newest first.
 *
 * The totals are added up in metres and milliseconds and rounded once, when they are shown, like
 * the month totals of the Trips screen, so the rounding of single trips never accumulates.
 */
data class TodayTrips(val sessions: List<TodaySession>) {
    val count: Int get() = sessions.size

    val totalMetres: Double get() = sessions.sumOf { it.distanceMetres }

    val driveTimeMs: Long get() = sessions.sumOf { it.driveTimeMs }
}

/**
 * What counts as "today": the trips that started today and are counted ([isCounted]). The trip
 * in progress is left out until it ends; the screens show it apart.
 *
 * @param startedToday every trip that started today, whatever its status, in any order.
 */
fun todayTrips(startedToday: List<Trip>): TodayTrips = TodayTrips(
    startedToday
        .filter { it.isCounted }
        .sortedWith(compareByDescending<Trip> { it.startedAtMs }.thenByDescending { it.id })
        .map { TodaySession(it.id, it.startedAtMs, it.endedAtMs, it.distanceMetres) },
)

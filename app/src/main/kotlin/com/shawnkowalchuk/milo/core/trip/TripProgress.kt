package com.shawnkowalchuk.milo.core.trip

/**
 * The running picture of the open trip, built up one fix at a time: what the screens show while
 * it is being recorded, and what the rules need to know about it.
 *
 * It holds nothing that is not in the stored points, so after a restart it is rebuilt from them
 * with [of] and comes out the same.
 *
 * @param distance the distance calculation so far.
 * @param lastMovementAtMs wall-clock time of the newest fix that added distance, or null if the
 * truck has not moved yet. Feeds the no-movement guard for manual trips.
 * @param lastFixAtMs wall-clock time of the newest fix of any quality, or null before the first.
 * After a restart this is how old the trip's last sign of life is.
 * @param fixCount every fix received, used or not.
 */
data class TripProgress(
    val distance: DistanceState = DistanceState(),
    val lastMovementAtMs: Long? = null,
    val lastFixAtMs: Long? = null,
    val fixCount: Int = 0,
) {
    /** The picture after one more fix. */
    fun plus(point: TrackPoint, limits: DistanceLimits = DistanceLimits()): TripProgress {
        val measured = DistanceCalculator.add(distance, point, limits)
        // A fix can also take distance back (rule 4 of the calculator). Only a gain is movement.
        val moved = measured.metres > distance.metres
        return TripProgress(
            distance = measured,
            lastMovementAtMs = if (moved) point.wallClockMs else lastMovementAtMs,
            lastFixAtMs = point.wallClockMs,
            fixCount = fixCount + 1,
        )
    }

    companion object {
        /** The picture after all of [points], which must be in the order they were recorded. */
        fun of(points: List<TrackPoint>, limits: DistanceLimits = DistanceLimits()): TripProgress =
            points.fold(TripProgress()) { progress, point -> progress.plus(point, limits) }
    }
}

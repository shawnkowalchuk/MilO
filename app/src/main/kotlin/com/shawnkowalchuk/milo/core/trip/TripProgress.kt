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
 * truck has not moved yet. Feeds the parked rule. A fix whose distance the next fix takes back
 * (one bad fix while the truck stands) stops counting here in the same moment.
 * @param lastFixAtMs wall-clock time of the newest fix of any quality, or null before the first.
 * After a restart this is how old the trip's last sign of life is.
 * @param fixCount every fix received, used or not.
 * @param drivenAtMs wall-clock time of the first fix that showed the truck driving ([plus]), or
 * null while none has: the moment for the trip-start sound (Shawn's choice of 2026-10-08, "When
 * the truck drives off"). Read from the stored points like the rest, so a trip picked up after a
 * restart that had driven off already does not play the sound again.
 */
data class TripProgress(
    val distance: DistanceState = DistanceState(),
    val lastMovementAtMs: Long? = null,
    val lastFixAtMs: Long? = null,
    val fixCount: Int = 0,
    val drivenAtMs: Long? = null,
) {
    /** The picture after one more fix. */
    fun plus(point: TrackPoint, limits: DistanceLimits = DistanceLimits()): TripProgress {
        val measured = DistanceCalculator.add(distance, point, limits)
        return TripProgress(
            distance = measured,
            // Read from the calculation, not kept beside it: a fix can also take distance back
            // (its rule 4), and the movement it took back must go with it.
            lastMovementAtMs = measured.lastCountedAtMs,
            lastFixAtMs = point.wallClockMs,
            fixCount = fixCount + 1,
            drivenAtMs = drivenAtMs ?: point.wallClockMs.takeIf { showsDriving(point, measured) },
        )
    }

    /**
     * Whether [point] shows the truck driving, by the test the watch on a parked truck uses
     * ([DRIVING_OFF_KMH], `ParkedWatch`): a usable fix whose speed reading is 15 km/h or more.
     * A usable fix without a speed reading counts if distance was counted up to it, at that
     * speed or more from the usable fix before it. [measured] is the calculation with [point].
     *
     * Unlike beside a parked truck, one fast fix is enough and is not borne out: the answer
     * only decides when a sound plays, never whether a trip starts or how far it went.
     */
    private fun showsDriving(point: TrackPoint, measured: DistanceState): Boolean {
        if (measured.acceptedCount == distance.acceptedCount) return false
        val reported = point.speedMetresPerSecond
        if (reported != null) return reported >= DRIVING_OFF_METRES_PER_SECOND
        return measured.lastCountedAtMs == point.wallClockMs &&
            point.metresPerSecondFrom(distance.lastAccepted) >= DRIVING_OFF_METRES_PER_SECOND
    }

    companion object {
        /** The picture after all of [points], which must be in the order they were recorded. */
        fun of(points: List<TrackPoint>, limits: DistanceLimits = DistanceLimits()): TripProgress =
            points.fold(TripProgress()) { progress, point -> progress.plus(point, limits) }
    }
}

/**
 * What the trip rules have to be told about movement after one more fix, or null if the fix
 * changed nothing about it. [before] is the picture without the fix, the receiver the picture
 * with it.
 *
 * - The truck moved: [TripEvent.Moved], at the time distance was last counted.
 * - The movement last reported was one bad fix, which the calculation has now taken back:
 *   [TripEvent.MoveTakenBack], with the time of the movement before it, or [tripStartedAtMs] if
 *   there was none. Left untold, one stray fix every few minutes would keep a parked trip open.
 *
 * @param atMs wall-clock time of the fix.
 */
fun TripProgress.movementSince(
    before: TripProgress,
    tripStartedAtMs: Long,
    atMs: Long,
): TripEvent? {
    val was = before.lastMovementAtMs
    val now = lastMovementAtMs
    return when {
        now == was -> null
        now != null && (was == null || now > was) -> TripEvent.Moved(now)
        else -> TripEvent.MoveTakenBack(lastMovedAtMs = now ?: tripStartedAtMs, atMs = atMs)
    }
}

package com.shawnkowalchuk.milo.core.trip

/**
 * A fix that shows the parked truck somewhere else counts only if a later fix bears it out
 * within this long: four of the watch's fixes. Left to wait longer, one displaced fix followed
 * by a night of fixes too poor to use (a phone indoors) would be "borne out" by the first good
 * fix of the morning, and the trip would be dated the evening before.
 */
const val MOVEMENT_BORNE_OUT_WITHIN_MS = 2 * 60_000L

/**
 * The watch on a truck that is connected and parked (see [Parked]): has it driven off?
 *
 * The question is answered by the distance calculation itself, run over the place the truck is
 * parked at and the fixes that arrive while MilO waits. So "moved" means here exactly what it
 * means for the kilometres of a trip: further than GPS jitter can explain (rule 3), and not one
 * bad fix that the next fix takes back (rule 4). The second half is why the truck has moved only
 * once the distance is [DistanceState.settledMetres]: a second fix has borne the first out.
 * Starting a trip on the first fix alone would start one on every stray fix of the night.
 *
 * A plain value, built up one fix at a time like [TripProgress].
 *
 * @param distance the calculation so far. Its first accepted fix is the parked place.
 * @param sinceStirred every fix since the distance last stood at zero, in order: the fixes that
 * show the movement. They become the first points of the trip the movement starts.
 */
data class ParkedWatch(
    val distance: DistanceState = DistanceState(),
    val sinceStirred: List<TrackPoint> = emptyList(),
) {
    /** Where the truck is parked: the end of the trip before, or the first usable fix. */
    val place: TrackPoint? get() = distance.firstAccepted

    /**
     * Wall-clock time of the fix that first showed the truck somewhere else, once a later fix
     * has borne it out. Null while the truck stands. It is when the next trip starts.
     */
    val movedAtMs: Long?
        get() = distance.settledAnchor?.wallClockMs?.takeIf { distance.settledMetres > 0.0 }

    /**
     * The first points of the trip that the movement starts: the parked place, then the fixes
     * that showed the movement. With the place in front, the trip starts where the truck was
     * parked, and the stretch to the first fix is counted like any other. The trip controller
     * stores exactly these (`TripParking.startTrip`).
     */
    val tripStart: List<TrackPoint> get() = listOfNotNull(place) + sinceStirred

    /** The watch after one more fix. */
    fun plus(fix: TrackPoint, limits: DistanceLimits = DistanceLimits()): ParkedWatch {
        val watch = if (waitedTooLongFor(fix)) at(place, limits) else this
        val measured = DistanceCalculator.add(watch.distance, fix, limits)
        return ParkedWatch(
            distance = measured,
            sinceStirred = if (measured.metres > 0.0) watch.sinceStirred + fix else emptyList(),
        )
    }

    /**
     * Whether one fix has shown the truck somewhere else and nothing has borne it out by the
     * time [next] arrives ([MOVEMENT_BORNE_OUT_WITHIN_MS]). The watch then starts again from
     * the parked place, and [next] is judged as a first fix. Measured on the clock that counts
     * from boot, which both fixes carry and which never jumps.
     */
    private fun waitedTooLongFor(next: TrackPoint): Boolean {
        val unconfirmed = distance.metres > 0.0 && distance.settledMetres == 0.0
        val displaced = distance.anchor?.takeIf { unconfirmed } ?: return false
        return next.elapsedRealtimeMs - displaced.elapsedRealtimeMs > MOVEMENT_BORNE_OUT_WITHIN_MS
    }

    companion object {
        /**
         * A watch on a truck parked at [place], or with the place still to be found if null
         * (the trip before it had no usable fix). The first usable fix then becomes the place.
         */
        fun at(place: TrackPoint?, limits: DistanceLimits = DistanceLimits()): ParkedWatch =
            if (place == null) {
                ParkedWatch()
            } else {
                ParkedWatch(DistanceCalculator.add(DistanceState(), place, limits))
            }

        /**
         * Where the truck stands when the parked rule has closed a trip: the trip's end, which
         * is where it last moved. A trip that never moved has no such end (it is cut at its
         * start, before its first fix), but its fixes were all taken while it stood, so the
         * newest usable one of them is the place. Without it the first fix of the wait, up to
         * half a minute later, would stand in, and a truck that drove off in that half minute
         * would lose the stretch up to it.
         *
         * @param points every stored point of the closed trip, in the order recorded.
         */
        fun placeAfter(
            closed: ClosedTrip,
            points: List<TrackPoint>,
            limits: DistanceLimits = DistanceLimits(),
        ): TrackPoint? = closed.end ?: DistanceCalculator.measure(points, limits).lastAccepted
    }
}

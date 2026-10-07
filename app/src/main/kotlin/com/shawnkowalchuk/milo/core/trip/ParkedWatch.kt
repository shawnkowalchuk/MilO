package com.shawnkowalchuk.milo.core.trip

/**
 * A fix that shows the parked truck somewhere else dates the next trip only if a later fix bears
 * it out within this long: four of the watch's fixes. Left to wait longer, one displaced fix
 * followed by a night of fixes too poor to use (a phone indoors) would be "borne out" by the
 * first good fix of the morning, and the trip would be dated the evening before.
 *
 * A fix that comes later than this still counts as a sighting: see [ParkedWatch.seenAwayBefore].
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
 * TODO(debt): the first usable fix of a watch is checked for an impossible jump against a place
 *  that is at least ten minutes old, so one fix that is wrong by kilometres and claims to be
 *  good is believed, and the calculation's re-anchor rule then keeps its distance: a trip the
 *  truck never drove. The calculation's own known limit. See docs/FINDINGS_LOG.md, 2026-10-06.
 *
 * @param distance the calculation so far. Its first accepted fix is the parked place.
 * @param sinceStirred every fix since the distance last stood at zero, in order: the fixes that
 * show the movement. They become the first points of the trip the movement starts.
 * @param seenAwayBefore true once a fix that showed the truck somewhere else has been dropped
 * because nothing bore it out in time ([MOVEMENT_BORNE_OUT_WITHIN_MS]), and until a usable fix
 * shows the truck at the parked place again. The next usable fix that is also away from the
 * place is then the second sighting, and the truck has moved, dated at that fix and never at the
 * dropped one. Without it, a truck driven off while usable fixes come more than two minutes
 * apart would never be seen to move: each fix would be a first sighting, dropped in its turn,
 * and the whole drive would go unrecorded. A missed trip is worse than a short one that the
 * parked rule discards.
 */
data class ParkedWatch(
    val distance: DistanceState = DistanceState(),
    val sinceStirred: List<TrackPoint> = emptyList(),
    val seenAwayBefore: Boolean = false,
) {
    /** Where the truck is parked: the end of the trip before, or the first usable fix. */
    val place: TrackPoint? get() = distance.firstAccepted

    /**
     * When the next trip starts, or null while the truck stands: the wall-clock time of the fix
     * that first showed the truck somewhere else, once a later fix has borne it out. If that
     * first fix was dropped as too old ([seenAwayBefore]), it is the time of the later fix.
     */
    val movedAtMs: Long?
        get() = when {
            distance.settledMetres > 0.0 -> distance.settledAnchor?.wallClockMs
            seenAwayBefore && distance.metres > 0.0 -> distance.anchor?.wallClockMs
            else -> null
        }

    /**
     * The first points of the trip that the movement starts: the parked place, then the fixes
     * that showed the movement. With the place in front, the trip starts where the truck was
     * parked, and the stretch to the first fix is counted like any other. The trip controller
     * stores exactly these (`TripParking.startTrip`).
     */
    val tripStart: List<TrackPoint> get() = listOfNotNull(place) + sinceStirred

    /** The watch after one more fix. */
    fun plus(fix: TrackPoint, limits: DistanceLimits = DistanceLimits()): ParkedWatch {
        val tooLate = waitedTooLongFor(fix)
        val watch = if (tooLate) at(place, limits) else this
        val measured = DistanceCalculator.add(watch.distance, fix, limits)
        val away = measured.metres > 0.0
        // A usable fix at the parked place: the truck stands, whatever an older fix showed.
        val seenAtPlace = !away && measured.acceptedCount > watch.distance.acceptedCount
        return ParkedWatch(
            distance = measured,
            sinceStirred = if (away) watch.sinceStirred + fix else emptyList(),
            seenAwayBefore = (seenAwayBefore || tooLate) && !seenAtPlace,
        )
    }

    /**
     * Whether one fix has shown the truck somewhere else and nothing has borne it out by the
     * time [next] arrives ([MOVEMENT_BORNE_OUT_WITHIN_MS]). That fix is then dropped: the
     * distance starts again from the parked place, so [next] is measured from there, and the
     * dropped fix is remembered only as [seenAwayBefore]. Measured on the clock that counts
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
         * TODO(debt): a trip's end is its last fix that counted distance, and the truck can come
         *  to rest a few metres past it. If that is close to the movement threshold, GPS scatter
         *  starts a short trip during the wait, which the parked rule discards. Cure: watch from
         *  the newest usable fix, and still start the next trip at this end. See
         *  docs/FINDINGS_LOG.md, 2026-10-06 (evening).
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

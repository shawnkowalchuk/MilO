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
 * A parked truck has driven off once it moves at this speed or more (Shawn's decision of
 * 2026-10-07: "Speed over 15 km/h"). A person walks at about 5 km/h, so walking about a site with
 * the phone, the truck still connected, never starts a trip, as it did while being further from
 * the parked place than GPS jitter was enough; a truck pulling out of a yard is past it within
 * its first fixes.
 */
const val DRIVING_OFF_KMH = 15.0

/** [DRIVING_OFF_KMH] in metres a second, the unit of a fix's speed reading. */
internal const val DRIVING_OFF_METRES_PER_SECOND = DRIVING_OFF_KMH / 3.6

/**
 * The trip that driving off starts is dated at the first fix of the last stretch of this length
 * that kept the truck away from its parked place, and holds those fixes, less the ones at its
 * start that read the truck as standing ([AT_REST_METRES_PER_SECOND]). A truck that rolled on a
 * few metres after its trip ended, and stood there an hour before it was driven off, is not given
 * that hour: its trip starts with the drive. The stretch from the parked place to the first of
 * these fixes is still counted (see [ParkedWatch.tripStart]).
 */
const val DRIVING_OFF_LOOKBACK_MS = 2 * 60_000L

/** A speed reading under this, 3.6 km/h, is a truck standing, or barely creeping. */
private const val AT_REST_METRES_PER_SECOND = 1.0f

/**
 * The watch on a truck that is connected and parked (see [Parked]): has it driven off?
 *
 * **It has once a fix shows it away from the parked place, moving at [DRIVING_OFF_KMH] or more**
 * (since 2026-10-07). "Away" is answered by the distance calculation itself, run over the place
 * the truck is parked at and the fixes that arrive while MilO waits, so it means exactly what it
 * means for the kilometres of a trip: further than GPS jitter can explain (rule 3). The speed is
 * the phone's own reading, from the satellites' Doppler shift: a truck standing still reads
 * nought however its position scatters, so one fix that has both is enough. A fix without a
 * speed reading is judged by the rule the watch had before, with a speed worked out from the
 * positions: the distance has to be borne out by a second fix (rule 4,
 * [DistanceState.settledMetres]), so that one stray fix cannot start a trip, and the step from
 * the fix before has to be as fast.
 *
 * Until 2026-10-07 the truck had driven off as soon as it was borne out to be further away than
 * jitter, at any speed. A phone carried about near the connected truck then started a trip.
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
 * show the truck away from its parked place. Once it has driven off, only the last stretch of
 * them ([DRIVING_OFF_LOOKBACK_MS]), which become the first points of the trip it starts.
 * @param seenAwayBefore true once a fix that showed the truck somewhere else has been dropped
 * because nothing bore it out in time ([MOVEMENT_BORNE_OUT_WITHIN_MS]), and until a usable fix
 * shows the truck at the parked place again. The next usable fix that is also away from the
 * place is then the second sighting, and the truck has moved, dated at that fix and never at the
 * dropped one. Without it, a truck driven off while usable fixes come more than two minutes
 * apart would never be seen to move: each fix would be a first sighting, dropped in its turn,
 * and the whole drive would go unrecorded. A missed trip is worse than a short one that the
 * parked rule discards.
 * @param drivenOffAtMs see [movedAtMs]: set by [plus] once, never from outside.
 */
data class ParkedWatch(
    val distance: DistanceState = DistanceState(),
    val sinceStirred: List<TrackPoint> = emptyList(),
    val seenAwayBefore: Boolean = false,
    private val drivenOffAtMs: Long? = null,
) {
    /** Where the truck is parked: the end of the trip before, or the first usable fix. */
    val place: TrackPoint? get() = distance.firstAccepted

    /**
     * When the next trip starts, or null while the truck stands: the wall-clock time of the first
     * of the fixes that show it driving off (see [DRIVING_OFF_LOOKBACK_MS]), once one of them
     * has shown it fast enough. It does not change after that.
     */
    val movedAtMs: Long? get() = drivenOffAtMs

    /**
     * When the distance alone says the truck is away from its parked place, as the watch decided
     * until 2026-10-07: the fix that first showed it somewhere else, once a later fix has borne
     * it out. If that first fix was dropped as too old ([seenAwayBefore]), the later fix.
     */
    private val awayAtMs: Long?
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
        // Once the truck has driven off, the trip it starts is the controller's to open. A fix
        // that crosses with that is part of the drive, wherever it shows the truck.
        if (drivenOffAtMs != null) {
            return copy(
                distance = DistanceCalculator.add(distance, fix, limits),
                sinceStirred = sinceStirred + fix,
            )
        }
        val tooLate = waitedTooLongFor(fix)
        val watch = if (tooLate) at(place, limits) else this
        val measured = DistanceCalculator.add(watch.distance, fix, limits)
        val away = measured.metres > 0.0
        val usable = measured.acceptedCount > watch.distance.acceptedCount
        // A usable fix at the parked place: the truck stands, whatever an older fix showed.
        val seenAtPlace = !away && usable
        val next =
            ParkedWatch(
                distance = measured,
                sinceStirred = if (away) watch.sinceStirred + fix else emptyList(),
                seenAwayBefore = (seenAwayBefore || tooLate) && !seenAtPlace,
            )
        val drivingOff =
            away && usable &&
                when (val reported = fix.speedMetresPerSecond) {
                    // From the usable fix before this one, also if that was dropped as too old
                    // to date the trip: a truck driven off while usable fixes come minutes
                    // apart is still seen to go fast.
                    null ->
                        next.awayAtMs != null &&
                            fix.metresPerSecondFrom(distance.lastAccepted) >=
                            DRIVING_OFF_METRES_PER_SECOND

                    else -> reported >= DRIVING_OFF_METRES_PER_SECOND
                }
        if (!drivingOff) return next
        // This fix is fast, so it is always kept, and the trip is never dated after it.
        val drive =
            next.sinceStirred
                .filter { fix.elapsedRealtimeMs - it.elapsedRealtimeMs <= DRIVING_OFF_LOOKBACK_MS }
                .dropWhile { it !== fix && it.isAtRest() }
        return next.copy(sinceStirred = drive, drivenOffAtMs = drive.first().wallClockMs)
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

    private fun TrackPoint.isAtRest(): Boolean =
        speedMetresPerSecond?.let { it < AT_REST_METRES_PER_SECOND } ?: false

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

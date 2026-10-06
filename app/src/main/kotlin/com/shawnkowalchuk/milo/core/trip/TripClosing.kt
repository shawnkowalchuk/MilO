package com.shawnkowalchuk.milo.core.trip

/**
 * A trip as it is written when it closes.
 *
 * @param status [TripStatus.FINISHED], or [TripStatus.DISCARDED] when it was under the minimum
 * trip distance.
 * @param endedAtMs wall-clock time the trip ended.
 * @param start where the trip began, or null if no usable fix was recorded.
 * @param end where the trip ended, or null if no usable fix was recorded.
 * @param distance the distance and the fix counts behind it, for the event log.
 */
data class ClosedTrip(
    val status: TripStatus,
    val endedAtMs: Long,
    val start: TrackPoint?,
    val end: TrackPoint?,
    val distance: DistanceState,
)

/**
 * Works out how a trip ends from its stored points. Two rules of ADR-002 live here:
 *
 * - **A trip is closed at its last recorded point, not at the end of the grace period.** The
 *   state machine says how late that point may be ([TripEffect.EndTrip.lastPointNotAfterMs]: the
 *   moment the truck was found gone). Fixes recorded after that, while the grace period ran,
 *   are Shawn walking away with the phone. They count for nothing: not for the end time, not
 *   for the distance, not for the end position.
 * - **A trip under the minimum distance is discarded.** So is a false start, whatever distance
 *   the phone covered in its few seconds.
 */
object TripClosing {
    /**
     * @param points every stored point of the trip, in the order recorded.
     * @param lastPointNotAfterMs wall-clock cut-off from the state machine. The trip ends at the
     * last point that is followed only by points later than this.
     * @param minimumDistanceMetres the setting; a trip shorter than this is discarded.
     * @param falseStart true when the trip ended as [TripEndReason.FALSE_START]. It was opened
     * on the word of the companion callback alone and never confirmed, so it is discarded even
     * if the phone happened to be moving fast enough to cover the minimum distance.
     */
    fun close(
        points: List<TrackPoint>,
        lastPointNotAfterMs: Long,
        minimumDistanceMetres: Double,
        limits: DistanceLimits = DistanceLimits(),
        falseStart: Boolean = false,
    ): ClosedTrip {
        // Cut by stored order: only the fixes at the end of the list that are later than the
        // cut-off are dropped. Filtering every fix by its time would also throw out the start
        // of a trip recorded while the phone's clock was fast and corrected later.
        val counted = points.dropLastWhile { it.wallClockMs > lastPointNotAfterMs }
        val distance = DistanceCalculator.measure(counted, limits)
        return ClosedTrip(
            status =
                if (falseStart || distance.metres < minimumDistanceMetres) {
                    TripStatus.DISCARDED
                } else {
                    TripStatus.FINISHED
                },
            // The time comes from the last fix of any quality: even a poor fix shows the trip
            // was still running then. With no fix at all, the cut-off is the best time there is.
            endedAtMs = counted.lastOrNull()?.wallClockMs ?: lastPointNotAfterMs,
            // The positions come only from fixes that passed the filter.
            start = distance.firstAccepted,
            end = distance.lastAccepted,
            distance = distance,
        )
    }
}

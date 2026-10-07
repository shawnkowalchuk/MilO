package com.shawnkowalchuk.milo.core.trip

import kotlin.math.max

/** A fix whose accuracy radius is larger than this is not used: Wi-Fi and cell-tower positions. */
const val MAX_ACCURACY_METRES = 25f

/** No road vehicle Shawn drives goes faster, so a fix that implies more is a GPS error. */
const val MAX_SPEED_KMH = 180.0

/** Distance is counted only once the truck is at least this far from where it last counted. */
const val MIN_MOVE_METRES = 10.0

/**
 * How many times the two fixes' own stated error a move must exceed before it counts as real.
 *
 * Accuracy is a 68 % radius, so about one fix in three lies outside it. With a factor of 1 a
 * parked truck still "moves" on roughly one fix in ten; with 2 it is about one in ten thousand.
 */
const val NOISE_SAFETY_FACTOR = 2.0

/** This many rejected jumps in a row that agree with each other replace the fix they contradict. */
const val REANCHOR_AFTER_JUMPS = 3

private const val KMH_PER_METRE_PER_SECOND = 3.6
private const val MILLIS_PER_SECOND = 1000.0

/**
 * The thresholds of the point filter and the distance calculation, gathered so they can be tuned
 * from the raw points of the first test drives without touching the logic. The defaults are the
 * values in use.
 */
data class DistanceLimits(
    val maxAccuracyMetres: Float = MAX_ACCURACY_METRES,
    val maxSpeedMetresPerSecond: Double = MAX_SPEED_KMH / KMH_PER_METRE_PER_SECOND,
    val minMoveMetres: Double = MIN_MOVE_METRES,
    val noiseSafetyFactor: Double = NOISE_SAFETY_FACTOR,
    val reanchorAfterJumps: Int = REANCHOR_AFTER_JUMPS,
)

/**
 * The running result of the distance calculation: the total so far, plus what is needed to judge
 * the next fix. It is a plain value, so the same code serves a trip in progress (one fix at a
 * time) and a recalculation from the stored points (all of them in order).
 *
 * @param metres distance counted so far.
 * @param firstAccepted the first fix that passed the filter: where the trip started.
 * @param lastAccepted the newest fix that passed the filter: where the truck is now, and the fix
 * the next one is checked against for an impossible jump.
 * @param anchor the position distance was last counted up to. It stays put while the truck is
 * parked, which is what keeps GPS jitter from adding up.
 * @param lastStep the newest counted step, while the next fix can still take it back (rule 4 in
 * [DistanceCalculator]). Null once a later fix has confirmed it.
 * @param jumpCandidates rejected jumps in a row that agree with each other (see
 * [REANCHOR_AFTER_JUMPS]).
 * @param acceptedCount fixes that passed the filter.
 * @param rejectedForAccuracy fixes dropped for missing, poor or nonsensical accuracy or position.
 * @param rejectedAsJump fixes dropped because the truck could not have got there in the time.
 */
data class DistanceState(
    val metres: Double = 0.0,
    val firstAccepted: TrackPoint? = null,
    val lastAccepted: TrackPoint? = null,
    val anchor: TrackPoint? = null,
    val lastStep: CountedStep? = null,
    val jumpCandidates: List<TrackPoint> = emptyList(),
    val acceptedCount: Int = 0,
    val rejectedForAccuracy: Int = 0,
    val rejectedAsJump: Int = 0,
) {
    /**
     * Wall-clock time of the fix distance was last counted up to, or null if none has been
     * counted: when the truck last really moved, as far as the fixes so far can say. It goes
     * back to the step before when the newest step is taken back (rule 4), so one bad fix
     * leaves no trace in it.
     */
    val lastCountedAtMs: Long? get() = anchor?.wallClockMs?.takeIf { metres > 0.0 }

    /**
     * The distance that no later fix can take back: everything but the newest step while that
     * is still open to rule 4. Above zero, it says the truck has moved and a second fix has
     * borne it out, which is what MilO waits for beside a parked truck ([ParkedWatch]).
     */
    val settledMetres: Double get() = lastStep?.metresBefore ?: metres

    /** Where counting stood at [settledMetres]: the fix that distance was last counted up to. */
    val settledAnchor: TrackPoint? get() = lastStep?.from ?: anchor
}

/**
 * A counted step that the next fix can still take back.
 *
 * @param from where distance had been counted up to before the step.
 * @param metresBefore the total before the step. Kept so that taking the step back is exact.
 */
data class CountedStep(val from: TrackPoint, val metresBefore: Double)

/**
 * The point filter and the distance calculation (ADR-002, APP_ENCYCLOPEDIA "GPS recording and
 * distance"). Pure functions: the same points always give the same distance, which is what lets
 * a trip be recalculated later from its stored raw points.
 *
 * Every fix is stored whether or not it is used here. This code only decides which fixes count.
 *
 * Known limits, to be tuned from the raw points of the first drives (docs/FINDINGS_LOG.md): a
 * wrong first fix, or one wrong fix straight after a GPS gap, is believed if it lies inside the
 * speed limit; and a turn is cut short by up to the movement threshold of rule 3.
 */
object DistanceCalculator {
    /** The distance over [points], which must be in the order they were recorded. */
    fun measure(
        points: List<TrackPoint>,
        limits: DistanceLimits = DistanceLimits(),
    ): DistanceState = points.fold(DistanceState()) { state, point -> add(state, point, limits) }

    /** Judges one more fix and returns the new running result. */
    fun add(
        state: DistanceState,
        point: TrackPoint,
        limits: DistanceLimits = DistanceLimits(),
    ): DistanceState {
        if (!isUsable(point, limits)) {
            return state.copy(rejectedForAccuracy = state.rejectedForAccuracy + 1)
        }
        val previous =
            state.lastAccepted
                // The first usable fix has nothing to be checked against. It is where counting
                // starts; if it turns out to be wrong, the re-anchor rule below replaces it.
                ?: return state.copy(
                    firstAccepted = point,
                    lastAccepted = point,
                    anchor = point,
                    acceptedCount = state.acceptedCount + 1,
                )
        return if (isPossibleMove(previous, point, limits)) {
            accept(state.copy(jumpCandidates = emptyList()), point, limits)
        } else {
            rejectJump(state, point, limits)
        }
    }

    /**
     * Rule 1: accuracy. A fix with no accuracy, or an accuracy worse than the limit, is dropped.
     * Coordinates that are not a place on Earth are dropped here too: one of them accepted as the
     * reference would turn every later distance into "not a number".
     */
    private fun isUsable(point: TrackPoint, limits: DistanceLimits): Boolean =
        point.accuracyMetres >= 0f &&
            point.accuracyMetres <= limits.maxAccuracyMetres &&
            point.latitude in -MAX_LATITUDE..MAX_LATITUDE &&
            point.longitude in -MAX_LONGITUDE..MAX_LONGITUDE

    /**
     * Rule 2: impossible jumps. The speed needed to get from one fix to the next is worked out
     * with the elapsed-realtime clock, because the wall clock can jump. A long gap (a tunnel) is
     * not a jump: the same distance over a longer time is an ordinary speed, so it is kept.
     *
     * A fix whose clock did not move forward cannot be vouched for either way. That happens with
     * a fix delivered twice, and after a reboot in the middle of a trip, when the elapsed-realtime
     * clock starts again from zero. It is treated as a jump, so the re-anchor rule picks the
     * trip up again from the fixes that follow.
     */
    private fun isPossibleMove(from: TrackPoint, to: TrackPoint, limits: DistanceLimits): Boolean {
        val elapsedMs = to.elapsedRealtimeMs - from.elapsedRealtimeMs
        if (elapsedMs <= 0) return false
        val metresPerSecond = from.metresTo(to) / (elapsedMs / MILLIS_PER_SECOND)
        return metresPerSecond <= limits.maxSpeedMetresPerSecond
    }

    /**
     * Rule 3: only real movement counts. The anchor stays where distance was last counted, and
     * the next stretch is added only once the truck is far enough from it that the gap cannot be
     * GPS error: at least [DistanceLimits.minMoveMetres], and at least the two fixes' own stated
     * error times [DistanceLimits.noiseSafetyFactor]. That threshold is 20 m with 5 m fixes,
     * 40 m with 10 m fixes and 100 m with 25 m fixes.
     *
     * What is added is the straight line from the anchor. On a straight road that loses nothing.
     * Through a turn it cuts the corner, and the last stretch of a trip, shorter than the
     * threshold, is never added. Both grow with the stated error.
     */
    private fun accept(
        state: DistanceState,
        point: TrackPoint,
        limits: DistanceLimits,
    ): DistanceState {
        val settled = withoutSpike(state, point)
        // add() and rejectJump() always set the anchor before calling this.
        val anchor = checkNotNull(settled.anchor) { "A fix was accepted before any anchor was set" }
        val moved = anchor.metresTo(point)
        val statedError = (anchor.accuracyMetres + point.accuracyMetres).toDouble()
        val isRealMovement =
            moved >= max(limits.minMoveMetres, limits.noiseSafetyFactor * statedError)
        return settled.copy(
            metres = if (isRealMovement) settled.metres + moved else settled.metres,
            anchor = if (isRealMovement) point else anchor,
            lastStep = if (isRealMovement) CountedStep(anchor, settled.metres) else null,
            lastAccepted = point,
            acceptedCount = settled.acceptedCount + 1,
        )
    }

    /**
     * Rule 4: out and back is not a drive. One bad fix can lie inside the speed limit: 200 m
     * away while the truck stands at a light. Rule 3 counts it as a step, and would then count
     * the way back as well. So the newest step can be taken back by the fix after it: if [next]
     * is nearer to where counting stood before the step than to where the step went, the step
     * was a bad fix, and counting goes back to where it stood.
     *
     * The cost: pulling forward and reversing within one fix interval is not counted either.
     */
    private fun withoutSpike(state: DistanceState, next: TrackPoint): DistanceState {
        val step = state.lastStep ?: return state
        val steppedTo = checkNotNull(state.anchor) { "A step was counted with no anchor" }
        val wentBack = step.from.metresTo(next) < steppedTo.metresTo(next)
        if (!wentBack) return state
        return state.copy(metres = step.metresBefore, anchor = step.from, lastStep = null)
    }

    /**
     * The re-anchor rule. A jump is normally one bad fix, and dropping it is enough. But if the
     * fix the jumps are measured from is itself the bad one, every later fix looks like a jump
     * and the rest of the trip would be lost. So rejected jumps that agree with each other are
     * collected, and once there are [DistanceLimits.reanchorAfterJumps] of them they are believed
     * instead: counting restarts from the first of them. The leap between the old position and
     * the new one is never counted, since one end of it is wrong.
     */
    private fun rejectJump(
        state: DistanceState,
        point: TrackPoint,
        limits: DistanceLimits,
    ): DistanceState {
        val lastCandidate = state.jumpCandidates.lastOrNull()
        val candidates =
            if (lastCandidate != null && isPossibleMove(lastCandidate, point, limits)) {
                state.jumpCandidates + point
            } else {
                listOf(point)
            }
        if (candidates.size < limits.reanchorAfterJumps) {
            return state.copy(
                jumpCandidates = candidates,
                rejectedAsJump = state.rejectedAsJump + 1,
            )
        }

        val restart = candidates.first()
        val restarted =
            state.copy(
                // If nothing had been counted yet, the old position never agreed with any other
                // fix, so it was not where the trip started either.
                firstAccepted = if (state.metres == 0.0) restart else state.firstAccepted,
                lastAccepted = restart,
                anchor = restart,
                lastStep = null,
                jumpCandidates = emptyList(),
                acceptedCount = state.acceptedCount + 1,
                // Every candidate but the newest had been counted as rejected. They are accepted
                // now: the first one just above, the others by accept() below.
                rejectedAsJump = state.rejectedAsJump - (candidates.size - 1),
            )
        return candidates.drop(1).fold(restarted) { accepted, next ->
            accept(accepted, next, limits)
        }
    }

    private const val MAX_LATITUDE = 90.0
    private const val MAX_LONGITUDE = 180.0
}

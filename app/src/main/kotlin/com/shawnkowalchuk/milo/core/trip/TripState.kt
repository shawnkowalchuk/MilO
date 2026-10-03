package com.shawnkowalchuk.milo.core.trip

/**
 * Everything the trip rules know at one moment.
 *
 * It holds two kinds of thing. [trip] and [autoStartHeldOff] are stored (the open trip row and a
 * setting), so they survive the process being killed. [truckConnected] and [androidAutoConnected]
 * are the latest readings of the outside world; they are not stored, and after a restart they are
 * read again and handed to [TripStateMachine.restore].
 *
 * @param trip the trip being recorded, or null when idle.
 * @param truckConnected whether the truck's Bluetooth link was up at the last reading.
 * @param androidAutoConnected whether Android Auto was connected at the last reading.
 * @param autoStartHeldOff true after Shawn pressed End with the truck still connected. While it
 * is set a connected truck does not start a trip. It clears when the truck is next seen
 * disconnected.
 */
data class TripState(
    val trip: ActiveTrip? = null,
    val truckConnected: Boolean = false,
    val androidAutoConnected: Boolean = false,
    val autoStartHeldOff: Boolean = false,
)

/**
 * The trip that is open.
 *
 * @param startedBy what started it.
 * @param truckSeen whether the truck has been connected at any point during it. Always true for
 * a trip the truck started. A manual trip becomes one when the truck connects, and from then on
 * it ends the way an automatic trip does.
 * @param grace the running grace period, or null while something is holding the trip open.
 * @param lastMovementAtMs wall-clock time the truck last really moved (the trip's start, until
 * it has). Only the no-movement guard for manual trips reads it. It is not stored: after a
 * restart it is worked out again from the stored points.
 */
data class ActiveTrip(
    val startedBy: TripStartCause,
    val truckSeen: Boolean,
    val grace: Grace? = null,
    val lastMovementAtMs: Long,
)

/**
 * A grace period that is running: the truck and Android Auto are both gone, and the trip closes
 * at [deadlineMs] unless one of them comes back.
 *
 * Both times are wall-clock times, because they are stored and must still mean something after a
 * reboot, when the elapsed-realtime clock has started again from zero.
 *
 * TODO(debt): a wall clock corrected by more than the time left stretches or cuts short this
 *  deadline (and the no-movement limit), and moves the point a trip is cut at. Rare, and it needs
 *  elapsed realtime carried beside every wall-clock time. See docs/FINDINGS_LOG.md.
 *
 * @param startedAtMs when the truck was found gone. If the grace period runs out, this is where
 * the trip is cut: whatever the phone recorded afterwards was Shawn walking away from the truck.
 */
data class Grace(val startedAtMs: Long, val deadlineMs: Long)

/**
 * The settings the rules run with.
 *
 * @param gracePeriodMs how long a trip waits for the truck to come back. It has no default here:
 * the value comes from the settings store, which owns the default.
 * @param noMovementLimitMs how long a manual trip with no truck may sit still before it is ended.
 * @param lateCheckToleranceMs see [LATE_CHECK_TOLERANCE_MS].
 * @param restartGapLimitMs see [RESTART_GAP_LIMIT_MS].
 */
data class TripRules(
    val gracePeriodMs: Long,
    val noMovementLimitMs: Long = NO_MOVEMENT_LIMIT_MS,
    val lateCheckToleranceMs: Long = LATE_CHECK_TOLERANCE_MS,
    val restartGapLimitMs: Long = RESTART_GAP_LIMIT_MS,
) {
    init {
        require(gracePeriodMs >= 0) { "The grace period cannot be negative: $gracePeriodMs ms" }
        require(noMovementLimitMs > 0) {
            "The no-movement limit must be positive: $noMovementLimitMs ms"
        }
        require(lateCheckToleranceMs >= 0) {
            "The late-check tolerance cannot be negative: $lateCheckToleranceMs ms"
        }
        require(restartGapLimitMs >= 0) {
            "The restart gap limit cannot be negative: $restartGapLimitMs ms"
        }
    }
}

/** ADR-002: a forgotten manual trip ends after 30 minutes without movement. */
const val NO_MOVEMENT_LIMIT_MS = 30L * 60L * 1000L

/**
 * How long after a grace deadline a reading of "the truck is connected" still saves the trip.
 *
 * The reading taken when the grace timer fires always arrives a little after the deadline, and
 * it must still be able to cancel the grace period (the disconnect was a false alarm, or the
 * reconnect event was lost). A deadline missed by more than this was not a late timer: the timer
 * was lost with a frozen or killed process, and the trip ended when its grace period ran out.
 * 30 seconds is six GPS fixes; the value is a judgement, and Shawn's to change.
 */
const val LATE_CHECK_TOLERANCE_MS = 30_000L

/**
 * After a restart, a trip that was recording is carried on only if its newest stored point is no
 * older than this. Up to the limit the gap is treated as an interruption of one drive (the process
 * was killed and came back, with the truck connected again or still). Beyond it nobody can say
 * the truck stayed connected, and the old trip is closed at that last point. 30 minutes is a
 * judgement, and Shawn's to change.
 */
const val RESTART_GAP_LIMIT_MS = 30L * 60L * 1000L

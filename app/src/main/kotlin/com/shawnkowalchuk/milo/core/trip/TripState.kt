package com.shawnkowalchuk.milo.core.trip

/**
 * Everything the trip rules know at one moment.
 *
 * It holds two kinds of thing. [trip] and [autoStartHeldOffSinceMs] are stored (the open trip row
 * and a setting), so they survive the process being killed. [truckConnected] and
 * [androidAutoConnected] are the latest readings of the outside world; they are not stored, and
 * after a restart they are read again and handed to [TripStateMachine.restore].
 *
 * @param trip the trip being recorded, or null when idle.
 * @param truckConnected whether the truck's Bluetooth link was up at the last reading.
 * @param androidAutoConnected whether Android Auto was connected at the last reading.
 * @param autoStartHeldOffSinceMs when Shawn pressed End with the truck still connected, or null
 * when automatic start is not held off. While it is set a connected truck does not start a trip.
 * What releases it is in [HoldOffRelease].
 */
data class TripState(
    val trip: ActiveTrip? = null,
    val truckConnected: Boolean = false,
    val androidAutoConnected: Boolean = false,
    val autoStartHeldOffSinceMs: Long? = null,
) {
    /** Whether automatic start is held off (see [autoStartHeldOffSinceMs]). */
    val autoStartHeldOff: Boolean get() = autoStartHeldOffSinceMs != null
}

/**
 * The trip that is open.
 *
 * @param startedBy what started it.
 * @param truckSeen whether the truck has been seen connected, by something that can be trusted,
 * at any point during it. A manual trip becomes such a trip when the truck connects, and from
 * then on it ends the way an automatic trip does. A trip the truck started has it from the
 * start, with one exception: see [confirmByMs].
 * @param grace the running grace period, or null while something is holding the trip open.
 * @param lastMovementAtMs wall-clock time the truck last really moved (the trip's start, until
 * it has). Only the no-movement guard for manual trips reads it. It is not stored: after a
 * restart it is worked out again from the stored points.
 * @param confirmByMs set only on a trip opened by the companion "appeared" callback alone, which
 * can fire when the truck is merely nearby. If nothing trustworthy has shown the truck connected
 * by this time, the trip was a false start. Null on every other trip, and once the truck has
 * been seen. It is not stored: after a restart it is worked out again by [confirmByMsFor].
 */
data class ActiveTrip(
    val startedBy: TripStartCause,
    val truckSeen: Boolean,
    val grace: Grace? = null,
    val lastMovementAtMs: Long,
    val confirmByMs: Long? = null,
)

/**
 * The confirmation deadline of a stored trip (see [ActiveTrip.confirmByMs]). A trip the truck
 * started, in which the truck was never seen, can only be a companion start still waiting to be
 * confirmed, so the deadline follows from the stored columns and survives a restart.
 */
fun confirmByMsFor(
    startedBy: TripStartCause,
    truckSeen: Boolean,
    startedAtMs: Long,
    rules: TripRules,
): Long? = if (startedBy == TripStartCause.TRUCK && !truckSeen) {
    startedAtMs + rules.startConfirmationMs
} else {
    null
}

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
 * @param holdOffNewLinkAfterMs see [HOLD_OFF_NEW_LINK_AFTER_MS].
 * @param holdOffLimitMs see [HOLD_OFF_LIMIT_MS].
 * @param startConfirmationMs see [START_CONFIRMATION_MS].
 */
data class TripRules(
    val gracePeriodMs: Long,
    val noMovementLimitMs: Long = NO_MOVEMENT_LIMIT_MS,
    val lateCheckToleranceMs: Long = LATE_CHECK_TOLERANCE_MS,
    val restartGapLimitMs: Long = RESTART_GAP_LIMIT_MS,
    val holdOffNewLinkAfterMs: Long = HOLD_OFF_NEW_LINK_AFTER_MS,
    val holdOffLimitMs: Long = HOLD_OFF_LIMIT_MS,
    val startConfirmationMs: Long = START_CONFIRMATION_MS,
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
        require(holdOffNewLinkAfterMs >= 0 && holdOffLimitMs >= 0 && startConfirmationMs >= 0) {
            "The hold-off and confirmation times cannot be negative"
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

/**
 * A link-level connect event releases the hold-off only if it arrives later than this after the
 * End press that set it. A new link can only form after the old one dropped, so such an event
 * proves a disconnect nobody saw. The 60 seconds ignore late duplicates of the connect event
 * from the connection that was already up when End was pressed.
 */
const val HOLD_OFF_NEW_LINK_AFTER_MS = 60_000L

/**
 * The hold-off never lasts longer than this. If neither the disconnect nor the next connect was
 * seen (the app was dead for both), the next reading of a connected truck starts a trip again:
 * a missed trip is worse than an unwanted restart.
 */
const val HOLD_OFF_LIMIT_MS = 12L * 60L * 60L * 1000L

/**
 * How long a trip opened by the companion "appeared" callback alone may wait for the truck's
 * connection to be confirmed (ADR-002). The Bluetooth profiles connect a few seconds after the
 * link, so the confirmation cannot be asked for at once.
 */
const val START_CONFIRMATION_MS = 15_000L

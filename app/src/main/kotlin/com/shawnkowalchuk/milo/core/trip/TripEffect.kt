package com.shawnkowalchuk.milo.core.trip

/**
 * Something the caller must do because the state changed. The rules themselves touch nothing:
 * they return these, and the caller stores them, starts or stops recording, and writes each one
 * to the event log.
 *
 * Together the effects describe every change to the stored part of [TripState], so a caller that
 * carries them all out keeps the database and the rules in step.
 */
sealed interface TripEffect {
    /** Open a trip row and start recording. */
    data class StartTrip(
        val startedBy: TripStartCause,
        val truckSeen: Boolean,
        val startedAtMs: Long,
    ) : TripEffect

    /** The truck connected during a manual trip: record that on the open trip. */
    data object MarkTruckSeen : TripEffect

    /** The truck and Android Auto are both gone: store the grace period. */
    data class StartGrace(val startedAtMs: Long, val deadlineMs: Long) : TripEffect

    /** The truck or Android Auto came back in time: the same trip carries on. */
    data object CancelGrace : TripEffect

    /**
     * Close the open trip and stop recording.
     *
     * @param lastPointNotAfterMs the trip ends at its last recorded point that is not later than
     * this. Fixes recorded after it stay stored but are not part of the trip. See [TripClosing].
     */
    data class EndTrip(val reason: TripEndReason, val lastPointNotAfterMs: Long) : TripEffect

    /** Store whether automatic start is held off (see [TripState.autoStartHeldOff]). */
    data class SetAutoStartHeldOff(val heldOff: Boolean) : TripEffect
}

/** Why a trip was closed. Written to the event log. */
enum class TripEndReason {
    /** Shawn pressed End. */
    MANUAL,

    /** The truck stayed disconnected for the whole grace period. */
    GRACE_EXPIRED,

    /** A manual trip the truck never joined sat still for [NO_MOVEMENT_LIMIT_MS]. */
    NO_MOVEMENT,

    /**
     * Shawn pressed Start while the trip was waiting out its grace period. It ended where the
     * truck was found gone, and the press started a new trip.
     */
    REPLACED_BY_MANUAL_START,

    /**
     * The process came back more than [RESTART_GAP_LIMIT_MS] after the trip's last recorded
     * point. Nobody watched the truck in between, so the trip ended at that point.
     */
    STALE_AT_RESTART,
}

/** What one step of the rules produced: the new state, and what the caller must do about it. */
data class TripTransition(val state: TripState, val effects: List<TripEffect>)

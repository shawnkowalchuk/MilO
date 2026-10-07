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
    /**
     * Open a trip row and start recording.
     *
     * @param fromParked true when the truck, connected and parked, moved again. The trip then
     * starts where the truck was parked: the caller gives it that position and the fixes that
     * showed the movement, so the first stretch is not lost. No trip-start sound is played for
     * it: the sound means "connected to the truck", and nothing connected.
     * @param atParkedPlace true when the trip begins where the truck was parked, with the
     * parked place and the watch's fixes as its first points. Always so with [fromParked]; and
     * for a press of Start while MilO waits beside the parked truck (Shawn's choice of
     * 2026-10-07, "At the parked spot"), so that the stretch driven before the press counts.
     * Such a press is not [fromParked]: Shawn vouched for the trip, so it plays the sound and is
     * never taken for a drive in another vehicle.
     */
    data class StartTrip(
        val startedBy: TripStartCause,
        val truckSeen: Boolean,
        val startedAtMs: Long,
        val fromParked: Boolean = false,
        val atParkedPlace: Boolean = fromParked,
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

    /** Store that automatic start is held off (see [TripState.autoStartHeldOffSinceMs]). */
    data class HoldOffAutoStart(val sinceMs: Long) : TripEffect

    /** Clear the stored hold-off: the truck may start a trip again. */
    data class ReleaseHoldOff(val reason: HoldOffRelease) : TripEffect

    /**
     * The trip was closed with the truck still connected: store that MilO is waiting for it to
     * move, with the place it is parked at, and watch its position at a low rate.
     */
    data class StartWaiting(val sinceMs: Long) : TripEffect

    /** Clear the stored wait and stop watching, unless a trip starts in the same step. */
    data class EndWaiting(val reason: WaitingEnd) : TripEffect
}

/** Why MilO stopped waiting for a parked truck to move. Written to the event log. */
enum class WaitingEnd {
    /** The truck moved: a trip starts where it was parked. */
    MOVED,

    /**
     * A reading or a disconnect event showed the truck gone, and Android Auto is not connected
     * either. Nothing is recorded.
     */
    TRUCK_DISCONNECTED,

    /** A new link to the truck was reported: a trip starts as it does on every connect. */
    NEW_LINK,

    /** Shawn pressed Start: a trip starts at once. */
    START_PRESSED,

    /** Shawn pressed End: automatic start is held off, so there is nothing to wait for. */
    END_PRESSED,

    /** The wait lasted [WAITING_LIMIT_MS]: MilO stops watching to spare the battery. */
    TIME_LIMIT,

    /** A restart found a trip open as well as a stored wait. The trip is what counts. */
    TRIP_OPEN,
}

/**
 * What released the hold-off. Whichever comes first does it; each exists because the one before
 * it can be missed while the app is dead. Written to the event log.
 */
enum class HoldOffRelease {
    /** A reading showed the truck disconnected: the rule as ADR-002 first had it. */
    TRUCK_SEEN_DISCONNECTED,

    /**
     * A link-level connect event arrived more than [HOLD_OFF_NEW_LINK_AFTER_MS] after End was
     * pressed. The disconnect in between was never seen, but it must have happened.
     */
    NEW_LINK,

    /** [HOLD_OFF_LIMIT_MS] passed. */
    TIME_LIMIT,

    /**
     * A trip Shawn started after that End press was closed by the parked rule with the truck
     * still connected, and MilO waits beside it. The wait does what the hold-off was for (a
     * reading of "connected" starts nothing), and the truck moving starts the next trip.
     */
    TRIP_PARKED,
}

/** Why a trip was closed. Written to the event log. */
enum class TripEndReason {
    /** Shawn pressed End. */
    MANUAL,

    /** The truck stayed disconnected for the whole grace period. */
    GRACE_EXPIRED,

    /**
     * The parked rule: the trip went without real movement for [TripRules.parkedLimitMs]. It
     * ended where and when it last moved, whatever started it and whatever is still connected.
     */
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

    /**
     * The companion "appeared" callback opened the trip and nothing confirmed the truck's
     * connection within [START_CONFIRMATION_MS]. It was never a trip: it is discarded whatever
     * its distance, and it does not wait out a grace period.
     */
    FALSE_START,
}

/** What one step of the rules produced: the new state, and what the caller must do about it. */
data class TripTransition(val state: TripState, val effects: List<TripEffect>) {
    /**
     * Whether this step is the moment a trip has really begun, which is the moment for the
     * trip-start sound. That is when a trip starts, with two exceptions. A trip opened by the
     * companion callback alone may yet turn out to be a false start, so its moment comes when
     * the truck is confirmed; a false start never has one. And a trip that starts because a
     * parked truck moved has none at all: the sound is for a connect or a button.
     *
     * @param before the state the step started from, or null if there was none.
     */
    fun tripReallyBegan(before: TripState?): Boolean {
        val wasUnconfirmed = before?.trip?.confirmByMs != null
        val isUnconfirmed = state.trip?.confirmByMs != null
        val started = effects.any { it is TripEffect.StartTrip && !it.fromParked }
        val confirmed = wasUnconfirmed && effects.any { it is TripEffect.MarkTruckSeen }
        return (started && !isUnconfirmed) || confirmed
    }
}

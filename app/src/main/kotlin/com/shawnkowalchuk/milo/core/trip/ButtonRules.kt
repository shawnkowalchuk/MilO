package com.shawnkowalchuk.milo.core.trip

/**
 * What the Start and End buttons do to the trip. They are the only events that act on a trip
 * directly; every other event only updates what is known. This is a part of [TripStateMachine],
 * in a file of its own only to keep both readable.
 */
internal object ButtonRules {
    /**
     * The state a button press acts on: the fresh truck reading taken in, and an open trip
     * brought up to date with it first. Without this, a press could act on a trip whose time
     * had already run out: Start would be swallowed by a trip that is about to close, and End
     * would stretch a forgotten trip to the moment of the press.
     */
    fun pressedWith(
        state: TripState,
        truckConnected: Boolean,
        atMs: Long,
        rules: TripRules,
        effects: MutableList<TripEffect>,
    ): TripState {
        val informed = state.copy(truckConnected = truckConnected)
        val trip = informed.trip ?: return informed
        return OpenTripRules.settle(
            informed,
            trip,
            atMs,
            rules,
            truckWasRead = true,
            effects,
            mayWait = false,
        )
    }

    fun startByHand(state: TripState, atMs: Long, effects: MutableList<TripEffect>): TripState {
        val open = state.trip
        return when {
            // Beside a parked truck too: Shawn says he is driving, and the trip starts at once.
            // It starts where the truck was parked (his choice of 2026-10-07): on 2026-10-07 he
            // pressed Start 244 m down the road, the trip having been closed in the moment he
            // drove off, and those 244 m were lost.
            open == null -> {
                val watched = state.waitingToMove
                val left = ParkedRules.leave(state, WaitingEnd.START_PRESSED, effects)
                TripStateMachine.startTrip(
                    left,
                    TripStartCause.MANUAL,
                    atMs,
                    effects,
                    atParkedPlace = watched,
                )
            }

            // The open trip is only waiting out its grace period: the truck is gone, and Shawn
            // says he is driving. Left alone, the press would be swallowed and the waiting trip
            // would close a moment later with nothing recording. So that trip ends where the
            // truck was found gone, and the press starts a trip of its own.
            open.grace != null -> {
                val reason = TripEndReason.REPLACED_BY_MANUAL_START
                effects += TripEffect.EndTrip(reason, open.grace.startedAtMs)
                TripStateMachine.startTrip(
                    state.copy(trip = null),
                    TripStartCause.MANUAL,
                    atMs,
                    effects,
                )
            }

            // A second press, or a press that crossed with an automatic start. There is still
            // exactly one trip.
            else -> state
        }
    }

    fun endByHand(state: TripState, atMs: Long, effects: MutableList<TripEffect>): TripState {
        val trip = state.trip
        if (trip != null) {
            // If the grace period is running the truck has gone, and the drive ended when it
            // went, not when the button was pressed. Otherwise the trip ends now.
            effects += TripEffect.EndTrip(TripEndReason.MANUAL, trip.grace?.startedAtMs ?: atMs)
        }

        // The truck is still connected, so the very next look at the connection would start a
        // new trip. Hold automatic start off (what releases it is in HoldOffRelease). This
        // applies even when no trip was open: End must never be the press that starts one. For
        // the same reason a hold-off that is already set starts again from this press: an old
        // one could be at its time limit, and the press itself would then start a trip. And a
        // wait beside the parked truck ends: movement must not start a trip while held off.
        val left = ParkedRules.leave(state, WaitingEnd.END_PRESSED, effects)
        if (!left.truckConnected || left.autoStartHeldOffSinceMs == atMs) {
            return left.copy(trip = null)
        }
        effects += TripEffect.HoldOffAutoStart(atMs)
        return left.copy(trip = null, autoStartHeldOffSinceMs = atMs)
    }
}

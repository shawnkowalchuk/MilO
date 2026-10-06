package com.shawnkowalchuk.milo.core.trip

/**
 * What an open trip should be doing, given what is known. This is the second half of
 * [TripStateMachine]'s "settle" step, in a file of its own only to keep both readable.
 */
internal object OpenTripRules {
    /**
     * Brings [openTrip] up to date: it carries on, starts or cancels its grace period, or ends.
     *
     * @param truckWasRead whether what is known about the truck was read just now. Only then may
     * a trip whose time has run out be closed (see the top of [TripStateMachine]).
     */
    fun settle(
        state: TripState,
        openTrip: ActiveTrip,
        atMs: Long,
        rules: TripRules,
        truckWasRead: Boolean,
        effects: MutableList<TripEffect>,
    ): TripState {
        // A grace period that ran out long ago: its timer was lost with a frozen or killed
        // process, and the trip ended back then. Nothing that is connected now can revive it,
        // or yesterday's trip would swallow today's. It waits for a reading of the truck, so
        // that a truck found connected starts the new trip in the same step.
        val expired = openTrip.grace
        if (expired != null && atMs > expired.deadlineMs + rules.lateCheckToleranceMs) {
            if (!truckWasRead) return state
            effects += TripEffect.EndTrip(TripEndReason.GRACE_EXPIRED, expired.startedAtMs)
            return state.copy(trip = null)
        }

        var trip = openTrip

        // The truck joined a manual trip, and from here on it ends the way an automatic trip
        // does. The same step confirms a trip the companion callback opened.
        if (state.truckConnected && !trip.truckSeen) {
            effects += TripEffect.MarkTruckSeen
            trip = trip.copy(truckSeen = true, confirmByMs = null)
        }

        if (!trip.truckSeen) {
            return settleWithoutTruck(
                state.copy(trip = trip),
                trip,
                atMs,
                rules,
                truckWasRead,
                effects,
            )
        }

        // Either connection holds the trip open. Shawn sometimes runs Android Auto over a cable
        // while Bluetooth drops, and the trip must carry on.
        if (state.truckConnected || state.androidAutoConnected) {
            if (trip.grace != null) {
                effects += TripEffect.CancelGrace
                trip = trip.copy(grace = null)
            }
            return state.copy(trip = trip)
        }

        // Nothing holds the trip open. Start the grace period unless one is already running: a
        // repeated disconnect must not push the deadline further away.
        val grace =
            trip.grace
                ?: Grace(startedAtMs = atMs, deadlineMs = atMs + rules.gracePeriodMs).also {
                    effects += TripEffect.StartGrace(it.startedAtMs, it.deadlineMs)
                }
        if (atMs >= grace.deadlineMs && truckWasRead) {
            // Closed at the moment the truck was found gone, not at the end of the grace period.
            effects += TripEffect.EndTrip(TripEndReason.GRACE_EXPIRED, grace.startedAtMs)
            return state.copy(trip = null)
        }
        return state.copy(trip = trip.copy(grace = grace))
    }

    /**
     * A trip in which the truck has not been seen has no disconnect to end it. It is one of two
     * kinds, each with its own way out.
     */
    private fun settleWithoutTruck(
        state: TripState,
        trip: ActiveTrip,
        atMs: Long,
        rules: TripRules,
        truckWasRead: Boolean,
        effects: MutableList<TripEffect>,
    ): TripState {
        // Opened by the companion callback alone and still waiting to be confirmed. Until the
        // deadline a reading of "not connected" proves nothing, because the profiles connect
        // seconds after the link. At the deadline, a reading that still does not show the truck
        // makes it a false start: it ends at once, with no grace period, and is discarded.
        val confirmBy = trip.confirmByMs
        if (confirmBy != null) {
            if (atMs < confirmBy || !truckWasRead) return state
            effects += TripEffect.EndTrip(TripEndReason.FALSE_START, atMs)
            return state.copy(trip = null)
        }

        // A manual trip the truck never joined: a forgotten one would run all night. It ends
        // where the truck last moved.
        val stillFor = atMs - trip.lastMovementAtMs
        val stoodTooLong =
            noMovementGuardApplies(state, trip) && stillFor >= rules.noMovementLimitMs
        if (stoodTooLong && truckWasRead) {
            effects += TripEffect.EndTrip(TripEndReason.NO_MOVEMENT, trip.lastMovementAtMs)
            return state.copy(trip = null)
        }
        return state
    }

    /**
     * The no-movement guard is for manual trips the truck never joined, and it waits while
     * Android Auto is connected: a phone plugged into a running head unit is not a forgotten trip.
     */
    fun noMovementGuardApplies(state: TripState, trip: ActiveTrip): Boolean =
        !trip.truckSeen && trip.confirmByMs == null && !state.androidAutoConnected
}

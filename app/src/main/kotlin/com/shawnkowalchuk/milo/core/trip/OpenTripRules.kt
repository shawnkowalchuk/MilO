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
     * @param mayWait see [ParkedRules.closeIfParked].
     */
    fun settle(
        state: TripState,
        openTrip: ActiveTrip,
        atMs: Long,
        rules: TripRules,
        truckWasRead: Boolean,
        effects: MutableList<TripEffect>,
        mayWait: Boolean = true,
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

        val current = state.copy(trip = trip)
        if (!trip.truckSeen) {
            val unconfirmed = settleUnconfirmed(current, trip, atMs, truckWasRead, effects)
            if (unconfirmed != null) return unconfirmed
            // A manual trip the truck never joined has no disconnect to end it. Only the parked
            // rule does, so a forgotten one cannot run all night.
            return ParkedRules.closeIfParked(
                current,
                trip,
                atMs,
                rules,
                truckWasRead,
                mayWait,
                effects,
            )
        }

        // Either connection holds the trip open against a disconnect. Shawn sometimes runs
        // Android Auto over a cable while Bluetooth drops, and the trip must carry on. Neither
        // holds it open against standing still: that is the parked rule.
        if (state.truckConnected || state.androidAutoConnected) {
            if (trip.grace != null) {
                effects += TripEffect.CancelGrace
                trip = trip.copy(grace = null)
            }
            return ParkedRules.closeIfParked(
                state.copy(trip = trip),
                trip,
                atMs,
                rules,
                truckWasRead,
                mayWait,
                effects,
            )
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
     * A trip opened by the companion callback alone, still waiting to be confirmed. Until the
     * deadline a reading of "not connected" proves nothing, because the profiles connect
     * seconds after the link. At the deadline, a reading that still does not show the truck
     * makes it a false start: it ends at once, with no grace period, and is discarded.
     *
     * @return the state to stop at, or null if [trip] is not such a trip.
     */
    private fun settleUnconfirmed(
        state: TripState,
        trip: ActiveTrip,
        atMs: Long,
        truckWasRead: Boolean,
        effects: MutableList<TripEffect>,
    ): TripState? {
        val confirmBy = trip.confirmByMs ?: return null
        if (atMs < confirmBy || !truckWasRead) return state
        effects += TripEffect.EndTrip(TripEndReason.FALSE_START, atMs)
        return state.copy(trip = null)
    }
}

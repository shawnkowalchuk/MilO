package com.shawnkowalchuk.milo.core.trip

import kotlin.math.max

/**
 * The trip rules of ADR-002 as a pure function: a state and an event go in, the new state and a
 * list of things to do come out. Nothing here touches Android, the database or the clock, so
 * every rule can be tested without a phone.
 *
 * How it stays correct when events are dropped, repeated or arrive in the wrong order:
 * 1. An event only updates what is known (the truck is connected, Shawn pressed End).
 * 2. [settle] then works out what the trip should be doing from what is known. It never asks
 *    "what just happened", only "what is true now", so it gives the same answer however the
 *    events arrived, and running it again changes nothing.
 * 3. A timeout (the grace period, the no-movement limit) closes a trip only on an event that
 *    brings a fresh reading of the truck. What the rules believe about the truck can be out of
 *    date, and ADR-002 has the connection read again before a trip is closed.
 */
object TripStateMachine {
    /** Applies one event. Giving it the same event twice has no further effect. */
    fun step(state: TripState, event: TripEvent, rules: TripRules): TripTransition {
        val effects = mutableListOf<TripEffect>()
        val informed = applyEvent(state, event, rules, effects)
        val settled = settle(informed, event.atMs, rules, event.readsTheTruck(), effects)
        return TripTransition(settled, effects)
    }

    /**
     * Rebuilds the state after the process was restarted (ADR-002, "State survives the process").
     *
     * [storedTrip] and [autoStartHeldOff] come from storage; the two connection readings are
     * taken fresh. A trip is carried on only if it can still be the same drive:
     * - A trip that was recording, whose newest stored point is older than
     *   [TripRules.restartGapLimitMs], is closed at that point. Nobody watched the truck in
     *   between, so yesterday's trip must not swallow today's.
     * - A trip in grace is judged by its stored deadline, like any other (see [settleOpenTrip]).
     *
     * The ordinary rules then apply: truck connected means the same trip carries on, or a new
     * one starts if the old one was just closed or none was stored (the "already connected at
     * boot" case, for which no Bluetooth event ever arrives); truck gone means the grace period
     * keeps running, or starts now for a trip that was recording when the process died.
     *
     * @param lastRecordedAtMs wall-clock time of the stored trip's newest point, or of its start
     * if it has no point yet. Null only when there is no stored trip. Wall clock, because the
     * elapsed-realtime clock does not survive a reboot.
     */
    fun restore(
        storedTrip: ActiveTrip?,
        lastRecordedAtMs: Long?,
        autoStartHeldOff: Boolean,
        truckConnected: Boolean,
        androidAutoConnected: Boolean,
        atMs: Long,
        rules: TripRules,
    ): TripTransition {
        val effects = mutableListOf<TripEffect>()
        var trip = storedTrip
        if (trip != null && trip.grace == null) {
            val lastRecorded =
                requireNotNull(lastRecordedAtMs) { "A stored trip needs its last recorded time" }
            if (atMs - lastRecorded > rules.restartGapLimitMs) {
                effects += TripEffect.EndTrip(TripEndReason.STALE_AT_RESTART, lastRecorded)
                trip = null
            }
        }
        val stored = TripState(trip, truckConnected, androidAutoConnected, autoStartHeldOff)
        return TripTransition(settle(stored, atMs, rules, truckWasRead = true, effects), effects)
    }

    /**
     * The wall-clock time at which the caller must look again even if nothing else happens, or
     * null when no timer is needed. When it is reached the caller reads the truck's connection
     * and sends a [TripEvent.TruckConnection]. A time already in the past means: read it now.
     *
     * It is worked out from the state, not handed out as an effect, so a timer lost with a killed
     * process is simply set again from the restored state.
     */
    fun nextCheckAtMs(state: TripState, rules: TripRules): Long? {
        val trip = state.trip ?: return null
        return when {
            trip.grace != null -> trip.grace.deadlineMs
            noMovementGuardApplies(state, trip) -> trip.lastMovementAtMs + rules.noMovementLimitMs
            else -> null
        }
    }

    /** Step 1: take in what the event says. Only the two buttons act on the trip directly. */
    private fun applyEvent(
        state: TripState,
        event: TripEvent,
        rules: TripRules,
        effects: MutableList<TripEffect>,
    ): TripState = when (event) {
        is TripEvent.TruckConnection -> state.copy(truckConnected = event.connected)

        is TripEvent.AndroidAutoConnection -> state.copy(androidAutoConnected = event.connected)

        is TripEvent.ManualStart -> {
            val pressed = pressedWith(state, event.truckConnected, event.atMs, rules, effects)
            startByHand(pressed, event.atMs, effects)
        }

        is TripEvent.ManualEnd -> {
            val pressed = pressedWith(state, event.truckConnected, event.atMs, rules, effects)
            endByHand(pressed, event.atMs, effects)
        }

        is TripEvent.Moved ->
            // max(), so a late or repeated report can never move the time backwards.
            state.copy(
                trip =
                    state.trip?.let {
                        it.copy(lastMovementAtMs = max(it.lastMovementAtMs, event.atMs))
                    },
            )
    }

    /**
     * The state a button press acts on: the fresh truck reading taken in, and an open trip
     * brought up to date with it first. Without this, a press could act on a trip whose time
     * had already run out: Start would be swallowed by a trip that is about to close, and End
     * would stretch a forgotten trip to the moment of the press.
     */
    private fun pressedWith(
        state: TripState,
        truckConnected: Boolean,
        atMs: Long,
        rules: TripRules,
        effects: MutableList<TripEffect>,
    ): TripState {
        val informed = state.copy(truckConnected = truckConnected)
        val trip = informed.trip ?: return informed
        return settleOpenTrip(informed, trip, atMs, rules, truckWasRead = true, effects)
    }

    private fun startByHand(
        state: TripState,
        atMs: Long,
        effects: MutableList<TripEffect>,
    ): TripState {
        val open = state.trip
        return when {
            open == null -> startTrip(state, TripStartCause.MANUAL, atMs, effects)

            // The open trip is only waiting out its grace period: the truck is gone, and Shawn
            // says he is driving. Left alone, the press would be swallowed and the waiting trip
            // would close a moment later with nothing recording. So that trip ends where the
            // truck was found gone, and the press starts a trip of its own.
            open.grace != null -> {
                val reason = TripEndReason.REPLACED_BY_MANUAL_START
                effects += TripEffect.EndTrip(reason, open.grace.startedAtMs)
                startTrip(state.copy(trip = null), TripStartCause.MANUAL, atMs, effects)
            }

            // A second press, or a press that crossed with an automatic start. There is still
            // exactly one trip.
            else -> state
        }
    }

    private fun startTrip(
        state: TripState,
        startedBy: TripStartCause,
        atMs: Long,
        effects: MutableList<TripEffect>,
    ): TripState {
        effects += TripEffect.StartTrip(startedBy, truckSeen = state.truckConnected, atMs)
        val trip = ActiveTrip(startedBy, truckSeen = state.truckConnected, lastMovementAtMs = atMs)
        return state.copy(trip = trip)
    }

    private fun endByHand(
        state: TripState,
        atMs: Long,
        effects: MutableList<TripEffect>,
    ): TripState {
        val trip = state.trip
        if (trip != null) {
            // If the grace period is running the truck has gone, and the drive ended when it
            // went, not when the button was pressed. Otherwise the trip ends now.
            effects += TripEffect.EndTrip(TripEndReason.MANUAL, trip.grace?.startedAtMs ?: atMs)
        }

        // The truck is still connected, so the very next look at the connection would start a
        // new trip. Hold automatic start off until the truck is seen disconnected. This applies
        // even when no trip was open: End must never be the press that starts one.
        val holdOff = state.autoStartHeldOff || state.truckConnected
        if (holdOff != state.autoStartHeldOff) effects += TripEffect.SetAutoStartHeldOff(holdOff)
        return state.copy(trip = null, autoStartHeldOff = holdOff)
    }

    /**
     * Step 2: make the trip agree with what is known.
     *
     * @param truckWasRead whether what is known about the truck was read just now (see point 3
     * at the top of this file).
     */
    private fun settle(
        state: TripState,
        atMs: Long,
        rules: TripRules,
        truckWasRead: Boolean,
        effects: MutableList<TripEffect>,
    ): TripState {
        var current = state

        // The hold-off lasts "until the truck next disconnects": seeing it gone releases it.
        if (current.autoStartHeldOff && !current.truckConnected) {
            effects += TripEffect.SetAutoStartHeldOff(false)
            current = current.copy(autoStartHeldOff = false)
        }

        val trip = current.trip
        if (trip != null) {
            current = settleOpenTrip(current, trip, atMs, rules, truckWasRead, effects)
        }

        // Idle and the truck is connected: a trip starts. This one rule covers a connect event,
        // every reconcile, and a truck found connected when a long-expired trip has just been
        // closed above. Only the truck starts a trip; Android Auto never does.
        val startNow = current.trip == null && current.truckConnected && !current.autoStartHeldOff
        return if (startNow) startTrip(current, TripStartCause.TRUCK, atMs, effects) else current
    }

    private fun settleOpenTrip(
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

        // The truck joined a manual trip. From here on it ends the way an automatic trip does.
        if (state.truckConnected && !trip.truckSeen) {
            effects += TripEffect.MarkTruckSeen
            trip = trip.copy(truckSeen = true)
        }

        if (!trip.truckSeen) {
            // A manual trip the truck never joined has no disconnect to end it, so a forgotten
            // one would run all night. It ends where the truck last moved.
            val current = state.copy(trip = trip)
            val stillFor = atMs - trip.lastMovementAtMs
            val stoodTooLong =
                noMovementGuardApplies(current, trip) && stillFor >= rules.noMovementLimitMs
            if (stoodTooLong && truckWasRead) {
                effects += TripEffect.EndTrip(TripEndReason.NO_MOVEMENT, trip.lastMovementAtMs)
                return current.copy(trip = null)
            }
            return current
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
     * The no-movement guard is for manual trips the truck never joined, and it waits while
     * Android Auto is connected: a phone plugged into a running head unit is not a forgotten trip.
     */
    private fun noMovementGuardApplies(state: TripState, trip: ActiveTrip): Boolean =
        !trip.truckSeen && !state.androidAutoConnected
}

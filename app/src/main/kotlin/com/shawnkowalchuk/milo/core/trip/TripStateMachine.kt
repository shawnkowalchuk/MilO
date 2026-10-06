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
 * 3. A timeout (the grace period, the no-movement limit, the wait for a companion start to be
 *    confirmed) closes a trip only on an event that brings a fresh reading of the truck. What
 *    the rules believe about the truck can be out of date, and ADR-002 has the connection read
 *    again before a trip is closed.
 *
 * The rules for an open trip are in [OpenTripRules], and those for the hold-off in
 * [HoldOffRules].
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
     * [storedTrip] and [autoStartHeldOffSinceMs] come from storage; the two connection readings
     * are taken fresh. A trip is carried on only if it can still be the same drive:
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
        autoStartHeldOffSinceMs: Long?,
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
        val stored = TripState(trip, truckConnected, androidAutoConnected, autoStartHeldOffSinceMs)
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
            trip.confirmByMs != null -> trip.confirmByMs

            trip.grace != null -> trip.grace.deadlineMs

            OpenTripRules.noMovementGuardApplies(state, trip) ->
                trip.lastMovementAtMs + rules.noMovementLimitMs

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

        is TripEvent.TruckLinkConnected ->
            HoldOffRules
                .releaseForNewLink(state, event.atMs, rules, effects)
                .copy(truckConnected = true)

        is TripEvent.TruckAppeared -> {
            val released = HoldOffRules.releaseForNewLink(state, event.atMs, rules, effects)
            if (state.trip == null) {
                startUnconfirmed(released, event.atMs, rules, effects)
            } else {
                // With a trip open the callback is not believed (see startUnconfirmed). It
                // still shows that an old hold-off is out of date, but what is believed about
                // the truck stands: dropped here, the open trip would start its grace period
                // on a disconnect nobody saw.
                released.copy(truckConnected = state.truckConnected)
            }
        }

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
        return OpenTripRules.settle(informed, trip, atMs, rules, truckWasRead = true, effects)
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

    /**
     * The companion "appeared" callback while idle: ADR-002 has it start a trip at once, because
     * waiting for proof would miss the start window. The callback can also fire when the truck
     * is only nearby, so the trip is opened with the truck not yet seen and a deadline to
     * confirm it by. What happens at the deadline is in [OpenTripRules.settle].
     *
     * A trip that is already open is left alone, so the caller does not come here for one:
     * believing the callback wrongly there would mark the truck as seen in a manual trip, and
     * the trip would then end on a disconnect that never happened. The signals that can be
     * trusted bring such a trip up to date.
     */
    private fun startUnconfirmed(
        state: TripState,
        atMs: Long,
        rules: TripRules,
        effects: MutableList<TripEffect>,
    ): TripState {
        if (state.autoStartHeldOff) return state
        effects += TripEffect.StartTrip(TripStartCause.TRUCK, truckSeen = false, atMs)
        val trip =
            ActiveTrip(
                startedBy = TripStartCause.TRUCK,
                truckSeen = false,
                lastMovementAtMs = atMs,
                confirmByMs = atMs + rules.startConfirmationMs,
            )
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
        // new trip. Hold automatic start off (what releases it is in HoldOffRelease). This
        // applies even when no trip was open: End must never be the press that starts one. For
        // the same reason a hold-off that is already set starts again from this press: an old
        // one could be at its time limit, and the press itself would then start a trip.
        if (!state.truckConnected || state.autoStartHeldOffSinceMs == atMs) {
            return state.copy(trip = null)
        }
        effects += TripEffect.HoldOffAutoStart(atMs)
        return state.copy(trip = null, autoStartHeldOffSinceMs = atMs)
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
        var current = HoldOffRules.releaseIfDue(state, atMs, rules, truckWasRead, effects)

        val trip = current.trip
        if (trip != null) {
            current = OpenTripRules.settle(current, trip, atMs, rules, truckWasRead, effects)
        }

        // Idle and the truck is connected: a trip starts. This one rule covers a connect event,
        // every reconcile, and a truck found connected when a long-expired trip has just been
        // closed above. Only the truck starts a trip; Android Auto never does.
        val startNow = current.trip == null && current.truckConnected && !current.autoStartHeldOff
        return if (startNow) startTrip(current, TripStartCause.TRUCK, atMs, effects) else current
    }
}

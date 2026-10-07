package com.shawnkowalchuk.milo.core.trip

import kotlin.math.max
import kotlin.math.min

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
 * 3. A timeout (the grace period, the parked limit, the wait for a companion start to be
 *    confirmed) closes a trip only on an event that brings a fresh reading of the truck. What
 *    the rules believe about the truck can be out of date, and ADR-002 has the connection read
 *    again before a trip is closed.
 *
 * The rules for an open trip are in [OpenTripRules], those for a truck that stands still in
 * [ParkedRules], those for the two buttons in [ButtonRules], and those for the hold-off in
 * [HoldOffRules].
 */
object TripStateMachine {
    /** Applies one event. Giving it the same event twice has no further effect. */
    fun step(state: TripState, event: TripEvent, rules: TripRules): TripTransition {
        val effects = mutableListOf<TripEffect>()
        val informed = applyEvent(state, event, rules, effects)
        // A new link starts a trip as every connect does. If it finds a trip that has stood too
        // long, that trip is closed and the new one starts in this same step, with no wait in
        // between: both Bluetooth receivers deliver the broadcast during a trip, and the
        // second delivery must find nothing left to do.
        val mayWait = event !is TripEvent.TruckLinkConnected
        val read = event.readsTheTruck()
        return TripTransition(settle(informed, event.atMs, rules, read, effects, mayWait), effects)
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
     * A stored wait beside a parked truck ([parkedSinceMs]) is found again as a wait, not as a
     * new trip, as long as the truck is still connected and the wait is inside its limit. Past
     * the limit nobody was watching, and this is a restart with the truck connected like any
     * other: a trip starts, and the parked rule closes it if the truck is still standing.
     *
     * @param lastRecordedAtMs wall-clock time of the stored trip's newest point, or of its start
     * if it has no point yet. Null only when there is no stored trip. Wall clock, because the
     * elapsed-realtime clock does not survive a reboot.
     * @param parkedSinceMs the stored [Parked.sinceMs], or null if MilO was not waiting.
     */
    fun restore(
        storedTrip: ActiveTrip?,
        lastRecordedAtMs: Long?,
        autoStartHeldOffSinceMs: Long?,
        truckConnected: Boolean,
        androidAutoConnected: Boolean,
        atMs: Long,
        rules: TripRules,
        parkedSinceMs: Long? = null,
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
        val stillWaiting =
            when {
                parkedSinceMs == null -> null
                storedTrip != null -> WaitingEnd.TRIP_OPEN
                atMs - parkedSinceMs >= rules.waitingLimitMs -> WaitingEnd.TIME_LIMIT
                else -> null
            }
        if (stillWaiting != null) effects += TripEffect.EndWaiting(stillWaiting)
        val parked = parkedSinceMs?.takeIf { stillWaiting == null }?.let { Parked(sinceMs = it) }
        val stored =
            TripState(trip, truckConnected, androidAutoConnected, autoStartHeldOffSinceMs, parked)
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
        val trip = state.trip ?: return ParkedRules.waitingDeadlineMs(state, rules)
        return when {
            trip.confirmByMs != null -> trip.confirmByMs
            trip.grace != null -> trip.grace.deadlineMs
            else -> ParkedRules.parkedDeadlineMs(trip, rules)
        }
    }

    /** Step 1: take in what the event says. Only the two buttons act on the trip directly. */
    private fun applyEvent(
        state: TripState,
        event: TripEvent,
        rules: TripRules,
        effects: MutableList<TripEffect>,
    ): TripState = when (event) {
        is TripEvent.TruckConnection -> {
            val informed = state.copy(truckConnected = event.connected)
            if (event.connected) ParkedRules.readConnected(informed) else informed
        }

        is TripEvent.TruckLinkConnected -> {
            // A new link while MilO waits beside the parked truck: the old link dropped unseen,
            // and the truck starts a trip as it does on every connect.
            val released = HoldOffRules.releaseForNewLink(state, event.atMs, rules, effects)
            ParkedRules.leave(released, WaitingEnd.NEW_LINK, effects).copy(truckConnected = true)
        }

        is TripEvent.TruckAppeared -> {
            val released = HoldOffRules.releaseForNewLink(state, event.atMs, rules, effects)
            if (state.trip == null) {
                // Beside a parked truck the callback means what it means when idle: a new
                // link, to be confirmed. What was believed about the old link is out of date.
                val left = ParkedRules.leave(released, WaitingEnd.NEW_LINK, effects)
                val fresh = if (released.parked != null) left.copy(truckConnected = false) else left
                startUnconfirmed(fresh, event.atMs, rules, effects)
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
            val pressed =
                ButtonRules.pressedWith(state, event.truckConnected, event.atMs, rules, effects)
            ButtonRules.startByHand(pressed, event.atMs, effects)
        }

        is TripEvent.ManualEnd -> {
            val pressed =
                ButtonRules.pressedWith(state, event.truckConnected, event.atMs, rules, effects)
            ButtonRules.endByHand(pressed, event.atMs, effects)
        }

        is TripEvent.Moved -> {
            val open = state.trip
            if (open == null) {
                ParkedRules.startBecauseMoved(state, event.atMs, effects)
            } else {
                // max(), so a late or repeated report can never move the time backwards.
                val movedAtMs = max(open.lastMovementAtMs, event.atMs)
                state.copy(trip = open.copy(lastMovementAtMs = movedAtMs))
            }
        }

        is TripEvent.MoveTakenBack ->
            // min(): taking a movement back can only make the truck have stood for longer.
            state.copy(
                trip =
                    state.trip?.let {
                        it.copy(lastMovementAtMs = min(it.lastMovementAtMs, event.lastMovedAtMs))
                    },
            )
    }

    /**
     * Opens a trip now. Whether the truck has been seen in it is what is known at this moment.
     *
     * @param atParkedPlace see [TripEffect.StartTrip.atParkedPlace].
     */
    internal fun startTrip(
        state: TripState,
        startedBy: TripStartCause,
        atMs: Long,
        effects: MutableList<TripEffect>,
        atParkedPlace: Boolean = false,
    ): TripState {
        effects +=
            TripEffect.StartTrip(
                startedBy,
                truckSeen = state.truckConnected,
                atMs,
                atParkedPlace = atParkedPlace,
            )
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

    /**
     * Step 2: make the trip agree with what is known.
     *
     * @param truckWasRead whether what is known about the truck was read just now (see point 3
     * at the top of this file).
     * @param mayWait see [ParkedRules.closeIfParked].
     */
    private fun settle(
        state: TripState,
        atMs: Long,
        rules: TripRules,
        truckWasRead: Boolean,
        effects: MutableList<TripEffect>,
        mayWait: Boolean = true,
    ): TripState {
        var current = HoldOffRules.releaseIfDue(state, atMs, rules, truckWasRead, effects)
        current = ParkedRules.settle(current, atMs, rules, truckWasRead, effects)

        val trip = current.trip
        if (trip != null) {
            current =
                OpenTripRules.settle(current, trip, atMs, rules, truckWasRead, effects, mayWait)
        }

        // Idle and the truck is connected: a trip starts. This one rule covers a connect event,
        // every reconcile, and a truck found connected when a long-expired trip has just been
        // closed above. Only the truck starts a trip; Android Auto never does. A truck that is
        // parked and waited for is not idle: there the trip starts when it moves.
        val idle = current.trip == null && current.parked == null
        val startNow = idle && current.truckConnected && !current.autoStartHeldOff
        return if (startNow) startTrip(current, TripStartCause.TRUCK, atMs, effects) else current
    }
}

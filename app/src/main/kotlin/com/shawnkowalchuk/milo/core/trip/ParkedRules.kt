package com.shawnkowalchuk.milo.core.trip

/**
 * The parked rule (ADR-002, amendment 28; the owner's decision of 2026-10-06). The truck can stay
 * connected long after it is switched off, so a Bluetooth disconnect cannot be what ends a trip.
 * Two halves:
 *
 * - **A trip that is being recorded and has not really moved for [TripRules.parkedLimitMs] is
 *   closed where and when it last moved** ([closeIfParked]), whatever started it and whatever
 *   is connected. Android Auto does not hold it open: a parked truck is parked.
 * - **If the truck is still connected, MilO then waits** ([Parked]): the trip service stays up
 *   and watches the position at a low rate, and the truck moving starts the next trip
 *   ([startBecauseMoved]). The wait ends without a trip when the truck is seen gone, and MilO
 *   stops watching once it has lasted [TripRules.waitingLimitMs] ([settle]).
 *
 * "Still connected" means what it means for a trip: the truck's Bluetooth, or Android Auto.
 * Shawn sometimes runs Android Auto on a cable while Bluetooth drops; such a trip was held open
 * through a stop before the parked rule, so the drive after the stop must still be recorded.
 *
 * "Really moved" is never judged here: the caller reports it as [TripEvent.Moved], from the same
 * calculation that counts the distance, so GPS jitter is not movement and a slow crawl is.
 */
internal object ParkedRules {
    /**
     * Closes [trip], which is being recorded (no grace period is running), if it has stood still
     * for the limit. Like every other timeout it waits for a fresh reading of the truck: what
     * happens next depends on whether the truck is still connected.
     *
     * @param mayWait false when what is being dealt with decides itself what follows, so that no
     * wait is begun only to be ended in the same step: a press of Start or End, and a new link
     * to the truck, which starts a trip as every connect does.
     */
    fun closeIfParked(
        state: TripState,
        trip: ActiveTrip,
        atMs: Long,
        rules: TripRules,
        truckWasRead: Boolean,
        mayWait: Boolean,
        effects: MutableList<TripEffect>,
    ): TripState {
        val limit = rules.parkedLimitMs ?: return state
        if (!truckWasRead || atMs - trip.lastMovementAtMs < limit) return state
        // Closed where it last moved. What the phone recorded since is the truck standing.
        effects += TripEffect.EndTrip(TripEndReason.NO_MOVEMENT, trip.lastMovementAtMs)
        val closed = state.copy(trip = null)
        // With neither the truck nor Android Auto connected there is nothing to wait beside:
        // nothing could ever end the wait.
        if (!mayWait || !(closed.truckConnected || closed.androidAutoConnected)) return closed
        // A hold-off that still stands is from an End press before this trip, which Shawn then
        // started by hand. Kept, it would leave the truck connected, parked and unwatched, and
        // the drive after this stop would not be recorded. The wait takes its place: like the
        // hold-off it keeps a reading of "connected" from starting a trip.
        val free =
            if (closed.autoStartHeldOff) {
                effects += TripEffect.ReleaseHoldOff(HoldOffRelease.TRIP_PARKED)
                closed.copy(autoStartHeldOffSinceMs = null)
            } else {
                closed
            }
        effects += TripEffect.StartWaiting(atMs)
        return free.copy(parked = Parked(sinceMs = atMs))
    }

    /** When [trip], which is being recorded, is closed if it does not move, or null without a limit. */
    fun parkedDeadlineMs(trip: ActiveTrip, rules: TripRules): Long? =
        rules.parkedLimitMs?.let { trip.lastMovementAtMs + it }

    /** When MilO stops watching a parked truck, or null if it is not watching one. */
    fun waitingDeadlineMs(state: TripState, rules: TripRules): Long? =
        state.parked?.takeIf { state.waitingToMove }?.let { it.sinceMs + rules.waitingLimitMs }

    /**
     * Brings a wait up to date with what is known about the truck: it ends when the truck is
     * gone, and MilO stops watching when the wait has lasted too long. The limit is applied only
     * with a fresh reading, like every timeout, and leaves [Parked] in place with
     * [Parked.watching] false: the truck is still connected, so the reading that brought the
     * limit must not start a trip in the same step, as it would for an idle truck.
     *
     * Android Auto holds a wait as it holds a trip, but only while MilO is watching: it is
     * reported by the running trip service, and once watching has stopped nothing would ever
     * say that it has gone. For the same reason a wait that Android Auto alone was holding is
     * simply over at the limit.
     */
    fun settle(
        state: TripState,
        atMs: Long,
        rules: TripRules,
        truckWasRead: Boolean,
        effects: MutableList<TripEffect>,
    ): TripState {
        val parked = state.parked ?: return state
        val overLimit = atMs - parked.sinceMs >= rules.waitingLimitMs
        val held = state.truckConnected || (parked.watching && state.androidAutoConnected)
        return when {
            !held -> leave(state, WaitingEnd.TRUCK_DISCONNECTED, effects)

            parked.watching && truckWasRead && overLimit -> {
                effects += TripEffect.EndWaiting(WaitingEnd.TIME_LIMIT)
                val unwatched = parked.copy(watching = false)
                state.copy(parked = unwatched.takeIf { state.truckConnected })
            }

            else -> state
        }
    }

    /**
     * The truck is no longer parked and waited for, because of [reason]. A wait that MilO had
     * already stopped watching is left without a word: its end was logged when watching stopped.
     */
    fun leave(state: TripState, reason: WaitingEnd, effects: MutableList<TripEffect>): TripState {
        val parked = state.parked ?: return state
        if (parked.watching) effects += TripEffect.EndWaiting(reason)
        return state.copy(parked = null)
    }

    /**
     * A reading shows the truck connected after MilO stopped watching it. That is a reconcile
     * like any other with no trip open (MilO was opened, the process restarted), and it starts a
     * trip: if the truck is in fact still standing, the parked rule closes that trip and the
     * wait begins again. While MilO is watching, the same reading changes nothing.
     */
    fun readConnected(state: TripState): TripState =
        if (state.parked?.watching == false) state.copy(parked = null) else state

    /**
     * The parked truck moved: the next trip starts, at the time the movement was first seen.
     * The truck or Android Auto is connected (or MilO would not be waiting), and the truck
     * counts as seen in the trip, so it ends the way every truck trip does: on a disconnect,
     * or when it is parked. It does nothing unless MilO is waiting: with a trip open the caller
     * has already counted the movement, and when idle or held off movement starts nothing.
     */
    fun startBecauseMoved(
        state: TripState,
        atMs: Long,
        effects: MutableList<TripEffect>,
    ): TripState {
        if (!state.waitingToMove || state.autoStartHeldOff) return state
        val left = leave(state, WaitingEnd.MOVED, effects)
        effects +=
            TripEffect.StartTrip(TripStartCause.TRUCK, truckSeen = true, atMs, fromParked = true)
        val trip = ActiveTrip(TripStartCause.TRUCK, truckSeen = true, lastMovementAtMs = atMs)
        return left.copy(trip = trip)
    }
}

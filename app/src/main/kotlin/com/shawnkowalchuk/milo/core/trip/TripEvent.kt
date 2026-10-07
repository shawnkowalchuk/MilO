package com.shawnkowalchuk.milo.core.trip

/**
 * Something the trip rules are told about.
 *
 * The two connection events state a level ("the truck is connected"), never an edge ("the truck
 * just connected"). That is the heart of ADR-002: Bluetooth events can be dropped, repeated or
 * delivered in reverse order, so each one is only a reason to say what is true now. Telling the
 * rules the same thing twice changes nothing.
 *
 * @property atMs wall-clock time of the event. Every event is also a look at the clock, but a
 * deadline that has passed closes a trip only on an event that brings a fresh reading of the
 * truck: [TruckConnection], [TruckLinkConnected], [ManualStart] or [ManualEnd]. [Moved],
 * [MoveTakenBack] and [AndroidAutoConnection] know nothing new about the truck, and
 * [TruckAppeared] cannot be trusted as a reading, so they leave an overdue trip for the reading
 * that [TripStateMachine.nextCheckAtMs] asks for.
 */
sealed interface TripEvent {
    val atMs: Long

    /**
     * The truck's Bluetooth link is up or down, to the best of the caller's knowledge (ADR-002,
     * "What counts as the truck is connected"). Sent for:
     * - a disconnect event that names the truck (trusted as it stands);
     * - a reconcile at boot, after an update, at launch or when the service restarts, with the
     *   state read from the phone;
     * - a timer set from [TripStateMachine.nextCheckAtMs], again with the state read from the
     *   phone. This is how "read the connection again before closing the trip" is done.
     */
    data class TruckConnection(val connected: Boolean, override val atMs: Long) : TripEvent

    /**
     * A link-level connect event that names the truck: the Bluetooth ACL connect broadcast. It
     * says everything `TruckConnection(connected = true)` says, and one thing more: a link has
     * just formed. A new link can only form after the old one dropped, so this also releases a
     * hold-off that is older than [TripRules.holdOffNewLinkAfterMs].
     */
    data class TruckLinkConnected(override val atMs: Long) : TripEvent

    /**
     * The companion device "appeared" callback. Like [TruckLinkConnected] it means a new link,
     * so it releases an old hold-off. But it can also fire when the truck is only nearby, so it
     * is not a reading of the truck. When idle it starts a trip at once, as ADR-002 requires,
     * and that trip is a false start unless the connection is confirmed within
     * [TripRules.startConfirmationMs]. It never changes a trip that is already open: there it
     * can only release the old hold-off.
     */
    data class TruckAppeared(override val atMs: Long) : TripEvent

    /** Android Auto is connected or not. It can hold a trip open; it never starts one. */
    data class AndroidAutoConnection(val connected: Boolean, override val atMs: Long) : TripEvent

    /**
     * Shawn pressed Start, on the phone or on the Android Auto screen.
     *
     * @param truckConnected whether the truck is connected right now, checked when the button was
     * pressed. A press is a good moment to look again, and it decides whether the new trip ends
     * the way an automatic trip does.
     */
    data class ManualStart(val truckConnected: Boolean, override val atMs: Long) : TripEvent

    /**
     * Shawn pressed End, on the phone or on the Android Auto screen.
     *
     * @param truckConnected whether the truck is connected right now, checked when the button was
     * pressed. It decides whether automatic start is held off, so it must not be a stale belief:
     * holding off for a truck that has already gone would swallow the next real trip.
     */
    data class ManualEnd(val truckConnected: Boolean, override val atMs: Long) : TripEvent

    /**
     * The truck really moved at this time, judged the way the distance is: beyond what GPS
     * jitter can explain (`DistanceCalculator`, rule 3). During a trip it moves the parked limit
     * on. While MilO waits beside a parked, connected truck it is what starts the next trip; the
     * caller sends it then only once a second fix has borne the movement out.
     */
    data class Moved(override val atMs: Long) : TripEvent

    /**
     * The movement last reported during a trip was one bad fix, and the distance calculation has
     * taken it back (its rule 4). Without this, a single stray fix every few minutes would keep a
     * parked trip open for ever.
     *
     * @param lastMovedAtMs when the truck last really moved, now that the bad fix is gone: the
     * trip's start, if it has not moved at all.
     */
    data class MoveTakenBack(val lastMovedAtMs: Long, override val atMs: Long) : TripEvent
}

/**
 * Whether the event carries a reading of the truck's connection taken just now. Only such an
 * event may close a trip whose time has run out. Written as an exhaustive `when`, so a new kind
 * of event cannot be added without deciding this.
 */
internal fun TripEvent.readsTheTruck(): Boolean = when (this) {
    is TripEvent.TruckConnection,
    is TripEvent.TruckLinkConnected,
    is TripEvent.ManualStart,
    is TripEvent.ManualEnd,
    -> true

    is TripEvent.TruckAppeared,
    is TripEvent.AndroidAutoConnection,
    is TripEvent.Moved,
    is TripEvent.MoveTakenBack,
    -> false
}

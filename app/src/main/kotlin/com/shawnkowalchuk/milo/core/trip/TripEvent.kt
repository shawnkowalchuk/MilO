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
 * truck: [TruckConnection], [ManualStart] or [ManualEnd]. [Moved] and [AndroidAutoConnection] know
 * nothing new about the truck, so they leave an overdue trip for the reading that
 * [TripStateMachine.nextCheckAtMs] asks for.
 */
sealed interface TripEvent {
    val atMs: Long

    /**
     * The truck's Bluetooth link is up or down, to the best of the caller's knowledge (ADR-002,
     * "What counts as the truck is connected"). Sent for:
     * - a connect or disconnect event that names the truck (trusted as it stands);
     * - a reconcile at boot, after an update, at launch or when the service restarts, with the
     *   state read from the phone;
     * - a timer set from [TripStateMachine.nextCheckAtMs], again with the state read from the
     *   phone. This is how "read the connection again before closing the trip" is done.
     */
    data class TruckConnection(val connected: Boolean, override val atMs: Long) : TripEvent

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

    /** The distance calculation counted real movement at this time. For the no-movement guard. */
    data class Moved(override val atMs: Long) : TripEvent
}

/**
 * Whether the event carries a reading of the truck's connection taken just now. Only such an
 * event may close a trip whose time has run out. Written as an exhaustive `when`, so a new kind
 * of event cannot be added without deciding this.
 */
internal fun TripEvent.readsTheTruck(): Boolean = when (this) {
    is TripEvent.TruckConnection, is TripEvent.ManualStart, is TripEvent.ManualEnd -> true
    is TripEvent.AndroidAutoConnection, is TripEvent.Moved -> false
}

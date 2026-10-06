package com.shawnkowalchuk.milo.core.trip

/** Any moment will do; this one is 2026-10-03 12:00 UTC. */
const val T0 = 1_791_028_800_000L

const val MINUTE = 60_000L
const val HOUR = 60 * MINUTE

/** The default grace period: 2 minutes. */
const val GRACE = 2 * MINUTE

val RULES = TripRules(gracePeriodMs = GRACE)

/** Runs one event through the rules with the default settings. */
fun TripState.on(event: TripEvent): TripTransition = TripStateMachine.step(this, event, RULES)

/** Runs several events one after another and returns everything they asked to be done. */
fun TripState.onAll(vararg events: TripEvent): List<TripEffect> {
    var state = this
    val effects = mutableListOf<TripEffect>()
    for (event in events) {
        val result = state.on(event)
        effects += result.effects
        state = result.state
    }
    return effects
}

/** One of every event the rules can be given, all at the same moment. */
fun everyEventAt(atMs: Long): List<TripEvent> = listOf(
    TripEvent.TruckConnection(true, atMs),
    TripEvent.TruckConnection(false, atMs),
    TripEvent.AndroidAutoConnection(true, atMs),
    TripEvent.AndroidAutoConnection(false, atMs),
    TripEvent.ManualStart(truckConnected = true, atMs),
    TripEvent.ManualStart(truckConnected = false, atMs),
    TripEvent.ManualEnd(truckConnected = true, atMs),
    TripEvent.ManualEnd(truckConnected = false, atMs),
    TripEvent.Moved(atMs),
)

/** Nothing connected, no trip. */
val IDLE = TripState()

/** A trip the truck started at [T0], with the truck still connected. */
val RECORDING =
    TripState(
        trip = ActiveTrip(TripStartCause.TRUCK, truckSeen = true, lastMovementAtMs = T0),
        truckConnected = true,
    )

/** When the truck was found gone in [IN_GRACE]. */
const val GRACE_START = T0 + 10 * MINUTE

/** The same trip, ten minutes later, with the truck gone and the grace period running. */
val IN_GRACE =
    TripState(
        trip =
            ActiveTrip(
                TripStartCause.TRUCK,
                truckSeen = true,
                grace = Grace(GRACE_START, GRACE_START + GRACE),
                lastMovementAtMs = T0,
            ),
    )

/** A trip Shawn started by hand at [T0] with no truck anywhere. */
val MANUAL_NO_TRUCK =
    TripState(
        trip = ActiveTrip(TripStartCause.MANUAL, truckSeen = false, lastMovementAtMs = T0),
    )

/** Shawn ended a trip by hand; the truck is still connected, so automatic start is held off. */
val HELD_OFF = TripState(truckConnected = true, autoStartHeldOff = true)

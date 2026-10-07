package com.shawnkowalchuk.milo.core.trip

/** The parked limit out of the box: 10 minutes. */
const val PARKED_LIMIT = 10 * MINUTE

/**
 * The rules as the trip controller runs them, with the parked rule on. [RULES] leaves it off,
 * which is what lets the tests of every other rule stand a truck still for hours.
 */
val PARKED_RULES = TripRules(gracePeriodMs = GRACE, parkedLimitMs = PARKED_LIMIT)

/** Runs one event through the rules with the parked rule on. */
fun TripState.onParked(event: TripEvent): TripTransition =
    TripStateMachine.step(this, event, PARKED_RULES)

/** Runs several events one after another, with the parked rule on. */
fun TripState.onAllParked(vararg events: TripEvent): TripTransition {
    var state = this
    val effects = mutableListOf<TripEffect>()
    for (event in events) {
        val result = state.onParked(event)
        effects += result.effects
        state = result.state
    }
    return TripTransition(state, effects)
}

/** When the wait in [WAITING] began: the trip of [RECORDING] was closed ten minutes after it. */
const val WAITING_SINCE = T0 + PARKED_LIMIT

/** No trip: the truck stood still, its trip was closed, and it is still connected. */
val WAITING = TripState(truckConnected = true, parked = Parked(sinceMs = WAITING_SINCE))

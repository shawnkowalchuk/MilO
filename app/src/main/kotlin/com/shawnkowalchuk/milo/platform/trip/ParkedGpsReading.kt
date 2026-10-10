package com.shawnkowalchuk.milo.platform.trip

import com.shawnkowalchuk.milo.core.trip.ParkedGps

// What the trip service does with the times the controller gives it for a wait beside the
// parked truck (`ParkedGps`). Plain functions, apart from the service, so that a unit test can
// ask them what a moment means.

/**
 * How GPS is read at [nowMs] beside the parked truck: every 5 seconds until
 * [ParkedGps.fastUntilMs], every 30 until [ParkedGps.untilMs], and not at all (null) after it.
 *
 * @param nowMs the time of day by MilO's clock, which is what the two times were worked out on.
 */
internal fun ParkedGps.rateAt(nowMs: Long): FixRate? {
    val fast = fastUntilMs?.let { nowMs < it } ?: false
    val on = untilMs?.let { nowMs < it } ?: true
    return when {
        fast -> FixRate.RECORDING
        on -> FixRate.WATCHING_PARKED
        else -> null
    }
}

/** The next moment after [nowMs] at which [rateAt] changes, or null if it does not. */
internal fun ParkedGps.nextChangeAfter(nowMs: Long): Long? =
    listOfNotNull(fastUntilMs, untilMs).filter { it > nowMs }.minOrNull()

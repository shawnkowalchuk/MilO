package com.shawnkowalchuk.milo.core.trip

/**
 * When the hold-off ends. Automatic start is held off after Shawn presses End with the truck
 * still connected ([TripStateMachine] sets it), and it is released by whichever of three things
 * comes first ([HoldOffRelease]). Three, because each of the first two can be missed while the
 * app is dead, and a hold-off that is never released swallows the next trip. A missed trip is
 * worse than an unwanted restart.
 */
internal object HoldOffRules {
    /**
     * A link-level connect event releases a hold-off that is old enough. The link the hold-off
     * was waiting on has dropped, or this one could not have formed, so the truck is no longer
     * known to be connected. The caller then says what is true now: connected for the link
     * broadcast, not yet known for the companion callback while idle, and unchanged for the
     * companion callback while a trip is open (it is not believed there).
     */
    fun releaseForNewLink(
        state: TripState,
        atMs: Long,
        rules: TripRules,
        effects: MutableList<TripEffect>,
    ): TripState {
        val since = state.autoStartHeldOffSinceMs ?: return state
        if (atMs - since <= rules.holdOffNewLinkAfterMs) return state
        effects += TripEffect.ReleaseHoldOff(HoldOffRelease.NEW_LINK)
        return state.copy(autoStartHeldOffSinceMs = null, truckConnected = false)
    }

    /**
     * The two releases that need no event of their own: the truck is known to be disconnected,
     * or the hold-off has reached its time limit. The limit is applied only with a fresh reading
     * of the truck, because releasing lets a connected truck start a trip in the same step, and
     * that must not rest on a belief that is hours old.
     */
    fun releaseIfDue(
        state: TripState,
        atMs: Long,
        rules: TripRules,
        truckWasRead: Boolean,
        effects: MutableList<TripEffect>,
    ): TripState {
        val since = state.autoStartHeldOffSinceMs ?: return state
        val reason =
            when {
                !state.truckConnected -> HoldOffRelease.TRUCK_SEEN_DISCONNECTED
                truckWasRead && atMs - since >= rules.holdOffLimitMs -> HoldOffRelease.TIME_LIMIT
                else -> return state
            }
        effects += TripEffect.ReleaseHoldOff(reason)
        return state.copy(autoStartHeldOffSinceMs = null)
    }
}

package com.shawnkowalchuk.milo.core.clock

/**
 * What the phone's clock did, for whoever writes it down and acts on it.
 *
 * In each, `offsetMs` is how far the phone's clock stood from MilO's: positive for ahead.
 */
sealed interface ClockNews {
    /** The phone's clock was found changed by more than MilO follows at once. Not believed. */
    data class SetAside(val offsetMs: Long) : ClockNews

    /**
     * The phone's clock agrees with MilO's again: the change was never believed.
     *
     * @param lastedMs from the first reading that saw the change to the one that saw it gone.
     */
    data class CameBack(val offsetMs: Long, val lastedMs: Long) : ClockNews

    /** The phone's clock was changed again before it came back. Neither change is believed. */
    data class ChangedAgain(val offsetMs: Long, val lastedMs: Long) : ClockNews

    /**
     * The change held for [HOLD_MS]: MilO's clock is the phone's again, from this reading.
     *
     * @param heldMs for how long it was seen without a gap, which is what made it believed.
     */
    data class Followed(val offsetMs: Long, val heldMs: Long) : ClockNews

    /**
     * MilO's clock had been taken from the phone's with nothing to check it against, and while
     * that anchor was on probation the phone's clock was found far behind it. By far the
     * likeliest reason is that MilO started while the phone's date was set ahead, and that the
     * date has now come back. MilO's clock is the phone's again, from this reading.
     *
     * @param aheadMs how far ahead of the phone's clock MilO's own time stood: what the phone's
     * date had been set ahead by when MilO took it.
     */
    data class StartedAhead(val aheadMs: Long) : ClockNews
}

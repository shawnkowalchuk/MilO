package com.shawnkowalchuk.milo.platform.clock

import com.shawnkowalchuk.milo.core.clock.JumpingPhone

/**
 * A daily alarm as Android's alarm service holds it, for the tests of the two daily looks: MilO
 * has one request at a time, and the newest takes the place of the one before, whichever of
 * the two clocks either was asked on.
 */
sealed interface AskedAlarm {
    /** Whether Android would deliver the alarm now, on [phone]. */
    fun isDueOn(phone: JumpingPhone): Boolean

    /** Asked for a time of day. Android goes by the phone's clock, so a date set ahead makes it due. */
    data class AtTimeOfDay(val atMs: Long) : AskedAlarm {
        override fun isDueOn(phone: JumpingPhone) = atMs <= phone.phoneNowMs
    }

    /** Asked for after a wait, counted on the clock that runs from boot. No date moves it. */
    data class CountedFromBoot(val dueAtElapsedMs: Long) : AskedAlarm {
        override fun isDueOn(phone: JumpingPhone) = dueAtElapsedMs <= phone.elapsedMs
    }
}

package com.shawnkowalchuk.milo.platform.clock

import com.shawnkowalchuk.milo.core.clock.PROBATION_MS

/**
 * How one of MilO's two daily alarms was asked of Android (ADR-005). Android's alarm service
 * goes by the phone's clock, so which way is right depends on how MilO's clock stands to it.
 */
internal sealed interface DailyAlarmAsked {
    /** For a time of day: MilO's time is the phone's, and it is confirmed. The normal way. */
    data object AtTimeOfDay : DailyAlarmAsked

    /**
     * After the time that is really left, counted on the clock that runs from boot: MilO does
     * not believe the phone's clock, by which Android would judge a time of day.
     */
    data class Counted(val leftMs: Long) : DailyAlarmAsked

    /**
     * After a short wait, counted from boot, at the end of which MilO asks again: MilO's time
     * is the phone's, but nothing has confirmed it yet (its anchor is on probation).
     */
    data class ToAskAgain(val waitMs: Long) : DailyAlarmAsked
}

/**
 * Asks Android for a daily alarm that is due at [atMs] on MilO's clock, in the way that fits
 * how MilO's clock stands to the phone's. The monthly reminder and the daily check both ask
 * through here, so that the two cannot come to differ.
 *
 * - **The clocks disagree:** [setAfter] the time that is left. Setting the phone's date does
 *   not move the clock that counts from boot, so this alarm cannot go off at a jump, and asking
 *   for it starts no loop. It is there at the real time if no MilO is running when the clocks
 *   agree again.
 * - **They agree, and MilO's anchor is on probation:** the phone's date may be set ahead this
 *   very moment, and [atMs] may be a day out. So MilO is woken as soon as the probation can be
 *   over ([PROBATION_MS]), counted from boot, and asks again then: by a clock that has been
 *   confirmed, or that has seen the date come back. Until then it shows no notification.
 *   Without this a MilO that Android started inside a jump and froze again would keep the
 *   alarm of a wrong day, and its anchor would stay unconfirmed until something else read the
 *   time. It is asked only while the clocks agree: a phone's clock that stays ahead of an
 *   anchor on probation gets the counted alarm above, not a wake every two minutes.
 * - **Otherwise:** [setFor] the time of day, the normal way.
 *
 * Each request takes the place of the one before, whichever kind either was.
 *
 * @param nowMs MilO's time, read just before.
 * @param clockOnProbation what the last reading of MilO's clock left: it reads no clock itself.
 */
internal fun askForDailyAlarm(
    atMs: Long,
    nowMs: Long,
    phoneClockAgrees: () -> Boolean,
    clockOnProbation: () -> Boolean,
    setFor: (atMs: Long) -> Unit,
    setAfter: (delayMs: Long) -> Unit,
): DailyAlarmAsked {
    val leftMs = atMs - nowMs
    return when {
        !phoneClockAgrees() -> DailyAlarmAsked.Counted(leftMs).also { setAfter(leftMs) }

        clockOnProbation() -> {
            val waitMs = minOf(leftMs, PROBATION_MS)
            DailyAlarmAsked.ToAskAgain(waitMs).also { setAfter(waitMs) }
        }

        else -> DailyAlarmAsked.AtTimeOfDay.also { setFor(atMs) }
    }
}

/**
 * The sentence both daily looks write for an alarm that was asked for [DailyAlarmAsked.ToAskAgain]:
 * what follows "Nothing-recorded check: " or "Monthly reminder: ".
 */
internal fun askAgainText(waitMs: Long, source: String): String =
    "MilO's time is not confirmed yet: it was taken from the phone's clock at a start with " +
        "nothing to check it against ($source). So Android was asked to count " +
        "${spanText(waitMs)} from now. MilO then asks for the next daily look, and shows no " +
        "notification while its time is not confirmed."

/**
 * What both daily looks write after "…: " for a notification that is due by a clock on
 * probation and is therefore not shown.
 */
internal const val HELD_BACK_WHY =
    "MilO's time is not confirmed yet. It was taken from the phone's clock at a start with " +
        "nothing to check it against, and the phone's date may be set ahead. MilO looks again " +
        "once its time is confirmed."

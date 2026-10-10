package com.shawnkowalchuk.milo.platform.clock

import com.shawnkowalchuk.milo.core.clock.PROBATION_MS
import org.junit.Assert.assertEquals
import org.junit.Test

private const val HOUR_MS = 3_600_000L
private const val NOW_MS = 1_791_419_562_000L

/**
 * The three ways a daily alarm is asked of Android, by how MilO's clock stands to the phone's
 * (ADR-005). The monthly reminder and the daily check both ask through [askForDailyAlarm].
 */
class DailyAlarmAskedTest {
    /** What Android was asked: "for <time of day>" or "after <wait>". */
    private val requests = mutableListOf<String>()

    private fun ask(leftMs: Long, agrees: Boolean, onProbation: Boolean): DailyAlarmAsked =
        askForDailyAlarm(
            atMs = NOW_MS + leftMs,
            nowMs = NOW_MS,
            phoneClockAgrees = { agrees },
            clockOnProbation = { onProbation },
            setFor = { requests += "for $it" },
            setAfter = { requests += "after $it" },
        )

    @Test
    fun `a clock that agrees and is confirmed asks for the time of day`() {
        val asked = ask(17 * HOUR_MS, agrees = true, onProbation = false)

        assertEquals(DailyAlarmAsked.AtTimeOfDay, asked)
        assertEquals(listOf("for ${NOW_MS + 17 * HOUR_MS}"), requests)
    }

    @Test
    fun `clocks that disagree ask to count the time that is left`() {
        val asked = ask(17 * HOUR_MS, agrees = false, onProbation = false)

        assertEquals(DailyAlarmAsked.Counted(17 * HOUR_MS), asked)
        assertEquals(listOf("after ${17 * HOUR_MS}"), requests)
    }

    @Test
    fun `a clock on probation that agrees asks to be woken when the probation can be over`() {
        val asked = ask(17 * HOUR_MS, agrees = true, onProbation = true)

        assertEquals(DailyAlarmAsked.ToAskAgain(PROBATION_MS), asked)
        assertEquals(listOf("after $PROBATION_MS"), requests)
    }

    @Test
    fun `on probation an alarm that is due sooner is not put off`() {
        val asked = ask(45_000L, agrees = true, onProbation = true)

        assertEquals(DailyAlarmAsked.ToAskAgain(45_000L), asked)
        assertEquals(listOf("after 45000"), requests)
    }

    @Test
    fun `on probation with the phone's clock ahead, it counts the time left, not two minutes`() {
        // A wake every two minutes for as long as a date stays ahead would never end: a MilO
        // that Android keeps frozen in between cannot watch the change for its ten minutes.
        val asked = ask(17 * HOUR_MS, agrees = false, onProbation = true)

        assertEquals(DailyAlarmAsked.Counted(17 * HOUR_MS), asked)
        assertEquals(listOf("after ${17 * HOUR_MS}"), requests)
    }

    @Test
    fun `the line for an alarm asked to ask again says why and for how long`() {
        assertEquals(
            "MilO's time is not confirmed yet: it was taken from the phone's clock at a start " +
                "with nothing to check it against (process start). So Android was asked to " +
                "count 2 min from now. MilO then asks for the next daily look, and shows no " +
                "notification while its time is not confirmed.",
            askAgainText(PROBATION_MS, "process start"),
        )
    }
}

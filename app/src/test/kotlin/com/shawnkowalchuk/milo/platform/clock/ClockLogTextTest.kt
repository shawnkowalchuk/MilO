package com.shawnkowalchuk.milo.platform.clock

import com.shawnkowalchuk.milo.core.clock.ClockNews
import com.shawnkowalchuk.milo.core.clock.DAY_MS
import com.shawnkowalchuk.milo.core.clock.HOLD_MS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private const val SECOND_MS = 1_000L
private const val MINUTE_MS = 60 * SECOND_MS
private const val HOUR_MS = 60 * MINUTE_MS

/** The one line the event log gets for a change of the phone's clock that MilO did not follow. */
class ClockLogTextTest {
    @Test
    fun `the date trick reads as it happened`() {
        assertEquals(
            "The phone's date was set 24 h 0 min ahead. MilO saw it back 9 s later and kept " +
                "its own time.",
            clockNewsText(ClockNews.CameBack(offsetMs = DAY_MS, lastedMs = 9 * SECOND_MS)),
        )
    }

    @Test
    fun `a day set by hand comes out some milliseconds short, and still reads as 24 hours`() {
        // The phone's own time log, 2026-10-06: the date was set 12 ms short of a day ahead.
        val line = clockNewsText(ClockNews.CameBack(offsetMs = DAY_MS - 12, lastedMs = 5_356))

        assertEquals(
            "The phone's date was set 24 h 0 min ahead. MilO saw it back 5 s later and kept " +
                "its own time.",
            line,
        )
    }

    @Test
    fun `a clock set back by less than a day is called a clock, and back`() {
        assertEquals(
            "The phone's clock was set 3 h 0 min back. MilO saw it back 1 min 15 s later and " +
                "kept its own time.",
            clockNewsText(ClockNews.CameBack(-3 * HOUR_MS, 75 * SECOND_MS)),
        )
    }

    @Test
    fun `a change that held says that MilO follows it now`() {
        assertEquals(
            "The phone's clock is 3 h 0 min behind where it was and has stayed so for 10 min. " +
                "MilO now follows it.",
            clockNewsText(ClockNews.Followed(-3 * HOUR_MS, HOLD_MS)),
        )
        assertEquals(
            "The phone's clock is 24 h 0 min ahead of where it was and has stayed so for 10 min. " +
                "MilO now follows it.",
            clockNewsText(ClockNews.Followed(DAY_MS, HOLD_MS)),
        )
    }

    @Test
    fun `a change that was changed again says so`() {
        assertEquals(
            "The phone's date was set 24 h 0 min ahead. MilO saw it changed again 5 min later " +
                "and kept its own time.",
            clockNewsText(ClockNews.ChangedAgain(DAY_MS, 5 * MINUTE_MS)),
        )
    }

    @Test
    fun `a start that took a date which was set ahead says so, without a length of time`() {
        assertEquals(
            "MilO started while the phone's date was set 24 h 0 min ahead, and took the phone's " +
                "time when it came back.",
            clockNewsText(ClockNews.StartedAhead(aheadMs = DAY_MS - 12)),
        )
        assertEquals(
            "MilO started while the phone's clock was set 3 h 0 min ahead, and took the phone's " +
                "time when it came back.",
            clockNewsText(ClockNews.StartedAhead(aheadMs = 3 * HOUR_MS)),
        )
    }

    @Test
    fun `a change that has only just been seen has no line yet`() {
        assertNull(clockNewsText(ClockNews.SetAside(DAY_MS)))
    }

    @Test
    fun `a length of time is written in the largest two units that fit`() {
        assertEquals("0 s", spanText(0))
        assertEquals("0 s", spanText(499))
        assertEquals("1 s", spanText(500))
        assertEquals("59 s", spanText(59 * SECOND_MS))
        assertEquals("1 min", spanText(MINUTE_MS))
        assertEquals("10 min", spanText(HOLD_MS))
        assertEquals("12 min 30 s", spanText(750 * SECOND_MS))
        assertEquals("1 h 0 min", spanText(HOUR_MS))
        assertEquals("48 h 5 min", spanText(2 * DAY_MS + 5 * MINUTE_MS + 20 * SECOND_MS))
    }
}

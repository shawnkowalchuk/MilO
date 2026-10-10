package com.shawnkowalchuk.milo.core.clock

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val SECOND_MS = 1_000L
private const val MINUTE_MS = 60 * SECOND_MS
private const val HOUR_MS = 60 * MINUTE_MS

/** Wednesday 7 October 2026, 18:32:42 in Edmonton: an evening on which the date was set ahead. */
private const val EVENING_MS = 1_791_419_562_000L

/**
 * The rule of MilO's clock (ADR-005): the phone's clock is followed while it changes by no more
 * than a network correction, and a larger change is believed only once it has held for ten
 * minutes. The phone of these tests is [JumpingPhone]: its wall clock can be set, and its time
 * since boot runs on.
 */
class TrustedClockTest {
    private val phone = JumpingPhone(EVENING_MS)
    private val clock get() = phone.clock

    /** Reads the clock once a [step] for [span], and answers every reading. */
    private fun readFor(span: Long, step: Long = SECOND_MS): List<Long> = buildList {
        var passed = 0L
        while (passed < span) {
            phone.advance(step)
            passed += step
            add(clock.now())
        }
    }

    // ---- Nobody touches the phone's clock --------------------------------------------------------

    @Test
    fun `while the phone's clock is left alone MilO's time is exactly the phone's`() {
        val random = Random(20261007)

        repeat(5_000) {
            // From a millisecond to three days between two readings: a GPS fix, a night asleep.
            phone.advance(random.nextLong(1, 3 * DAY_MS))
            assertEquals(phone.phoneNowMs, clock.now())
            assertTrue(clock.agreesWithPhone())
        }

        assertEquals(emptyList<ClockNews>(), phone.news)
    }

    @Test
    fun `the anchor is stored when it is first set, and then every few minutes at most`() {
        assertEquals(1, phone.stored.size)

        readFor(30 * MINUTE_MS, step = 5 * SECOND_MS)

        // 360 readings, and seven anchors: the first, and one for each five minutes.
        assertEquals(7, phone.stored.size)
        phone.stored.zipWithNext { earlier, later ->
            assertTrue(later.elapsedMs - earlier.elapsedMs >= ANCHOR_SAVE_EVERY_MS)
        }
        assertEquals(phone.phoneNowMs, phone.stored.last().wallMs)
    }

    @Test
    fun `a correction of up to two minutes, either way, is followed at once`() {
        val corrections = listOf(40L, -900L, 90 * SECOND_MS, -FOLLOW_AT_ONCE_MS, FOLLOW_AT_ONCE_MS)
        for (correction in corrections) {
            phone.advance(MINUTE_MS)
            clock.now()
            // The network puts the phone's clock right by this much.
            phone.setBy(phone.aheadMs + correction)

            assertEquals(phone.phoneNowMs, clock.now())
            assertTrue(clock.agreesWithPhone())
        }
        assertEquals(emptyList<ClockNews>(), phone.news)
    }

    @Test
    fun `a change of a millisecond more than two minutes is not followed`() {
        clock.now()
        phone.setBy(FOLLOW_AT_ONCE_MS + 1)

        assertEquals(phone.trueNowMs, clock.now())
        assertFalse(clock.agreesWithPhone())
        assertEquals(listOf(ClockNews.SetAside(FOLLOW_AT_ONCE_MS + 1)), phone.news)
    }

    // ---- The date trick: a day ahead for some seconds --------------------------------------------

    @Test
    fun `a day ahead for 6, 16, 60 and 599 seconds is never believed, and time runs on evenly`() {
        for (seconds in listOf(6L, 16L, 60L, 599L)) {
            phone.news.clear()
            readFor(MINUTE_MS)
            phone.setAhead()
            val during = readFor(seconds * SECOND_MS)
            phone.setBack()
            val after = readFor(MINUTE_MS)

            // One reading a second, and each a second after the one before, through the
            // jump and out of it. None is a day ahead.
            val readings = during + after
            readings.zipWithNext { earlier, later -> assertEquals(SECOND_MS, later - earlier) }
            assertEquals("after $seconds s", phone.trueNowMs, readings.last())
            assertEquals(phone.phoneNowMs, clock.now())
            assertEquals(
                listOf(
                    ClockNews.SetAside(DAY_MS),
                    // "Lasted" runs from the first reading that saw the jump, a second into it.
                    ClockNews.CameBack(DAY_MS, lastedMs = seconds * SECOND_MS),
                ),
                phone.news,
            )
        }
    }

    @Test
    fun `while the date is ahead the clock says that it does not agree with the phone`() {
        clock.now()
        phone.setAhead()
        assertFalse(clock.agreesWithPhone())
        phone.advance(9 * SECOND_MS)
        assertFalse(clock.agreesWithPhone())

        phone.setBack()

        assertTrue(clock.agreesWithPhone())
    }

    @Test
    fun `a day back is not believed either, and time does not run backwards`() {
        val before = clock.now()
        phone.setBy(-3 * HOUR_MS)
        val during = readFor(20 * SECOND_MS)
        phone.setBack()

        assertTrue(during.first() > before)
        during.zipWithNext { earlier, later -> assertEquals(SECOND_MS, later - earlier) }
        assertEquals(phone.phoneNowMs, clock.now())
        assertEquals(
            listOf(
                ClockNews.SetAside(-3 * HOUR_MS),
                ClockNews.CameBack(-3 * HOUR_MS, 19 * SECOND_MS),
            ),
            phone.news,
        )
    }

    // ---- A change that holds ----------------------------------------------------------------------

    @Test
    fun `a change that holds for ten minutes is followed, once, and not a moment before`() {
        clock.now()
        phone.setBy(3 * HOUR_MS)
        clock.now()

        // Looked at every 30 seconds, as the watch does. For ten minutes MilO keeps its own
        // time, which runs on evenly and never goes back.
        val held = readFor(HOLD_MS - 30 * SECOND_MS, step = 30 * SECOND_MS)
        held.zipWithNext { earlier, later -> assertEquals(30 * SECOND_MS, later - earlier) }
        assertEquals(phone.trueNowMs, held.last())
        assertFalse(clock.agreesWithPhone())

        phone.advance(30 * SECOND_MS)
        assertEquals(phone.phoneNowMs, clock.now())
        assertTrue(clock.agreesWithPhone())
        assertEquals(
            listOf(ClockNews.SetAside(3 * HOUR_MS), ClockNews.Followed(3 * HOUR_MS, HOLD_MS)),
            phone.news,
        )

        // From here on it is the phone's clock again, and nothing more is told.
        readFor(HOUR_MS, step = MINUTE_MS).forEach { assertTrue(it > held.last() + 3 * HOUR_MS) }
        assertEquals(phone.phoneNowMs, clock.now())
        assertEquals(2, phone.news.size)
    }

    @Test
    fun `a clock set back for good is followed after ten minutes, and only then goes back`() {
        clock.now()
        phone.setBy(-3 * HOUR_MS)
        clock.now()
        val held = readFor(HOLD_MS - 30 * SECOND_MS, step = 30 * SECOND_MS)
        held.zipWithNext { earlier, later -> assertTrue(later > earlier) }

        phone.advance(30 * SECOND_MS)

        assertEquals(phone.trueNowMs - 3 * HOUR_MS, clock.now())
        assertEquals(ClockNews.Followed(-3 * HOUR_MS, HOLD_MS), phone.news.last())
    }

    @Test
    fun `a second, different change during the ten minutes starts them again`() {
        clock.now()
        phone.setAhead()
        clock.now()
        readFor(5 * MINUTE_MS, step = 30 * SECOND_MS)
        // Five minutes in, the date is set a second day ahead.
        phone.setBy(2 * DAY_MS)
        clock.now()

        // Ten minutes after the first change, five after the second: neither is believed.
        readFor(5 * MINUTE_MS, step = 30 * SECOND_MS)
        assertEquals(phone.trueNowMs, clock.now())

        // Ten minutes after the second, it is.
        readFor(5 * MINUTE_MS, step = 30 * SECOND_MS)
        assertEquals(phone.phoneNowMs, clock.now())
        assertEquals(
            listOf(
                ClockNews.SetAside(DAY_MS),
                ClockNews.ChangedAgain(DAY_MS, lastedMs = 5 * MINUTE_MS),
                ClockNews.SetAside(2 * DAY_MS),
                ClockNews.Followed(2 * DAY_MS, HOLD_MS),
            ),
            phone.news,
        )
    }

    @Test
    fun `a date held ahead for over ten minutes is believed, and its way back ten minutes late`() {
        // The price of the rule (ADR-005): MilO cannot tell a date that was really changed
        // from the trick held for too long.
        clock.now()
        phone.setAhead()
        clock.now()
        readFor(11 * MINUTE_MS, step = 30 * SECOND_MS)
        assertEquals(phone.trueNowMs + DAY_MS, clock.now())

        phone.setBack()
        clock.now()
        readFor(9 * MINUTE_MS, step = 30 * SECOND_MS)
        assertEquals("still a day ahead", phone.trueNowMs + DAY_MS, clock.now())
        readFor(MINUTE_MS, step = 30 * SECOND_MS)

        assertEquals(phone.trueNowMs, clock.now())
    }

    // ---- "Held" means seen to hold ----------------------------------------------------------------

    @Test
    fun `two looks eleven minutes apart with nothing between do not make a change that held`() {
        clock.now()
        phone.setAhead()
        clock.now()

        // Nobody looks for eleven minutes: Android has put MilO's process to sleep. The date
        // may have gone back and been set ahead again in between, and on 2026-10-07 it had.
        phone.advance(11 * MINUTE_MS)

        assertEquals(phone.trueNowMs, clock.now())
        assertFalse(clock.agreesWithPhone())
    }

    @Test
    fun `the evening of 7 October, one look inside each of its five jumps, is never believed`() {
        // When the date was set ahead that evening, and for how long, in seconds from the
        // first time (the phone's own time log). MilO looks once inside each jump, and at no
        // other time: what a process does that only the daily alarm wakes.
        val jumps = listOf(0L to 8L, 407L to 10L, 729L to 6L, 1_141L to 15L, 1_763L to 9L)
        clock.now()
        val began = phone.trueNowMs

        for ((at, lasts) in jumps) {
            phone.advanceTo(began + at * SECOND_MS)
            phone.setAhead()
            phone.advance(lasts * SECOND_MS / 2)
            assertEquals("the jump at $at s", phone.trueNowMs, clock.now())
            phone.advance(lasts * SECOND_MS / 2)
            phone.setBack()
        }

        phone.advance(MINUTE_MS)
        assertEquals(phone.phoneNowMs, clock.now())
        assertTrue(phone.news.none { it is ClockNews.Followed })
    }

    @Test
    fun `a change that is seen again after a gap is followed ten minutes after that`() {
        clock.now()
        phone.setBy(3 * HOUR_MS)
        clock.now()
        phone.advance(HOUR_MS)
        clock.now()

        readFor(HOLD_MS - 30 * SECOND_MS, step = 30 * SECOND_MS)
        assertEquals(phone.trueNowMs, clock.now())
        phone.advance(30 * SECOND_MS)

        assertEquals(phone.phoneNowMs, clock.now())
        assertEquals(ClockNews.Followed(3 * HOUR_MS, HOLD_MS), phone.news.last())
    }
}

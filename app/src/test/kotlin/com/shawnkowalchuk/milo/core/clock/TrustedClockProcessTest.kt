package com.shawnkowalchuk.milo.core.clock

import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val SECOND_MS = 1_000L
private const val MINUTE_MS = 60 * SECOND_MS
private const val HOUR_MS = 60 * MINUTE_MS

/** Wednesday 7 October 2026, 18:32:42 in Edmonton. */
private const val EVENING_MS = 1_791_419_562_000L

/**
 * MilO's clock across a start of MilO's process and a restart of the phone, with the anchor it
 * stores for the next process, and read by many threads at once.
 */
class TrustedClockProcessTest {
    private val phone = JumpingPhone(EVENING_MS)

    // ---- A process that starts while the date is set ahead ---------------------------------------

    @Test
    fun `a process started while the date is ahead has the right time from its first reading`() {
        phone.clock.now()
        phone.advance(3 * HOUR_MS)
        phone.setAhead()

        // The daily alarm, a day overdue all at once, has Android start MilO.
        phone.newProcess()

        assertEquals(phone.trueNowMs, phone.clock.now())
        assertFalse(phone.clock.agreesWithPhone())
        phone.advance(9 * SECOND_MS)
        assertEquals(phone.trueNowMs, phone.clock.now())

        phone.setBack()
        assertEquals(phone.phoneNowMs, phone.clock.now())
        assertEquals(
            listOf(ClockNews.SetAside(DAY_MS), ClockNews.CameBack(DAY_MS, 9 * SECOND_MS)),
            phone.news,
        )
    }

    @Test
    fun `a process started after a month of the same boot still has the anchor's time`() {
        phone.clock.now()
        phone.advance(31 * DAY_MS)
        phone.setAhead()

        phone.newProcess()

        assertEquals(phone.trueNowMs, phone.clock.now())
    }

    @Test
    fun `each new process waits its own ten minutes before it follows a change`() {
        phone.clock.now()
        phone.setBy(3 * HOUR_MS)
        phone.clock.now()
        phone.advance(9 * MINUTE_MS)

        // What an earlier process saw is not handed on: it cannot say that the change held.
        phone.newProcess()
        phone.advance(2 * MINUTE_MS)

        assertEquals(phone.trueNowMs, phone.clock.now())
    }

    // ---- No anchor to go by ----------------------------------------------------------------------

    @Test
    fun `with no stored anchor the phone's clock is taken as it is, and an anchor is stored`() {
        // The first start after the update that brought MilO its own clock.
        val anchors = mutableListOf<ClockAnchor>()
        val first =
            TrustedClock({ EVENING_MS }, { 5_000L }, bootCount = 7, stored = null, anchors::add)

        assertEquals(EVENING_MS, first.now())
        // Stored with the mark that nothing has confirmed it yet (`TrustedClockProbationTest`).
        assertEquals(
            listOf(ClockAnchor(7, EVENING_MS, elapsedMs = 5_000L, onProbationSinceMs = 5_000L)),
            anchors,
        )
    }

    @Test
    fun `after a restart of the phone the stored anchor is not used`() {
        phone.clock.now()
        val before = phone.stored.last()
        phone.advance(HOUR_MS)
        // Switched off and on with the date a day ahead: MilO cannot know better, and takes
        // the phone's clock, as it did before it kept its own.
        phone.setAhead()

        phone.reboot()

        assertTrue(phone.elapsedMs < before.elapsedMs)
        assertEquals(phone.phoneNowMs, phone.clock.now())
        assertTrue(phone.clock.agreesWithPhone())
        assertEquals(phone.bootCount, phone.stored.last().bootCount)
        assertEquals(phone.phoneNowMs, phone.stored.last().wallMs)
    }

    @Test
    fun `an anchor of another boot is not used even if the phone has been on for longer since`() {
        val old =
            ClockAnchor(6, EVENING_MS - 40 * DAY_MS, elapsedMs = 1_000L, onProbationSinceMs = null)
        val clock =
            TrustedClock({ EVENING_MS }, { 90_000L }, bootCount = 7, stored = old, save = {})

        assertEquals(EVENING_MS, clock.now())
    }

    @Test
    fun `an anchor that claims a later moment of this boot than now is not used`() {
        val impossible =
            ClockAnchor(7, EVENING_MS - DAY_MS, elapsedMs = 90_001L, onProbationSinceMs = null)
        val clock =
            TrustedClock({ EVENING_MS }, { 90_000L }, bootCount = 7, stored = impossible, save = {})

        assertEquals(EVENING_MS, clock.now())
    }

    @Test
    fun `a phone that keeps no boot count gets the phone's clock, and stores no anchor`() {
        val anchors = mutableListOf<ClockAnchor>()
        val left =
            ClockAnchor(-1, EVENING_MS - DAY_MS, elapsedMs = 1_000L, onProbationSinceMs = null)
        val clock = TrustedClock({ EVENING_MS }, { 90_000L }, bootCount = null, left, anchors::add)

        assertEquals(EVENING_MS, clock.now())
        assertEquals(emptyList<ClockAnchor>(), anchors)
    }

    @Test
    fun `a change that is followed is stored at once, for the next process`() {
        phone.clock.now()
        phone.setBy(3 * HOUR_MS)
        phone.clock.now()
        repeat(20) {
            phone.advance(30 * SECOND_MS)
            phone.clock.now()
        }
        assertEquals(ClockNews.Followed(3 * HOUR_MS, HOLD_MS), phone.news.last())
        assertEquals(phone.phoneNowMs, phone.stored.last().wallMs)

        phone.newProcess()

        assertEquals(phone.phoneNowMs, phone.clock.now())
        assertTrue(phone.clock.agreesWithPhone())
    }

    // ---- News ------------------------------------------------------------------------------------

    @Test
    fun `what was noticed before anyone listened is handed over, in order`() {
        var phoneMs = EVENING_MS
        var elapsedMs = 5_000L
        val clock =
            TrustedClock({ phoneMs }, { elapsedMs }, bootCount = 7, stored = null, save = {})
        phoneMs += DAY_MS
        clock.now()
        elapsedMs += 9 * SECOND_MS
        phoneMs -= DAY_MS - 9 * SECOND_MS
        clock.now()

        val told = mutableListOf<ClockNews>()
        clock.tellNewsTo { told += it }

        assertEquals(
            listOf(ClockNews.SetAside(DAY_MS), ClockNews.CameBack(DAY_MS, 9 * SECOND_MS)),
            told,
        )
        // And each is handed over once.
        clock.tellNewsTo { told += it }
        assertEquals(2, told.size)
    }

    // ---- Many readers ----------------------------------------------------------------------------

    @Test
    fun `read by many threads while the date jumps, no reading is ever a day ahead`() {
        val elapsed = AtomicLong(1_000_000_000L)
        val ahead = AtomicLong(0L)
        val clock =
            TrustedClock(
                phoneNow = { EVENING_MS + elapsed.get() + ahead.get() },
                elapsedNow = { elapsed.get() },
                bootCount = 7,
                stored = null,
                save = {},
            )
        val wrong = AtomicLong(0)
        val failed = AtomicReference<Throwable?>(null)
        val begin = CountDownLatch(1)
        val readers =
            List(8) {
                thread {
                    begin.await()
                    try {
                        repeat(20_000) {
                            // Held against the true time as it is after the reading. These
                            // two clocks are not read in one step as a phone's are, so a
                            // reading may be off by what passed in between: minutes at most
                            // here, never a day.
                            val off = clock.now() - (EVENING_MS + elapsed.get())
                            if (abs(off) > OFF_ALLOWED_MS) wrong.incrementAndGet()
                        }
                    } catch (failure: Throwable) {
                        failed.compareAndSet(null, failure)
                    }
                }
            }

        begin.countDown()
        // Meanwhile the date is set a day ahead and back, over and over, for as long as
        // anything reads. Less than the ten minutes passes in all, so nothing may be followed.
        var passedMs = 0L
        while (readers.any { it.isAlive }) {
            ahead.set(DAY_MS)
            if (passedMs < TIME_THAT_PASSES_MS) {
                elapsed.incrementAndGet()
                passedMs++
            }
            ahead.set(0L)
        }
        readers.forEach { it.join() }

        assertEquals(null, failed.get())
        assertEquals(0L, wrong.get())
        assertTrue(abs(clock.now() - (EVENING_MS + elapsed.get())) <= OFF_ALLOWED_MS)
    }

    private companion object {
        /** How much time the many-threads test lets pass: less than the ten minutes. */
        const val TIME_THAT_PASSES_MS = 5 * MINUTE_MS

        /** How far a reading of that test may be from the true time. Far less than a day. */
        const val OFF_ALLOWED_MS = TIME_THAT_PASSES_MS
    }
}

package com.shawnkowalchuk.milo.clockjump

import com.shawnkowalchuk.milo.core.clock.JumpingPhone
import com.shawnkowalchuk.milo.platform.trip.TripCheckTimer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val SECOND_MS = 1_000L
private const val TEN_MINUTES_MS = 10L * 60L * SECOND_MS

/**
 * The trip service's own timer, the real `TripCheckTimer`: it waits for "deadline minus the
 * clock at the moment it is set", and the wait then runs on a coroutine delay, which no change
 * of a clock touches. So what a date jump does to a timer depends on one thing: what the clock
 * read when it was SET.
 *
 * Until 2026-10-07 the timer read the phone's clock itself, and these were the investigation's
 * proofs of what followed: a deadline from before the jump, set inside it, was due at once; one
 * stored inside the jump and set after it was a day away. The timer is now handed MilO's clock,
 * on a phone whose date a test sets ([JumpingPhone]), and each timer runs out when it should.
 * The delay itself runs on the test's virtual time.
 */
// advanceTimeBy() and runCurrent() are how a test moves virtual time; both are experimental.
@OptIn(ExperimentalCoroutinesApi::class)
class ClockJumpTripTimerTest {
    private val phone = JumpingPhone(FRIDAY_NOON_MS)
    private val clock = { phone.clock.now() }

    @Test
    fun `a deadline from before the jump, set while the date is ahead, is still ten minutes off`() =
        runTest {
            val fired = mutableListOf<Long>()
            val timer = TripCheckTimer(backgroundScope, clock) { fired += it }
            // The parked limit of a trip that last moved now: ten minutes from now.
            val deadline = clock() + TEN_MINUTES_MS

            phone.setAhead()
            timer.set(deadline)
            runCurrent()
            assertTrue("fired at once", fired.isEmpty())
            advanceTimeBy(TEN_MINUTES_MS - SECOND_MS)
            runCurrent()
            assertTrue("fired early", fired.isEmpty())

            advanceTimeBy(2 * SECOND_MS)
            runCurrent()
            assertEquals(listOf(deadline), fired)
        }

    @Test
    fun `a deadline worked out inside the jump, set once the clock is back, is ten minutes off`() =
        runTest {
            val fired = mutableListOf<Long>()
            val timer = TripCheckTimer(backgroundScope, clock) { fired += it }
            // A trip started inside the jump: its parked limit is its start plus ten minutes,
            // on MilO's clock. The phone's clock is back when the timer is set.
            phone.setAhead()
            val deadline = clock() + TEN_MINUTES_MS
            phone.advance(4 * SECOND_MS)
            phone.setBack()

            timer.set(deadline)
            advanceTimeBy(TEN_MINUTES_MS - 5 * SECOND_MS)
            runCurrent()
            assertTrue("fired early", fired.isEmpty())

            advanceTimeBy(2 * SECOND_MS)
            runCurrent()
            assertEquals(listOf(deadline), fired)
        }

    @Test
    fun `a timer that is already running is not disturbed, and is not set again`() = runTest {
        val fired = mutableListOf<Long>()
        val timer = TripCheckTimer(backgroundScope, clock) { fired += it }
        val deadline = clock() + TEN_MINUTES_MS

        timer.set(deadline)
        advanceTimeBy(TEN_MINUTES_MS / 2)
        // The controller names the same deadline after every GPS fix: nothing is recomputed,
        // whatever the phone's clock reads by then.
        phone.setAhead()
        timer.set(deadline)
        advanceTimeBy(TEN_MINUTES_MS / 2 + SECOND_MS)
        runCurrent()

        assertEquals(listOf(deadline), fired)
    }
}

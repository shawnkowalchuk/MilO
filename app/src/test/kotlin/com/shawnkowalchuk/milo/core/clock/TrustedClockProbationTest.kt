package com.shawnkowalchuk.milo.core.clock

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val SECOND_MS = 1_000L
private const val MINUTE_MS = 60 * SECOND_MS
private const val HOUR_MS = 60 * MINUTE_MS

/** Wednesday 7 October 2026, 18:32:42 in Edmonton: an evening on which the date was set ahead. */
private const val EVENING_MS = 1_791_419_562_000L

/**
 * An anchor on probation (ADR-005, decided on 2026-10-09). A MilO process that starts with no
 * anchor to go by takes the phone's clock as it is, and that can be in the very seconds the
 * date is set a day ahead. Until the phone's clock has agreed with such an anchor at a reading
 * two minutes or more after it was taken, a phone's clock found far BEHIND MilO's time is
 * taken at once: the trick goes ahead and then back, so a clock that goes back says that the
 * anchor was taken inside a jump.
 *
 * [fresh] is a phone on which no MilO has run in this boot. [phone] is the usual one, with a
 * proven anchor from an earlier process.
 */
class TrustedClockProbationTest {
    private val fresh = JumpingPhone(EVENING_MS, miloHasRun = false)
    private val phone = JumpingPhone(EVENING_MS)

    /** Reads [of]'s clock once a second for [span], and answers how far each reading was off. */
    private fun offFor(of: JumpingPhone, span: Long): List<Long> = buildList {
        repeat((span / SECOND_MS).toInt()) {
            of.advance(SECOND_MS)
            add(of.clock.now() - of.trueNowMs)
        }
    }

    // ---- When probation begins and ends ----------------------------------------------------------

    @Test
    fun `a start with no anchor stores the phone's time at once, marked as on probation`() {
        val taken = fresh.stored.single()

        assertTrue(fresh.clock.onProbation)
        assertEquals(ClockAnchor(7, EVENING_MS, taken.elapsedMs, taken.elapsedMs), taken)
        // A start that finds a proven anchor is on none, and stores nothing new.
        assertFalse(phone.clock.onProbation)
        assertEquals(1, phone.stored.size)
    }

    @Test
    fun `probation ends at the first agreeing reading two minutes on, and not a moment before`() {
        fresh.advance(PROBATION_MS - 1)
        assertEquals(fresh.phoneNowMs, fresh.clock.now())
        assertTrue(fresh.clock.onProbation)

        fresh.advance(1)
        assertEquals(fresh.phoneNowMs, fresh.clock.now())

        assertFalse(fresh.clock.onProbation)
        // Stored at once, so that the next process knows, and with nothing told to anyone.
        assertNull(fresh.stored.last().onProbationSinceMs)
        assertEquals(fresh.phoneNowMs, fresh.stored.last().wallMs)
        assertEquals(emptyList<ClockNews>(), fresh.news)
    }

    @Test
    fun `time alone does not end probation, and neither does a reading that disagrees`() {
        // Nobody reads the clock for three hours: Android has put MilO's process to sleep.
        fresh.advance(3 * HOUR_MS)
        assertTrue(fresh.clock.onProbation)

        // The first reading falls into a jump. It says nothing for the anchor.
        fresh.setAhead()
        assertEquals(fresh.trueNowMs, fresh.clock.now())
        assertTrue(fresh.clock.onProbation)

        fresh.advance(9 * SECOND_MS)
        fresh.setBack()
        assertEquals(fresh.trueNowMs, fresh.clock.now())
        assertFalse(fresh.clock.onProbation)
    }

    // ---- What probation changes: a step back is taken at once -------------------------------------

    @Test
    fun `during probation a phone's clock found far behind is taken at once`() {
        fresh.advance(30 * SECOND_MS)
        fresh.setBy(-3 * HOUR_MS)

        assertEquals(fresh.phoneNowMs, fresh.clock.now())
        assertTrue(fresh.clock.agreesWithPhone())
        assertEquals(listOf(ClockNews.StartedAhead(aheadMs = 3 * HOUR_MS)), fresh.news)
        // The new anchor is stored, and is on probation itself, from this reading.
        val taken = fresh.stored.last()
        assertEquals(fresh.phoneNowMs, taken.wallMs)
        assertEquals(fresh.elapsedMs, taken.onProbationSinceMs)
        assertTrue(fresh.clock.onProbation)
    }

    @Test
    fun `a step back of two minutes or less is a correction, as it always was`() {
        fresh.advance(30 * SECOND_MS)
        fresh.setBy(-FOLLOW_AT_ONCE_MS)

        assertEquals(fresh.phoneNowMs, fresh.clock.now())
        assertEquals(emptyList<ClockNews>(), fresh.news)
    }

    @Test
    fun `during probation a phone's clock found far ahead is set aside, as always`() {
        fresh.advance(30 * SECOND_MS)
        fresh.setBy(3 * HOUR_MS)

        assertEquals(fresh.trueNowMs, fresh.clock.now())
        assertFalse(fresh.clock.agreesWithPhone())
        assertEquals(listOf(ClockNews.SetAside(3 * HOUR_MS)), fresh.news)

        // And it is followed only after its ten watched minutes, like any change.
        repeat(19) {
            fresh.advance(30 * SECOND_MS)
            assertEquals(fresh.trueNowMs, fresh.clock.now())
        }
        fresh.advance(30 * SECOND_MS)
        assertEquals(fresh.phoneNowMs, fresh.clock.now())
        assertEquals(ClockNews.Followed(3 * HOUR_MS, HOLD_MS), fresh.news.last())
        // Watched for ten minutes, which is more than probation asks.
        assertFalse(fresh.clock.onProbation)
    }

    @Test
    fun `a proven anchor is never taken back at once`() {
        phone.clock.now()
        phone.setBy(-DAY_MS)

        assertEquals(phone.trueNowMs, phone.clock.now())
        assertFalse(phone.clock.agreesWithPhone())
        assertEquals(listOf(ClockNews.SetAside(-DAY_MS)), phone.news)

        // Nor is one that has only just left probation.
        fresh.advance(PROBATION_MS)
        fresh.clock.now()
        fresh.setBy(-DAY_MS)
        assertEquals(fresh.trueNowMs, fresh.clock.now())
        assertEquals(listOf(ClockNews.SetAside(-DAY_MS)), fresh.news)
    }

    // ---- The mark, from one process to the next ---------------------------------------------------

    @Test
    fun `the mark survives a start of MilO, and probation still counts from the first taking`() {
        fresh.advance(30 * SECOND_MS)
        fresh.clock.now()

        fresh.newProcess()

        assertTrue(fresh.clock.onProbation)
        // Ninety seconds into the new process, two minutes after the anchor was first taken.
        fresh.advance(PROBATION_MS - 30 * SECOND_MS - 1)
        fresh.clock.now()
        assertTrue(fresh.clock.onProbation)
        fresh.advance(1)
        fresh.clock.now()
        assertFalse(fresh.clock.onProbation)

        // "Proven" is handed on as well: the next process does not take a step back at once.
        fresh.newProcess()
        assertFalse(fresh.clock.onProbation)
        fresh.setBy(-3 * HOUR_MS)
        assertEquals(fresh.trueNowMs, fresh.clock.now())
    }

    @Test
    fun `the mark does not survive a restart of the phone`() {
        phone.clock.now()
        assertFalse(phone.clock.onProbation)

        phone.reboot()

        // The anchor of the boot before is of no use, proven or not: a new one, on probation.
        assertTrue(phone.clock.onProbation)
        val taken = phone.stored.last()
        assertEquals(8, taken.bootCount)
        assertEquals(taken.elapsedMs, taken.onProbationSinceMs)
    }

    @Test
    fun `a stored anchor whose probation would have begun after it was taken is not used`() {
        // No clock writes such an anchor: the file is damaged, and nothing in it is believed.
        val impossible =
            ClockAnchor(7, EVENING_MS - DAY_MS, elapsedMs = 60_000L, onProbationSinceMs = 60_001L)
        val clock =
            TrustedClock({ EVENING_MS }, { 90_000L }, bootCount = 7, stored = impossible, save = {})

        assertEquals(EVENING_MS, clock.now())
        assertTrue(clock.onProbation)
    }

    @Test
    fun `a phone that gives no boot number is on probation at every start of MilO`() {
        val unnumbered = JumpingPhone(EVENING_MS, bootCount = null)
        unnumbered.advance(HOUR_MS)
        unnumbered.clock.now()
        assertFalse(unnumbered.clock.onProbation)

        unnumbered.newProcess()

        assertTrue(unnumbered.clock.onProbation)
        assertEquals(emptyList<ClockAnchor>(), unnumbered.stored)
    }

    // ---- The owner's trick around a start with no anchor ------------------------------------------

    @Test
    fun `his trick one minute after a start with no anchor leaves MilO on the true time`() {
        val before = offFor(fresh, MINUTE_MS)
        fresh.setAhead()
        val during = offFor(fresh, 9 * SECOND_MS)
        fresh.setBack()
        val after = offFor(fresh, 3 * MINUTE_MS)

        // One reading a second, before the jump, through it and after it: none is off.
        assertEquals(List(249) { 0L }, before + during + after)
        assertEquals(
            listOf(ClockNews.SetAside(DAY_MS), ClockNews.CameBack(DAY_MS, 9 * SECOND_MS)),
            fresh.news,
        )
        assertFalse(fresh.clock.onProbation)
    }

    @Test
    fun `a start inside the jump is on the true time as soon as the phone comes back`() {
        // The first start after the update that brought the clock, in the seconds the date
        // is ahead. For those seconds MilO goes by the date, as it did before it kept a clock.
        phone.setAhead()
        phone.newProcessWithoutAnchor()
        assertEquals(phone.trueNowMs + DAY_MS, phone.clock.now())
        assertTrue(phone.clock.agreesWithPhone())
        phone.advance(9 * SECOND_MS)
        assertEquals(phone.trueNowMs + DAY_MS, phone.clock.now())

        phone.setBack()

        assertEquals(phone.trueNowMs, phone.clock.now())
        assertTrue(phone.clock.agreesWithPhone())
        assertEquals(listOf(ClockNews.StartedAhead(aheadMs = DAY_MS)), phone.news)
        // From here on it is the phone's clock, and nothing more is told.
        assertEquals(List(600) { 0L }, offFor(phone, 10 * MINUTE_MS))
        assertEquals(1, phone.news.size)
        assertFalse(phone.clock.onProbation)
    }

    @Test
    fun `the first MilO after a restart of the phone, started inside the jump, comes back too`() {
        phone.clock.now()
        phone.advance(HOUR_MS)
        phone.setAhead()
        phone.reboot()
        assertEquals(phone.trueNowMs + DAY_MS, phone.clock.now())

        phone.advance(9 * SECOND_MS)
        phone.setBack()

        assertEquals(phone.trueNowMs, phone.clock.now())
        assertEquals(listOf(ClockNews.StartedAhead(aheadMs = DAY_MS)), phone.news)
    }

    @Test
    fun `a process that took the date and died is put right by the next one's first reading`() {
        phone.setAhead()
        phone.newProcessWithoutAnchor()
        // Android ends that process before the date comes back. Its anchor, a day ahead, is
        // in the file, with the mark.
        assertEquals(phone.trueNowMs + DAY_MS, phone.stored.last().wallMs)
        phone.advance(9 * SECOND_MS)
        phone.setBack()

        // Five hours later, with no reading in between, something starts MilO.
        phone.advance(5 * HOUR_MS)
        phone.newProcess()

        assertEquals(phone.trueNowMs, phone.clock.now())
        assertEquals(listOf(ClockNews.StartedAhead(aheadMs = DAY_MS)), phone.news)
        // And the process after that inherits the right time.
        phone.advance(MINUTE_MS)
        phone.newProcess()
        assertEquals(phone.trueNowMs, phone.clock.now())
        assertEquals(1, phone.news.size)
    }

    @Test
    fun `a second date set on top of the first is told of before MilO takes the phone's time`() {
        phone.setAhead()
        phone.newProcessWithoutAnchor()
        phone.clock.now()
        // Three seconds in, the date is set one more day ahead: set aside, like any change.
        phone.advance(3 * SECOND_MS)
        phone.setBy(2 * DAY_MS)
        assertEquals(phone.trueNowMs + DAY_MS, phone.clock.now())

        phone.advance(6 * SECOND_MS)
        phone.setBack()

        assertEquals(phone.trueNowMs, phone.clock.now())
        assertEquals(
            listOf(
                ClockNews.SetAside(DAY_MS),
                ClockNews.ChangedAgain(DAY_MS, lastedMs = 6 * SECOND_MS),
                ClockNews.StartedAhead(aheadMs = DAY_MS),
            ),
            phone.news,
        )
    }

    // ---- What probation does not cover ------------------------------------------------------------

    @Test
    fun `a date left ahead for two minutes after such a start is believed, as it was`() {
        // The price of the rule: probation is two minutes, and his jumps last 6 to 16 seconds.
        phone.setAhead()
        phone.newProcessWithoutAnchor()
        phone.advance(PROBATION_MS)
        assertEquals(phone.trueNowMs + DAY_MS, phone.clock.now())
        assertFalse(phone.clock.onProbation)

        phone.setBack()

        assertEquals(phone.trueNowMs + DAY_MS, phone.clock.now())
        assertFalse(phone.clock.agreesWithPhone())
    }

    @Test
    fun `an anchor taken inside one jump and next read inside a later one passes for proven`() {
        // The known gap (FINDINGS_LOG 2026-10-09, debt): MilO starts with no anchor inside
        // one jump, nothing of MilO reads the clock until the next jump seven minutes later,
        // and a reading falls into that one. The two readings agree, two minutes apart.
        phone.setAhead()
        phone.newProcessWithoutAnchor()
        phone.advance(9 * SECOND_MS)
        phone.setBack()
        phone.advance(7 * MINUTE_MS)
        phone.setAhead()
        phone.newProcess()

        assertEquals(phone.trueNowMs + DAY_MS, phone.clock.now())
        assertFalse(phone.clock.onProbation)
        phone.advance(9 * SECOND_MS)
        phone.setBack()
        assertEquals(phone.trueNowMs + DAY_MS, phone.clock.now())
    }

    // ---- A proven anchor --------------------------------------------------------------------------

    @Test
    fun `no reading goes backwards for a proven anchor, whatever the phone's clock is set to`() {
        val random = Random(20261009)
        var last = phone.clock.now()

        repeat(4_000) {
            // Mostly the phone is left alone. Now and then its clock is set far ahead or far
            // back, by more than a correction, and comes back within nine minutes.
            if (random.nextInt(20) == 0) {
                val far = random.nextLong(FOLLOW_AT_ONCE_MS + 1, 3 * DAY_MS)
                phone.setBy(if (random.nextBoolean()) far else -far)
                val backAt = phone.elapsedMs + random.nextLong(SECOND_MS, 9 * MINUTE_MS)
                while (phone.elapsedMs < backAt) {
                    val step = random.nextLong(1, 40 * SECOND_MS)
                    phone.advance(minOf(step, backAt - phone.elapsedMs))
                    val inside = phone.clock.now()
                    assertEquals(phone.trueNowMs, inside)
                    assertTrue(inside >= last)
                    last = inside
                }
                phone.setBack()
            }
            phone.advance(random.nextLong(1, HOUR_MS))
            val now = phone.clock.now()
            assertEquals(phone.trueNowMs, now)
            assertTrue(now >= last)
            last = now
        }
        assertTrue(phone.news.none { it is ClockNews.Followed || it is ClockNews.StartedAhead })
    }
}

package com.shawnkowalchuk.milo.platform.clock

import com.shawnkowalchuk.milo.core.clock.JumpingPhone
import com.shawnkowalchuk.milo.core.clock.PROBATION_MS
import com.shawnkowalchuk.milo.data.crash.CrashFileStore
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogEntry
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.platform.trip.FakeEventLogDao
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private const val SECOND_MS = 1_000L
private const val HOUR_MS = 3_600 * SECOND_MS

/** Wednesday 7 October 2026, 18:32:42 in Edmonton. */
private const val EVENING_MS = 1_791_419_562_000L

private const val STARTED_AHEAD_LINE =
    "MilO started while the phone's date was set 24 h 0 min ahead, and took the phone's time " +
        "when it came back."

private val ASKED_FOR_BOTH =
    listOf("reminder, the phone's clock is back", "daily check, the phone's clock is back")

/**
 * The clock watch around a MilO that started with no anchor to go by (ADR-005, decided on
 * 2026-10-09): its anchor is on probation. If it was taken while the phone's date was set
 * ahead, the watch writes the line for that when the date comes back, and has both daily
 * alarms asked for again and both checks look again, as after any change that came back.
 * The clock is the real one ([JumpingPhone]).
 */
// advanceTimeBy() and runCurrent() are how a test lets the watch's coroutines run.
@OptIn(ExperimentalCoroutinesApi::class)
class ClockWatchProbationTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val phone = JumpingPhone(EVENING_MS)
    private val log = FakeEventLogDao()

    /** Every asking for a daily alarm again: which of the two, and what prompted it. */
    private val asked = mutableListOf<String>()

    private fun TestScope.watch(): ClockWatch {
        fun asking(who: String): (String, () -> Unit) -> Unit = { source, done ->
            asked += "$who, $source"
            done()
        }
        return ClockWatch(
            clock = phone.clock,
            askAgain = listOf(asking("reminder"), asking("daily check")),
            eventLog = EventLogRepository(log),
            crashFileStore = CrashFileStore(File(temporaryFolder.root, "crashes")),
            scope = backgroundScope,
        ).also {
            it.start()
            runCurrent()
        }
    }

    /** Time passes, a second at a time, on the phone and for the watch's own waits alike. */
    private fun TestScope.pass(ms: Long) {
        repeat((ms / SECOND_MS).toInt()) {
            phone.advance(SECOND_MS)
            advanceTimeBy(SECOND_MS)
            runCurrent()
        }
    }

    private fun clockLines(): List<EventLogEntry> =
        log.entries.filter { it.category == EventCategory.PROCESS }

    @Test
    fun `a MilO that started inside the jump says so when Android tells it the date is back`() =
        runTest {
            // The first MilO process after a restart of the phone, and Android starts it for
            // the very change of the date.
            phone.setAhead()
            phone.reboot()
            val watch = watch()
            watch.onPhoneClockSet {}
            runCurrent()
            // It goes by the date for now: the phone's clock is all it has. Nothing is written.
            assertEquals(emptyList<EventLogEntry>(), clockLines())

            phone.advance(9 * SECOND_MS)
            phone.setBack()
            watch.onPhoneClockSet {}
            runCurrent()

            val line = clockLines().single()
            assertEquals(STARTED_AHEAD_LINE, line.message)
            assertEquals(phone.trueNowMs, line.atMs)
            // What the start asked of Android, it asked by the date: both are asked again.
            assertEquals(ASKED_FOR_BOTH, asked.takeLast(2))
        }

    @Test
    fun `with no word from Android it sees the date back at its next look`() = runTest {
        phone.setAhead()
        phone.newProcessWithoutAnchor()
        watch()
        phone.advance(9 * SECOND_MS)
        phone.setBack()

        pass(CLOCK_WATCH_EVERY_MS)

        assertEquals(STARTED_AHEAD_LINE, clockLines().single().message)
        assertEquals(ASKED_FOR_BOTH, asked)
    }

    @Test
    fun `the next process writes the line if the one that took the date is gone`() = runTest {
        phone.setAhead()
        phone.newProcessWithoutAnchor()
        phone.advance(9 * SECOND_MS)
        phone.setBack()
        phone.advance(5 * HOUR_MS)

        // The truck's connect starts MilO the next morning. Its clock reads the stored anchor,
        // a day ahead and on probation, and the first reading puts it right.
        phone.newProcess()
        watch()

        assertEquals(STARTED_AHEAD_LINE, clockLines().single().message)
        assertEquals(phone.trueNowMs, clockLines().single().atMs)
        assertEquals(ASKED_FOR_BOTH, asked)
    }

    @Test
    fun `an anchor that was right is proven after two minutes of looks, which then stop`() =
        runTest {
            val fresh = JumpingPhone(EVENING_MS, miloHasRun = false)
            ClockWatch(
                clock = fresh.clock,
                askAgain = emptyList(),
                eventLog = EventLogRepository(log),
                crashFileStore = CrashFileStore(File(temporaryFolder.root, "crashes")),
                scope = backgroundScope,
            ).start()
            runCurrent()
            assertTrue(fresh.clock.onProbation)

            // Nothing else reads the clock: the watch's own looks are what proves the anchor.
            repeat((PROBATION_MS / SECOND_MS).toInt()) {
                fresh.advance(SECOND_MS)
                advanceTimeBy(SECOND_MS)
                runCurrent()
            }
            assertFalse(fresh.clock.onProbation)
            assertEquals(emptyList<EventLogEntry>(), log.entries)

            // It has stopped looking: a jump that nothing reads the clock in goes unseen.
            fresh.setAhead()
            repeat(60) {
                fresh.advance(SECOND_MS)
                advanceTimeBy(SECOND_MS)
                runCurrent()
            }
            fresh.setBack()
            advanceTimeBy(HOUR_MS)
            runCurrent()
            assertEquals(emptyList<EventLogEntry>(), log.entries)
        }
}

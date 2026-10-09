package com.shawnkowalchuk.milo.platform.clock

import com.shawnkowalchuk.milo.core.clock.HOLD_MS
import com.shawnkowalchuk.milo.core.clock.JumpingPhone
import com.shawnkowalchuk.milo.data.crash.CrashFileStore
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogDao
import com.shawnkowalchuk.milo.data.eventlog.EventLogEntry
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.platform.trip.FakeEventLogDao
import java.io.File
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private const val SECOND_MS = 1_000L
private const val HOUR_MS = 3_600 * SECOND_MS

/** Wednesday 7 October 2026, 18:32:42 in Edmonton. */
private const val EVENING_MS = 1_791_419_562_000L

private const val BACK_LINE =
    "The phone's date was set 24 h 0 min ahead. MilO saw it back 9 s later and kept its own time."

/**
 * What MilO does about a change of the phone's clock beside keeping its own time: the one line
 * in the event log, the looking again while it does not believe the phone, and the two daily
 * alarms being asked for again once the clocks agree. The clock is the real one, on a phone
 * whose date a test sets ([JumpingPhone]).
 */
// advanceTimeBy() and runCurrent() are how a test lets the watch's coroutines run.
@OptIn(ExperimentalCoroutinesApi::class)
class ClockWatchTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val phone = JumpingPhone(EVENING_MS)
    private val log = FakeEventLogDao()
    private val crashFiles by lazy { CrashFileStore(File(temporaryFolder.root, "crashes")) }

    /** Every asking for a daily alarm again: which of the two, and what prompted it. */
    private val asked = mutableListOf<String>()

    /** How many broadcasts have been let go of. */
    private var broadcastsDone = 0

    /** False for an asking that never says it has finished. */
    private var askingFinishes = true

    private fun TestScope.watch(eventLog: EventLogDao = log): ClockWatch {
        fun asking(who: String): (String, () -> Unit) -> Unit = { source, done ->
            asked += "$who, $source"
            if (askingFinishes) done()
        }
        return ClockWatch(
            clock = phone.clock,
            askAgain = listOf(asking("reminder"), asking("daily check")),
            eventLog = EventLogRepository(eventLog),
            crashFileStore = crashFiles,
            scope = backgroundScope,
        ).also {
            it.start()
            runCurrent()
        }
    }

    /** Android's broadcast that the phone's clock was set reaches MilO. */
    private fun TestScope.timeSet(watch: ClockWatch) {
        watch.onPhoneClockSet { broadcastsDone++ }
        runCurrent()
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
    fun `the date trick leaves one line and has both alarms asked for once`() = runTest {
        val watch = watch()

        phone.setAhead()
        timeSet(watch)
        // While the date is ahead: nothing written, nothing asked of Android.
        assertEquals(emptyList<EventLogEntry>(), clockLines())
        assertEquals(emptyList<String>(), asked)
        assertEquals(1, broadcastsDone)

        phone.advance(9 * SECOND_MS)
        phone.setBack()
        timeSet(watch)

        val line = clockLines().single()
        assertEquals(BACK_LINE, line.message)
        assertEquals(phone.trueNowMs, line.atMs)
        assertEquals(
            listOf(
                "reminder, the phone's clock is back",
                "daily check, the phone's clock is back",
            ),
            asked,
        )
        assertEquals(2, broadcastsDone)
    }

    @Test
    fun `with no word from Android, MilO sees the clock back at its next look`() = runTest {
        val watch = watch()
        phone.setAhead()
        timeSet(watch)
        phone.advance(9 * SECOND_MS)
        phone.setBack()

        pass(CLOCK_WATCH_EVERY_MS)

        // The line says when MilO saw it, which is all MilO knows: 9 s, and the half minute.
        assertEquals(
            "The phone's date was set 24 h 0 min ahead. MilO saw it back 39 s later and kept " +
                "its own time.",
            clockLines().single().message,
        )
        assertEquals(2, asked.size)
        // And it stops looking: an hour on, nothing more has been written or asked.
        pass(HOUR_MS)
        assertEquals(1, clockLines().size)
        assertEquals(2, asked.size)
    }

    @Test
    fun `a change no broadcast told of is still watched, from the reading that saw it`() = runTest {
        watch()
        phone.setAhead()
        // A GPS fix, a screen, anything that asks the time.
        phone.clock.now()
        runCurrent()
        phone.advance(9 * SECOND_MS)
        phone.setBack()

        pass(CLOCK_WATCH_EVERY_MS)

        assertEquals(1, clockLines().size)
        assertEquals(2, asked.size)
    }

    @Test
    fun `a process that Android starts while the date is ahead watches from its start`() = runTest {
        phone.clock.now()
        phone.advance(HOUR_MS)
        phone.setAhead()
        phone.newProcess()

        watch()
        assertEquals(emptyList<String>(), asked)
        phone.advance(9 * SECOND_MS)
        phone.setBack()
        pass(CLOCK_WATCH_EVERY_MS)

        assertEquals(1, clockLines().size)
        assertEquals(
            listOf(
                "reminder, the phone's clock is back",
                "daily check, the phone's clock is back",
            ),
            asked,
        )
    }

    @Test
    fun `a change that holds is followed after ten minutes, with its line`() = runTest {
        val watch = watch()
        phone.setBy(-3 * HOUR_MS)
        timeSet(watch)
        // A clock that is set back has the alarms asked for at once: the next test.
        assertEquals(2, asked.size)
        asked.clear()

        pass(HOLD_MS - CLOCK_WATCH_EVERY_MS)
        assertEquals(emptyList<EventLogEntry>(), clockLines())
        assertEquals(emptyList<String>(), asked)
        pass(CLOCK_WATCH_EVERY_MS)

        val line = clockLines().single()
        assertEquals(
            "The phone's clock is 3 h 0 min behind where it was and has stayed so for 10 min. " +
                "MilO now follows it.",
            line.message,
        )
        // Dated by the clock MilO now follows.
        assertEquals(phone.phoneNowMs, line.atMs)
        assertEquals(
            listOf(
                "reminder, the phone's clock was changed and is followed",
                "daily check, the phone's clock was changed and is followed",
            ),
            asked,
        )
    }

    @Test
    fun `a clock that is set back has both alarms asked for at once, and one set ahead does not`() =
        runTest {
            val watch = watch()
            // Set ahead, Android delivers both daily alarms, and each asks for its next one
            // by itself. The watch asks for nothing.
            phone.setBy(3 * HOUR_MS)
            timeSet(watch)
            assertEquals(emptyList<String>(), asked)
            phone.setBack()
            timeSet(watch)
            asked.clear()

            // Set back, Android delivers nothing, and both alarms would wait for a time of day
            // on a clock MilO does not believe. The watch has them asked for, once.
            phone.setBy(-3 * HOUR_MS)
            timeSet(watch)

            assertEquals(
                listOf(
                    "reminder, the phone's clock was set back and MilO keeps its own time",
                    "daily check, the phone's clock was set back and MilO keeps its own time",
                ),
                asked,
            )
            assertEquals(3, broadcastsDone)
        }

    @Test
    fun `a correction MilO follows at once has both alarms asked for again`() = runTest {
        val watch = watch()
        phone.setBy(3 * SECOND_MS)

        timeSet(watch)

        assertEquals(emptyList<EventLogEntry>(), clockLines())
        assertEquals(
            listOf("reminder, the phone's clock was set", "daily check, the phone's clock was set"),
            asked,
        )
        assertEquals(1, broadcastsDone)
    }

    @Test
    fun `a log that cannot be written does not leave the alarms unasked for`() = runTest {
        val watch = watch(eventLog = UnwritableLog)
        phone.setAhead()
        timeSet(watch)
        phone.advance(9 * SECOND_MS)
        phone.setBack()

        timeSet(watch)

        assertEquals(2, asked.size)
        assertEquals(2, broadcastsDone)
        val written = crashFiles.read(crashFiles.pendingFiles().single())
        assertTrue(written.summary, written.summary.contains("MilO's clock watch failed while"))
    }

    @Test
    fun `an asking that never finishes does not hold up the next change`() = runTest {
        askingFinishes = false
        val watch = watch()
        phone.setAhead()
        timeSet(watch)
        phone.advance(9 * SECOND_MS)
        phone.setBack()

        timeSet(watch)
        assertEquals(1, broadcastsDone)
        pass(ASKED_AGAIN_WAIT_MS)
        assertEquals(2, broadcastsDone)

        // The next evening's trick is handled like the first.
        phone.setAhead()
        timeSet(watch)
        phone.advance(6 * SECOND_MS)
        phone.setBack()
        timeSet(watch)
        pass(ASKED_AGAIN_WAIT_MS)
        assertEquals(2, clockLines().size)
        assertEquals(4, asked.size)
    }

    @Test
    fun `a phone that gives no boot number is written down at a start, as an error`() = runTest {
        val unnumbered = JumpingPhone(EVENING_MS, bootCount = null)
        ClockWatch(
            clock = unnumbered.clock,
            askAgain = emptyList(),
            eventLog = EventLogRepository(log),
            crashFileStore = crashFiles,
            scope = backgroundScope,
        ).start()
        runCurrent()

        val line = log.entries.single()
        assertEquals(EventCategory.ERROR, line.category)
        assertTrue(line.message, line.message.startsWith("MilO's clock: this phone gives no"))
        // The phone of every other test here has one, and writes nothing of the kind.
        watch()
        assertEquals(1, log.entries.size)
    }

    /** An event log whose table is broken: every write fails. */
    private object UnwritableLog : EventLogDao by FakeEventLogDao() {
        override suspend fun insert(entry: EventLogEntry): Long =
            throw IOException("the event log table is broken")
    }
}

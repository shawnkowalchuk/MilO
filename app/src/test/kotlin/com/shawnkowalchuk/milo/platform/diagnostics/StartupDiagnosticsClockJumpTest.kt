package com.shawnkowalchuk.milo.platform.diagnostics

import com.shawnkowalchuk.milo.core.clock.DAY_MS
import com.shawnkowalchuk.milo.core.clock.JumpingPhone
import com.shawnkowalchuk.milo.data.crash.CrashFileStore
import com.shawnkowalchuk.milo.data.crash.CrashRecord
import com.shawnkowalchuk.milo.data.eventlog.EVENT_LOG_KEEP_DAYS
import com.shawnkowalchuk.milo.data.eventlog.EVENT_LOG_KEEP_NEWEST
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogEntry
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.settings.SettingsStore
import com.shawnkowalchuk.milo.platform.trip.FakeEventLogDao
import com.shawnkowalchuk.milo.platform.trip.FakeSettingsFile
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private const val MINUTE_MS = 60_000L
private const val HOUR_MS = 60 * MINUTE_MS

/** 2026-10-07 18:32:42 in Edmonton: the real moment the date was set ahead. */
private const val EVENING_MS = 1_791_419_562_000L

/**
 * What a process start writes to the event log when the phone's date is, or just was, one day
 * ahead for a few seconds (ADR-006). The start-up diagnostics run on MilO's clock
 * ([JumpingPhone]); Android stamps each record of a process's end with the PHONE's clock at
 * the moment the process died (`ApplicationExitInfo.getTimestamp`).
 *
 * The system's exit records are played by a list that is read as `ProcessExitReader` reads the
 * real ones: the records newer than the stored mark, oldest first.
 *
 * These began as the investigation's proofs (2026-10-07): a death stamped tomorrow hid every
 * death of the next 24 hours, and a process started inside the jump dated its start tomorrow
 * and trimmed a day too far. Each now asserts the right outcome.
 */
class StartupDiagnosticsClockJumpTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val crashFiles by lazy { CrashFileStore(File(temporaryFolder.root, "crashes")) }
    private val log = FakeEventLogDao()
    private val settingsFile = FakeSettingsFile()
    private val exits = mutableListOf<ProcessExit>()
    private val phone = JumpingPhone(EVENING_MS)

    /** A process of MilO dies now, and Android writes its record down with the phone's clock. */
    private fun processDies(pid: Int) {
        exits +=
            ProcessExit(
                atMs = phone.phoneNowMs,
                pid = pid,
                reason = 13,
                description = "SwipeUpClean",
                importance = 400,
                status = 0,
            )
    }

    /** A new process of MilO starts now, with its clock from the stored anchor. */
    private fun startProcess(pid: Int) = runBlocking {
        phone.newProcess()
        StartupDiagnostics(
            crashFileStore = crashFiles,
            processExitsAfter = { afterMs ->
                exits.filter { it.atMs > afterMs }.sortedBy { it.atMs }
            },
            eventLog = EventLogRepository(log),
            settings = SettingsStore(settingsFile),
            clock = { phone.clock.now() },
            processId = pid,
        ).record()
    }

    private fun exitLines() = log.entries.filter { " ended: " in it.message }

    private fun importedUpToMs(): Long =
        runBlocking { SettingsStore(settingsFile).current().lastProcessExitImportedAtMs }

    @Test
    fun `a process that dies while the date is ahead is written once, and hides no later death`() {
        // MilO is idle. The date is set ahead; opening the game has the phone clear MilO away
        // in those seconds, so the system stamps the death with tomorrow's time.
        phone.clock.now()
        phone.setAhead()
        phone.advance(4_000)
        processDies(pid = 100)
        phone.advance(5_000)
        phone.setBack()

        // An hour later, on the real evening, the truck connects and starts MilO.
        phone.advance(HOUR_MS)
        startProcess(pid = 101)
        val first = exitLines().single()
        assertEquals(
            "Process 100 ended: OTHER (SwipeUpClean). It ended while the phone's clock was " +
                "set ahead; this line stands at the time MilO read the record, not at the " +
                "time the process ended",
            first.message,
        )
        // Dated when MilO read it, not tomorrow, and the mark has not moved past the present.
        assertEquals(phone.trueNowMs, first.atMs)
        assertTrue(importedUpToMs() <= phone.trueNowMs)

        // That night and the next morning MilO is cleared away twice more. Before the fix
        // neither death reached the log: both were older than a mark of tomorrow.
        phone.advance(4 * HOUR_MS)
        processDies(pid = 101)
        phone.advance(8 * HOUR_MS)
        startProcess(pid = 102)
        phone.advance(3 * HOUR_MS)
        processDies(pid = 102)
        phone.advance(HOUR_MS)
        startProcess(pid = 103)

        assertEquals(
            listOf("Process 100 ended", "Process 101 ended", "Process 102 ended"),
            exitLines().sortedBy { it.id }.map { it.message.substringBefore(":") },
        )

        // Two days on, the record of tomorrow has long had its time. It is not written again.
        phone.advance(2 * DAY_MS)
        processDies(pid = 103)
        phone.advance(MINUTE_MS)
        startProcess(pid = 104)
        assertEquals(
            listOf(
                "Process 100 ended",
                "Process 101 ended",
                "Process 102 ended",
                "Process 103 ended",
            ),
            exitLines().sortedBy { it.id }.map { it.message.substringBefore(":") },
        )
        assertEquals(exits.last().atMs, importedUpToMs())
    }

    @Test
    fun `a process that starts while the date is ahead dates its start now, and trims no line`() {
        // More than the 1,000 newest lines that are kept whatever their age: 1,000 of this
        // week, and 20 that are 89 and a half days old. They have half a day left.
        repeat(EVENT_LOG_KEEP_NEWEST) { number ->
            log.entries +=
                EventLogEntry(
                    id = 10_000L + number,
                    atMs = EVENING_MS - number * MINUTE_MS,
                    category = EventCategory.TRIP,
                    message = "this week $number",
                )
        }
        val nearlyTooOld = EVENING_MS - EVENT_LOG_KEEP_DAYS * DAY_MS + DAY_MS / 2
        repeat(20) { number ->
            log.entries +=
                EventLogEntry(
                    id = 100L + number,
                    atMs = nearlyTooOld + number,
                    category = EventCategory.TRIP,
                    message = "89 and a half days old $number",
                )
        }

        // The daily alarm, delivered at the jump, starts MilO's process: the phone is ahead.
        phone.clock.now()
        phone.setAhead()
        startProcess(pid = 200)

        // Before the fix: started "tomorrow", and the 20 lines gone half a day early.
        val started = log.entries.single { it.message == "Process 200 started" }
        assertEquals(EVENING_MS, started.atMs)
        assertEquals(20, log.entries.count { it.message.startsWith("89 and a half days old") })
        assertTrue(log.entries.none { it.message.startsWith("Event log trimmed") })
        val ofThisWeek = log.entries.count { it.message.startsWith("this week") }
        assertEquals(EVENT_LOG_KEEP_NEWEST, ofThisWeek)
    }

    @Test
    fun `a crash file dated after now, by a file system on the phone's clock, is dated now`() {
        // A crash file that cannot be read in full is dated by its file's own time, which the
        // file system takes from the phone's clock. Here the file was written a day ahead.
        phone.clock.now()
        crashFiles.write(
            CrashRecord(phone.trueNowMs + DAY_MS, "main", "A crash that happened now", "trace"),
        )
        phone.advance(HOUR_MS)

        startProcess(pid = 300)

        val crash = log.entries.single { it.category == EventCategory.CRASH }
        assertEquals(phone.trueNowMs, crash.atMs)
    }

    @Test
    fun `a mark the build before this one left in the future no longer hides anything`() {
        // What that build did with a death stamped tomorrow: the line under tomorrow's time,
        // and the mark moved to it.
        phone.clock.now()
        val stampedAhead = phone.trueNowMs + DAY_MS + 4_000
        exits += ProcessExit(stampedAhead, pid = 100, 13, "SwipeUpClean", 400, 0)
        runBlocking {
            EventLogRepository(log).add(
                stampedAhead,
                EventCategory.PROCESS,
                exits.single().message(),
                exits.single().detail(),
            )
            SettingsStore(settingsFile).setLastProcessExitImportedAtMs(stampedAhead)
        }

        // Five hours later MilO is cleared away again, and then started by the truck.
        phone.advance(5 * HOUR_MS)
        processDies(pid = 101)
        phone.advance(8 * HOUR_MS)
        startProcess(pid = 102)

        // The death of the night is in the log, and the old line is not written a second time.
        assertEquals(
            listOf(
                "Process 100 ended: OTHER (SwipeUpClean)",
                "Process 101 ended: OTHER (SwipeUpClean)",
            ),
            exitLines().sortedBy { it.id }.map { it.message },
        )
        assertEquals(exits.last().atMs, importedUpToMs())
    }
}

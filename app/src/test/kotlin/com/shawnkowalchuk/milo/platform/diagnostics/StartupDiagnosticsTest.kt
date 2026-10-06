package com.shawnkowalchuk.milo.platform.diagnostics

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.shawnkowalchuk.milo.data.crash.CrashFileStore
import com.shawnkowalchuk.milo.data.crash.CrashRecord
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogDao
import com.shawnkowalchuk.milo.data.eventlog.EventLogEntry
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.settings.SettingsStore
import java.io.File
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private const val NOW_MS = 1_791_028_800_000L
private const val PID = 4321

/**
 * The start-up import, with the system's exit records, the event log table and the settings
 * file each replaced by a stand-in, so that a process dying half-way and storage that cannot be
 * read can be played through on the JVM.
 */
class StartupDiagnosticsTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val crashFiles by lazy { CrashFileStore(File(temporaryFolder.root, "crashes")) }
    private val log = FakeEventLogDao()
    private val settingsFile = FakeSettingsFile()

    /** A clock that moves on a millisecond each time it is read, as a real one would. */
    private var now = NOW_MS

    /** Five exits, a minute apart, as the system would hand them over. */
    private val exits = List(5) { exit(atMs = NOW_MS - (5 - it) * 60_000L) }

    private fun exit(atMs: Long) = ProcessExit(
        atMs = atMs,
        pid = 100,
        reason = 13,
        description = "SwipeUpClean",
        importance = 400,
        status = 0,
    )

    /** One process start. The settings and the log carry over from one start to the next. */
    private fun startProcess(settings: DataStore<Preferences> = settingsFile) = runBlocking {
        StartupDiagnostics(
            crashFileStore = crashFiles,
            processExitsAfter = { afterMs -> exits.filter { it.atMs > afterMs } },
            eventLog = EventLogRepository(log),
            settings = SettingsStore(settings),
            clock = { now++ },
            processId = PID,
        ).record()
    }

    private fun exitLines() = log.entries.filter { it.message.contains("ended") }

    @Test
    fun `a start imports the waiting crash, every exit record and notes the new process`() {
        crashFiles.write(CrashRecord.from(NOW_MS - 1_000, "main", IllegalStateException("boom")))

        startProcess()

        assertEquals(
            listOf(EventCategory.CRASH) + List(6) { EventCategory.PROCESS },
            log.entries.map { it.category },
        )
        assertEquals(NOW_MS - 1_000, log.entries.first().atMs)
        assertEquals("Process 100 ended: OTHER (SwipeUpClean)", log.entries[1].message)
        assertEquals("Process $PID started", log.entries.last().message)
        assertEquals(emptyList<File>(), crashFiles.pendingFiles())
    }

    @Test
    fun `a second start imports nothing twice`() {
        startProcess()
        startProcess()

        assertEquals(exits.map { it.atMs }, exitLines().map { it.atMs })
    }

    @Test
    fun `a process that dies in mid-import does not import the finished records again`() {
        // The third write to the log fails, standing in for the process being killed there.
        log.failOnInsert = 3
        startProcess()
        log.failOnInsert = null

        // Two records went in, and the settings already say so.
        assertEquals(exits[1].atMs, importedUpToMs())

        startProcess()

        // Each of the five exactly once. Marked only after the whole batch, the first two
        // would be in the log twice, and again at every start that died early.
        assertEquals(exits.map { it.atMs }, exitLines().map { it.atMs })
    }

    @Test
    fun `an unreadable settings file does not stop the app, or the other steps`() {
        crashFiles.write(CrashRecord.from(NOW_MS - 1_000, "main", IllegalStateException("boom")))
        val unreadable = FakeSettingsFile(failure = IOException("settings file is corrupt"))

        // Must not throw: an exception here would end the process at every single start.
        startProcess(settings = unreadable)

        assertEquals(
            listOf(EventCategory.CRASH, EventCategory.ERROR, EventCategory.PROCESS),
            log.entries.map { it.category },
        )
        val error = log.entries[1]
        assertEquals(
            "Start-up diagnostics failed while importing process exit records",
            error.message,
        )
        assertTrue(error.detail.orEmpty().contains("settings file is corrupt"))
    }

    @Test
    fun `when the event log cannot be written the failure goes to a crash file instead`() {
        log.failOnInsert = 1
        log.failForGood = true

        startProcess()

        // One file, not one per step: without the log no step can do its work.
        val written = crashFiles.read(crashFiles.pendingFiles().single())
        assertTrue(written.summary.contains("Start-up diagnostics failed while"))
        assertTrue(written.stackTrace.contains("the event log table is broken"))

        // Once the log works again, the next start brings that record in.
        log.failOnInsert = null
        startProcess()
        assertEquals(EventCategory.CRASH, log.entries.first().category)
        assertEquals(emptyList<File>(), crashFiles.pendingFiles())
    }

    /** The stored import mark, read the way the next start will read it. */
    private fun importedUpToMs(): Long =
        runBlocking { SettingsStore(settingsFile).current().lastProcessExitImportedAtMs }

    /** Stands in for the event log table. It can be told to fail, like a full or broken disk. */
    private class FakeEventLogDao : EventLogDao {
        val entries = mutableListOf<EventLogEntry>()

        /** The number of the write that fails, counted from 1, or null for none. */
        var failOnInsert: Int? = null

        /** Whether every write after that one fails as well. */
        var failForGood = false

        private var inserts = 0

        override suspend fun insert(entry: EventLogEntry): Long {
            inserts++
            val failAt = failOnInsert
            if (failAt != null && (inserts == failAt || (failForGood && inserts > failAt))) {
                throw IOException("the event log table is broken")
            }
            entries += entry
            return entries.size.toLong()
        }

        override fun observeNewest(limit: Int): Flow<List<EventLogEntry>> =
            flowOf(entries.sortedByDescending { it.atMs }.take(limit))
    }

    /** Stands in for the settings file: held in memory, or failing every read with [failure]. */
    private class FakeSettingsFile(private val failure: IOException? = null) :
        DataStore<Preferences> {
        private val stored = MutableStateFlow(emptyPreferences())

        override val data: Flow<Preferences> =
            if (failure == null) stored else flow { throw failure }

        override suspend fun updateData(
            transform: suspend (t: Preferences) -> Preferences,
        ): Preferences {
            if (failure != null) throw failure
            return transform(stored.value).also { stored.value = it }
        }
    }
}

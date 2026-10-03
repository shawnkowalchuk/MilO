package com.shawnkowalchuk.milo.platform.diagnostics

import android.os.Process
import com.shawnkowalchuk.milo.data.crash.CrashFileStore
import com.shawnkowalchuk.milo.data.crash.CrashRecord
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.settings.SettingsStore
import kotlin.coroutines.cancellation.CancellationException

/**
 * Runs once at every process start and writes to the event log what happened since the last one:
 * the crashes left behind as files, the reasons the system recorded for earlier processes ending,
 * and the fact that a new process has started.
 *
 * Each record is written to the log first and only then marked as done (its file deleted, the
 * timestamp moved on), one record at a time. If the process dies in between, that one record is
 * imported again at the next start. A duplicate line is harmless; a lost one could be the line
 * that explains a missed trip.
 *
 * Gathering evidence must never be what stops the app. Each of the three steps runs on its own:
 * a step that fails is written to the event log and the next one still runs (see [attempt]).
 *
 * @param processExitsAfter the system's exit records newer than a wall-clock time, oldest first.
 * In the app this is `ProcessExitReader.exitsAfter`; a parameter so the import is tested on the
 * JVM.
 * @param processId this process's id, for the "started" line.
 * @param clock wall-clock milliseconds.
 */
class StartupDiagnostics(
    private val crashFileStore: CrashFileStore,
    private val processExitsAfter: (afterMs: Long) -> List<ProcessExit>,
    private val eventLog: EventLogRepository,
    private val settings: SettingsStore,
    private val clock: () -> Long,
    private val processId: Int = Process.myPid(),
) {
    suspend fun record() {
        if (!attempt("importing crash files") { importCrashes() }) return
        if (!attempt("importing process exit records") { importProcessExits() }) return
        attempt("logging the process start") {
            eventLog.add(clock(), EventCategory.PROCESS, "Process $processId started")
        }
    }

    private suspend fun importCrashes() {
        for (file in crashFileStore.pendingFiles()) {
            val crash = crashFileStore.read(file)
            eventLog.add(
                atMs = crash.atMs,
                category = EventCategory.CRASH,
                message = crash.summary,
                detail = "Thread: ${crash.threadName}\n${crash.stackTrace}",
            )
            crashFileStore.delete(file)
        }
    }

    private suspend fun importProcessExits() {
        val exits = processExitsAfter(settings.current().lastProcessExitImportedAtMs)
        for (exit in exits) {
            eventLog.add(exit.atMs, EventCategory.PROCESS, exit.message(), exit.detail())
            // Moved on after every record, not once after the batch: a process that keeps dying
            // early would otherwise import the whole batch again at each start, and the system
            // holds up to 16 records.
            settings.setLastProcessExitImportedAtMs(exit.atMs)
        }
    }

    /**
     * Runs one step and keeps its failure from reaching the other steps, or the process. Without
     * this, an unreadable settings file would crash every start from here, and the app could
     * never be opened again without clearing its data, trips included.
     *
     * @return false only when the event log itself cannot be written. Every step needs the log,
     * so the caller stops there.
     */
    private suspend fun attempt(step: String, block: suspend () -> Unit): Boolean = try {
        block()
        true
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        // Every kind of failure, on purpose: whatever went wrong, the app must still start.
        report(step, failure)
    }

    /**
     * Writes a failed step to the event log. If the log is what failed, the failure goes to a
     * crash file, which reaches the log at a later start. If that cannot be written either, its
     * exception is let through: there is nowhere left to record the failure, and it must not
     * vanish.
     *
     * @return whether the event log took the entry.
     */
    private suspend fun report(step: String, failure: Exception): Boolean {
        val message = "Start-up diagnostics failed while $step"
        val atMs = clock()
        val threadName = Thread.currentThread().name
        return try {
            val trace = CrashRecord.from(atMs, threadName, failure).stackTrace
            eventLog.add(atMs, EventCategory.ERROR, message, trace)
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (logFailure: Exception) {
            val unlogged = IllegalStateException(message, failure)
            unlogged.addSuppressed(logFailure)
            crashFileStore.write(CrashRecord.from(atMs, threadName, unlogged))
            false
        }
    }
}

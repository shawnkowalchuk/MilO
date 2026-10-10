package com.shawnkowalchuk.milo.platform.diagnostics

import android.os.Process
import com.shawnkowalchuk.milo.data.crash.CrashFileStore
import com.shawnkowalchuk.milo.data.crash.CrashRecord
import com.shawnkowalchuk.milo.data.eventlog.EVENT_LOG_KEEP_DAYS
import com.shawnkowalchuk.milo.data.eventlog.EVENT_LOG_KEEP_NEWEST
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.settings.SettingsStore
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.first

/**
 * How many of the newest process lines are looked through for a record that is already in the
 * log. Far more than the system keeps records for (16), so a record is found again for as long
 * as it can come back.
 */
private const val KNOWN_LINES_LOOKED_AT = 500

/**
 * Runs once at every process start and writes to the event log what happened since the last one:
 * the crashes left behind as files, the reasons the system recorded for earlier processes ending,
 * and the fact that a new process has started. Then it trims the log: lines that are too old to
 * keep are removed, so that the log does not grow without end beside the trips.
 *
 * Each record is written to the log first and only then marked as done (its file deleted, the
 * timestamp moved on), one record at a time. If the process dies in between, that one record is
 * imported again at the next start. A duplicate line is harmless; a lost one could be the line
 * that explains a missed trip.
 *
 * **Android dates a process's end with the phone's clock, and MilO has a clock of its own**
 * (ADR-005). A process that died while the phone's date was set a day ahead is recorded as
 * having died tomorrow. Such a record is written at the time MilO reads it, with a note that
 * says so, and the "imported up to" mark is never moved past the present: moved to tomorrow, it
 * would hide every death of the next 24 hours. Since the mark then cannot say that this record
 * is done, a record is looked for in the log before it is written.
 *
 * Gathering evidence must never be what stops the app. Each of the four steps runs on its own:
 * a step that fails is written to the event log and the next one still runs (see [attempt]).
 *
 * @param processExitsAfter the system's exit records newer than a wall-clock time, oldest first.
 * In the app this is `ProcessExitReader.exitsAfter`; a parameter so the import is tested on the
 * JVM.
 * @param processId this process's id, for the "started" line.
 * @param clock the time of day in milliseconds: MilO's clock (`AppContainer.clock`).
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
        val logged =
            attempt("logging the process start") {
                eventLog.add(clock(), EventCategory.PROCESS, "Process $processId started")
            }
        // Last, so that everything this start has to say is written before anything is removed.
        if (logged) attempt("trimming the event log") { trim() }
    }

    /**
     * Removes the lines that are too old to keep (`EventLogRepository.trim`), and says so in the
     * log when it removed any. On most starts there is nothing to remove, and nothing is written.
     */
    private suspend fun trim() {
        val nowMs = clock()
        val removed = eventLog.trim(nowMs)
        if (removed == 0) return
        eventLog.add(
            nowMs,
            EventCategory.PROCESS,
            "Event log trimmed: $removed lines older than $EVENT_LOG_KEEP_DAYS days removed. " +
                "The newest $EVENT_LOG_KEEP_NEWEST lines are kept whatever their age",
        )
    }

    private suspend fun importCrashes() {
        for (file in crashFileStore.pendingFiles()) {
            val crash = crashFileStore.read(file)
            eventLog.add(
                // Never after now: a file that cannot be read in full is dated by the file
                // system, which goes by the phone's clock.
                atMs = minOf(crash.atMs, clock()),
                category = EventCategory.CRASH,
                message = crash.summary,
                detail = "Thread: ${crash.threadName}\n${crash.stackTrace}",
            )
            crashFileStore.delete(file)
        }
    }

    private suspend fun importProcessExits() {
        val nowMs = clock()
        val importedUpToMs = settings.current().lastProcessExitImportedAtMs
        // A mark that lies after now was stored by a build that went by the phone's clock,
        // from a record dated ahead. What it has hidden since is read again, from the start.
        val exits = processExitsAfter(if (importedUpToMs > nowMs) 0L else importedUpToMs)
        if (exits.isEmpty()) return
        val known =
            eventLog.observeNewest(KNOWN_LINES_LOOKED_AT, listOf(EventCategory.PROCESS)).first()
        for (exit in exits) {
            val datedAhead = exit.atMs > nowMs
            // The same record, as it is written when it is dated ahead, or under the time
            // Android gave it: that is how it is written otherwise, and how a build that
            // went by the phone's clock wrote every record.
            val aheadMessage = exit.messageDatedAhead()
            val aheadDetail = exit.detailDatedAhead()
            val written =
                known.any {
                    (it.message == aheadMessage && it.detail == aheadDetail) ||
                        (it.atMs == exit.atMs && it.message == exit.message())
                }
            if (!written && datedAhead) {
                eventLog.add(nowMs, EventCategory.PROCESS, aheadMessage, aheadDetail)
            } else if (!written) {
                eventLog.add(exit.atMs, EventCategory.PROCESS, exit.message(), exit.detail())
            }
            // Moved on after every record, not once after the batch: a process that keeps dying
            // early would otherwise import the whole batch again at each start, and the system
            // holds up to 16 records. Never past the present: see the class comment.
            if (!datedAhead) settings.setLastProcessExitImportedAtMs(exit.atMs)
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

package com.shawnkowalchuk.milo.platform.transfer

import com.shawnkowalchuk.milo.data.crash.CrashFileStore
import com.shawnkowalchuk.milo.data.crash.CrashRecord
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import kotlin.coroutines.cancellation.CancellationException

/**
 * Writes down a failure of an export, an import or the reading of the backup's notes, and
 * keeps it from going any further.
 *
 * All of that work runs in the application scope, in the process the trip service runs in. An
 * exception that got out there would end the process, and a recording with it. So every
 * failure is caught where it happens, said on the screen by whoever caught it, and written
 * here, as in the driving alert and the monthly reminder.
 *
 * @param clock wall-clock milliseconds.
 */
internal class TransferFailures(
    private val eventLog: EventLogRepository,
    private val crashFileStore: CrashFileStore,
    private val clock: () -> Long,
) {
    /**
     * Writes one `ERROR` line with the stack trace. If the log is what failed, the failure
     * goes to a crash file, which reaches the log at a later start.
     *
     * @param doing what was being done, in words that finish "… failed while".
     */
    suspend fun report(doing: String, failure: Exception) {
        val message = "MilO's backup, export or import failed while $doing"
        val atMs = clock()
        try {
            eventLog.add(atMs, EventCategory.ERROR, message, failure.stackTraceToString())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (logFailure: Exception) {
            val unlogged = IllegalStateException(message, failure)
            unlogged.addSuppressed(logFailure)
            crashFileStore.write(CrashRecord.from(atMs, Thread.currentThread().name, unlogged))
        }
    }

    /**
     * Runs a step whose failure takes nothing back of what was done before it. The failure is
     * written down and not passed on; being cancelled is not a failure and is passed on.
     */
    suspend fun contained(doing: String, step: suspend () -> Unit) {
        try {
            step()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            // Every kind of failure, on purpose: see the class comment.
            report(doing, failure)
        }
    }
}

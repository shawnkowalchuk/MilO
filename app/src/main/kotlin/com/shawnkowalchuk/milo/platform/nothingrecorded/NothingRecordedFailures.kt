package com.shawnkowalchuk.milo.platform.nothingrecorded

import com.shawnkowalchuk.milo.data.crash.CrashFileStore
import com.shawnkowalchuk.milo.data.crash.CrashRecord
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import kotlin.coroutines.cancellation.CancellationException

/**
 * Keeps a failure of the "nothing recorded" check away from everything else.
 *
 * The check's work runs in the application scope, in the process the trip service runs in. An
 * exception that got out there (a database that cannot be read, an alarm Android refuses) would
 * end the process, and a recording with it. A notification is never worth a recording, so every
 * failure is caught, as in the monthly reminder and the driving alert
 * (`platform/driving/DrivingFailures.kt`).
 *
 * @param clock wall-clock milliseconds.
 */
internal class NothingRecordedFailures(
    private val eventLog: EventLogRepository,
    private val crashFileStore: CrashFileStore,
    private val clock: () -> Long,
) {
    /**
     * Runs [work]. A failure is written to the event log as one `ERROR` line with its stack
     * trace, and is not passed on. Being cancelled is not a failure and is passed on.
     *
     * @param doing what the work is, in words that finish "The nothing-recorded check failed
     * while".
     */
    suspend fun keptApart(doing: String, work: suspend () -> Unit) {
        try {
            work()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            // Every kind of failure, on purpose: see the class comment.
            report(doing, failure)
        }
    }

    /**
     * If the log is what failed, the failure goes to a crash file, which reaches the log at a
     * later start (the same route as the driving alert's).
     */
    private suspend fun report(doing: String, failure: Exception) {
        val message = "The nothing-recorded check failed while $doing"
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
}

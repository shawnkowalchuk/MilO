package com.shawnkowalchuk.milo.platform.driving

import com.shawnkowalchuk.milo.data.crash.CrashFileStore
import com.shawnkowalchuk.milo.data.crash.CrashRecord
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import kotlin.coroutines.cancellation.CancellationException

/**
 * Keeps a failure of the driving alert away from everything else.
 *
 * The alert's work runs in the application scope, in the process the trip service runs in, and
 * a report of driving arrives about a minute into every drive, while the trip is being
 * recorded. An exception that got out there (a database that cannot be read or written) would
 * end the process and the recording with it. The safety net is never worth a recording, so
 * every failure is caught, as in the address lookup (`TripAddresses.runPass`) and the catch-up
 * of Business and Personal.
 *
 * @param clock wall-clock milliseconds.
 */
internal class DrivingFailures(
    private val eventLog: EventLogRepository,
    private val crashFileStore: CrashFileStore,
    private val clock: () -> Long,
) {
    /**
     * Runs [work]. A failure is written to the event log as one `ERROR` line with its stack
     * trace, and is not passed on. Being cancelled is not a failure and is passed on.
     *
     * @param doing what the work is, in words that finish "The driving alert failed while".
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
     * later start (the same route as the address lookup's).
     */
    private suspend fun report(doing: String, failure: Exception) {
        val message = "The driving alert failed while $doing"
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

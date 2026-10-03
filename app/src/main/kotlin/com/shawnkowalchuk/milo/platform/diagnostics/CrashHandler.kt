package com.shawnkowalchuk.milo.platform.diagnostics

import com.shawnkowalchuk.milo.data.crash.CrashFileStore
import com.shawnkowalchuk.milo.data.crash.CrashRecord

/**
 * Catches every uncaught exception, writes it to a crash file, and then hands it on to the
 * handler that was there before (Android's own, which ends the process).
 *
 * MilO has no crash-reporting service. This file, copied into the event log at the next start by
 * [StartupDiagnostics], is the only record that a crash happened and why.
 *
 * @param previous the handler to pass the exception on to. Handing it on is not optional:
 * without it the process would be left half alive after a crash.
 * @param clock wall-clock milliseconds, passed in so a test can fix the time.
 */
class CrashHandler(
    private val store: CrashFileStore,
    private val previous: Thread.UncaughtExceptionHandler?,
    private val clock: () -> Long,
) : Thread.UncaughtExceptionHandler {
    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            store.write(CrashRecord.from(clock(), thread.name, throwable))
        } catch (
            // Throwable, not Exception: the crash may be an OutOfMemoryError, and writing the
            // file can then fail the same way. Whatever goes wrong here must not replace the
            // crash that is being reported.
            writeFailure: Throwable,
        ) {
            // There is nowhere left to report this to, so it travels with the original crash:
            // the system prints suppressed exceptions along with the stack trace.
            throwable.addSuppressed(writeFailure)
        } finally {
            previous?.uncaughtException(thread, throwable)
        }
    }

    companion object {
        /** Puts a [CrashHandler] in front of whatever handler is installed now. */
        fun install(store: CrashFileStore, clock: () -> Long = System::currentTimeMillis) {
            val previous = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler(CrashHandler(store, previous, clock))
        }
    }
}

package com.shawnkowalchuk.milo.platform.diagnostics

import com.shawnkowalchuk.milo.data.crash.CrashFileStore
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private const val CRASH_TIME_MS = 1_791_028_800_000L

class CrashHandlerTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    /** Stands in for Android's own handler, the one that ends the process. */
    private class RecordingHandler : Thread.UncaughtExceptionHandler {
        val received = mutableListOf<Throwable>()

        override fun uncaughtException(thread: Thread, throwable: Throwable) {
            received += throwable
        }
    }

    private val previous = RecordingHandler()

    @Test
    fun `a crash is written to a file and then handed on`() {
        val store = CrashFileStore(File(temporaryFolder.root, "crashes"))
        val handler = CrashHandler(store, previous) { CRASH_TIME_MS }
        val crash = IllegalStateException("trip row missing")

        handler.uncaughtException(Thread.currentThread(), crash)

        val written = store.read(store.pendingFiles().single())
        assertEquals(CRASH_TIME_MS, written.atMs)
        assertEquals(Thread.currentThread().name, written.threadName)
        assertEquals("java.lang.IllegalStateException: trip row missing", written.summary)
        // Handed on exactly once, and it is the very same exception.
        assertEquals(1, previous.received.size)
        assertSame(crash, previous.received.single())
    }

    @Test
    fun `the crash is still handed on when the file cannot be written`() {
        // A plain file sits where the crash folder should be, so every write fails.
        val blocked = temporaryFolder.newFile("blocked")
        val handler = CrashHandler(CrashFileStore(File(blocked, "crashes")), previous) { 0L }
        val crash = IllegalStateException("trip row missing")

        handler.uncaughtException(Thread.currentThread(), crash)

        assertSame(crash, previous.received.single())
        // The write failure is not lost: it rides along on the crash it failed to record.
        assertTrue(crash.suppressed.single().message.orEmpty().contains("crash folder"))
    }

    @Test
    fun `a missing previous handler is tolerated`() {
        val store = CrashFileStore(File(temporaryFolder.root, "crashes"))
        val handler = CrashHandler(store, previous = null) { CRASH_TIME_MS }

        handler.uncaughtException(Thread.currentThread(), IllegalStateException("boom"))

        assertEquals(1, store.pendingFiles().size)
    }
}

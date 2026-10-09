package com.shawnkowalchuk.milo.platform.clock

import com.shawnkowalchuk.milo.core.clock.ClockAnchor
import com.shawnkowalchuk.milo.data.clock.ClockAnchorStore
import com.shawnkowalchuk.milo.data.crash.CrashFileStore
import java.io.File
import java.util.concurrent.Executor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** How the clock's anchor reaches its file, and what becomes of one that cannot be written. */
class AnchorStoringTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val anchor =
        ClockAnchor(212, wallMs = 1_791_419_562_000L, elapsedMs = 9_000L, onProbationSinceMs = null)
    private val crashFiles by lazy { CrashFileStore(File(temporaryFolder.root, "crashes")) }

    /** Runs each write at once, in place of the thread the app gives it. */
    private fun storing(folder: File) =
        AnchorStoring(ClockAnchorStore(folder), crashFiles, writer = Executor { it.run() })

    @Test
    fun `an anchor is written to the file`() {
        val folder = File(temporaryFolder.root, "clock")

        storing(folder).store(anchor)

        assertEquals(anchor, ClockAnchorStore(folder).read())
        assertEquals(emptyList<File>(), crashFiles.pendingFiles())
    }

    @Test
    fun `an anchor that cannot be written leaves one crash file and throws nothing`() {
        val blocked = File(temporaryFolder.root, "clock")
        blocked.writeText("a file, not a folder")
        val storing = storing(blocked)

        // Must not throw: the trip service runs in this process.
        storing.store(anchor)
        storing.store(anchor.copy(wallMs = anchor.wallMs + 300_000))
        storing.store(anchor.copy(wallMs = anchor.wallMs + 600_000))

        val written = crashFiles.read(crashFiles.pendingFiles().single())
        assertEquals(anchor.wallMs, written.atMs)
        // Dated by the anchor itself: MilO's time at the moment the anchor was made.
        assertTrue(written.summary, "MilO's clock could not store its anchor" in written.summary)
        assertTrue(written.stackTrace, written.stackTrace.contains("Cannot create the folder"))
    }
}

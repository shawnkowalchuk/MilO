package com.shawnkowalchuk.milo.data.crash

import java.io.File
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CrashFileStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    // A folder that does not exist yet, as on a fresh install.
    private val crashFolder by lazy { File(temporaryFolder.root, "crashes") }
    private val store by lazy { CrashFileStore(crashFolder) }

    private fun crashAt(atMs: Long) =
        CrashRecord.from(atMs, "main", IllegalStateException("trip row missing"))

    @Test
    fun `a crash record names the exception, its message and its cause`() {
        val cause = IOException("disk full")

        val record = CrashRecord.from(1_000, "main", IllegalStateException("cannot save", cause))

        assertEquals("java.lang.IllegalStateException: cannot save", record.summary)
        assertTrue(record.stackTrace.contains("Caused by: java.io.IOException: disk full"))
        assertTrue(record.stackTrace.contains("CrashFileStoreTest"))
    }

    @Test
    fun `the summary is one line however long the message is`() {
        val message = "first line\nsecond line " + "x".repeat(1_000)

        val record = CrashRecord.from(1_000, "main", IllegalStateException(message))

        assertEquals("java.lang.IllegalStateException: first line", record.summary)
    }

    @Test
    fun `an exception with no message still gives a summary`() {
        val record = CrashRecord.from(1_000, "main", NullPointerException())

        assertEquals("java.lang.NullPointerException: ", record.summary)
    }

    @Test
    fun `a very long stack trace is cut`() {
        val record = CrashRecord.from(1_000, "main", deepFailure(depth = 2_000))

        assertEquals(16_000, record.stackTrace.length)
    }

    @Test
    fun `a written crash is read back exactly`() {
        val written = crashAt(1_791_028_800_000)

        store.write(written)
        val read = store.read(store.pendingFiles().single())

        assertEquals(written, read)
    }

    @Test
    fun `the crash folder is created when the first crash is written`() {
        assertTrue(!crashFolder.exists())

        store.write(crashAt(1_000))

        assertTrue(crashFolder.isDirectory)
    }

    @Test
    fun `nothing is pending before the first crash`() {
        assertEquals(emptyList<File>(), store.pendingFiles())
    }

    @Test
    fun `several crashes wait side by side, oldest first`() {
        store.write(crashAt(3_000))
        store.write(crashAt(1_000))
        store.write(crashAt(2_000))

        val times = store.pendingFiles().map { store.read(it).atMs }

        assertEquals(listOf(1_000L, 2_000L, 3_000L), times)
    }

    @Test
    fun `a deleted crash is no longer pending`() {
        store.write(crashAt(1_000))

        store.delete(store.pendingFiles().single())

        assertEquals(emptyList<File>(), store.pendingFiles())
    }

    @Test
    fun `an app that crashes at every start keeps only the first twenty crashes`() {
        for (crash in 1..30) store.write(crashAt(crash * 1_000L))

        val times = store.pendingFiles().map { store.read(it).atMs }

        assertEquals(20, times.size)
        assertEquals(1_000L, times.min())
        assertEquals(20_000L, times.max())
    }

    @Test
    fun `a crash file that was cut short is still read, as far as it goes`() {
        crashFolder.mkdirs()
        val cutShort = File(crashFolder, "crash-5000.txt").apply { writeText("5000\nmai") }

        val record = store.read(cutShort)

        assertEquals("A crash record that could not be read in full", record.summary)
        assertEquals("5000\nmai", record.stackTrace)
    }

    @Test
    fun `a file that is not a crash file at all is still turned into a record`() {
        crashFolder.mkdirs()
        val garbage = File(crashFolder, "crash-garbage.txt").apply { writeText("not a crash") }

        val record = store.read(garbage)

        assertEquals("not a crash", record.stackTrace)
        assertEquals(garbage.lastModified(), record.atMs)
    }

    @Test
    fun `a thread name with a line break cannot break the file layout`() {
        val written = CrashRecord.from(1_000, "worker\n2", IllegalStateException("boom"))

        store.write(written)
        val read = store.read(store.pendingFiles().single())

        assertEquals("worker 2", read.threadName)
        assertEquals("java.lang.IllegalStateException: boom", read.summary)
    }

    @Test
    fun `writing fails loudly when the folder cannot be created`() {
        // A plain file sits where the folder should be.
        val blocked = temporaryFolder.newFile("blocked")

        assertThrows(IOException::class.java) {
            CrashFileStore(File(blocked, "crashes")).write(crashAt(1_000))
        }
    }

    /** An exception thrown from [depth] nested calls, for a stack trace of that many lines. */
    private fun deepFailure(depth: Int): Throwable = try {
        recurse(depth)
        error("unreachable")
    } catch (failure: IllegalArgumentException) {
        failure
    }

    private fun recurse(depth: Int): Int {
        require(depth > 0) { "bottom" }
        return recurse(depth - 1) + 1
    }
}

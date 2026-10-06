package com.shawnkowalchuk.milo.data.report

import java.io.File
import java.io.IOException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The report files, against a real folder. The store needs nothing from Android once it is
 * given a folder, so this is a plain JVM test.
 */
class ReportFileStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val folder: File get() = File(temporaryFolder.root, "reports")
    private val store: ReportFileStore get() = ReportFileStore(folder)

    private fun names(): List<String> = folder.list().orEmpty().sorted()

    @Test
    fun `a file is written under its name, and the folder is made for it`() {
        assertFalse(folder.exists())

        val file = store.write("Mileage-2026-10.pdf") { it.write("the report".toByteArray()) }

        assertEquals(File(folder, "Mileage-2026-10.pdf"), file)
        assertEquals("the report", file.readText())
        // Nothing else is left: the name it was written under is gone.
        assertEquals(listOf("Mileage-2026-10.pdf"), names())
    }

    @Test
    fun `a report made again takes the place of the one before`() {
        store.write("Mileage-2026-10.pdf") { it.write("first".toByteArray()) }

        val file = store.write("Mileage-2026-10.pdf") { it.write("second".toByteArray()) }

        assertEquals("second", file.readText())
        assertEquals(listOf("Mileage-2026-10.pdf"), names())
    }

    @Test
    fun `a write that fails leaves nothing behind, and the file from before as it was`() {
        store.write("Mileage-2026-10.pdf") { it.write("the good one".toByteArray()) }

        val failure =
            assertThrows(IOException::class.java) {
                store.write("Mileage-2026-10.pdf") {
                    it.write("half a rep".toByteArray())
                    throw IOException("disk full")
                }
            }

        assertEquals("disk full", failure.message)
        assertEquals("the good one", File(folder, "Mileage-2026-10.pdf").readText())
        assertEquals(listOf("Mileage-2026-10.pdf"), names())
    }

    @Test
    fun `a first write that fails leaves no file of that name at all`() {
        assertThrows(IOException::class.java) {
            store.write("Mileage-2026-10.pdf") { throw IOException("disk full") }
        }

        assertEquals(emptyList<String>(), names())
    }

    @Test
    fun `the bytes are stored as they are written`() {
        val bytes = ByteArray(70_000) { (it % 251).toByte() }

        val file = store.write("Mileage-2026-10.pdf") { it.write(bytes) }

        assertArrayEquals(bytes, file.readBytes())
    }

    @Test
    fun `a name that would leave the folder is refused`() {
        for (name in listOf("", " ", "../settings", "a/b.pdf", "/etc/passwd", "..")) {
            assertThrows(name, IllegalArgumentException::class.java) {
                store.write(name) { it.write(1) }
            }
        }
        assertFalse(File(temporaryFolder.root, "settings").exists())
    }

    @Test
    fun `old files are removed, and the ones from this week are kept`() {
        val old = store.write("Mileage-2026-08.pdf") { it.write(1) }
        val recent = store.write("Mileage-2026-09.pdf") { it.write(1) }
        val leftover = File(folder, "Mileage-2026-07.pdf.part").apply { writeText("half") }
        val now = 1_791_000_000_000L
        assertTrue(old.setLastModified(now - KEEP_REPORT_FILES_MS - 1_000))
        assertTrue(leftover.setLastModified(now - KEEP_REPORT_FILES_MS - 1_000))
        assertTrue(recent.setLastModified(now - KEEP_REPORT_FILES_MS + 60_000))

        val removed = store.removeOld(beforeMs = now - KEEP_REPORT_FILES_MS)

        assertEquals(2, removed)
        assertEquals(listOf("Mileage-2026-09.pdf"), names())
    }

    @Test
    fun `removing old files from a folder that was never made removes nothing`() {
        assertEquals(0, store.removeOld(beforeMs = Long.MAX_VALUE))
        assertFalse(folder.exists())
    }

    @Test
    fun `the store says whether a file it wrote is still there`() {
        val file = store.write("Mileage-2026-10.pdf") { it.write(1) }
        assertTrue(store.holds(file))

        // Android empties the cache by itself when the phone runs short of room.
        assertTrue(file.delete())

        assertFalse(store.holds(file))
        // And a file somewhere else is not one of its own.
        assertFalse(store.holds(temporaryFolder.newFile("Mileage-2026-10.pdf")))
    }
}

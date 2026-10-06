package com.shawnkowalchuk.milo.data.transfer

import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The files an import keeps inside MilO, and the notes of the backup agent, each against a
 * real folder.
 */
class TransferFilesTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val folder: File get() = File(temporaryFolder.root, "kept")

    @Test
    fun `a picked file is copied whole, in place of the one before`() {
        val incoming = IncomingImport(folder)
        assertNull(incoming.waiting())

        incoming.take(ByteArrayInputStream("first".toByteArray()))
        val copy = incoming.take(ByteArrayInputStream("the second file".toByteArray()))

        assertEquals("the second file", copy.readText())
        assertEquals(copy, incoming.waiting())
        assertEquals(1, folder.listFiles()?.size)
    }

    @Test
    fun `the note that an import has begun is read back as it was left, beside the copy`() {
        val incoming = IncomingImport(folder)
        assertNull(incoming.begun())
        val copy = incoming.take(ByteArrayInputStream("the file".toByteArray()))
        val note =
            ImportBegun(50, 1_791_400_000_000, 1_791_400_004_000, "An import has. Replaced", 1)

        incoming.noteBegun(ImportBegun(7, 8, 9, "an earlier one"))
        incoming.noteBegun(note)

        assertEquals(note, IncomingImport(folder).begun())
        assertEquals("The copy is not the note's to remove", copy, incoming.waiting())
        incoming.clear()
        assertEquals("Nor the note the copy's", note, incoming.begun())
        incoming.clearBegun()
        assertNull(incoming.begun())
        assertTrue(folder.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `a note keeps its line of the log whole, and says which line it is`() {
        val note = ImportBegun(50, 8, 9, "a line\nwith a second line, and 4 numbers: 1\n2")

        assertEquals(note, importBegunOf(note.asText()))
        assertEquals(0, importBegunOf("50\n8\n9\n0\n")?.tries)
        val line = note.logLine()
        assertEquals(9L, line.atMs)
        assertEquals(note.said, line.message)
    }

    @Test
    fun `a file that is not a note is not taken for one`() {
        for (text in listOf("", "50\n8\n9\n0", "50\n8\nnine\n0\na line", "50\n8\n9\n-1\na line")) {
            assertNull(text, importBegunOf(text))
        }
        val incoming = IncomingImport(folder)
        incoming.noteBegun(ImportBegun(7, 8, 9, "a line"))
        File(folder, "begun.txt").writeText("not a note")

        assertThrows(IOException::class.java) { incoming.begun() }
    }

    @Test
    fun `one safety copy is removed by the time it was written, and the others stay`() = runTest {
        val copies = SafetyCopies(folder)
        for (atMs in listOf(1_000L, 2_000L, 3_000L)) copies.write(atMs) { it.write(1) }

        copies.remove(2_000)
        copies.remove(9_999)

        assertEquals(listOf(3_000L, 1_000L), copies.all().map { it.writtenAtMs })
        assertEquals("MilO-before-import-1000.json", copies.nameOf(1_000))
        assertEquals(0, copies.keepNewest(Int.MAX_VALUE))
    }

    @Test
    fun `a file larger than any export is refused, and nothing of it is kept`() {
        val incoming = IncomingImport(folder)

        val refusal =
            assertThrows(ImportTooLargeException::class.java) {
                incoming.take(ByteArrayInputStream(ByteArray(1_001)), maxBytes = 1_000)
            }

        assertEquals(1_000L, refusal.limitBytes)
        assertNull(incoming.waiting())
        val atTheLimit = incoming.take(ByteArrayInputStream(ByteArray(1_000)), maxBytes = 1_000)
        assertArrayEquals(ByteArray(1_000), atTheLimit.readBytes())
    }

    @Test
    fun `a file that cannot be read to its end leaves nothing behind`() {
        val incoming = IncomingImport(folder)
        val breaksOff =
            object : InputStream() {
                private var served = 0

                override fun read(): Int =
                    if (served++ < 10) 'x'.code else throw IOException("gone")
            }

        assertThrows(IOException::class.java) { incoming.take(breaksOff) }

        assertNull(incoming.waiting())
    }

    @Test
    fun `the copy is removed when the import is over, and removing nothing is no failure`() {
        val incoming = IncomingImport(folder)
        incoming.take(ByteArrayInputStream("x".toByteArray()))

        incoming.clear()
        incoming.clear()

        assertNull(incoming.waiting())
    }

    @Test
    fun `safety copies are listed newest first, by the time in their names`() = runTest {
        val copies = SafetyCopies(folder)
        assertTrue(copies.all().isEmpty())

        for (atMs in listOf(2_000L, 1_000L, 3_000L)) {
            copies.write(atMs) { it.write("copy $atMs".toByteArray()) }
        }

        assertEquals(listOf(3_000L, 2_000L, 1_000L), copies.all().map { it.writtenAtMs })
        assertEquals("copy 3000", copies.all().first().file.readText())
        assertEquals("MilO-before-import-3000.json", copies.all().first().file.name)
    }

    @Test
    fun `only the newest few safety copies are kept`() = runTest {
        val copies = SafetyCopies(folder)
        for (atMs in 1L..5L) copies.write(atMs) { it.write(1) }

        val removed = copies.keepNewest()

        assertEquals(2, removed)
        assertEquals(SAFETY_COPIES_KEPT, copies.all().size)
        assertEquals(listOf(5L, 4L, 3L), copies.all().map { it.writtenAtMs })
    }

    @Test
    fun `a safety copy that could not be finished is not listed, and its remains go`() = runTest {
        val copies = SafetyCopies(folder)
        copies.write(1) { it.write(1) }

        assertThrows(IOException::class.java) {
            kotlinx.coroutines.runBlocking {
                copies.write(2) {
                    it.write(1)
                    throw IOException("the disk is full")
                }
            }
        }
        // What a writer that dies without an I/O failure can leave behind.
        File(folder, "MilO-before-import-3.json.part").writeText("half")

        assertEquals(listOf(1L), copies.all().map { it.writtenAtMs })
        copies.keepNewest()
        assertEquals(listOf("MilO-before-import-1.json"), folder.list()?.toList())
    }

    @Test
    fun `a note from the backup agent is read back at the next start`() {
        val notes = BackupNoteStore(folder)
        assertTrue(notes.waiting().isEmpty())

        notes.leave(BackupNote(BackupNoteKind.QUOTA_EXCEEDED, 2_000, "26.1 MB of 25.0 MB"))
        notes.leave(BackupNote(BackupNoteKind.RESTORED, 1_000))

        val waiting = notes.waiting().map { it.second }
        assertEquals(
            listOf(
                BackupNote(BackupNoteKind.RESTORED, 1_000),
                BackupNote(BackupNoteKind.QUOTA_EXCEEDED, 2_000, "26.1 MB of 25.0 MB"),
            ),
            waiting,
        )
    }

    @Test
    fun `a note is removed once it has been told, and the others stay`() {
        val notes = BackupNoteStore(folder)
        notes.leave(BackupNote(BackupNoteKind.RESTORED, 1_000))
        notes.leave(BackupNote(BackupNoteKind.RESTORED, 2_000))

        notes.remove(notes.waiting().first().first)

        assertEquals(listOf(2_000L), notes.waiting().map { it.second.atMs })
    }

    @Test
    fun `the two calls Android makes for one backup leave one note, the later one`() {
        val notes = BackupNoteStore(folder)

        notes.leave(BackupNote(BackupNoteKind.COLLECTED, 1_000, "for a cloud backup"))
        notes.leave(BackupNote(BackupNoteKind.COLLECTED, 1_050, "for a cloud backup"))

        assertEquals(listOf(1_050L), notes.waiting().map { it.second.atMs })
    }

    @Test
    fun `a backup that was refused for its size is not also noted as collected`() {
        val notes = BackupNoteStore(folder)
        // Android calls for the files to measure them, and then refuses them.
        notes.leave(BackupNote(BackupNoteKind.COLLECTED, 1_000, "for a cloud backup"))
        notes.leave(BackupNote(BackupNoteKind.QUOTA_EXCEEDED, 1_001, "28.0 MB of 25.0 MB"))

        notes.withdrawCollected()
        notes.withdrawCollected()

        assertEquals(listOf(BackupNoteKind.QUOTA_EXCEEDED), notes.waiting().map { it.second.kind })
    }

    @Test
    fun `refused backups do not pile up, and a restore is noted whatever else waits`() {
        val notes = BackupNoteStore(folder)
        for (night in 1L..30L) notes.leave(BackupNote(BackupNoteKind.QUOTA_EXCEEDED, night))

        notes.leave(BackupNote(BackupNoteKind.RESTORED, 99))

        val waiting = notes.waiting().map { it.second }
        assertEquals(20, waiting.count { it.kind == BackupNoteKind.QUOTA_EXCEEDED })
        // The earliest are the ones that are kept: they show when it began.
        assertEquals(1L, waiting.first().atMs)
        assertTrue(waiting.any { it.kind == BackupNoteKind.RESTORED })
    }

    @Test
    fun `a file in the folder that is no note is passed over`() {
        val notes = BackupNoteStore(folder)
        notes.leave(BackupNote(BackupNoteKind.RESTORED, 1_000))
        File(folder, "stray.txt").writeText("not a note")
        File(folder, "other.bin").writeText("RESTORED\n5\n")

        assertEquals(1, notes.waiting().size)
        assertFalse(notes.waiting().any { it.first.name == "stray.txt" })
    }

    @Test
    fun `a database file that is not there is not made, and is no problem`() {
        val missing = File(folder, "milo.db")

        assertNull(settleDatabaseFile(missing))

        assertFalse(missing.exists())
    }
}

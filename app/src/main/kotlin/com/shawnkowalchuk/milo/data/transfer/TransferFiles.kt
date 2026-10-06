package com.shawnkowalchuk.milo.data.transfer

import android.content.Context
import com.shawnkowalchuk.milo.data.report.ReportFileStore
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files

// The files an import keeps inside MilO: the copy of the file that is being imported, the note
// that its trips have begun to be stored (`ImportBegun.kt`), and the safety copies of what the
// phone held before an import. All are under the app's no-backup files. Android never backs
// that folder up or moves it to another phone, which matters for the safety copies: each can
// be tens of megabytes, and Android's cloud backup takes nothing at all from an app that is
// over its 25 MB. It is also not the cache, which Android empties by itself: a file that is
// being imported must not vanish half-way.

private const val IMPORT_FOLDER = "import"
private const val INCOMING_NAME = "incoming.json"
private const val BEGUN_NAME = "begun.txt"
private const val SAFETY_FOLDER = "safety_copies"
private const val SAFETY_PREFIX = "MilO-before-import-"
private const val SAFETY_SUFFIX = ".json"
private const val COPY_BUFFER_BYTES = 64 * 1024

/** How many safety copies are kept. An older one is removed when a newer one has been written. */
const val SAFETY_COPIES_KEPT = 3

/**
 * The largest file that is taken in for an import. An export of several years with every GPS
 * point is some tens of megabytes; this is far more, and it stops a wrong pick (a film) from
 * filling the phone before MilO has found out that it is no export.
 */
const val MAX_IMPORT_BYTES = 512L * 1024 * 1024

/** The picked file is larger than any export. Nothing was kept. */
class ImportTooLargeException(val limitBytes: Long) :
    IOException("The file is larger than $limitBytes bytes")

/**
 * MilO's own copy of the file that is being imported.
 *
 * The file Shawn picks belongs to another app, and MilO may read it only for a short while
 * after the pick. An import reads its file three times (to check it, and again while the raw
 * points are stored) with a question to him in between, so the file is copied here first.
 * What was checked is then what is imported, whatever happens to the original.
 *
 * @param folder holds the one copy. Created on first use.
 */
class IncomingImport(private val folder: File) {
    private val file = File(folder, INCOMING_NAME)

    // Written under another name and renamed, like a report file, so that the note is there
    // whole or not at all.
    private val notes = ReportFileStore(folder)

    /**
     * Copies [source] here, in place of whatever was here, and returns the copy.
     *
     * @throws ImportTooLargeException if the source is longer than [maxBytes].
     * @throws IOException if the source cannot be read or the copy cannot be written. Nothing
     * is left behind then.
     */
    fun take(source: InputStream, maxBytes: Long = MAX_IMPORT_BYTES): File {
        if (!folder.isDirectory && !folder.mkdirs() && !folder.isDirectory) {
            throw IOException("The folder for a file that is being imported could not be created")
        }
        try {
            file.outputStream().use { written ->
                val buffer = ByteArray(COPY_BUFFER_BYTES)
                var copied = 0L
                while (true) {
                    val read = source.read(buffer)
                    if (read < 0) break
                    copied += read
                    if (copied > maxBytes) throw ImportTooLargeException(maxBytes)
                    written.write(buffer, 0, read)
                }
            }
        } catch (failure: IOException) {
            try {
                clear()
            } catch (leftBehind: IOException) {
                // The failed copy is the news; that its remains could not be removed rides
                // along. They are removed by the next clear.
                failure.addSuppressed(leftBehind)
            }
            throw failure
        }
        return file
    }

    /** The copy, or null if none is waiting. */
    fun waiting(): File? = file.takeIf { it.isFile }

    /** Removes the copy: the import is over, or was not wanted after all. */
    fun clear() {
        Files.deleteIfExists(file.toPath())
    }

    /**
     * Leaves the note that an import is about to replace the trips, in place of any earlier
     * one. It is on the disk when this returns.
     *
     * @throws IOException if it cannot be written. No note is there then.
     */
    fun noteBegun(begun: ImportBegun) {
        notes.write(BEGUN_NAME) { it.write(begun.asText().toByteArray(Charsets.UTF_8)) }
    }

    /**
     * The note of an import that has not come to its end, or null if there is none. While this
     * process is importing, it is its own; at a process start it is what the last one left.
     *
     * @throws IOException if there is a note that cannot be read.
     */
    fun begun(): ImportBegun? {
        val note = File(folder, BEGUN_NAME)
        if (!note.isFile) return null
        return importBegunOf(note.readText(Charsets.UTF_8))
            ?: throw IOException("The note of an import that had begun cannot be understood")
    }

    /** Removes the note: every step of the import was taken, or none was. */
    fun clearBegun() {
        Files.deleteIfExists(File(folder, BEGUN_NAME).toPath())
    }
}

/**
 * One safety copy.
 *
 * @param writtenAtMs when it was written, which is just before the import it guards against.
 */
data class SafetyCopy(val file: File, val writtenAtMs: Long)

/**
 * The safety copies: a complete export of what the phone held, written by MilO itself just
 * before an import replaces it. An import of the wrong file is undone by importing the newest
 * of them.
 *
 * A copy is complete or it is not there, like a report file, and the writing is the same code
 * ([ReportFileStore]).
 *
 * @param folder where the copies live. Created on first use.
 */
class SafetyCopies(private val folder: File) {
    private val files = ReportFileStore(folder)

    /**
     * Writes a new copy and returns it. The older ones are not touched here: see [keepNewest].
     *
     * @param atMs wall-clock milliseconds, for the file's name.
     * @param content writes the whole export to the stream it is handed.
     * @throws IOException if the file cannot be written. No half-written copy is left behind.
     */
    suspend fun write(atMs: Long, content: suspend (OutputStream) -> Unit): SafetyCopy {
        val file = files.writeInPieces(nameOf(atMs), content)
        return SafetyCopy(file, atMs)
    }

    /** Every copy that is there, newest first. */
    fun all(): List<SafetyCopy> = folder
        .listFiles { file -> file.name.startsWith(SAFETY_PREFIX) }
        .orEmpty()
        .mapNotNull { file ->
            val writtenAtMs =
                file.name.removePrefix(SAFETY_PREFIX).removeSuffix(SAFETY_SUFFIX).toLongOrNull()
            writtenAtMs?.let { SafetyCopy(file, it) }
        }.sortedByDescending { it.writtenAtMs }

    /**
     * Removes the copy written at [writtenAtMs], if it is there.
     *
     * @throws IOException if it is still there afterwards.
     */
    fun remove(writtenAtMs: Long) {
        Files.deleteIfExists(File(folder, nameOf(writtenAtMs)).toPath())
    }

    /** What the copy written at [writtenAtMs] is called. */
    fun nameOf(writtenAtMs: Long): String = "$SAFETY_PREFIX$writtenAtMs$SAFETY_SUFFIX"

    /**
     * Removes all but the newest [kept] copies, and anything a failed write left behind.
     *
     * @return how many files were removed.
     */
    fun keepNewest(kept: Int = SAFETY_COPIES_KEPT): Int {
        val staying = all().take(kept).map { it.file }.toSet()
        val going = folder.listFiles { file -> file !in staying }.orEmpty()
        going.forEach { Files.deleteIfExists(it.toPath()) }
        return going.size
    }
}

/** Builds the place for a file that is being imported. Called once, for the `AppContainer`. */
fun buildIncomingImport(context: Context): IncomingImport =
    IncomingImport(File(context.noBackupFilesDir, IMPORT_FOLDER))

/** Builds the store of safety copies. Called once, for the `AppContainer`. */
fun buildSafetyCopies(context: Context): SafetyCopies =
    SafetyCopies(File(context.noBackupFilesDir, SAFETY_FOLDER))

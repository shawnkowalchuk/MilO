package com.shawnkowalchuk.milo.data.report

import android.content.Context
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * The folder the report files are kept in, inside the app's cache. `res/xml/report_file_paths`
 * names the same folder: it is the one folder another app can be handed a file from.
 */
const val REPORTS_FOLDER = "reports"

/** A file that is still being written carries this at the end of its name. */
private const val UNFINISHED_SUFFIX = ".part"

private const val DAY_MS = 24 * 60 * 60 * 1000L

/**
 * A report file is removed once it is this old. It is kept that long, and not removed when the
 * email app has been opened, because that app reads the file only when the email is sent, and
 * a draft can wait.
 */
const val KEEP_REPORT_FILES_MS = 7 * DAY_MS

/**
 * The files MilO makes to be handed to another app: the PDF and the CSV for the accountant,
 * and the event log as a text file when Shawn shares it from the Log screen.
 *
 * They are in the cache because each can be made again from the trips at any time, and the cache
 * is never part of a backup. The price: Android empties the cache by itself when the phone runs
 * short of room, so a file can be gone soon after it was written ([holds] says whether it is).
 *
 * **A file is complete or it is not there.** It is written under another name and given its
 * real one only when all of it is on the disk ([write]), so a report that failed half-way can
 * never be attached to an email.
 *
 * @param folder where the files live. Created on first use.
 */
class ReportFileStore(private val folder: File) {
    /**
     * Writes a report file and returns it. A file of the same name is replaced: a report made
     * again for the same period takes the place of the one before.
     *
     * @param name the file's name, without a folder.
     * @param content writes the whole file to the stream it is handed.
     * @throws IOException if the file cannot be written. Nothing is left behind then, and a
     * file of that name from before is still as it was.
     */
    fun write(name: String, content: (OutputStream) -> Unit): File = writing(name, content)

    /**
     * The same for a file whose content is read from storage piece by piece while it is
     * written (the event log), so that the whole of it is never held in memory.
     */
    suspend fun writeInPieces(name: String, content: suspend (OutputStream) -> Unit): File =
        writing(name) { stream -> content(stream) }

    // Inline, so that the one way of writing a file serves both a plain and a suspending
    // writer: a suspending call may stand inside the block only because it is copied into its
    // caller.
    private inline fun writing(name: String, content: (OutputStream) -> Unit): File {
        // Never a path: a name with a folder in it, or one that means a folder, could put the
        // file outside the one folder that is shared.
        require(name.isNotBlank() && File(name).name == name && name != "." && name != "..") {
            "A report file's name is a plain file name, but was \"$name\""
        }
        if (!folder.isDirectory && !folder.mkdirs() && !folder.isDirectory) {
            throw IOException("The folder for the report files could not be created")
        }
        val unfinished = File(folder, name + UNFINISHED_SUFFIX)
        val finished = File(folder, name)
        try {
            unfinished.outputStream().use { stream ->
                content(stream)
                // On the disk before the file gets its name, so the name never stands for less
                // than the whole report.
                stream.fd.sync()
            }
            Files.move(
                unfinished.toPath(),
                finished.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (failure: IOException) {
            try {
                Files.deleteIfExists(unfinished.toPath())
            } catch (leftBehind: IOException) {
                // The failed write is the news; that its remains could not be removed rides
                // along. They are removed by a later removeOld.
                failure.addSuppressed(leftBehind)
            }
            throw failure
        }
        return finished
    }

    /** Whether [file] is a report file of this store that is still on the disk. */
    fun holds(file: File): Boolean = file.parentFile == folder && file.isFile

    /**
     * Removes the report files that were last written before [beforeMs], and anything a failed
     * write left behind before then, so that old reports do not pile up.
     *
     * @return how many files were removed.
     * @throws IOException if a file cannot be removed.
     */
    fun removeOld(beforeMs: Long): Int {
        val old = folder.listFiles { file -> file.lastModified() < beforeMs }.orEmpty()
        old.forEach { Files.deleteIfExists(it.toPath()) }
        return old.size
    }
}

/** Builds the store on the app's cache folder. Called once, for the `AppContainer`. */
fun buildReportFileStore(context: Context): ReportFileStore =
    ReportFileStore(File(context.cacheDir, REPORTS_FOLDER))

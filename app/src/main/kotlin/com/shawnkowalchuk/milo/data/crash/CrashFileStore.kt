package com.shawnkowalchuk.milo.data.crash

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter

/** A stack trace longer than this is cut. Deep recursion can produce hundreds of kilobytes. */
private const val MAX_STACK_TRACE_CHARS = 16_000

/** The first line of an exception message can be long; the event log wants one short line. */
private const val MAX_SUMMARY_CHARS = 200

/**
 * At most this many crash files wait to be imported. An app that crashes at every start would
 * otherwise write a file each time. The earliest crashes are kept: they show how it began.
 */
private const val MAX_PENDING_CRASH_FILES = 20

/** The folder the crash files are kept in, inside the app's never-backed-up files. */
private const val CRASH_FOLDER = "crashes"

private const val CRASH_FILE_PREFIX = "crash-"
private const val CRASH_FILE_SUFFIX = ".txt"

/** The header lines of a crash file, before the stack trace: time, thread, summary. */
private const val HEADER_LINES = 3

/**
 * One uncaught exception, as it is written to a crash file and later to the event log.
 *
 * @param atMs wall-clock time of the crash.
 * @param summary the exception class and the first line of its message.
 * @param stackTrace the full trace including causes, cut at a sane length.
 */
data class CrashRecord(
    val atMs: Long,
    val threadName: String,
    val summary: String,
    val stackTrace: String,
) {
    companion object {
        fun from(atMs: Long, threadName: String, throwable: Throwable): CrashRecord {
            val firstMessageLine = throwable.message?.lineSequence()?.firstOrNull().orEmpty()
            val summary = "${throwable.javaClass.name}: $firstMessageLine".take(MAX_SUMMARY_CHARS)
            val trace = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }
            return CrashRecord(
                atMs = atMs,
                // A thread name is free text. A line break in one would break the file's layout.
                threadName = threadName.replace('\n', ' '),
                summary = summary,
                stackTrace = trace.toString().take(MAX_STACK_TRACE_CHARS),
            )
        }
    }
}

/**
 * Crash files: one small text file per uncaught exception, written while the process is dying
 * and read at the next start.
 *
 * A file, not the database, because a dying process can still do one blocking write to a file,
 * while a database write needs coroutines and threads that may already be gone, and the database
 * may be the very thing that crashed.
 *
 * The class itself uses plain `java.io` and no Android classes, so it is tested on the JVM.
 *
 * @param directory where the files go: see [buildCrashFileStore].
 */
class CrashFileStore(private val directory: File) {
    /**
     * Writes [record] and forces it to disk before returning, since the process ends right after.
     *
     * @throws IOException if the file cannot be written.
     */
    fun write(record: CrashRecord) {
        if (!directory.isDirectory && !directory.mkdirs()) {
            throw IOException("Cannot create the crash folder $directory")
        }
        if (pendingFiles().size >= MAX_PENDING_CRASH_FILES) return

        val file = File(directory, "$CRASH_FILE_PREFIX${record.atMs}$CRASH_FILE_SUFFIX")
        val text =
            buildString {
                appendLine(record.atMs)
                appendLine(record.threadName)
                appendLine(record.summary)
                append(record.stackTrace)
            }
        FileOutputStream(file).use { output ->
            output.write(text.toByteArray())
            output.fd.sync()
        }
    }

    /** The crash files waiting to be imported, oldest first. */
    fun pendingFiles(): List<File> = directory
        .listFiles { file -> file.name.startsWith(CRASH_FILE_PREFIX) }
        .orEmpty()
        .sortedBy { it.name }

    /**
     * Reads one crash file. A file that was cut short (the process died mid-write) or is
     * otherwise not in the expected layout still comes back as a record, holding whatever text
     * is there: half a crash report is worth more than none.
     *
     * @throws IOException if the file cannot be read at all.
     */
    fun read(file: File): CrashRecord {
        val text = file.readText()
        val lines = text.lines()
        val atMs = lines.firstOrNull()?.toLongOrNull()
        if (atMs == null || lines.size < HEADER_LINES) {
            return CrashRecord(
                atMs = file.lastModified(),
                threadName = "unknown",
                summary = "A crash record that could not be read in full",
                stackTrace = text,
            )
        }
        return CrashRecord(
            atMs = atMs,
            threadName = lines[1],
            summary = lines[2],
            stackTrace = lines.drop(HEADER_LINES).joinToString("\n"),
        )
    }

    /** @throws IOException if the file is still there afterwards. */
    fun delete(file: File) {
        if (!file.delete() && file.exists()) throw IOException("Cannot delete the crash file $file")
    }
}

/**
 * Builds the crash file store. Called once, by the `AppContainer`.
 *
 * The folder is under `noBackupFilesDir`, so a crash file is never backed up or moved to a new
 * phone, where it would show up as a crash that never happened there.
 */
fun buildCrashFileStore(context: Context): CrashFileStore =
    CrashFileStore(File(context.noBackupFilesDir, CRASH_FOLDER))

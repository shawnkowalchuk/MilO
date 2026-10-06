package com.shawnkowalchuk.milo.data.transfer

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/** The folder the notes are kept in, inside the app's never-backed-up files. */
private const val NOTES_FOLDER = "backup_notes"

private const val NOTE_SUFFIX = ".txt"

/**
 * At most this many notes wait at once. A backup that fails every night on a phone where MilO
 * is not opened for months must not fill the folder. The earliest are kept.
 */
private const val MAX_WAITING_NOTES = 20

/** The lines of a note's file before its detail: the kind and the time. */
private const val HEADER_LINES = 2

/** What Android's backup did with MilO, as far as MilO's backup agent is told. */
enum class BackupNoteKind {
    /**
     * Android collected MilO's files for a backup, and did not refuse them for their size.
     * Whether they then reached Google, or the other phone, Android does not say.
     */
    COLLECTED,

    /** The files are more than the backup takes: nothing was backed up. */
    QUOTA_EXCEEDED,

    /** A database could not be brought into one file before it was collected. */
    NOT_SETTLED,

    /** Android has put a backup's files in place of MilO's. */
    RESTORED,
}

/**
 * One thing the backup agent has to tell.
 *
 * @param atMs wall-clock time at which it happened.
 * @param detail whatever there is to add, in plain English: sizes, a reason.
 */
data class BackupNote(val kind: BackupNoteKind, val atMs: Long, val detail: String = "")

/**
 * Notes from MilO's backup agent to the next ordinary start of MilO.
 *
 * Android runs the agent in a process of its own kind, in which the `Application` class is not
 * MilO's and nothing of the app is set up; it must not open the database it is about to copy
 * or to replace. So the agent writes a small file, as a crash does, and the next start writes
 * it into the event log (`platform/transfer/BackupAftermath`).
 *
 * The files are under the no-backup folder: a note is about this installation, and one that was
 * restored on another phone would report a backup that never happened there.
 *
 * Plain `java.io`, so it is tested on the JVM.
 */
class BackupNoteStore(private val directory: File) {
    /**
     * Writes [note]. A [BackupNoteKind.COLLECTED] note replaces the one before it: Android
     * calls for the files twice for one backup, once to measure them, and only the last time
     * is worth a line.
     *
     * @throws IOException if the file cannot be written.
     */
    fun leave(note: BackupNote) {
        if (!directory.isDirectory && !directory.mkdirs() && !directory.isDirectory) {
            throw IOException("Cannot create the folder for the backup's notes, $directory")
        }
        val repeats = note.kind == BackupNoteKind.COLLECTED
        val name = if (repeats) note.kind.name else "${note.kind}-${note.atMs}"
        val file = File(directory, name + NOTE_SUFFIX)
        // A restore is told whatever else waits: it is the one note that changes what MilO does.
        val unlimited = repeats || note.kind == BackupNoteKind.RESTORED
        if (!unlimited && waiting().size >= MAX_WAITING_NOTES) return
        FileOutputStream(file).use { output ->
            output.write("${note.kind}\n${note.atMs}\n${note.detail}".toByteArray())
            output.fd.sync()
        }
    }

    /**
     * Takes back the [BackupNoteKind.COLLECTED] note, if one waits. Android calls for the
     * files to measure them before it refuses them for their size (seen on an emulator), so
     * by the time a backup is refused the note that it was collected has been left already.
     *
     * @throws IOException if the note is still there afterwards.
     */
    fun withdrawCollected() {
        val file = File(directory, BackupNoteKind.COLLECTED.name + NOTE_SUFFIX)
        if (file.exists()) remove(file)
    }

    /** The notes that wait, oldest first, each with its file. A file that is no note is skipped. */
    fun waiting(): List<Pair<File, BackupNote>> = directory
        .listFiles { file -> file.name.endsWith(NOTE_SUFFIX) }
        .orEmpty()
        .mapNotNull { file -> read(file)?.let { file to it } }
        .sortedBy { it.second.atMs }

    /** @throws IOException if the file is still there afterwards. */
    fun remove(file: File) {
        if (!file.delete() && file.exists()) throw IOException("Cannot delete the note $file")
    }

    private fun read(file: File): BackupNote? {
        val lines = file.readText().lines()
        val kind = BackupNoteKind.entries.firstOrNull { it.name == lines.firstOrNull() }
        val atMs = lines.getOrNull(1)?.toLongOrNull()
        if (kind == null || atMs == null) return null
        return BackupNote(kind, atMs, lines.drop(HEADER_LINES).joinToString("\n"))
    }
}

/** Builds the store. Called by the backup agent itself, and once for the `AppContainer`. */
fun buildBackupNoteStore(context: Context): BackupNoteStore =
    BackupNoteStore(File(context.noBackupFilesDir, NOTES_FOLDER))

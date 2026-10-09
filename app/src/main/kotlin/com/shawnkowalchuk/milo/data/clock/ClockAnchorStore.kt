package com.shawnkowalchuk.milo.data.clock

import android.content.Context
import com.shawnkowalchuk.milo.core.clock.ClockAnchor
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** The folder the anchor is kept in, inside the app's never-backed-up files. */
private const val CLOCK_FOLDER = "clock"

private const val ANCHOR_FILE = "anchor"

/** The file the anchor is written into before it takes the real one's place. */
private const val ANCHOR_FILE_BEING_WRITTEN = "anchor.new"

/** The first word of the file. Another number is a layout this build does not know. */
private const val LAYOUT = "1"

/**
 * The words of the file: the layout, the boot's number, the time of day, the time since boot,
 * and the anchor's probation: [PROVEN], or the time since boot at which the probation began.
 */
private const val WORDS = 5

/** The last word of an anchor that is not on probation. */
private const val PROVEN = "proven"

/**
 * What every anchor line ends with. A line without it was cut short, and its last number may
 * be the front part of a longer one: it is no anchor.
 */
private const val LINE_END = "\n"

/**
 * Where the anchor of MilO's clock (`core/clock/TrustedClock`) is kept between two processes: one
 * line in one small file.
 *
 * A file of its own, not the settings: the anchor has to be read before anything else in a new
 * process reads the clock, on the main thread and without waiting, and the settings file is
 * read by coroutines. It is under the no-backup folder, so it is in neither Android's backup
 * nor an export: an anchor is true of one boot of one phone, and nowhere else.
 *
 * Plain `java.io`, so it is tested on the JVM.
 *
 * @param directory where the file goes: see [buildClockAnchorStore].
 */
class ClockAnchorStore(private val directory: File) {
    private val file = File(directory, ANCHOR_FILE)

    /**
     * The stored anchor, or null if there is none that can be read: no file yet, a file that
     * cannot be opened, or one that does not hold a whole anchor line. Null is a full answer,
     * not a failure passed over: the clock then takes the phone's time as it is, on probation,
     * and writes a new anchor.
     */
    fun read(): ClockAnchor? {
        val text =
            try {
                file.readText()
            } catch (unreadable: IOException) {
                return null
            }
        if (!text.endsWith(LINE_END)) return null
        val words = text.removeSuffix(LINE_END).split(' ')
        if (words.size != WORDS || words[0] != LAYOUT) return null
        val bootCount = words[1].toIntOrNull() ?: return null
        val wallMs = words[2].toLongOrNull() ?: return null
        val elapsedMs = words[3].toLongOrNull() ?: return null
        val onProbationSinceMs =
            if (words[4] == PROVEN) null else words[4].toLongOrNull() ?: return null
        return ClockAnchor(bootCount, wallMs, elapsedMs, onProbationSinceMs)
    }

    /**
     * Stores [anchor] in place of the one before. The whole line is written into a second file,
     * forced to disk, and then given the real file's name in one step, so a process that dies
     * in the middle leaves the old anchor or the new one, never half of either.
     *
     * @throws IOException if the file cannot be written.
     */
    fun write(anchor: ClockAnchor) {
        if (!directory.isDirectory && !directory.mkdirs() && !directory.isDirectory) {
            throw IOException("Cannot create the folder for the clock's anchor, $directory")
        }
        val beingWritten = File(directory, ANCHOR_FILE_BEING_WRITTEN)
        val probation = anchor.onProbationSinceMs?.toString() ?: PROVEN
        val line =
            "$LAYOUT ${anchor.bootCount} ${anchor.wallMs} ${anchor.elapsedMs} $probation$LINE_END"
        FileOutputStream(beingWritten).use { output ->
            output.write(line.toByteArray())
            output.fd.sync()
        }
        Files.move(
            beingWritten.toPath(),
            file.toPath(),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING,
        )
    }
}

/**
 * Builds the store of the clock's anchor. Called once per process, by the clock itself.
 *
 * The folder is under `noBackupFilesDir`: Android's backup never takes it, and the export reads
 * the databases and the settings only.
 */
fun buildClockAnchorStore(context: Context): ClockAnchorStore =
    ClockAnchorStore(File(context.noBackupFilesDir, CLOCK_FOLDER))

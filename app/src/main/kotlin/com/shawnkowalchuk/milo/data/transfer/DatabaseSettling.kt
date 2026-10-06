package com.shawnkowalchuk.milo.data.transfer

import android.content.Context
import androidx.sqlite.SQLiteException
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.shawnkowalchuk.milo.data.MILO_DATABASE_FILE
import com.shawnkowalchuk.milo.data.POINTS_DATABASE_FILE
import java.io.File

/**
 * How long the move waits for whatever else has the database open, in milliseconds. MilO's
 * own process can be alive while Android collects the files (below), and may be writing a line
 * to the event log at that moment.
 */
private const val WAIT_FOR_OTHERS_MS = 2_000

/**
 * Brings each of MilO's two databases into its one main file, for Android's backup to copy.
 *
 * Room writes a change into a second file first, the write-ahead log (`milo.db-wal`), and
 * moves it into the main file later. So the newest trips can be in that log alone. The backup
 * rules do take the log along with the main file, and SQLite reads the two together after a
 * restore; but that is two files which only mean something as a pair. Before the files are
 * collected, the log is therefore moved into the main file here and emptied ("checkpoint,
 * truncate"): what is copied then is one file that is complete by itself.
 *
 * This is called by the backup agent. Android runs the agent in a process it starts for the
 * backup if MilO is not running, and inside MilO's own process if it is (seen on an emulator,
 * FINDINGS_LOG 2026-10-06); it ends that process when the backup is done. In the first case the
 * connection made here is the only one, and closing it removes the empty log. In the second,
 * Room's connections are open beside it and MilO could write while the files are copied. Such
 * a write goes into the log, which was just emptied; nothing moves from the log into the main
 * file again before a thousand pages have been written, so the main file stays as it is for
 * the moment the copy takes, and a log copied half-way through a write is read by SQLite up
 * to its last whole change.
 *
 * The database is opened with the SQLite that Room uses, not through Room, so no migration
 * runs and no table is read or written.
 *
 * @return what could not be done, one sentence for each database, for the event log. Empty when
 * both are settled, which is the usual case. A database that is not settled is still copied
 * together with its log.
 */
fun settleDatabasesForBackup(context: Context): List<String> =
    listOf(MILO_DATABASE_FILE, POINTS_DATABASE_FILE).mapNotNull { name ->
        settleDatabaseFile(context.getDatabasePath(name))?.let { why -> "$name: $why" }
    }

/** @return null if [file] is settled or is not there at all, or else why it is not. */
internal fun settleDatabaseFile(file: File): String? {
    // Opening a file that is not there would create one, and an empty file is no database.
    if (!file.isFile) return null
    return try {
        BundledSQLiteDriver().open(file.path).use { connection ->
            connection.execSQL("PRAGMA busy_timeout = $WAIT_FOR_OTHERS_MS")
            connection.prepare("PRAGMA wal_checkpoint(TRUNCATE)").use { answer ->
                // The first number of the answer is 1 if something else held the database.
                val busy = answer.step() && answer.getLong(0) != 0L
                "the database stayed in use, so its log was not moved into it".takeIf { busy }
            }
        }
    } catch (refused: SQLiteException) {
        "SQLite refused: $refused"
    } catch (unloaded: LinkageError) {
        // SQLite itself could not be loaded into this process. The backup is worth more than
        // this step, so it goes on with the main file and its log as they are.
        "SQLite could not be loaded: $unloaded"
    }
}

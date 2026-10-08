package com.shawnkowalchuk.milo.data

import android.content.Context
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.shawnkowalchuk.milo.data.eventlog.EventLogDao
import com.shawnkowalchuk.milo.data.eventlog.EventLogEntry
import com.shawnkowalchuk.milo.data.report.SentReport
import com.shawnkowalchuk.milo.data.report.SentReportDao
import com.shawnkowalchuk.milo.data.transfer.MainTransferDao
import com.shawnkowalchuk.milo.data.trip.Trip
import com.shawnkowalchuk.milo.data.trip.TripDao
import kotlinx.coroutines.Dispatchers

/**
 * The file name of the main database. Renaming it would leave the trips already on the phone
 * behind in the old file. Android's backup takes the file under this name, with the `-wal`
 * file Room keeps beside it (`res/xml/data_extraction_rules.xml`).
 */
const val MILO_DATABASE_FILE = "milo.db"

/**
 * The version of the main database's tables. An export file says which one it was written from.
 */
const val MILO_DATABASE_VERSION = 7

/**
 * The main database: trips, the event log and the list of sent reports. It is the small,
 * valuable part of the app's data and the part that Android's cloud backup takes, which is why
 * the bulky raw GPS points are kept out of it (see [PointsDatabase]).
 *
 * Raising [MILO_DATABASE_VERSION] needs a migration (`MiloMigrations.kt`) and the schema file
 * Room writes to `app/schemas`. Version 2 added the trips' addresses; version 3, Business or
 * Personal; version 4, the marks of a trip that was added or edited by hand and what was
 * recorded before; version 5, the table of reports sent to the accountant; version 6, the
 * unit each of those reports was printed in; version 7, the vehicle each trip was in and its label. A new column of `trips` or `sent_reports` also has
 * to be carried by the export file (`data/transfer/ExportFormat.kt`), under a new format
 * version, as version 6's column is by format 2.
 */
@Database(
    entities = [Trip::class, EventLogEntry::class, SentReport::class],
    version = MILO_DATABASE_VERSION,
)
abstract class MiloDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao

    abstract fun eventLogDao(): EventLogDao

    abstract fun sentReportDao(): SentReportDao

    /** For an export and an import only: it can empty the two tables of Shawn's own data. */
    abstract fun mainTransferDao(): MainTransferDao
}

/**
 * Builds the main database. It is called once, by the `AppContainer`.
 *
 * There is deliberately no destructive-migration fallback: that option deletes every table when
 * a migration is missing, and these tables hold Shawn's real trips. A missing migration must
 * fail loudly instead. Every version the phone has ever held needs its step in [MILO_MIGRATIONS].
 *
 * That holds for a file Android's backup restored as well: one from an older build is brought
 * up to date by the same steps, and one from a newer build makes this build fail to open it,
 * with the file left as it is for the newer build to read.
 */
fun buildMiloDatabase(context: Context): MiloDatabase = Room
    .databaseBuilder<MiloDatabase>(context = context, name = MILO_DATABASE_FILE)
    .setDriver(BundledSQLiteDriver())
    .setQueryCoroutineContext(Dispatchers.IO)
    .addMigrations(*MILO_MIGRATIONS)
    .build()

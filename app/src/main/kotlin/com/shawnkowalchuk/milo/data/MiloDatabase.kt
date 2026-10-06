package com.shawnkowalchuk.milo.data

import android.content.Context
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.shawnkowalchuk.milo.data.eventlog.EventLogDao
import com.shawnkowalchuk.milo.data.eventlog.EventLogEntry
import com.shawnkowalchuk.milo.data.trip.Trip
import com.shawnkowalchuk.milo.data.trip.TripDao
import kotlinx.coroutines.Dispatchers

/**
 * The file name of the main database. Phase 4's backup rules refer to it by this name, and
 * renaming it would leave the trips already on the phone behind in the old file.
 */
const val MILO_DATABASE_FILE = "milo.db"

/**
 * The main database: trips and the event log. It is the small, valuable part of the app's data
 * and the part that will be backed up in phase 4, which is why the bulky raw GPS points are kept
 * out of it (see [PointsDatabase]).
 *
 * Raising [Database.version] needs a migration (`MiloMigrations.kt`) and the schema file Room
 * writes to `app/schemas`. Version 2 added the trips' addresses.
 */
@Database(entities = [Trip::class, EventLogEntry::class], version = 2)
abstract class MiloDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao

    abstract fun eventLogDao(): EventLogDao
}

/**
 * Builds the main database. It is called once, by the `AppContainer`.
 *
 * There is deliberately no destructive-migration fallback: that option deletes every table when
 * a migration is missing, and these tables hold Shawn's real trips. A missing migration must
 * fail loudly instead. Every version the phone has ever held needs its step in [MILO_MIGRATIONS].
 */
fun buildMiloDatabase(context: Context): MiloDatabase = Room
    .databaseBuilder<MiloDatabase>(context = context, name = MILO_DATABASE_FILE)
    .setDriver(BundledSQLiteDriver())
    .setQueryCoroutineContext(Dispatchers.IO)
    .addMigrations(*MILO_MIGRATIONS)
    .build()

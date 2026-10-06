package com.shawnkowalchuk.milo.data

import android.content.Context
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.shawnkowalchuk.milo.data.point.RawPoint
import com.shawnkowalchuk.milo.data.point.RawPointDao
import com.shawnkowalchuk.milo.data.transfer.PointsTransferDao
import kotlinx.coroutines.Dispatchers

/**
 * The file name of the raw points database. The backup rules
 * (`res/xml/data_extraction_rules.xml`) keep this file out of cloud backup by this name,
 * together with the three files Room keeps beside it: `points.db-wal`, `points.db-shm` and
 * `points.db.lck`. Renaming it here without renaming it there would put the points into the
 * cloud backup, and past its 25 MB limit nothing at all is backed up.
 */
const val POINTS_DATABASE_FILE = "points.db"

/**
 * Raw GPS points, in a database file of their own.
 *
 * Android's cloud backup holds at most 25 MB per app and is all-or-nothing: over the limit it
 * backs up nothing and says nothing. A fix every 5 seconds would push a single database past
 * that within about a year and take the trips down with it. Kept in a separate file, the points
 * can be left out of the backup while the trips stay in
 * (docs/research/2026-10-03-pdf-email-backup.md).
 */
@Database(entities = [RawPoint::class], version = 1)
abstract class PointsDatabase : RoomDatabase() {
    abstract fun rawPointDao(): RawPointDao

    /** For an export and an import only: it can remove raw points. */
    abstract fun pointsTransferDao(): PointsTransferDao
}

/** Builds the raw points database. Called once, by the `AppContainer`. No destructive fallback. */
fun buildPointsDatabase(context: Context): PointsDatabase = Room
    .databaseBuilder<PointsDatabase>(context = context, name = POINTS_DATABASE_FILE)
    .setDriver(BundledSQLiteDriver())
    .setQueryCoroutineContext(Dispatchers.IO)
    .build()

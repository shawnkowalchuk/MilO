package com.shawnkowalchuk.milo.data

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

// The steps that bring an older main database up to the current version. The phone holds real
// trips, so a step only ever adds: no table is dropped or rebuilt, and no stored value is
// rewritten.
//
// Room runs a step, checks the result against the tables the code expects, and stores the new
// version number, all inside one transaction. If the step or the check fails, the transaction
// is rolled back and the file is left exactly as it was, at its old version; the app then fails
// to open the database, loudly, and loses nothing.

/**
 * Version 1 to 2: the trips' addresses.
 *
 * Four columns are added to `trips`, each empty on every existing row (no address yet, no
 * lookup attempted), so the trips recorded before this version are looked up like new ones. The
 * index on the start time is the one the Trips screen has wanted since it was built; it waited
 * for the first migration.
 *
 * The statements must produce exactly what `Trip` declares, or Room refuses the result. The
 * exported `app/schemas/.../2.json` is the reference.
 */
internal val MIGRATION_1_2: Migration =
    object : Migration(1, 2) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE trips ADD COLUMN startAddress TEXT")
            connection.execSQL("ALTER TABLE trips ADD COLUMN endAddress TEXT")
            connection.execSQL(
                "ALTER TABLE trips ADD COLUMN addressAttempts INTEGER NOT NULL DEFAULT 0",
            )
            connection.execSQL("ALTER TABLE trips ADD COLUMN addressLastAttemptAtMs INTEGER")
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS index_trips_startedAtMs ON trips (startedAtMs)",
            )
        }
    }

/** Every step, in order. `buildMiloDatabase` hands them to Room. */
internal val MILO_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)

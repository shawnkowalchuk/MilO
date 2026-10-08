package com.shawnkowalchuk.milo.data

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

// The steps that bring an older main database up to the current version. The phone holds real
// trips, so a step only ever adds: a column, an index or a whole new table. No table is dropped
// or rebuilt, and no stored value is rewritten.
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

/**
 * Version 2 to 3: Business or Personal.
 *
 * Four columns are added to `trips`. On every existing row `category` is empty, which means
 * "not sorted yet", and the three flags are 0: not set by hand, not past the schedule, not
 * ignored. No stored trip is sorted here. A migration cannot read the settings, where the
 * schedule is, and must not fail for a reason that has nothing to do with the tables; the
 * catch-up at the same process start sorts them (`data/trip/TripCategoryCatchUp.kt`).
 *
 * The exported `app/schemas/.../3.json` is the reference.
 */
internal val MIGRATION_2_3: Migration =
    object : Migration(2, 3) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE trips ADD COLUMN category TEXT")
            connection.execSQL(
                "ALTER TABLE trips ADD COLUMN categorySetByHand INTEGER NOT NULL DEFAULT 0",
            )
            connection.execSQL(
                "ALTER TABLE trips ADD COLUMN ranPastSchedule INTEGER NOT NULL DEFAULT 0",
            )
            connection.execSQL(
                "ALTER TABLE trips ADD COLUMN ignoredOutsideSchedule INTEGER NOT NULL DEFAULT 0",
            )
        }
    }

/**
 * Version 3 to 4: trips added or edited by hand.
 *
 * Seven columns are added to `trips`. On every existing row the four marks are 0 (not added by
 * hand, not edited, neither address typed by hand) and the three "recorded" columns are empty,
 * which is how "never edited" is stored: the row's own times and distance are what MilO
 * recorded, and they are copied into those columns by the first edit, not here. No stored
 * value is read or rewritten by this step.
 *
 * The exported `app/schemas/.../4.json` is the reference.
 */
internal val MIGRATION_3_4: Migration =
    object : Migration(3, 4) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                "ALTER TABLE trips ADD COLUMN addedByHand INTEGER NOT NULL DEFAULT 0",
            )
            connection.execSQL(
                "ALTER TABLE trips ADD COLUMN editedByHand INTEGER NOT NULL DEFAULT 0",
            )
            connection.execSQL(
                "ALTER TABLE trips ADD COLUMN startAddressByHand INTEGER NOT NULL DEFAULT 0",
            )
            connection.execSQL(
                "ALTER TABLE trips ADD COLUMN endAddressByHand INTEGER NOT NULL DEFAULT 0",
            )
            connection.execSQL("ALTER TABLE trips ADD COLUMN recordedStartedAtMs INTEGER")
            connection.execSQL("ALTER TABLE trips ADD COLUMN recordedEndedAtMs INTEGER")
            connection.execSQL("ALTER TABLE trips ADD COLUMN recordedDistanceMetres REAL")
        }
    }

/**
 * Version 4 to 5: the reports sent to the accountant.
 *
 * One new table, `sent_reports`, empty. Nothing was sent before this version existed, so there
 * is nothing to fill it with, and no month is submitted after the step. The `trips` and
 * `event_log` tables are not named by this step at all: no statement here can reach a stored
 * trip.
 *
 * The statement is the one Room itself would run to make the table new, so a database that was
 * migrated and one that was created at version 5 are the same. The exported
 * `app/schemas/.../5.json` is the reference.
 */
internal val MIGRATION_4_5: Migration =
    object : Migration(4, 5) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS sent_reports (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "kind TEXT NOT NULL, " +
                    "firstDay INTEGER NOT NULL, " +
                    "lastDay INTEGER NOT NULL, " +
                    "sentAtMs INTEGER NOT NULL, " +
                    "tripCount INTEGER NOT NULL, " +
                    "distanceMetres REAL NOT NULL, " +
                    "revision INTEGER NOT NULL)",
            )
        }
    }

/**
 * Version 5 to 6: the unit a sent report was printed in.
 *
 * One column is added to `sent_reports`. On every existing row it is `KILOMETRES`, which is
 * true of every one of them: until this version MilO printed nothing else. No stored figure is
 * read or rewritten, and the `trips` table is not named by this step.
 *
 * The exported `app/schemas/.../6.json` is the reference.
 */
internal val MIGRATION_5_6: Migration =
    object : Migration(5, 6) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                "ALTER TABLE sent_reports ADD COLUMN distanceUnit TEXT NOT NULL " +
                    "DEFAULT 'KILOMETRES'",
            )
        }
    }

/**
 * Version 6 to 7, both of 2026-10-08: the vehicle each trip was in (MilO learned several), and
 * the label Shawn gives a trip.
 *
 * Two columns are added to `trips`, null on every existing row: the vehicle's Bluetooth
 * address, and the label. The trips that were in the truck are given its address afterwards,
 * once, by `data/trip/TripVehicleCatchUp`: this step cannot read the settings file the truck is
 * in. No trip had a label before.
 *
 * The exported `app/schemas/.../7.json` is the reference.
 */
internal val MIGRATION_6_7: Migration =
    object : Migration(6, 7) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE trips ADD COLUMN vehicleAddress TEXT")
            connection.execSQL("ALTER TABLE trips ADD COLUMN label TEXT")
        }
    }

/**
 * Every step, in order. `buildMiloDatabase` hands them to Room, which runs them one after the
 * other for a database that is more than one version behind.
 */
internal val MILO_MIGRATIONS: Array<Migration> =
    arrayOf(
        MIGRATION_1_2,
        MIGRATION_2_3,
        MIGRATION_3_4,
        MIGRATION_4_5,
        MIGRATION_5_6,
        MIGRATION_6_7,
    )

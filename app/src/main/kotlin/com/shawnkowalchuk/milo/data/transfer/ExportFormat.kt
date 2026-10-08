package com.shawnkowalchuk.milo.data.transfer

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// The export file: one JSON document that holds everything of Shawn's that MilO stores. It is
// the one copy of his data that depends on nothing else: not on the signing key, not on his
// Google account and not on Android's backup quota. So its form is fixed here, in types of its
// own, and never follows the database tables by itself: a table can gain a column in a
// migration, and a file written before that must still be read.
//
// The document, in the order it is written (a reader takes the keys in any order):
//
//   format, formatVersion          what the file is, and which version of this form
//   exportedAtMs, exportedAt       when it was written, as a number and for a person to read
//   appVersion, databaseVersion    which MilO wrote it
//   about                          a sentence for whoever opens the file
//   contents                       how many of each thing it holds
//   settings                       the settings that mean the same on another phone, or null
//   sentReports                    every report recorded as sent
//   trips                          every closed trip: finished, discarded and deleted
//   pointColumns, points           the raw GPS points as rows of numbers, or null if left out
//
// Times are milliseconds since 1970 and distances are metres, as in storage. A calendar day is
// written as text ("2026-09-01"). A stored constant is written by the name the tables use.
//
// Format 2 (2026-10-07) differs from format 1 in one thing: a sent report says which unit it
// was printed in ("distanceUnit"). A file of format 1 has no such key, and its reports are all
// in kilometres, which is all MilO printed then.
//
// Format 3 (2026-10-08) differs from format 2 in two things: a trip says which paired vehicle it
// was in ("vehicleAddress", its Bluetooth address, or null for none), and what Shawn labelled it
// ("label", or null). A file of format 1 or 2 has neither key; its trips are given the first
// vehicle after the import, as the trips on the phone were (`TripVehicleCatchUp`), and no label.

/** What the key "format" holds. A file that says anything else is not an export of MilO's. */
const val EXPORT_FORMAT = "milo-export"

/**
 * The version of the form described above. Raise it for any change to the form, and keep
 * reading every earlier version: a file from a newer version is refused, never guessed at.
 */
const val EXPORT_FORMAT_VERSION = 3

/** The first version of the form in which a sent report names its unit. */
internal const val FORMAT_WITH_REPORT_UNIT = 2

/** The first version of the form in which a trip names its vehicle and has its label. */
internal const val FORMAT_WITH_TRIP_VEHICLE = 3

/** The names of the numbers of a row of "points", in order. Written into the file as a legend. */
val EXPORT_POINT_COLUMNS: List<String> =
    listOf(
        "tripId",
        "wallClockMs",
        "elapsedRealtimeMs",
        "latitude",
        "longitude",
        "accuracyMetres",
        "speedMetresPerSecond",
    )

/** The sentence under "about": for a person who opens the file, years from now. */
const val EXPORT_ABOUT =
    "MilO mileage log. Every closed trip (finished, discarded and deleted), every report " +
        "recorded as sent, the settings that mean the same on another phone, and the raw GPS " +
        "points if \"points\" is not null. Times are milliseconds since 1970-01-01 UTC, " +
        "distances are metres. Not in this file: the event log, the companion device " +
        "association with the truck, the sounds chosen from the phone, and the confirmations " +
        "of the setup checklist."

/**
 * The JSON this file is written and read with: strict. An unknown key, a missing one, a text
 * where a number belongs and a number that is not finite are all refused, and every key is
 * written, also one whose value is null, so that "not there" always means "damaged". Two keys
 * have a value to fall back on, so that a file of an older format, which lacks them, can still
 * be read: a report's unit, and a trip's vehicle and label. They are written always, also when
 * null (`encodeDefaults`).
 */
internal val ExportJson: Json = Json { encodeDefaults = true }

/**
 * How many of each thing the file holds. Written near the top, and checked by the reader
 * against what it finds: a file that holds less than it says was cut short.
 *
 * @param points null if the raw GPS points were left out of the export.
 * @param settings false if the settings could not be read when the file was written.
 */
@Serializable
data class ExportContents(
    val trips: Int,
    val sentReports: Int,
    val points: Long?,
    val settings: Boolean,
)

/**
 * A closed trip as the file holds it: every column of the table that outlives the recording.
 *
 * @param vehicleAddress the paired vehicle it was in, or null for none. Written by format 3 and
 * later, always. It falls back on null so that a file of an older format, which has no such
 * key, can still be read; one of those that names a vehicle is refused (`ExportedTrip.problem`).
 * @param label what Shawn called the trip, or null. The same as [vehicleAddress] in every way.
 */
@Serializable
data class ExportedTrip(
    val id: Long,
    val startedAtMs: Long,
    val endedAtMs: Long?,
    val status: String,
    val startedBy: String,
    val truckSeen: Boolean,
    val distanceMetres: Double,
    val startLatitude: Double?,
    val startLongitude: Double?,
    val endLatitude: Double?,
    val endLongitude: Double?,
    val startAddress: String?,
    val endAddress: String?,
    val addressAttempts: Int,
    val addressLastAttemptAtMs: Long?,
    val category: String?,
    val categorySetByHand: Boolean,
    val ranPastSchedule: Boolean,
    val ignoredOutsideSchedule: Boolean,
    val addedByHand: Boolean,
    val editedByHand: Boolean,
    val startAddressByHand: Boolean,
    val endAddressByHand: Boolean,
    val recordedStartedAtMs: Long?,
    val recordedEndedAtMs: Long?,
    val recordedDistanceMetres: Double?,
    val vehicleAddress: String? = null,
    val label: String? = null,
)

/**
 * A report recorded as sent.
 *
 * @param firstDay and [lastDay] are calendar days as text, such as "2026-09-01".
 * @param distanceUnit the unit the report was printed in, "KILOMETRES" or "MILES": the total
 * in [distanceMetres] is a whole number of tenths of it. Written by format 2 and later, always.
 * It is the one key with a value to fall back on, and only so that a file of format 1, which
 * has no such key, can still be read; the reader refuses a file of format 2 that lacks it
 * (`ExportedSentReport.problem`).
 */
@Serializable
data class ExportedSentReport(
    val id: Long,
    val kind: String,
    val firstDay: String,
    val lastDay: String,
    val sentAtMs: Long,
    val tripCount: Int,
    val distanceMetres: Double,
    val revision: Int,
    val distanceUnit: String? = null,
)

/** The paired truck: which device it is and what it is called, without its association. */
@Serializable
data class ExportedTruck(val address: String, val name: String?)

/**
 * One day of the work schedule.
 *
 * @param day the day in English and small letters, such as "monday".
 * @param startMinute and [endMinute] are minutes since midnight.
 */
@Serializable
data class ExportedDay(
    val day: String,
    val tracked: Boolean,
    val startMinute: Int,
    val endMinute: Int,
)

/** The settings that mean the same on another phone (`TransferredSettings`). */
@Serializable
data class ExportedSettings(
    val truck: ExportedTruck?,
    val gracePeriodSeconds: Int,
    val minimumTripDistanceMetres: Int,
    val soundEnabled: Boolean,
    val schedule: List<ExportedDay>,
    val ignoreTripsOutsideSchedule: Boolean,
    val drivingAlertEnabled: Boolean,
    val reportName: String?,
    val reportCompany: String?,
    val reportVehicle: String?,
    val accountantEmail: String?,
    val reminderEnabled: Boolean,
    val reminderDay: Int,
)

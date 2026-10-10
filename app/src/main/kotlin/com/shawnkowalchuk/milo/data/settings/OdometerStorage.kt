package com.shawnkowalchuk.milo.data.settings

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.shawnkowalchuk.milo.core.odometer.MAX_ODOMETER_READING
import com.shawnkowalchuk.milo.core.odometer.OdometerReading
import com.shawnkowalchuk.milo.core.odometer.TripAtReading
import com.shawnkowalchuk.milo.core.util.DistanceUnit

// The odometer readings Shawn typed in (his decision of 2026-10-07: "I enter it, MilO adds
// trips"), kept in the settings file. They are few, a handful a year, and the settings file is
// backed up and restored with the rest by Android, so they need no table of their own: a table
// would have needed a new database version and its migration for a list this small.
//
// Each reading is one entry of a string set, "time:km", the time in wall-clock milliseconds.
// The set is read back sorted by time; an entry that is not of that form is skipped, never
// guessed at.
//
// Since 2026-10-07 a reading typed with MilO set to miles is "time:miles:mi": the figure as
// typed and the unit it was typed in, so that it comes back exactly. A reading in kilometres
// is written as it always was, with no third part, and every entry from before that day is
// one.
//
// Since 2026-10-08 a reading names the vehicle it was read on, after a bar:
// "time:km|AA:BB:CC:DD:EE:FF" (the address has colons of its own, so it stands behind a
// separator that cannot be in the rest). An entry without one is from before, and is the
// first vehicle's (`ofVehicle`).
//
// Since 2026-10-10 a reading typed while a trip is being recorded keeps which trip that was and
// how far it had gone (`TripAtReading`). That goes into a second string set, and the reading's
// own entry stays exactly as it was: "time of the reading:trip number:metres". A build from
// before that day reads a reading's entry only if it has the form that build knows, and skips
// any other, so a longer entry would have taken the reading away from it. With a set of their
// own, an older build (after a restore, or a copy from Google Play that is a version behind)
// still shows every reading, by the rule it always had, and this build finds the trip. A
// reading without a note here was typed with no trip open, or before that day.

private val READINGS = stringSetPreferencesKey("odometer_readings")
private val TRIPS_AT_READINGS = stringSetPreferencesKey("odometer_reading_trips")

private const val SEPARATOR = ':'
private const val VEHICLE_SEPARATOR = '|'

/** Every reading typed in, oldest first, each with the trip it was typed during, if any. */
internal fun Preferences.readOdometerReadings(): List<OdometerReading> {
    // Keyed by the time of the reading. Two notes for one time cannot both be right, and
    // neither is used: the reading then counts as one typed before 2026-10-10.
    val trips =
        this[TRIPS_AT_READINGS]
            .orEmpty()
            .mapNotNull(::tripNoteOf)
            .groupBy({ it.first }, { it.second })
            .mapNotNull { (atMs, found) -> found.singleOrNull()?.let { atMs to it } }
            .toMap()
    return this[READINGS]
        .orEmpty()
        .mapNotNull(::readingOf)
        .map { it.copy(duringTrip = trips[it.atMs]) }
        .sortedBy { it.atMs }
}

/**
 * Keeps [reading] beside every reading before it: a correction is a new reading, and the old
 * one stays, with its date ("each correction is kept with its date"). The trip it was typed
 * during is stored with it in the same write, so the two are there together or not at all.
 */
suspend fun SettingsStore.addOdometerReading(reading: OdometerReading) {
    require(reading.atMs >= 0) { "A timestamp cannot be negative: ${reading.atMs} ms" }
    require(reading.value in 0..MAX_ODOMETER_READING) {
        "Not an odometer reading: ${reading.value}"
    }
    dataStore.edit { stored ->
        stored[READINGS] = stored[READINGS].orEmpty() + entryOf(reading)
        val trip = tripNoteEntryOf(reading) ?: return@edit
        stored[TRIPS_AT_READINGS] = stored[TRIPS_AT_READINGS].orEmpty() + trip
    }
}

/**
 * The note of the trip a reading was typed during, or null for a reading typed with none open.
 * The metres are written as Kotlin writes a Double, so they come back to the last digit.
 */
internal fun tripNoteEntryOf(reading: OdometerReading): String? = reading.duringTrip?.let {
    "${reading.atMs}$SEPARATOR${it.tripId}$SEPARATOR${it.metres}"
}

private const val TRIP_NOTE_PARTS = 3

/** A stored note as the time of its reading and the trip, or null for one not of that form. */
private fun tripNoteOf(stored: String): Pair<Long, TripAtReading>? {
    val parts = stored.split(SEPARATOR)
    if (parts.size != TRIP_NOTE_PARTS) return null
    val atMs = parts[0].toLongOrNull() ?: return null
    val tripId = parts[1].toLongOrNull() ?: return null
    val metres = parts[2].toDoubleOrNull() ?: return null
    if (atMs < 0 || !metres.isFinite() || metres < 0.0) return null
    return atMs to TripAtReading(tripId, metres)
}

/**
 * The entry a reading is stored as. In kilometres and for no vehicle it is the entry MilO has
 * always written. The trip it was typed during is not in it ([tripNoteEntryOf]).
 */
internal fun entryOf(reading: OdometerReading): String {
    val timeAndValue = "${reading.atMs}$SEPARATOR${reading.value}"
    val figure =
        when (reading.unit) {
            DistanceUnit.KILOMETRES -> timeAndValue
            DistanceUnit.MILES -> "$timeAndValue$SEPARATOR${reading.unit.storedWord}"
        }
    val vehicle = reading.vehicle ?: return figure
    return "$figure$VEHICLE_SEPARATOR$vehicle"
}

private const val PARTS_WITHOUT_UNIT = 2
private const val PARTS_WITH_UNIT = 3

internal fun readingOf(stored: String): OdometerReading? {
    val entry = stored.substringBefore(VEHICLE_SEPARATOR)
    val vehicle = stored.substringAfter(VEHICLE_SEPARATOR, missingDelimiterValue = "")
    val parts = entry.split(SEPARATOR)
    val unit =
        when (parts.size) {
            PARTS_WITHOUT_UNIT -> DistanceUnit.KILOMETRES
            PARTS_WITH_UNIT -> distanceUnitOf(parts.last()) ?: return null
            else -> return null
        }
    val atMs = parts[0].toLongOrNull() ?: return null
    val value = parts[1].toLongOrNull() ?: return null
    if (atMs < 0 || value !in 0..MAX_ODOMETER_READING) return null
    return OdometerReading(atMs, value, unit, vehicle.ifBlank { null })
}

/**
 * Gives the readings typed before several vehicles (2026-10-08), which name none, the first
 * vehicle's [address]: removing that vehicle later must not hand them to the next one. Run
 * once, with the trips (`TripVehicleCatchUp`).
 */
suspend fun SettingsStore.fillOdometerVehicle(address: String) {
    dataStore.edit { stored ->
        val entries = stored[READINGS] ?: return@edit
        stored[READINGS] =
            entries
                .map { entry ->
                    val reading = readingOf(entry)
                    if (reading == null || reading.vehicle != null) {
                        entry
                    } else {
                        entryOf(reading.copy(vehicle = address))
                    }
                }.toSet()
    }
}

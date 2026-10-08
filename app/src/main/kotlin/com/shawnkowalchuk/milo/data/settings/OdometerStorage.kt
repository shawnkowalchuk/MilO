package com.shawnkowalchuk.milo.data.settings

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.shawnkowalchuk.milo.core.odometer.MAX_ODOMETER_READING
import com.shawnkowalchuk.milo.core.odometer.OdometerReading
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

private val READINGS = stringSetPreferencesKey("odometer_readings")

private const val SEPARATOR = ':'
private const val VEHICLE_SEPARATOR = '|'

/** Every reading typed in, oldest first. */
internal fun Preferences.readOdometerReadings(): List<OdometerReading> =
    this[READINGS].orEmpty().mapNotNull(::readingOf).sortedBy { it.atMs }

/**
 * Keeps [reading] beside every reading before it: a correction is a new reading, and the old
 * one stays, with its date ("each correction is kept with its date").
 */
suspend fun SettingsStore.addOdometerReading(reading: OdometerReading) {
    require(reading.atMs >= 0) { "A timestamp cannot be negative: ${reading.atMs} ms" }
    require(reading.value in 0..MAX_ODOMETER_READING) {
        "Not an odometer reading: ${reading.value}"
    }
    dataStore.edit { stored -> stored[READINGS] = stored[READINGS].orEmpty() + entryOf(reading) }
}

/**
 * The entry a reading is stored as. In kilometres and for no vehicle it is the entry MilO has
 * always written.
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

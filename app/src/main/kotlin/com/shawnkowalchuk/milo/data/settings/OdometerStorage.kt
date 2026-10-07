package com.shawnkowalchuk.milo.data.settings

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.shawnkowalchuk.milo.core.odometer.MAX_ODOMETER_KM
import com.shawnkowalchuk.milo.core.odometer.OdometerReading

// The odometer readings Shawn typed in (his decision of 2026-10-07: "I enter it, MilO adds
// trips"), kept in the settings file. They are few, a handful a year, and the settings file is
// backed up and restored with the rest by Android, so they need no table of their own: a table
// would have needed a new database version and its migration for a list this small.
//
// Each reading is one entry of a string set, "time:km", the time in wall-clock milliseconds.
// The set is read back sorted by time; an entry that is not of that form is skipped, never
// guessed at.

private val READINGS = stringSetPreferencesKey("odometer_readings")

private const val SEPARATOR = ':'

/** Every reading typed in, oldest first. */
internal fun Preferences.readOdometerReadings(): List<OdometerReading> =
    this[READINGS].orEmpty().mapNotNull(::readingOf).sortedBy { it.atMs }

/**
 * Keeps [reading] beside every reading before it: a correction is a new reading, and the old
 * one stays, with its date ("each correction is kept with its date").
 */
suspend fun SettingsStore.addOdometerReading(reading: OdometerReading) {
    require(reading.atMs >= 0) { "A timestamp cannot be negative: ${reading.atMs} ms" }
    require(reading.km in 0..MAX_ODOMETER_KM) { "Not an odometer reading: ${reading.km} km" }
    dataStore.edit { stored ->
        stored[READINGS] = stored[READINGS].orEmpty() + "${reading.atMs}$SEPARATOR${reading.km}"
    }
}

private fun readingOf(entry: String): OdometerReading? {
    val atMs = entry.substringBefore(SEPARATOR).toLongOrNull() ?: return null
    val km = entry.substringAfter(SEPARATOR, missingDelimiterValue = "").toLongOrNull()
    if (km == null || atMs < 0 || km !in 0..MAX_ODOMETER_KM) return null
    return OdometerReading(atMs, km)
}

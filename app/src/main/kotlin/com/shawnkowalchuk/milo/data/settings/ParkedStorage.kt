package com.shawnkowalchuk.milo.data.settings

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey

/**
 * A trip ends once the truck has not moved for 10 minutes, even with Bluetooth still connected:
 * the owner's decision of 2026-10-06, when the truck stayed connected for an hour after it was
 * parked at home.
 */
const val DEFAULT_PARKED_LIMIT_SECONDS = 600

/**
 * That MilO is waiting beside the truck: its trip was closed because it stood still, Bluetooth
 * is still connected, and the next trip starts when it moves. Not a setting Shawn chooses: like
 * the hold-off, it is a piece of the trip rules' state that must outlive the process, so that a
 * restart finds the wait again and not a new trip.
 *
 * @param sinceMs when the wait began. The limit on waiting is counted from it.
 * @param place where the truck is parked, or null if the trip before it had no usable GPS fix.
 */
data class ParkedTruck(val sinceMs: Long, val place: ParkedPlace?)

/**
 * Where the truck is parked: the last position of the trip that was closed there. The trip that
 * starts when the truck moves begins at this place. It is the same position that trip's row
 * holds as its end, or, for a trip that never moved and so has no end position, that of its
 * newest usable fix: either way nothing is stored here that storage did not hold already.
 *
 * @param atMs when the truck stopped there: the time of that trip's last movement, or of that
 * newest fix.
 * @param accuracyMetres the radius the phone was 68 % sure of, as on every stored fix.
 */
data class ParkedPlace(
    val atMs: Long,
    val latitude: Double,
    val longitude: Double,
    val accuracyMetres: Float,
)

// How the parked limit and the wait are kept in the settings file. The key names are what is
// written to the file; renaming one silently resets that value.

private val LIMIT_SECONDS = intPreferencesKey("parked_limit_seconds")
private val SINCE_MS = longPreferencesKey("parked_since_ms")
private val PLACE_AT_MS = longPreferencesKey("parked_place_at_ms")
private val PLACE_LATITUDE = doublePreferencesKey("parked_place_latitude")
private val PLACE_LONGITUDE = doublePreferencesKey("parked_place_longitude")
private val PLACE_ACCURACY_METRES = floatPreferencesKey("parked_place_accuracy_metres")
private val DRIVEN_OFF_TRIP_ID = longPreferencesKey("driven_off_trip_id")

/**
 * How long a trip may stand still before it is closed. A stored number that is not positive,
 * which no setter can write, reads as the default: with no limit a trip would never close.
 */
internal fun Preferences.readParkedLimitSeconds(): Int =
    this[LIMIT_SECONDS]?.takeIf { it > 0 } ?: DEFAULT_PARKED_LIMIT_SECONDS

/**
 * The stored wait, or null if MilO was not waiting. The place is written in one piece, so a
 * file that holds only part of it reads as a wait with no known place: the first GPS fix of the
 * wait then stands in for it.
 */
internal fun Preferences.readParkedTruck(): ParkedTruck? {
    val sinceMs = this[SINCE_MS]?.takeIf { it >= 0 } ?: return null
    val atMs = this[PLACE_AT_MS]
    val latitude = this[PLACE_LATITUDE]
    val longitude = this[PLACE_LONGITUDE]
    val accuracy = this[PLACE_ACCURACY_METRES]
    val whole = atMs != null && latitude != null && longitude != null && accuracy != null
    return ParkedTruck(
        sinceMs = sinceMs,
        place = if (whole) ParkedPlace(atMs, latitude, longitude, accuracy) else null,
    )
}

/**
 * The open trip that a parked truck's moving started, or null if the open trip (if any) started
 * any other way. Kept beside the wait because a restart of the process in the middle of that
 * trip must still know how it began: the trip is removed for good if it loses the truck within
 * its first kilometre (`leftInAnotherVehicle`), and nothing in its row says how it started.
 */
internal fun Preferences.readDrivenOffTripId(): Long? = this[DRIVEN_OFF_TRIP_ID]

/**
 * Stores [tripId] as the open trip that a parked truck's moving started, or with null that no
 * such trip is open. See [readDrivenOffTripId].
 */
suspend fun SettingsStore.setDrivenOffTripId(tripId: Long?) {
    dataStore.edit { stored ->
        if (tripId == null) {
            stored.forgetDrivenOffTrip()
        } else {
            stored[DRIVEN_OFF_TRIP_ID] = tripId
        }
    }
}

/** Takes the trip that a parked truck's moving started out of the file. */
internal fun MutablePreferences.forgetDrivenOffTrip() {
    remove(DRIVEN_OFF_TRIP_ID)
}

/** Stores how long a trip may stand still before it is closed. */
suspend fun SettingsStore.setParkedLimitSeconds(seconds: Int) {
    require(seconds > 0) { "The parked limit must be positive: $seconds s" }
    dataStore.edit { it[LIMIT_SECONDS] = seconds }
}

/** Stores that MilO is waiting beside the parked truck, or with null that it no longer is. */
suspend fun SettingsStore.setParkedTruck(parked: ParkedTruck?) {
    require(parked == null || parked.sinceMs >= 0) {
        "A timestamp cannot be negative: ${parked?.sinceMs} ms"
    }
    dataStore.edit { stored ->
        stored.forgetParkedTruck()
        if (parked == null) return@edit
        stored[SINCE_MS] = parked.sinceMs
        val place = parked.place ?: return@edit
        stored[PLACE_AT_MS] = place.atMs
        stored[PLACE_LATITUDE] = place.latitude
        stored[PLACE_LONGITUDE] = place.longitude
        stored[PLACE_ACCURACY_METRES] = place.accuracyMetres
    }
}

/** Takes the stored wait out of the file, place and all. */
internal fun MutablePreferences.forgetParkedTruck() {
    remove(SINCE_MS)
    remove(PLACE_AT_MS)
    remove(PLACE_LATITUDE)
    remove(PLACE_LONGITUDE)
    remove(PLACE_ACCURACY_METRES)
}

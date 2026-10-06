package com.shawnkowalchuk.milo.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.shawnkowalchuk.milo.core.schedule.WorkSchedule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** The file DataStore keeps the settings in, under the app's private files. */
const val SETTINGS_FILE_NAME = "settings"

/**
 * The only way the rest of the app reads or writes settings. They live in DataStore, not in a
 * database table: a handful of values with defaults, read far more often than written.
 *
 * A value that was never written reads as its default from [MiloSettings]. Every setter refuses
 * a value that cannot be right (a negative duration, distance or time; a blank address, name or
 * URI), because this is the last point before a bad value would be stored. The work schedule
 * needs no such check here: its type cannot hold a day that ends before it starts.
 *
 * @param dataStore created once by the `AppContainer`. DataStore allows only one instance per
 * file in a process.
 */
class SettingsStore(private val dataStore: DataStore<Preferences>) {
    /** The current settings, and again each time one changes. */
    val settings: Flow<MiloSettings> = dataStore.data.map(::toSettings)

    /** The settings as they are right now. */
    suspend fun current(): MiloSettings = settings.first()

    /**
     * Stores the truck picked at pairing. The three values always change together.
     *
     * @param name the truck's name as the phone shows it, or null if it has none. A blank name
     * is refused: pass null.
     */
    suspend fun setTruck(address: String, name: String?, associationId: Int?) {
        require(address.isNotBlank()) { "A truck needs a Bluetooth address" }
        require(name == null || name.isNotBlank()) { "A truck name is text, or null for none" }
        dataStore.edit { preferences ->
            preferences[TRUCK_ADDRESS] = address
            preferences.setOrRemove(TRUCK_NAME, name)
            preferences.setOrRemove(TRUCK_ASSOCIATION_ID, associationId)
        }
    }

    /** Forgets the truck, for example before pairing a different one. */
    suspend fun clearTruck() {
        dataStore.edit { preferences ->
            preferences.remove(TRUCK_ADDRESS)
            preferences.remove(TRUCK_NAME)
            preferences.remove(TRUCK_ASSOCIATION_ID)
        }
    }

    suspend fun setGracePeriodSeconds(seconds: Int) {
        require(seconds >= 0) { "The grace period cannot be negative: $seconds s" }
        dataStore.edit { it[GRACE_PERIOD_SECONDS] = seconds }
    }

    suspend fun setMinimumTripDistanceMetres(metres: Int) {
        require(metres >= 0) { "The minimum trip distance cannot be negative: $metres m" }
        dataStore.edit { it[MINIMUM_TRIP_DISTANCE_METRES] = metres }
    }

    suspend fun setSoundEnabled(enabled: Boolean) {
        dataStore.edit { it[SOUND_ENABLED] = enabled }
    }

    /**
     * Stores the sound Shawn chose. The two values always change together.
     *
     * @param uri where MilO's own copy of the audio file is.
     * @param name what the file he picked was called, or null if the phone gave none. A blank
     * name is refused: pass null.
     */
    suspend fun setCustomSound(uri: String, name: String?) {
        require(uri.isNotBlank()) { "A custom sound needs a URI" }
        require(name == null || name.isNotBlank()) { "A sound's name is text, or null for none" }
        dataStore.edit { preferences ->
            preferences[CUSTOM_SOUND_URI] = uri
            preferences.setOrRemove(CUSTOM_SOUND_NAME, name)
        }
    }

    /** Goes back to the bundled chirp. */
    suspend fun clearCustomSound() {
        dataStore.edit { preferences ->
            preferences.remove(CUSTOM_SOUND_URI)
            preferences.remove(CUSTOM_SOUND_NAME)
        }
    }

    /**
     * Stores the work schedule, all seven days in one write. A schedule cannot hold a day whose
     * hours end before they start (`DayHours` refuses to be built), so there is nothing left to
     * refuse here.
     */
    suspend fun setSchedule(schedule: WorkSchedule) {
        dataStore.edit { it.writeSchedule(schedule) }
    }

    /** True has a trip that started outside the schedule discarded; false saves it as Personal. */
    suspend fun setIgnoreTripsOutsideSchedule(ignore: Boolean) {
        dataStore.edit { it[IGNORE_TRIPS_OUTSIDE_SCHEDULE] = ignore }
    }

    /** Pass the time End was pressed to hold automatic start off, null to release it. */
    suspend fun setAutoStartHeldOffSinceMs(sinceMs: Long?) {
        require(sinceMs == null || sinceMs >= 0) { "A timestamp cannot be negative: $sinceMs ms" }
        dataStore.edit { it.setOrRemove(AUTO_START_HELD_OFF_SINCE_MS, sinceMs) }
    }

    suspend fun setLastProcessExitImportedAtMs(timestampMs: Long) {
        require(timestampMs >= 0) { "A timestamp cannot be negative: $timestampMs ms" }
        dataStore.edit { it[LAST_PROCESS_EXIT_IMPORTED_AT_MS] = timestampMs }
    }

    /**
     * Stores that Shawn confirmed a setup step MilO cannot read, and when. Pass null to take the
     * confirmation back.
     */
    suspend fun setConfirmedAtMs(step: ConfirmedStep, atMs: Long?) {
        require(atMs == null || atMs >= 0) { "A timestamp cannot be negative: $atMs ms" }
        dataStore.edit { it.setOrRemove(longPreferencesKey(step.key), atMs) }
    }

    private fun toSettings(preferences: Preferences): MiloSettings {
        val defaults = MiloSettings()
        return MiloSettings(
            truckAddress = preferences[TRUCK_ADDRESS],
            truckName = preferences[TRUCK_NAME],
            truckAssociationId = preferences[TRUCK_ASSOCIATION_ID],
            gracePeriodSeconds = preferences[GRACE_PERIOD_SECONDS] ?: defaults.gracePeriodSeconds,
            minimumTripDistanceMetres =
                preferences[MINIMUM_TRIP_DISTANCE_METRES] ?: defaults.minimumTripDistanceMetres,
            soundEnabled = preferences[SOUND_ENABLED] ?: defaults.soundEnabled,
            customSoundUri = preferences[CUSTOM_SOUND_URI],
            customSoundName = preferences[CUSTOM_SOUND_NAME],
            schedule = preferences.readSchedule(),
            ignoreTripsOutsideSchedule =
                preferences[IGNORE_TRIPS_OUTSIDE_SCHEDULE] ?: defaults.ignoreTripsOutsideSchedule,
            autoStartHeldOffSinceMs = preferences[AUTO_START_HELD_OFF_SINCE_MS],
            lastProcessExitImportedAtMs =
                preferences[LAST_PROCESS_EXIT_IMPORTED_AT_MS]
                    ?: defaults.lastProcessExitImportedAtMs,
            confirmedAtMs =
                buildMap {
                    for (step in ConfirmedStep.entries) {
                        preferences[longPreferencesKey(step.key)]?.let { put(step, it) }
                    }
                },
        )
    }

    // The key names are what is written to the file. Renaming one silently resets that setting.
    // The keys of the confirmed setup steps are on ConfirmedStep itself, and the keys of the
    // work schedule, three for each day, are in ScheduleStorage.kt.
    private companion object {
        val TRUCK_ADDRESS = stringPreferencesKey("truck_address")
        val TRUCK_NAME = stringPreferencesKey("truck_name")
        val TRUCK_ASSOCIATION_ID = intPreferencesKey("truck_association_id")
        val GRACE_PERIOD_SECONDS = intPreferencesKey("grace_period_seconds")
        val MINIMUM_TRIP_DISTANCE_METRES = intPreferencesKey("minimum_trip_distance_metres")
        val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
        val CUSTOM_SOUND_URI = stringPreferencesKey("custom_sound_uri")
        val CUSTOM_SOUND_NAME = stringPreferencesKey("custom_sound_name")
        val IGNORE_TRIPS_OUTSIDE_SCHEDULE =
            booleanPreferencesKey("ignore_trips_outside_schedule")
        val AUTO_START_HELD_OFF_SINCE_MS = longPreferencesKey("auto_start_held_off_since_ms")
        val LAST_PROCESS_EXIT_IMPORTED_AT_MS =
            longPreferencesKey("last_process_exit_imported_at_ms")
    }
}

/**
 * Builds the settings store on its DataStore file. Called once, by the `AppContainer`.
 *
 * There is deliberately no corruption handler: the usual one replaces an unreadable file with
 * empty settings, which would drop the truck pairing without a trace. An unreadable file makes
 * every read throw instead, and each reader decides what to do about it.
 */
fun buildSettingsStore(context: Context): SettingsStore = SettingsStore(
    PreferenceDataStoreFactory.create(
        produceFile = { context.preferencesDataStoreFile(SETTINGS_FILE_NAME) },
    ),
)

/** Preferences cannot hold null: "no value" is stored by removing the key. */
private fun <T> MutablePreferences.setOrRemove(key: Preferences.Key<T>, value: T?) {
    if (value == null) remove(key) else this[key] = value
}

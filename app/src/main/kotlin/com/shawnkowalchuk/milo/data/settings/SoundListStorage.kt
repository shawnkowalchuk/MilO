package com.shawnkowalchuk.milo.data.settings

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey

// The sounds of Shawn's own that he has added (his choice of 2026-10-07: "A list of my own
// sounds"), and which of them each of the two sounds plays (since 2026-10-08, [TripSound]). Which
// one plays is a pair of values for each sound (`customSoundUri` and `customSoundName` for the
// connect sound, the first sound MilO had; the two `drivingOffSound…` values for the trip-start
// sound); the one list beside them is what both choose from. Each entry is MilO's copy of a file
// he picked (`data/sound/OwnSoundStore`), as "address<TAB>name", the name empty when the phone
// gave none.

private val OWN_SOUNDS = stringSetPreferencesKey("own_sounds")

private const val SEPARATOR = '\t'

/**
 * The two moments MilO plays a sound at (Shawn's request of 2026-10-08: "i want to use the
 * current one for connecting to a vehicle and another custom one when a trip starts"). Each has
 * its own switch and its own choice: the built-in sound, or one from the one list of his own.
 */
enum class TripSound {
    /**
     * A trip has really begun because the truck connected, or because Start was pressed
     * (`TripTransition.tripReallyBegan`): the sound MilO has played since its first day, kept
     * under the settings it always had. "Connect sound" on the screen.
     */
    CONNECT,

    /**
     * The open trip's first fix at 15 km/h or more (`TripProgress.drivenAtMs`): the truck drives
     * off. "Trip-start sound" on the screen.
     */
    DRIVING_OFF,
}

/**
 * One of the two sounds as the settings have it.
 *
 * @param enabled whether it plays.
 * @param ownUri MilO's copy of the sound of his own it plays, or null for the built-in sound.
 * @param ownName what the file he picked was called, or null if the phone gave no name.
 */
data class SoundChoice(val enabled: Boolean, val ownUri: String?, val ownName: String?)

/** One of the two sounds, as these settings have it. */
fun MiloSettings.sound(which: TripSound): SoundChoice = when (which) {
    TripSound.CONNECT -> SoundChoice(soundEnabled, customSoundUri, customSoundName)

    TripSound.DRIVING_OFF ->
        SoundChoice(drivingOffSoundEnabled, drivingOffSoundUri, drivingOffSoundName)
}

/** Where the switch of [this] sound is kept. */
internal val TripSound.enabledKey: Preferences.Key<Boolean>
    get() = when (this) {
        TripSound.CONNECT -> SettingsStore.SOUND_ENABLED
        TripSound.DRIVING_OFF -> SettingsStore.DRIVING_OFF_SOUND_ENABLED
    }

/** Where the copy [this] sound plays is kept, while it plays one of his own. */
internal val TripSound.uriKey: Preferences.Key<String>
    get() = when (this) {
        TripSound.CONNECT -> SettingsStore.CUSTOM_SOUND_URI
        TripSound.DRIVING_OFF -> SettingsStore.DRIVING_OFF_SOUND_URI
    }

/** And the name of the file that copy was made from. */
internal val TripSound.nameKey: Preferences.Key<String>
    get() = when (this) {
        TripSound.CONNECT -> SettingsStore.CUSTOM_SOUND_NAME
        TripSound.DRIVING_OFF -> SettingsStore.DRIVING_OFF_SOUND_NAME
    }

/**
 * One sound of Shawn's own that he can choose for either sound.
 *
 * @param uri MilO's copy of the file, never the file he picked.
 * @param name what the file he picked was called, or null if the phone gave no name.
 */
data class OwnSound(val uri: String, val name: String?)

/**
 * Every sound of his own, in the order they were added (the copies are named by the moment
 * they were made). The sounds in use are always among them, also one chosen for the connect
 * sound before there was a list, so nothing has to be converted.
 */
internal fun Preferences.readOwnSounds(): List<OwnSound> {
    val listed = this[OWN_SOUNDS].orEmpty().mapNotNull(::ownSoundOf)
    val chosenBefore =
        TripSound.entries
            .mapNotNull { which -> this[which.uriKey]?.let { OwnSound(it, this[which.nameKey]) } }
            .filter { inUse -> listed.none { it.uri == inUse.uri } }
            .distinctBy { it.uri }
    return (listed + chosenBefore).sortedBy { it.uri }
}

/** Adds the copy at [uri] to the list, and makes it the sound [which] plays. */
suspend fun SettingsStore.addOwnSound(which: TripSound, uri: String, name: String?) {
    require(uri.isNotBlank() && SEPARATOR !in uri) { "Not the address of a copy: $uri" }
    require(name == null || name.isNotBlank()) { "A sound's name is text, or null for none" }
    dataStore.edit { stored ->
        val kept = stored.readOwnSounds().filter { it.uri != uri }
        stored.writeOwnSounds(kept + OwnSound(uri, name))
        stored.useOwnSound(which, uri, name)
    }
}

/**
 * Makes the sound at [uri], one of the list, the one [which] plays.
 *
 * @return false, and nothing changes, if it is not in the list (removed meanwhile).
 */
suspend fun SettingsStore.chooseOwnSound(which: TripSound, uri: String): Boolean {
    var found = false
    dataStore.edit { stored ->
        val sound = stored.readOwnSounds().firstOrNull { it.uri == uri } ?: return@edit
        stored.useOwnSound(which, sound.uri, sound.name)
        found = true
    }
    return found
}

/**
 * Takes the sound at [uri] off the list. Each of the two sounds that played it plays its
 * built-in sound from then on.
 */
suspend fun SettingsStore.removeOwnSound(uri: String) {
    dataStore.edit { stored ->
        stored.writeOwnSounds(stored.readOwnSounds().filter { it.uri != uri })
        for (which in TripSound.entries) {
            if (stored[which.uriKey] == uri) {
                stored.remove(which.uriKey)
                stored.remove(which.nameKey)
            }
        }
    }
}

/** Takes every sound of his own out of the file: their copies are not on this phone. */
internal fun MutablePreferences.forgetOwnSounds() {
    remove(OWN_SOUNDS)
    for (which in TripSound.entries) {
        remove(which.uriKey)
        remove(which.nameKey)
    }
}

private fun MutablePreferences.writeOwnSounds(sounds: List<OwnSound>) {
    this[OWN_SOUNDS] = sounds.map { "${it.uri}$SEPARATOR${it.name.orEmpty()}" }.toSet()
}

private fun MutablePreferences.useOwnSound(which: TripSound, uri: String, name: String?) {
    this[which.uriKey] = uri
    setOrRemove(which.nameKey, name)
}

private fun ownSoundOf(entry: String): OwnSound? {
    val uri = entry.substringBefore(SEPARATOR)
    if (uri.isBlank() || SEPARATOR !in entry) return null
    return OwnSound(uri, entry.substringAfter(SEPARATOR).ifBlank { null })
}

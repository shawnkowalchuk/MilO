package com.shawnkowalchuk.milo.data.settings

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey

// The trip-start sounds of Shawn's own that he has added (his choice of 2026-10-07: "A list of
// my own sounds"), and which of them plays. Which one plays is still the pair of values every
// trip start reads (`customSoundUri`, `customSoundName`); the list beside it is what he can
// choose from. Each entry is MilO's copy of a file he picked (`data/sound/OwnSoundStore`), as
// "address<TAB>name", the name empty when the phone gave none.

private val OWN_SOUNDS = stringSetPreferencesKey("own_sounds")

private const val SEPARATOR = '\t'

/**
 * One sound of Shawn's own that he can choose for a trip start.
 *
 * @param uri MilO's copy of the file, never the file he picked.
 * @param name what the file he picked was called, or null if the phone gave no name.
 */
data class OwnSound(val uri: String, val name: String?)

/**
 * Every sound of his own, in the order they were added (the copies are named by the moment
 * they were made). The sound in use is always among them, also one chosen before there was a
 * list, so nothing has to be converted.
 */
internal fun Preferences.readOwnSounds(): List<OwnSound> {
    val listed = this[OWN_SOUNDS].orEmpty().mapNotNull(::ownSoundOf)
    val inUse = this[SettingsStore.CUSTOM_SOUND_URI]
    val chosenBefore =
        inUse?.takeIf { uri -> listed.none { it.uri == uri } }?.let {
            OwnSound(it, this[SettingsStore.CUSTOM_SOUND_NAME])
        }
    return (listed + listOfNotNull(chosenBefore)).sortedBy { it.uri }
}

/** Adds the copy at [uri] to the list, and makes it the sound a trip start plays. */
suspend fun SettingsStore.addOwnSound(uri: String, name: String?) {
    require(uri.isNotBlank() && SEPARATOR !in uri) { "Not the address of a copy: $uri" }
    require(name == null || name.isNotBlank()) { "A sound's name is text, or null for none" }
    dataStore.edit { stored ->
        val kept = stored.readOwnSounds().filter { it.uri != uri }
        stored.writeOwnSounds(kept + OwnSound(uri, name))
        stored.useOwnSound(uri, name)
    }
}

/**
 * Makes the sound at [uri], one of the list, the one a trip start plays.
 *
 * @return false, and nothing changes, if it is not in the list (removed meanwhile).
 */
suspend fun SettingsStore.chooseOwnSound(uri: String): Boolean {
    var found = false
    dataStore.edit { stored ->
        val sound = stored.readOwnSounds().firstOrNull { it.uri == uri } ?: return@edit
        stored.useOwnSound(sound.uri, sound.name)
        found = true
    }
    return found
}

/**
 * Takes the sound at [uri] off the list. If it was the one a trip start plays, the built-in
 * chirp plays from then on.
 */
suspend fun SettingsStore.removeOwnSound(uri: String) {
    dataStore.edit { stored ->
        stored.writeOwnSounds(stored.readOwnSounds().filter { it.uri != uri })
        if (stored[SettingsStore.CUSTOM_SOUND_URI] == uri) {
            stored.remove(SettingsStore.CUSTOM_SOUND_URI)
            stored.remove(SettingsStore.CUSTOM_SOUND_NAME)
        }
    }
}

/** Takes every sound of his own out of the file: their copies are not on this phone. */
internal fun MutablePreferences.forgetOwnSounds() {
    remove(OWN_SOUNDS)
    remove(SettingsStore.CUSTOM_SOUND_URI)
    remove(SettingsStore.CUSTOM_SOUND_NAME)
}

private fun MutablePreferences.writeOwnSounds(sounds: List<OwnSound>) {
    this[OWN_SOUNDS] = sounds.map { "${it.uri}$SEPARATOR${it.name.orEmpty()}" }.toSet()
}

private fun MutablePreferences.useOwnSound(uri: String, name: String?) {
    this[SettingsStore.CUSTOM_SOUND_URI] = uri
    if (name == null) {
        remove(SettingsStore.CUSTOM_SOUND_NAME)
    } else {
        this[SettingsStore.CUSTOM_SOUND_NAME] = name
    }
}

private fun ownSoundOf(entry: String): OwnSound? {
    val uri = entry.substringBefore(SEPARATOR)
    if (uri.isBlank() || SEPARATOR !in entry) return null
    return OwnSound(uri, entry.substringAfter(SEPARATOR).ifBlank { null })
}

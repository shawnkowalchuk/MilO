package com.shawnkowalchuk.milo.data.settings

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey

// Which version's list of changes this phone has been shown (since 2026-10-08): the What's new
// screen opens once by itself after an update, like GopherForms' "What's new" after a new
// version. Not in an export file: it is this phone's. Android's backup carries it with the rest
// of the settings file.

private val WHATS_NEW_SEEN_VERSION = stringPreferencesKey("whats_new_seen_version")

/** The versionName whose changes were last shown, or null if none ever were. */
internal fun Preferences.readWhatsNewSeenVersion(): String? = this[WHATS_NEW_SEEN_VERSION]

/** Stores that the changes up to [version] (a versionName) have been shown. */
suspend fun SettingsStore.setWhatsNewSeenVersion(version: String) {
    dataStore.edit { it[WHATS_NEW_SEEN_VERSION] = version }
}

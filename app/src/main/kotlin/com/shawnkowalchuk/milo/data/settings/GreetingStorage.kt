package com.shawnkowalchuk.milo.data.settings

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey

// The greeting's safety catch (2026-10-09, ADR-005). The mascot is drawn by native code, and
// what goes wrong in native code ends the whole app with nothing for Kotlin to catch. MilO
// greets when it is opened, so a phone on which the drawing fails would lose MilO at every
// start. So each greeting is counted before the drawing begins, and the count is cleared once
// the mascot has been seen: a count that stays tells the next start that the last one died.

private val GREETINGS_UNFINISHED = intPreferencesKey("greetings_unfinished")

/** How many greetings in a row began and never got as far as showing the mascot. */
internal fun Preferences.readGreetingsUnfinished(): Int = this[GREETINGS_UNFINISHED] ?: 0

/** Counts one more greeting as begun. Must be stored before the mascot's drawing starts. */
suspend fun SettingsStore.countGreetingBegun() {
    dataStore.edit { stored -> stored[GREETINGS_UNFINISHED] = stored.readGreetingsUnfinished() + 1 }
}

/** The mascot was seen: the drawing works on this phone, and the count starts again. */
suspend fun SettingsStore.clearGreetingsUnfinished() {
    dataStore.edit { stored -> stored.remove(GREETINGS_UNFINISHED) }
}

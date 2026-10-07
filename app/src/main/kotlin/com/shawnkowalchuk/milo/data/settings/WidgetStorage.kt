package com.shawnkowalchuk.milo.data.settings

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit

// The switch of the home-screen widget (Shawn's decision of 2026-10-07: the switch is for "the
// whole widget"). On out of the box: he asked for the widget, and with the switch on it is only
// offered in the phone's list of widgets; nothing is on his home screen until he puts it there.

private val HOME_WIDGET_ENABLED = booleanPreferencesKey("home_widget_enabled")

/** Whether MilO's widget can be put on the home screen. On unless switched off in Settings. */
internal fun Preferences.readHomeWidgetEnabled(): Boolean = this[HOME_WIDGET_ENABLED] ?: true

/** Stores the widget's switch. The caller then offers or withdraws the widget itself. */
suspend fun SettingsStore.setHomeWidgetEnabled(enabled: Boolean) {
    dataStore.edit { it[HOME_WIDGET_ENABLED] = enabled }
}

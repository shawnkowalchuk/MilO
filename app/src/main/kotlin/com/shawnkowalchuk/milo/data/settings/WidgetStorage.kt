package com.shawnkowalchuk.milo.data.settings

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.shawnkowalchuk.milo.core.allowance.DEFAULT_CENTS_PER_KM
import com.shawnkowalchuk.milo.core.allowance.MAX_CENTS_PER_KM
import com.shawnkowalchuk.milo.core.allowance.MIN_CENTS_PER_KM

// The switch of the home-screen widget (Shawn's decision of 2026-10-07: the switch is for "the
// whole widget"). On out of the box: he asked for the widget, and with the switch on it is only
// offered in the phone's list of widgets; nothing is on his home screen until he puts it there.
//
// And the rate its dollars are priced at, in cents a kilometre (Shawn's choice of the same day:
// "Settings, starts at 0.70"). Not stored until one is set, so 70¢ is read until then.

private val HOME_WIDGET_ENABLED = booleanPreferencesKey("home_widget_enabled")
private val HOME_WIDGET_CENTS_PER_KM = intPreferencesKey("home_widget_cents_per_km")

/** Whether MilO's widget can be put on the home screen. On unless switched off in Settings. */
internal fun Preferences.readHomeWidgetEnabled(): Boolean = this[HOME_WIDGET_ENABLED] ?: true

/** Stores the widget's switch. The caller then offers or withdraws the widget itself. */
suspend fun SettingsStore.setHomeWidgetEnabled(enabled: Boolean) {
    dataStore.edit { it[HOME_WIDGET_ENABLED] = enabled }
}

/**
 * The widget's rate in cents a kilometre: the one set in Settings, or 70¢ until one is. A stored
 * value MilO could not have written (outside the range) is read as 70¢, never used.
 */
internal fun Preferences.readHomeWidgetCentsPerKm(): Int =
    this[HOME_WIDGET_CENTS_PER_KM]?.takeIf { it in MIN_CENTS_PER_KM..MAX_CENTS_PER_KM }
        ?: DEFAULT_CENTS_PER_KM

/** Stores the widget's rate. The widget follows the settings and is drawn again by itself. */
suspend fun SettingsStore.setHomeWidgetCentsPerKm(centsPerKm: Int) {
    require(centsPerKm in MIN_CENTS_PER_KM..MAX_CENTS_PER_KM) {
        "Not a rate: $centsPerKm cents a km"
    }
    dataStore.edit { it[HOME_WIDGET_CENTS_PER_KM] = centsPerKm }
}

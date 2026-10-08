package com.shawnkowalchuk.milo.data.settings

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey

// How far the first start has got (Shawn's request of 2026-10-08: "when the app starts for the
// first time i want a onboarding. What the app does and how it works. than a ok. than go to the
// setup screen ... once finished please send them to the settings screen."). Nothing is stored
// until OK is pressed, so a phone that has had MilO for a while sees it once too, after the
// update that brings it (Shawn's choice: "Yes, once").

/** Where the first start stands, in the order it is gone through. */
enum class FirstRunStage(internal val stored: String?) {
    /** The page that says what MilO does has not been left with OK yet. */
    INTRO(null),

    /** OK was pressed. Setup has its Done button until Done is pressed. */
    SETUP("setup"),

    /** Done was pressed on Setup. Nothing of the first start shows again. */
    DONE("done"),
}

private val FIRST_RUN_STAGE = stringPreferencesKey("first_run_stage")

/**
 * The stage stored, [FirstRunStage.INTRO] while none is. A value this build does not know, which
 * only a newer build can have written, is read as [FirstRunStage.DONE]: whoever got past the
 * first start there is not sent through it again here.
 */
internal fun Preferences.readFirstRunStage(): FirstRunStage {
    val stored = this[FIRST_RUN_STAGE] ?: return FirstRunStage.INTRO
    return FirstRunStage.entries.firstOrNull { it.stored == stored } ?: FirstRunStage.DONE
}

/** Stores [stage]; [FirstRunStage.INTRO] removes what was stored. */
suspend fun SettingsStore.setFirstRunStage(stage: FirstRunStage) {
    dataStore.edit { stored ->
        val value = stage.stored
        if (value == null) stored.remove(FIRST_RUN_STAGE) else stored[FIRST_RUN_STAGE] = value
    }
}

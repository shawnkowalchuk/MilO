package com.shawnkowalchuk.milo.feature.settings

import com.shawnkowalchuk.milo.data.settings.GRACE_PERIOD_CHOICE
import com.shawnkowalchuk.milo.data.settings.MINIMUM_TRIP_DISTANCE_CHOICE
import com.shawnkowalchuk.milo.data.settings.MiloSettings
import com.shawnkowalchuk.milo.platform.trip.OwnSoundRefusal

// What the Settings screen shows, and the function that decides it from the stored settings.
// Pure, so it is tested without a phone.

/** Something the screen has to tell Shawn about a press that did not do what it said. */
enum class SettingsProblem {
    /** The picked file could not be read or copied. */
    SOUND_COULD_NOT_COPY,

    /** The picked file is larger than MilO copies. */
    SOUND_TOO_LARGE,

    /** The picked file was copied, but Android cannot play it. */
    SOUND_NOT_PLAYABLE,

    /** The phone has no app that can show a file picker. */
    NO_FILE_PICKER,

    /** The settings file could not be written or read: the change was not made. */
    COULD_NOT_SAVE,
}

/** What the Settings screen shows. */
sealed interface SettingsUiState {
    /** The settings have not been read yet. */
    data object Reading : SettingsUiState

    /** The settings file cannot be read, so there is nothing true to show or to change. */
    data object Unreadable : SettingsUiState

    /**
     * The settings as stored.
     *
     * @param truckPaired false while no truck is stored.
     * @param truckName the truck's name as the phone gave it, or null if it has none.
     * @param canShortenGrace false at the lower end of the range, and so for the other three:
     * the button is greyed out there.
     * @param usesOwnSound true if a trip start plays the file Shawn chose, false for the
     * built-in sound.
     * @param ownSoundName what that file was called, or null if the phone gave no name.
     * @param copyingSound true while a picked file is being copied and checked. The sound
     * buttons wait.
     * @param problem the last press that did not work, until the next one.
     */
    data class Ready(
        val truckPaired: Boolean,
        val truckName: String?,
        val gracePeriodSeconds: Int,
        val canShortenGrace: Boolean,
        val canLengthenGrace: Boolean,
        val minimumDistanceMetres: Int,
        val canLowerMinimum: Boolean,
        val canRaiseMinimum: Boolean,
        val soundEnabled: Boolean,
        val usesOwnSound: Boolean,
        val ownSoundName: String?,
        val copyingSound: Boolean,
        val problem: SettingsProblem?,
    ) : SettingsUiState
}

/**
 * The screen for the settings as they are stored. The two figures are shown as stored, even if
 * a value is outside what the screen offers; the first press of a button brings it inside.
 */
fun settingsUiState(
    settings: MiloSettings,
    copyingSound: Boolean,
    problem: SettingsProblem?,
): SettingsUiState.Ready = SettingsUiState.Ready(
    truckPaired = settings.truckAddress != null,
    truckName = settings.truckName,
    gracePeriodSeconds = settings.gracePeriodSeconds,
    canShortenGrace = GRACE_PERIOD_CHOICE.canStepDown(settings.gracePeriodSeconds),
    canLengthenGrace = GRACE_PERIOD_CHOICE.canStepUp(settings.gracePeriodSeconds),
    minimumDistanceMetres = settings.minimumTripDistanceMetres,
    canLowerMinimum = MINIMUM_TRIP_DISTANCE_CHOICE.canStepDown(settings.minimumTripDistanceMetres),
    canRaiseMinimum = MINIMUM_TRIP_DISTANCE_CHOICE.canStepUp(settings.minimumTripDistanceMetres),
    soundEnabled = settings.soundEnabled,
    usesOwnSound = settings.customSoundUri != null,
    ownSoundName = settings.customSoundName,
    copyingSound = copyingSound,
    problem = problem,
)

/** What the screen says when a picked file was refused. */
fun OwnSoundRefusal.asProblem(): SettingsProblem = when (this) {
    OwnSoundRefusal.COULD_NOT_COPY -> SettingsProblem.SOUND_COULD_NOT_COPY
    OwnSoundRefusal.TOO_LARGE -> SettingsProblem.SOUND_TOO_LARGE
    OwnSoundRefusal.NOT_PLAYABLE -> SettingsProblem.SOUND_NOT_PLAYABLE
}

package com.shawnkowalchuk.milo.feature.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.component.ActionButton
import com.shawnkowalchuk.milo.core.designsystem.component.ActionButtonRow
import com.shawnkowalchuk.milo.core.designsystem.component.ActionKind
import com.shawnkowalchuk.milo.core.designsystem.component.ChoiceRow
import com.shawnkowalchuk.milo.core.designsystem.component.MiloIcons
import com.shawnkowalchuk.milo.core.designsystem.component.RowStatus
import com.shawnkowalchuk.milo.core.designsystem.component.StatusRow
import com.shawnkowalchuk.milo.core.designsystem.component.Tile
import com.shawnkowalchuk.milo.core.designsystem.component.TilePadding
import com.shawnkowalchuk.milo.core.designsystem.component.TitledSwitchRow
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme
import com.shawnkowalchuk.milo.data.settings.SoundChoice
import com.shawnkowalchuk.milo.data.settings.TripSound
import com.shawnkowalchuk.milo.data.sound.MAX_OWN_SOUND_BYTES

private const val BYTES_PER_MEGABYTE = 1024 * 1024

/**
 * One of the two sounds ([TripSound], since 2026-10-08): the connect sound, laid out as the
 * design draws the trip-start sound it used to be, and the trip-start sound under it, laid out
 * the same. The title with the sound in use under it and the switch, all of which toggle it;
 * under them "Play" and "Add a sound" side by side.
 *
 * What the design does not draw and MilO has: a grey line on when the sound plays; the sounds
 * to choose from (since 2026-10-07, Shawn's choice "A list of my own sounds"), one row each, the
 * built-in sound first and his own after it in the order they were added, of which the one in
 * use is marked. The list is the same in both tiles: a file added on one can be chosen on the
 * other. While one of his own is in use, a button under the two takes it off the list, for both
 * sounds. A picked file that was refused is said in red, with its reason, on the tile it was
 * picked on; and the last tile says in one grey line that the sounds play like an alarm, which
 * is why they are heard when the phone is silent.
 */
@Composable
internal fun SoundTile(state: SettingsUiState.Ready, actions: SettingsActions, which: TripSound) {
    val sound = state.sound(which)
    // While a picked file is being copied the buttons wait: the answer decides what "the sound
    // in use" is, and what the list holds.
    val idle = !state.copyingSound
    val pickedHere = state.pickedFor == which
    Tile(
        modifier = Modifier.fillMaxWidth(),
        padding = TilePadding.EVEN,
        gap = MiloTheme.spacing.rowGap,
    ) {
        TitledSwitchRow(
            title = stringResource(which.title),
            line = soundInUse(sound, which),
            checked = sound.enabled,
            onCheckedChange = { actions.onSoundEnabled(which, it) },
        )
        Note(stringResource(which.whenItPlays))
        if (state.ownSounds.isNotEmpty()) {
            SoundChoices(state, which, { actions.onChooseSound(which, it) }, idle)
        }
        Column(verticalArrangement = Arrangement.spacedBy(MiloTheme.spacing.buttonGap)) {
            ActionButtonRow(
                actions =
                    listOf(
                        ActionButton(
                            label = stringResource(R.string.settings_sound_play),
                            kind = ActionKind.PLAIN,
                            onClick = { actions.onPlaySound(which) },
                            icon = MiloIcons.Play,
                            enabled = idle,
                        ),
                        ActionButton(
                            label = stringResource(R.string.settings_sound_add),
                            kind = ActionKind.PLAIN,
                            onClick = { actions.onPickOwnSound(which) },
                            enabled = idle,
                        ),
                    ),
            )
            val inUse = sound.ownUri
            if (inUse != null) {
                ActionButtonRow(
                    actions =
                        listOf(
                            ActionButton(
                                label = stringResource(R.string.settings_sound_remove),
                                kind = ActionKind.PLAIN,
                                onClick = { actions.onRemoveSound(inUse) },
                                enabled = idle,
                            ),
                        ),
                )
            }
        }
        if (state.copyingSound && pickedHere) Note(stringResource(R.string.settings_sound_copying))
        state.problem?.takeIf { pickedHere }?.soundText()?.let {
            StatusRow(label = it, status = RowStatus.PROBLEM)
        }
        // Once, on the last of the two tiles: it is true of both.
        if (which == TripSound.entries.last()) {
            Note(stringResource(R.string.settings_sound_alarm_note))
        }
    }
}

/** The tile's title. */
@get:StringRes
private val TripSound.title: Int
    get() = when (this) {
        TripSound.CONNECT -> R.string.settings_sound_title
        TripSound.DRIVING_OFF -> R.string.settings_trip_sound_title
    }

/** The grey line on when the sound plays. */
@get:StringRes
private val TripSound.whenItPlays: Int
    get() = when (this) {
        TripSound.CONNECT -> R.string.settings_sound_when
        TripSound.DRIVING_OFF -> R.string.settings_trip_sound_when
    }

/** What the built-in sound is called. */
@get:StringRes
private val TripSound.builtIn: Int
    get() = when (this) {
        TripSound.CONNECT -> R.string.settings_sound_line_built_in
        TripSound.DRIVING_OFF -> R.string.settings_trip_sound_line_built_in
    }

/**
 * One row for each sound there is to choose from: the built-in sound, then each of his own.
 * Shown once he has added one; before that the built-in sound is all there is.
 */
@Composable
private fun SoundChoices(
    state: SettingsUiState.Ready,
    which: TripSound,
    onChoose: (ownSoundUri: String?) -> Unit,
    idle: Boolean,
) {
    val inUse = state.sound(which).ownUri
    Column(modifier = Modifier.selectableGroup()) {
        ChoiceRow(
            label = stringResource(which.builtIn),
            selected = inUse == null,
            onSelect = { if (idle) onChoose(null) },
        )
        for (own in state.ownSounds) {
            ChoiceRow(
                label = own.name ?: stringResource(R.string.settings_sound_line_own),
                selected = own.uri == inUse,
                onSelect = { if (idle) onChoose(own.uri) },
            )
        }
    }
}

/** The line under the title: which sound plays. */
@Composable
private fun soundInUse(sound: SoundChoice, which: TripSound): String = when {
    sound.ownUri == null -> stringResource(which.builtIn)
    else -> sound.ownName ?: stringResource(R.string.settings_sound_line_own)
}

/** The sentence for a problem with the sound, or null for one that is said elsewhere. */
@Composable
private fun SettingsProblem.soundText(): String? = when (this) {
    SettingsProblem.SOUND_COULD_NOT_COPY -> stringResource(R.string.settings_sound_refused_copy)

    SettingsProblem.SOUND_TOO_LARGE ->
        stringResource(
            R.string.settings_sound_refused_large,
            MAX_OWN_SOUND_BYTES / BYTES_PER_MEGABYTE,
        )

    SettingsProblem.SOUND_NOT_PLAYABLE ->
        stringResource(R.string.settings_sound_refused_playable)

    SettingsProblem.NO_FILE_PICKER -> stringResource(R.string.settings_sound_no_picker)

    // Said at the top of the screen: it can be about any of the tiles.
    SettingsProblem.COULD_NOT_SAVE -> null

    // Said in the work schedule's tile, under the hours whose time was refused.
    SettingsProblem.HOURS_END_NOT_AFTER_START -> null
}

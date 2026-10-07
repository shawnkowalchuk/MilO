package com.shawnkowalchuk.milo.feature.settings

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
import com.shawnkowalchuk.milo.data.sound.MAX_OWN_SOUND_BYTES

private const val BYTES_PER_MEGABYTE = 1024 * 1024

/**
 * The trip-start sound, as the design draws it: the title with the sound in use under it and
 * the switch, all of which toggle it; under them "Play" and "Add a sound" side by side.
 *
 * What the design does not draw and MilO has: the sounds to choose from (since 2026-10-07,
 * Shawn's choice "A list of my own sounds"), one row each, the built-in chirp first and his own
 * after it in the order they were added, of which the one in use is marked; while one of his
 * own is in use, a button under the two that takes it off the list; a picked file that was
 * refused is said in red, with its reason; and one grey line says that the sound plays like an
 * alarm, which is why it is heard when the phone is silent.
 */
@Composable
internal fun SoundTile(state: SettingsUiState.Ready, actions: SettingsActions) {
    // While a picked file is being copied the buttons wait: the answer decides what "the sound
    // in use" is.
    val idle = !state.copyingSound
    Tile(
        modifier = Modifier.fillMaxWidth(),
        padding = TilePadding.EVEN,
        gap = MiloTheme.spacing.rowGap,
    ) {
        TitledSwitchRow(
            title = stringResource(R.string.settings_sound_title),
            line = soundInUse(state),
            checked = state.soundEnabled,
            onCheckedChange = actions.onSoundEnabled,
        )
        if (state.ownSounds.isNotEmpty()) SoundChoices(state, actions.onChooseSound, idle)
        Column(verticalArrangement = Arrangement.spacedBy(MiloTheme.spacing.buttonGap)) {
            ActionButtonRow(
                actions =
                    listOf(
                        ActionButton(
                            label = stringResource(R.string.settings_sound_play),
                            kind = ActionKind.PLAIN,
                            onClick = actions.onPlaySound,
                            icon = MiloIcons.Play,
                            enabled = idle,
                        ),
                        ActionButton(
                            label = stringResource(R.string.settings_sound_add),
                            kind = ActionKind.PLAIN,
                            onClick = actions.onPickOwnSound,
                            enabled = idle,
                        ),
                    ),
            )
            val inUse = state.soundInUseUri
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
        if (state.copyingSound) Note(stringResource(R.string.settings_sound_copying))
        state.problem?.soundText()?.let { StatusRow(label = it, status = RowStatus.PROBLEM) }
        Note(stringResource(R.string.settings_sound_alarm_note))
    }
}

/**
 * One row for each sound there is to choose from: the built-in chirp, then each of his own.
 * Shown once he has added one; before that the chirp is all there is.
 */
@Composable
private fun SoundChoices(
    state: SettingsUiState.Ready,
    onChoose: (ownSoundUri: String?) -> Unit,
    idle: Boolean,
) {
    Column(modifier = Modifier.selectableGroup()) {
        ChoiceRow(
            label = stringResource(R.string.settings_sound_line_built_in),
            selected = state.soundInUseUri == null,
            onSelect = { if (idle) onChoose(null) },
        )
        for (sound in state.ownSounds) {
            ChoiceRow(
                label = sound.name ?: stringResource(R.string.settings_sound_line_own),
                selected = sound.uri == state.soundInUseUri,
                onSelect = { if (idle) onChoose(sound.uri) },
            )
        }
    }
}

/** The line under the title: which sound a trip start plays. */
@Composable
private fun soundInUse(state: SettingsUiState.Ready): String = when {
    !state.usesOwnSound -> stringResource(R.string.settings_sound_line_built_in)
    else -> state.ownSoundName ?: stringResource(R.string.settings_sound_line_own)
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

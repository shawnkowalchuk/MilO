package com.shawnkowalchuk.milo.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.component.ActionButton
import com.shawnkowalchuk.milo.core.designsystem.component.ActionButtonRow
import com.shawnkowalchuk.milo.core.designsystem.component.ActionKind
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
 * the switch, all of which toggle it; under them "Play" and "Use my own sound" side by side.
 *
 * What the design does not draw and MilO has: while a sound of Shawn's own is in use, a third
 * button under the two leads back to the built-in one; a picked file that was refused is said
 * in red, with its reason; and one grey line says that the sound plays like an alarm, which is
 * why it is heard when the phone is silent.
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
                            label = stringResource(R.string.settings_sound_use_own),
                            kind = ActionKind.PLAIN,
                            onClick = actions.onPickOwnSound,
                            enabled = idle,
                        ),
                    ),
            )
            if (state.usesOwnSound) {
                ActionButtonRow(
                    actions =
                        listOf(
                            ActionButton(
                                label = stringResource(R.string.settings_sound_use_built_in),
                                kind = ActionKind.PLAIN,
                                onClick = actions.onUseBuiltInSound,
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

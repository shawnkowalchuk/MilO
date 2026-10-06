package com.shawnkowalchuk.milo.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.component.RowStatus
import com.shawnkowalchuk.milo.core.designsystem.component.SectionCard
import com.shawnkowalchuk.milo.core.designsystem.component.StatusRow
import com.shawnkowalchuk.milo.core.designsystem.component.StepperButton
import com.shawnkowalchuk.milo.core.designsystem.component.StepperRow
import com.shawnkowalchuk.milo.core.designsystem.component.SwitchRow
import com.shawnkowalchuk.milo.core.util.formatKilometres
import com.shawnkowalchuk.milo.core.util.formatMinutes
import com.shawnkowalchuk.milo.data.sound.MAX_OWN_SOUND_BYTES

// The three cards of the Settings screen.

private const val BYTES_PER_MEGABYTE = 1024 * 1024

/** The truck's name, and the way to the pairing screen, where it is changed. */
@Composable
internal fun TruckCard(state: SettingsUiState.Ready, actions: SettingsActions) {
    SectionCard(title = stringResource(R.string.settings_truck_title)) {
        Text(
            text =
                when {
                    !state.truckPaired -> stringResource(R.string.pairing_no_truck)
                    else -> state.truckName ?: stringResource(R.string.truck_without_a_name)
                },
            style = MaterialTheme.typography.bodyLarge,
        )
        Quiet(
            stringResource(
                if (state.truckPaired) {
                    R.string.settings_truck_detail
                } else {
                    R.string.pairing_no_truck_detail
                },
            ),
        )
        // The Setup screen's own words for the same button.
        EndButton(
            text =
                stringResource(
                    if (state.truckPaired) {
                        R.string.setup_action_change_truck
                    } else {
                        R.string.setup_action_pair_truck
                    },
                ),
            onClick = actions.onChangeTruck,
        )
    }
}

/** The two numbers the trip rules run with. */
@Composable
internal fun TripRulesCard(state: SettingsUiState.Ready, actions: SettingsActions) {
    val locale = LocalConfiguration.current.locales[0]
    SectionCard(title = stringResource(R.string.settings_trips_title)) {
        Quiet(stringResource(R.string.settings_applies_next_trip))
        StepperRow(
            label = stringResource(R.string.settings_grace_label),
            value =
                stringResource(
                    R.string.settings_grace_value,
                    formatMinutes(state.gracePeriodSeconds, locale),
                ),
            decrease =
                StepperButton(
                    description = stringResource(R.string.settings_grace_less),
                    onClick = { actions.onGraceStep(false) },
                    enabled = state.canShortenGrace,
                ),
            increase =
                StepperButton(
                    description = stringResource(R.string.settings_grace_more),
                    onClick = { actions.onGraceStep(true) },
                    enabled = state.canLengthenGrace,
                ),
            supportingText = stringResource(R.string.settings_grace_detail),
        )
        StepperRow(
            label = stringResource(R.string.settings_minimum_label),
            value =
                stringResource(
                    R.string.distance_km,
                    formatKilometres(state.minimumDistanceMetres.toDouble(), locale),
                ),
            decrease =
                StepperButton(
                    description = stringResource(R.string.settings_minimum_less),
                    onClick = { actions.onMinimumDistanceStep(false) },
                    enabled = state.canLowerMinimum,
                ),
            increase =
                StepperButton(
                    description = stringResource(R.string.settings_minimum_more),
                    onClick = { actions.onMinimumDistanceStep(true) },
                    enabled = state.canRaiseMinimum,
                ),
            supportingText = stringResource(R.string.settings_minimum_detail),
        )
    }
}

/** Whether a trip start makes a sound, which one, and the way to hear and to change it. */
@Composable
internal fun SoundCard(state: SettingsUiState.Ready, actions: SettingsActions) {
    SectionCard(title = stringResource(R.string.settings_sound_title)) {
        SwitchRow(
            label = stringResource(R.string.settings_sound_enabled),
            checked = state.soundEnabled,
            onCheckedChange = actions.onSoundEnabled,
        )
        Text(text = soundInUse(state), style = MaterialTheme.typography.bodyLarge)
        Quiet(stringResource(R.string.settings_sound_note))
        if (state.copyingSound) Quiet(stringResource(R.string.settings_sound_copying))
        state.problem?.soundText()?.let { StatusRow(label = it, status = RowStatus.PROBLEM) }

        // While a picked file is being copied the buttons wait: the answer decides what
        // "the sound in use" is.
        val idle = !state.copyingSound
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
            TextButton(onClick = actions.onPlaySound, enabled = idle) {
                Text(text = stringResource(R.string.settings_sound_play))
            }
            TextButton(onClick = actions.onPickOwnSound, enabled = idle) {
                Text(text = stringResource(R.string.settings_sound_use_own))
            }
            if (state.usesOwnSound) {
                TextButton(onClick = actions.onUseBuiltInSound, enabled = idle) {
                    Text(text = stringResource(R.string.settings_sound_use_built_in))
                }
            }
        }
    }
}

@Composable
private fun soundInUse(state: SettingsUiState.Ready): String {
    val name = state.ownSoundName
    return when {
        !state.usesOwnSound -> stringResource(R.string.settings_sound_built_in)
        name == null -> stringResource(R.string.settings_sound_own)
        else -> stringResource(R.string.settings_sound_own_named, name)
    }
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

    // Said at the top of the screen: it can be about any of the cards.
    SettingsProblem.COULD_NOT_SAVE -> null
}

/** A quieter line under a setting: what it is for. */
@Composable
private fun Quiet(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * A text button at the end of its line, where the buttons of a `StatusRow` sit too. The row
 * takes the card's whole width: a card is only as wide inside as its widest line, and with two
 * short lines above it the button would otherwise sit in the middle.
 */
@Composable
private fun EndButton(text: String, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = onClick) {
            Text(text = text)
        }
    }
}

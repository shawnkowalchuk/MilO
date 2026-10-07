package com.shawnkowalchuk.milo.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.component.QuietExpander
import com.shawnkowalchuk.milo.core.designsystem.component.StepperButton
import com.shawnkowalchuk.milo.core.designsystem.component.StepperRow
import com.shawnkowalchuk.milo.core.designsystem.component.SwitchTile
import com.shawnkowalchuk.milo.core.designsystem.component.Tile
import com.shawnkowalchuk.milo.core.designsystem.component.TileButton
import com.shawnkowalchuk.milo.core.designsystem.component.TileLabel
import com.shawnkowalchuk.milo.core.designsystem.component.TilePadding
import com.shawnkowalchuk.milo.core.designsystem.component.TilePair
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme
import com.shawnkowalchuk.milo.core.util.formatKilometres
import com.shawnkowalchuk.milo.core.util.formatMinutes

// The first tiles of the Settings screen, as the design draws them: the truck beside the
// driving alert, and the three numbers of the trip rules. With them, the three small parts
// every tile of the screen is made of: its label, a note, and a note with a name.

/**
 * The truck's tile and the driving alert's, side by side, and under the two the line that
 * opens what they are for.
 *
 * The explanation stands under the pair and not inside a tile. The driving alert's tile is one
 * switch, pressed as a whole, so it can hold no second thing to press; and its two sentences
 * would make a half tile three times as high as drawn. That the alert never starts a trip by
 * itself is said there, because a switch about driving in an app that starts trips by itself
 * would otherwise be read as one more way a trip starts.
 */
@Composable
internal fun TruckAndAlertTiles(state: SettingsUiState.Ready, actions: SettingsActions) {
    var aboutOpen by rememberSaveable { mutableStateOf(false) }
    TilePair(
        first = { half -> TruckTile(state, actions.onChangeTruck, half) },
        second = { half ->
            SwitchTile(
                label = stringResource(R.string.settings_driving_title),
                text = stringResource(R.string.settings_driving_enabled),
                checked = state.drivingAlertEnabled,
                onCheckedChange = actions.onDrivingAlertEnabled,
                modifier = half,
            )
        },
    )
    QuietExpander(
        label = stringResource(R.string.settings_about_pair),
        expanded = aboutOpen,
        onToggle = { aboutOpen = !aboutOpen },
        // In from the edge, like a label that stands on the page.
        modifier = Modifier.padding(horizontal = MiloTheme.spacing.extraSmall),
    ) {
        Note(
            stringResource(
                if (state.truckPaired) {
                    R.string.settings_truck_detail
                } else {
                    R.string.pairing_no_truck_detail
                },
            ),
        )
        Note(stringResource(R.string.settings_driving_detail))
        Note(stringResource(R.string.settings_driving_needs))
    }
}

/** The truck's name, and the way to the pairing screen, where it is changed. */
@Composable
private fun TruckTile(state: SettingsUiState.Ready, onChangeTruck: () -> Unit, modifier: Modifier) {
    Tile(modifier = modifier, padding = TilePadding.EVEN, gap = MiloTheme.spacing.tileGap) {
        TileHeading(stringResource(R.string.settings_truck_title))
        Text(
            text =
                when {
                    !state.truckPaired -> stringResource(R.string.settings_truck_none)
                    else -> state.truckName ?: stringResource(R.string.truck_without_a_name)
                },
            style = MaterialTheme.typography.titleSmall,
        )
        // The Setup screen's own words for the same button. It is the accent only while there
        // is something to put right: no truck.
        TileButton(
            text =
                stringResource(
                    if (state.truckPaired) {
                        R.string.setup_action_change_truck
                    } else {
                        R.string.setup_action_pair_truck
                    },
                ),
            onClick = onChangeTruck,
            modifier = Modifier.fillMaxWidth(),
            accent = !state.truckPaired,
        )
    }
}

/** The three numbers the trip rules run with, each between a minus and a plus. */
@Composable
internal fun TripRulesTile(state: SettingsUiState.Ready, actions: SettingsActions) {
    val locale = LocalConfiguration.current.locales[0]
    var aboutOpen by rememberSaveable { mutableStateOf(false) }
    val grace = stringResource(R.string.settings_grace_label)
    val parked = stringResource(R.string.settings_parked_label)
    val minimum = stringResource(R.string.settings_minimum_label)
    Tile(
        modifier = Modifier.fillMaxWidth(),
        padding = TilePadding.EVEN,
        gap = MiloTheme.spacing.controlPadding,
    ) {
        TileHeading(stringResource(R.string.settings_trips_label))
        StepperRow(
            label = grace,
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
        )
        StepperRow(
            label = parked,
            value =
                stringResource(
                    R.string.settings_grace_value,
                    formatMinutes(state.parkedLimitSeconds, locale),
                ),
            decrease =
                StepperButton(
                    description = stringResource(R.string.settings_parked_less),
                    onClick = { actions.onParkedLimitStep(false) },
                    enabled = state.canShortenParked,
                ),
            increase =
                StepperButton(
                    description = stringResource(R.string.settings_parked_more),
                    onClick = { actions.onParkedLimitStep(true) },
                    enabled = state.canLengthenParked,
                ),
        )
        StepperRow(
            label = minimum,
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
        )
        QuietExpander(
            label = stringResource(R.string.settings_about_trips),
            expanded = aboutOpen,
            onToggle = { aboutOpen = !aboutOpen },
        ) {
            Note(stringResource(R.string.settings_applies_next_trip))
            NamedNote(grace, stringResource(R.string.settings_grace_detail))
            NamedNote(parked, stringResource(R.string.settings_parked_detail))
            NamedNote(minimum, stringResource(R.string.settings_minimum_detail))
        }
    }
}

/**
 * A tile's small grey label, which says what the tile holds. A screen reader can jump from one
 * to the next, instead of reading every row.
 */
@Composable
internal fun TileHeading(text: String) {
    TileLabel(text = text, modifier = Modifier.semantics { heading() })
}

/** A quiet sentence: what a setting is for, or what is going on. */
@Composable
internal fun Note(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** A quiet sentence under the name of the setting or the choice it is about. */
@Composable
internal fun NamedNote(name: String, text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(MiloTheme.spacing.textGap)) {
        Text(text = name, style = MaterialTheme.typography.labelMedium)
        Note(text)
    }
}

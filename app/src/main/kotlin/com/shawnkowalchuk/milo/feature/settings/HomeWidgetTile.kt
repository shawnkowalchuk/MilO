package com.shawnkowalchuk.milo.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.allowance.MAX_CENTS_PER_KM
import com.shawnkowalchuk.milo.core.allowance.MIN_CENTS_PER_KM
import com.shawnkowalchuk.milo.core.allowance.centsPerMileOf
import com.shawnkowalchuk.milo.core.allowance.formatCentsPerKm
import com.shawnkowalchuk.milo.core.designsystem.component.QuietExpander
import com.shawnkowalchuk.milo.core.designsystem.component.RowStatus
import com.shawnkowalchuk.milo.core.designsystem.component.StatusRow
import com.shawnkowalchuk.milo.core.designsystem.component.TextEntry
import com.shawnkowalchuk.milo.core.designsystem.component.Tile
import com.shawnkowalchuk.milo.core.designsystem.component.TileButton
import com.shawnkowalchuk.milo.core.designsystem.component.TilePadding
import com.shawnkowalchuk.milo.core.designsystem.component.TitledSwitchRow
import com.shawnkowalchuk.milo.core.designsystem.component.ValueButton
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme
import com.shawnkowalchuk.milo.core.util.DistanceUnit

/** A rate is at most "5.00"; this leaves room for a dollar sign and spaces around it. */
private const val MAX_TYPED_LENGTH = 8

/** The widget's tile: see [HomeWidgetTileContent]. Nothing is drawn until the settings are read. */
@Composable
internal fun HomeWidgetTile(viewModel: HomeWidgetViewModel) {
    val state by viewModel.state.collectAsState()
    state?.let {
        HomeWidgetTileContent(
            it,
            viewModel::onEnabled,
            viewModel::onSaveRate,
            viewModel::onAddToHomeScreen,
        )
    }
}

/**
 * The home-screen widget: its switch, drawn like the two sounds' (a title, a line that
 * says what it is, and the switch), and while it is on, the rate its dollars are priced at, and
 * a button that asks the home screen to add it, or the sentence that says how. What the dollars
 * are, and that they are for reference only, is behind the line at the end of the tile. Made of
 * the screen's existing parts.
 *
 * @param onSaveRate stores what was typed as the rate, and answers false if it is not one.
 */
@Composable
internal fun HomeWidgetTileContent(
    shown: HomeWidgetCardState,
    onEnabled: (Boolean) -> Unit,
    onSaveRate: (String) -> Boolean,
    onAddToHomeScreen: () -> Unit,
) {
    var aboutOpen by rememberSaveable { mutableStateOf(false) }
    Tile(
        modifier = Modifier.fillMaxWidth(),
        padding = TilePadding.EVEN,
        gap = MiloTheme.spacing.rowGap,
    ) {
        TitledSwitchRow(
            title = stringResource(R.string.settings_widget_title),
            line = stringResource(R.string.settings_widget_line),
            checked = shown.enabled,
            onCheckedChange = onEnabled,
        )
        if (shown.couldNotSave) {
            StatusRow(
                label = stringResource(R.string.settings_could_not_save),
                status = RowStatus.PROBLEM,
            )
        }
        if (shown.enabled) {
            RateEntry(shown.centsPerKm, onSaveRate)
            // The rate is per kilometre whatever is shown. With miles, what it comes to per
            // mile is said beside it, for reading only: nothing is priced at that figure.
            if (shown.unit == DistanceUnit.MILES) {
                val locale = LocalConfiguration.current.locales[0]
                Note(
                    stringResource(
                        R.string.settings_widget_rate_per_mile,
                        formatCentsPerKm(shown.centsPerKm, locale),
                        formatCentsPerKm(centsPerMileOf(shown.centsPerKm), locale),
                    ),
                )
            }
            if (shown.canAskToAdd) {
                TileButton(
                    text = stringResource(R.string.settings_widget_add),
                    onClick = onAddToHomeScreen,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Note(stringResource(R.string.settings_widget_how))
        }
        QuietExpander(
            label = stringResource(R.string.settings_about_widget),
            expanded = aboutOpen,
            onToggle = { aboutOpen = !aboutOpen },
        ) {
            Note(
                stringResource(
                    when (shown.unit) {
                        DistanceUnit.KILOMETRES -> R.string.settings_widget_detail
                        DistanceUnit.MILES -> R.string.settings_widget_detail_miles
                    },
                ),
            )
        }
    }
}

/**
 * The widget's rate (Shawn's choice of 2026-10-07: "Settings, starts at 0.70"): a value button
 * with "Rate" over "$0.70/km", as the edit screen draws a date. A press on it opens the field
 * and its two buttons in its place, as the odometer's "Adjust" does; "Save rate" stores what was
 * typed, or says under the field what a rate is.
 */
@Composable
private fun RateEntry(centsPerKm: Int, onSaveRate: (String) -> Boolean) {
    val locale = LocalConfiguration.current.locales[0]
    var editing by rememberSaveable { mutableStateOf(false) }
    var typed by rememberSaveable { mutableStateOf("") }
    var refused by rememberSaveable { mutableStateOf(false) }
    if (!editing) {
        ValueButton(
            value =
                stringResource(
                    R.string.settings_widget_rate_value,
                    formatCentsPerKm(centsPerKm, locale),
                ),
            onClick = { editing = true },
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.settings_widget_rate),
        )
    } else {
        TextEntry(
            label = stringResource(R.string.settings_widget_rate_field),
            initialText = typed,
            onTextChange = {
                typed = it
                refused = false
            },
            maxLength = MAX_TYPED_LENGTH,
            decimalNumber = true,
            lastField = true,
            error =
                stringResource(
                    R.string.settings_widget_rate_refused,
                    formatCentsPerKm(MIN_CENTS_PER_KM, locale),
                    formatCentsPerKm(MAX_CENTS_PER_KM, locale),
                ).takeIf { refused },
            placeholder = stringResource(R.string.settings_widget_rate_example),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(MiloTheme.spacing.small)) {
            TileButton(
                text = stringResource(R.string.action_cancel),
                onClick = {
                    editing = false
                    typed = ""
                    refused = false
                },
                modifier = Modifier.weight(1f),
            )
            TileButton(
                text = stringResource(R.string.settings_widget_rate_save),
                onClick = {
                    if (onSaveRate(typed)) {
                        editing = false
                        typed = ""
                    } else {
                        refused = true
                    }
                },
                modifier = Modifier.weight(1f),
                accent = true,
            )
        }
    }
}

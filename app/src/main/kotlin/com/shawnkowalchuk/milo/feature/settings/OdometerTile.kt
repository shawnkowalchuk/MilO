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
import com.shawnkowalchuk.milo.core.designsystem.component.FigureText
import com.shawnkowalchuk.milo.core.designsystem.component.QuietExpander
import com.shawnkowalchuk.milo.core.designsystem.component.RowStatus
import com.shawnkowalchuk.milo.core.designsystem.component.StatusRow
import com.shawnkowalchuk.milo.core.designsystem.component.TextEntry
import com.shawnkowalchuk.milo.core.designsystem.component.Tile
import com.shawnkowalchuk.milo.core.designsystem.component.TileButton
import com.shawnkowalchuk.milo.core.designsystem.component.TilePadding
import com.shawnkowalchuk.milo.core.designsystem.text.distanceRes
import com.shawnkowalchuk.milo.core.designsystem.text.distanceSpokenRes
import com.shawnkowalchuk.milo.core.designsystem.text.unitNameRes
import com.shawnkowalchuk.milo.core.designsystem.text.unitShortRes
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme
import com.shawnkowalchuk.milo.core.odometer.OdometerFigure
import com.shawnkowalchuk.milo.core.odometer.formatOdometer
import com.shawnkowalchuk.milo.core.util.DistanceUnit
import com.shawnkowalchuk.milo.core.util.formatMediumDay
import com.shawnkowalchuk.milo.core.util.formatTenths
import com.shawnkowalchuk.milo.core.util.localDateOf
import java.time.ZoneId
import java.util.Locale

/** A reading is at most seven digits; this leaves room for the separators he may type. */
private const val MAX_TYPED_LENGTH = 12

/**
 * The odometer's tile: see [OdometerTileContent]. Nothing is drawn until the settings have
 * been read.
 */
@Composable
internal fun OdometerTile(viewModel: OdometerViewModel) {
    val state by viewModel.state.collectAsState()
    state?.let { OdometerTileContent(it, viewModel::onSaveReading) }
}

/**
 * The truck's odometer, under the truck's own tile (Shawn's choice of 2026-10-07: "Settings,
 * truck tile"; the truck's tile is a half tile beside the driving alert's switch, so the
 * odometer has the full width under the pair). The figure is set large, like a distance on the
 * other screens, with a line under it that says what it is made of. "Adjust" opens a field for
 * the reading on the dashboard; "Save reading" adds it, and the figure is that reading from
 * then on.
 *
 * Built from the screen's existing parts: a tile, its label, a figure, a note, the text field
 * of the report's details and the tile's buttons.
 *
 * @param onSaveReading stores what was typed as a reading in the unit the tile is in, and
 * answers false if it is not a reading.
 */
@Composable
internal fun OdometerTileContent(
    shown: OdometerCardState,
    onSaveReading: (String, DistanceUnit) -> Boolean,
) {
    val locale = LocalConfiguration.current.locales[0]
    var editing by rememberSaveable { mutableStateOf(false) }
    var typed by rememberSaveable { mutableStateOf("") }
    var refused by rememberSaveable { mutableStateOf(false) }
    var aboutOpen by rememberSaveable { mutableStateOf(false) }
    Tile(
        modifier = Modifier.fillMaxWidth(),
        padding = TilePadding.EVEN,
        gap = MiloTheme.spacing.tileGap,
    ) {
        TileHeading(stringResource(R.string.settings_odometer_title))
        val figure = shown.figure
        if (figure == null) {
            Note(stringResource(R.string.settings_odometer_none))
        } else {
            val whole = formatOdometer(figure.value, locale)
            FigureText(
                figure = whole,
                unit = stringResource(unitShortRes(figure.unit)),
                spoken = stringResource(distanceSpokenRes(figure.unit), whole),
            )
            Note(madeOf(figure, shown.zone, locale))
        }
        if (shown.couldNotSave) {
            StatusRow(
                label = stringResource(R.string.settings_could_not_save),
                status = RowStatus.PROBLEM,
            )
        }
        if (editing) {
            TextEntry(
                label = stringResource(R.string.settings_odometer_field),
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
                        R.string.settings_odometer_refused,
                        stringResource(unitNameRes(shown.unit)),
                    ).takeIf { refused },
                placeholder = stringResource(R.string.settings_odometer_example),
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
                    text = stringResource(R.string.settings_odometer_save),
                    onClick = {
                        if (onSaveReading(typed, shown.unit)) {
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
        } else {
            TileButton(
                text =
                    stringResource(
                        if (figure == null) {
                            R.string.settings_odometer_enter
                        } else {
                            R.string.settings_odometer_adjust
                        },
                    ),
                onClick = { editing = true },
                modifier = Modifier.fillMaxWidth(),
                accent = figure == null,
            )
        }
        QuietExpander(
            label = stringResource(R.string.settings_about_odometer),
            expanded = aboutOpen,
            onToggle = { aboutOpen = !aboutOpen },
        ) {
            Note(
                stringResource(
                    R.string.settings_odometer_detail,
                    stringResource(unitNameRes(shown.unit)),
                ),
            )
        }
    }
}

/**
 * The line under the figure: the reading it comes from, and the trips added since. The reading
 * is written as it was typed, in the unit it was typed in; the trips since are in the figure's
 * unit, the one on screen.
 */
@Composable
private fun madeOf(figure: OdometerFigure, zone: ZoneId, locale: Locale): String {
    val reading = figure.reading
    val typed = stringResource(distanceRes(reading.unit), formatOdometer(reading.value, locale))
    val day = formatMediumDay(localDateOf(reading.atMs, zone), locale)
    return when {
        !figure.estimated -> stringResource(R.string.settings_odometer_typed_today, typed)

        figure.drivenTenths == 0L ->
            stringResource(R.string.settings_odometer_no_trips_since, typed, day)

        else ->
            stringResource(
                R.string.settings_odometer_trips_since,
                typed,
                day,
                stringResource(
                    distanceRes(figure.unit),
                    formatTenths(figure.drivenTenths, locale),
                ),
            )
    }
}

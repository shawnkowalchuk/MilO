package com.shawnkowalchuk.milo.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.component.FigureSize
import com.shawnkowalchuk.milo.core.designsystem.component.FigureText
import com.shawnkowalchuk.milo.core.designsystem.component.Tile
import com.shawnkowalchuk.milo.core.designsystem.component.TileLabel
import com.shawnkowalchuk.milo.core.designsystem.component.TilePadding
import com.shawnkowalchuk.milo.core.designsystem.text.distanceRes
import com.shawnkowalchuk.milo.core.designsystem.text.distanceSpokenRes
import com.shawnkowalchuk.milo.core.designsystem.text.unitShortRes
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme
import com.shawnkowalchuk.milo.core.odometer.OdometerFigure
import com.shawnkowalchuk.milo.core.odometer.formatOdometer
import com.shawnkowalchuk.milo.data.trip.VehicleOdometer

/**
 * Each paired vehicle's odometer (Shawn's request of 2026-10-09: "i want the milage on the home
 * screen"; he chose the odometer reading), the figure Settings' odometer tile shows: the last
 * reading typed there plus every trip since. With one vehicle, its figure as large as the
 * month's; with several, a row for each, named. Nothing to press: a reading is typed in
 * Settings, and the tile says so before the first.
 */
@Composable
internal fun OdometerTile(odometers: List<VehicleOdometer>, format: HomeFormat) {
    Tile(
        modifier = Modifier.fillMaxWidth(),
        padding = TilePadding.EVEN,
        gap = MiloTheme.spacing.small,
    ) {
        TileLabel(stringResource(R.string.home_odometer_title))
        val only = odometers.singleOrNull()?.takeIf { !it.named }
        if (only != null) {
            OneOdometer(only.figure, format)
        } else {
            for (odometer in odometers) OdometerRow(odometer, format)
        }
    }
}

/** The one vehicle's odometer, or what to do before its first reading. */
@Composable
private fun OneOdometer(figure: OdometerFigure?, format: HomeFormat) {
    if (figure == null) {
        Sentence(stringResource(R.string.home_odometer_none))
        return
    }
    val whole = formatOdometer(figure.value, format.locale)
    FigureText(
        figure = whole,
        unit = stringResource(unitShortRes(figure.unit)),
        size = FigureSize.MEDIUM,
        spoken = stringResource(distanceSpokenRes(figure.unit), whole),
    )
    Caption(stringResource(R.string.home_odometer_from_reading))
}

/** One vehicle among several: its name, and its odometer at the end of the row. */
@Composable
private fun OdometerRow(odometer: VehicleOdometer, format: HomeFormat) {
    val figure = odometer.figure
    Row(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(MiloTheme.spacing.rowGap),
    ) {
        Text(
            text = odometer.shownName.orEmpty(),
            modifier = Modifier.weight(1f).alignByBaseline(),
            style = MaterialTheme.typography.bodyLarge,
        )
        if (figure == null) {
            Text(
                text = stringResource(R.string.home_odometer_no_reading),
                modifier = Modifier.alignByBaseline(),
                style = MiloTheme.textStyles.tileLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            val whole = formatOdometer(figure.value, format.locale)
            val spoken = stringResource(distanceSpokenRes(figure.unit), whole)
            Text(
                text = stringResource(distanceRes(figure.unit), whole),
                // A screen reader is read the unit's whole word, as for every figure of a trip.
                modifier = Modifier.alignByBaseline().semantics { contentDescription = spoken },
                style = MiloTheme.textStyles.rowFigure,
            )
        }
    }
}

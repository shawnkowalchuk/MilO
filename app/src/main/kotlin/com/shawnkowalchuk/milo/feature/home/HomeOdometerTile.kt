package com.shawnkowalchuk.milo.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.component.DashboardWheels
import com.shawnkowalchuk.milo.core.designsystem.component.LiveDot
import com.shawnkowalchuk.milo.core.designsystem.component.Tile
import com.shawnkowalchuk.milo.core.designsystem.component.TileLabel
import com.shawnkowalchuk.milo.core.designsystem.component.TilePadding
import com.shawnkowalchuk.milo.core.designsystem.text.distanceRes
import com.shawnkowalchuk.milo.core.designsystem.text.distanceSpokenRes
import com.shawnkowalchuk.milo.core.designsystem.text.unitShortRes
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme
import com.shawnkowalchuk.milo.core.odometer.OdometerWheels
import com.shawnkowalchuk.milo.core.util.formatDistance
import com.shawnkowalchuk.milo.core.util.formatTenths

/**
 * Each paired vehicle's odometer (Shawn's request of 2026-10-09: "i want the milage on the home
 * screen"; he chose the odometer reading), as a row of wheels like the truck's own (2026-10-10:
 * "i also want to make the odometer more prominent display the way it is actively live"; of
 * three drawings he chose "Dashboard wheels"). Each digit of the whole kilometres stands in a
 * dark cell, and one more cell holds the tenth.
 *
 * **While a trip moves an odometer,** a lime dot pulses before the tile's label, that
 * odometer's last cell is lime, its last wheel turns as the truck is driven, and the line
 * under it says how far this trip has come. **Otherwise** the cells stand still, the last one
 * quiet, over the line that says where the figure comes from.
 *
 * With one vehicle, its wheels alone; with several, each under its name. Nothing to press: a
 * reading is typed in Settings, and the tile says so before the first.
 */
@Composable
internal fun OdometerTile(odometers: List<HomeOdometer>, format: HomeFormat) {
    Tile(
        modifier = Modifier.fillMaxWidth(),
        padding = TilePadding.EVEN,
        gap = MiloTheme.spacing.small,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(MiloTheme.spacing.buttonGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (odometers.any { it.counting }) LiveDot()
            TileLabel(stringResource(R.string.home_odometer_title))
        }
        val only = odometers.singleOrNull()?.takeIf { !it.odometer.named }
        if (only != null) {
            OneOdometer(only, format)
        } else {
            for (odometer in odometers) NamedOdometer(odometer, format)
        }
    }
}

/** The one vehicle's odometer, or what to do before its first reading. */
@Composable
private fun OneOdometer(odometer: HomeOdometer, format: HomeFormat) {
    val wheels = odometer.wheels
    if (wheels == null) {
        Sentence(stringResource(R.string.home_odometer_none))
        return
    }
    Wheels(wheels, format)
    Caption(
        thisTripText(odometer, wheels, format)
            ?: stringResource(R.string.home_odometer_from_reading),
    )
}

/**
 * One vehicle among several: its name, and its wheels under it, or "No reading yet" at the
 * end of the name's line. The line about this trip stands only under the vehicle the trip is
 * in.
 */
@Composable
private fun NamedOdometer(odometer: HomeOdometer, format: HomeFormat) {
    val wheels = odometer.wheels
    Column(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(MiloTheme.spacing.buttonGap),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(MiloTheme.spacing.rowGap)) {
            Text(
                text = odometer.odometer.shownName.orEmpty(),
                modifier = Modifier.weight(1f).alignByBaseline(),
                style = MaterialTheme.typography.bodyLarge,
            )
            if (wheels == null) {
                Text(
                    text = stringResource(R.string.home_odometer_no_reading),
                    modifier = Modifier.alignByBaseline(),
                    style = MiloTheme.textStyles.tileLabel,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (wheels != null) {
            Wheels(wheels, format)
            thisTripText(odometer, wheels, format)?.let { Caption(it) }
        }
    }
}

/** The row of wheels. A screen reader is read the figure with its tenth and the unit's word. */
@Composable
private fun Wheels(wheels: OdometerWheels, format: HomeFormat) {
    DashboardWheels(
        steps = wheels.tenths,
        unit = stringResource(unitShortRes(wheels.unit)),
        spoken =
            stringResource(
                distanceSpokenRes(wheels.unit),
                formatTenths(wheels.tenths, format.locale),
            ),
        turn = wheels.turn?.toFloat(),
    )
}

/**
 * "+4.6 km this trip" while the trip being recorded moves this odometer, and null otherwise.
 * The distance is the one the tile of the trip shows, in the unit the wheels are in.
 */
@Composable
private fun thisTripText(
    odometer: HomeOdometer,
    wheels: OdometerWheels,
    format: HomeFormat,
): String? {
    val metres = odometer.tripMetres ?: return null
    val soFar = formatDistance(metres, wheels.unit, format.locale)
    return stringResource(
        R.string.home_odometer_this_trip,
        stringResource(distanceRes(wheels.unit), soFar),
    )
}

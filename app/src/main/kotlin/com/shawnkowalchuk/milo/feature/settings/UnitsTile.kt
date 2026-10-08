package com.shawnkowalchuk.milo.feature.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.component.Segment
import com.shawnkowalchuk.milo.core.designsystem.component.SegmentedChoice
import com.shawnkowalchuk.milo.core.designsystem.component.Tile
import com.shawnkowalchuk.milo.core.designsystem.component.TilePadding
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme
import com.shawnkowalchuk.milo.core.util.DistanceUnit

/**
 * "Units": whether distances are shown in kilometres or in miles (Shawn's request of
 * 2026-10-07), as the two parts of one control, with a sentence under it that says what follows
 * the choice and that the stored trips are left as they are.
 *
 * Drawn like "Trips outside the work hours", the screen's other choice of two: a tile, its
 * label, the segmented control and a quiet sentence. Nothing was added to the design system.
 *
 * @param unit the unit in force.
 * @param onUnit stores the other one. Every screen, the Android Auto screen, the widget and
 * the trip's notification follow the stored setting, so the change shows at once.
 */
@Composable
internal fun UnitsTile(unit: DistanceUnit, onUnit: (DistanceUnit) -> Unit) {
    Tile(
        modifier = Modifier.fillMaxWidth(),
        padding = TilePadding.EVEN,
        gap = MiloTheme.spacing.tileGap,
    ) {
        TileHeading(stringResource(R.string.settings_units_title))
        SegmentedChoice(
            segments =
                listOf(
                    Segment(
                        label = stringResource(R.string.settings_units_kilometres),
                        selected = unit == DistanceUnit.KILOMETRES,
                        onSelect = { onUnit(DistanceUnit.KILOMETRES) },
                    ),
                    Segment(
                        label = stringResource(R.string.settings_units_miles),
                        selected = unit == DistanceUnit.MILES,
                        onSelect = { onUnit(DistanceUnit.MILES) },
                    ),
                ),
        )
        Note(stringResource(R.string.settings_units_detail))
    }
}

package com.shawnkowalchuk.milo.feature.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.component.QuietExpander
import com.shawnkowalchuk.milo.core.designsystem.component.RowStatus
import com.shawnkowalchuk.milo.core.designsystem.component.StatusRow
import com.shawnkowalchuk.milo.core.designsystem.component.Tile
import com.shawnkowalchuk.milo.core.designsystem.component.TileButton
import com.shawnkowalchuk.milo.core.designsystem.component.TilePadding
import com.shawnkowalchuk.milo.core.designsystem.component.TitledSwitchRow
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme

/** The widget's tile: see [HomeWidgetTileContent]. Nothing is drawn until the settings are read. */
@Composable
internal fun HomeWidgetTile(viewModel: HomeWidgetViewModel) {
    val state by viewModel.state.collectAsState()
    state?.let {
        HomeWidgetTileContent(it, viewModel::onEnabled, viewModel::onAddToHomeScreen)
    }
}

/**
 * The home-screen widget: its switch, drawn like the trip-start sound's (a title, a line that
 * says what it is, and the switch), and while it is on, a button that asks the home screen to
 * add it, or the sentence that says how. What the dollars are, and that they are for reference
 * only, is behind the line at the end of the tile. Made of the screen's existing parts.
 */
@Composable
internal fun HomeWidgetTileContent(
    shown: HomeWidgetCardState,
    onEnabled: (Boolean) -> Unit,
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
                    R.string.settings_widget_detail,
                    shown.rate.year,
                    shown.rate.firstTierCents,
                    shown.rate.afterCents,
                ),
            )
        }
    }
}

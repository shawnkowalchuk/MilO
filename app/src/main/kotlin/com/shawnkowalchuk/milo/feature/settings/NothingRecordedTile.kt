package com.shawnkowalchuk.milo.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.component.QuietExpander
import com.shawnkowalchuk.milo.core.designsystem.component.RowStatus
import com.shawnkowalchuk.milo.core.designsystem.component.StatusRow
import com.shawnkowalchuk.milo.core.designsystem.component.Tile
import com.shawnkowalchuk.milo.core.designsystem.component.TileButton
import com.shawnkowalchuk.milo.core.designsystem.component.TilePadding
import com.shawnkowalchuk.milo.core.designsystem.component.TimeDialog
import com.shawnkowalchuk.milo.core.designsystem.component.TitledSwitchRow
import com.shawnkowalchuk.milo.core.designsystem.component.rememberTwentyFourHourClock
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme
import com.shawnkowalchuk.milo.core.util.formatClockTime

/**
 * The daily check's tile: see [NothingRecordedTileContent]. Nothing is drawn until the settings
 * have been read.
 */
@Composable
internal fun NothingRecordedTile(viewModel: NothingRecordedViewModel) {
    val state by viewModel.state.collectAsState()
    state?.let { NothingRecordedTileContent(it, viewModel::onEnabled, viewModel::onTimePicked) }
}

/**
 * The daily check: whether MilO says when a work day has no trip by a set time, and that time.
 * The design does not draw this setting, so it is drawn like the two it does draw with a
 * switch: a title, a line that says what the switch does, and the switch, all of which toggle
 * it. Switched off, the time is not shown; it is kept, and comes back with the switch.
 *
 * The time is a button that opens the time picker, and it is written by the phone's own
 * 24-hour switch, like the hours of the work schedule, so that the button and the picker's
 * dial agree.
 *
 * @param onTimePicked the time picker's answer: an hour from 0 to 23 and a minute.
 */
@Composable
internal fun NothingRecordedTileContent(
    shown: NothingRecordedCardState,
    onEnabled: (Boolean) -> Unit,
    onTimePicked: (hour: Int, minute: Int) -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val twentyFourHour = rememberTwentyFourHourClock()
    // Whether the picker is open. It belongs to the screen, not to the settings; saved, so that
    // turning the phone does not close the picker.
    var picking by rememberSaveable { mutableStateOf(false) }
    var aboutOpen by rememberSaveable { mutableStateOf(false) }

    Tile(
        modifier = Modifier.fillMaxWidth(),
        padding = TilePadding.EVEN,
        gap = MiloTheme.spacing.controlPadding,
    ) {
        TitledSwitchRow(
            title = stringResource(R.string.settings_nothing_recorded_title),
            line = stringResource(R.string.settings_nothing_recorded_enabled),
            checked = shown.enabled,
            onCheckedChange = onEnabled,
        )
        if (shown.enabled) {
            val time = formatClockTime(shown.checkAt, locale, twentyFourHour)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MiloTheme.spacing.rowGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Said by the button itself, which names what its time is for.
                Text(
                    text = stringResource(R.string.settings_nothing_recorded_time_label),
                    style = MiloTheme.textStyles.sentence,
                    modifier = Modifier.weight(1f).clearAndSetSemantics {},
                )
                TileButton(
                    text = time,
                    onClick = { picking = true },
                    spokenName = stringResource(R.string.settings_nothing_recorded_time, time),
                )
            }
        }
        if (shown.couldNotSave) {
            // The screen's own words for a change that was not stored, said here, where the
            // press was made, and not at the top of a long screen.
            StatusRow(
                label = stringResource(R.string.settings_could_not_save),
                status = RowStatus.PROBLEM,
            )
        }
        QuietExpander(
            label = stringResource(R.string.settings_about_check),
            expanded = aboutOpen,
            onToggle = { aboutOpen = !aboutOpen },
        ) {
            Note(stringResource(R.string.settings_nothing_recorded_detail))
            Note(stringResource(R.string.settings_nothing_recorded_time_detail))
        }
    }

    if (picking) {
        TimeDialog(
            title = stringResource(R.string.settings_nothing_recorded_pick_time),
            hour = shown.checkAt.hour,
            minute = shown.checkAt.minute,
            twelveHourClock = !twentyFourHour,
            confirmLabel = stringResource(R.string.action_ok),
            dismissLabel = stringResource(R.string.action_cancel),
            onConfirm = { hour, minute ->
                picking = false
                onTimePicked(hour, minute)
            },
            onDismiss = { picking = false },
        )
    }
}

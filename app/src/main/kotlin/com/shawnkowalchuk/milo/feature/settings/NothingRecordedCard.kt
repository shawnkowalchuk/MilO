package com.shawnkowalchuk.milo.feature.settings

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.component.RowStatus
import com.shawnkowalchuk.milo.core.designsystem.component.SectionCard
import com.shawnkowalchuk.milo.core.designsystem.component.StatusRow
import com.shawnkowalchuk.milo.core.designsystem.component.SwitchRow
import com.shawnkowalchuk.milo.core.designsystem.component.TimeDialog
import com.shawnkowalchuk.milo.core.designsystem.component.rememberTwentyFourHourClock
import com.shawnkowalchuk.milo.core.util.formatClockTime

/**
 * The daily check: whether MilO says when a work day has no trip by a set time, and that time.
 * Switched off, it shows the switch and what the check is for, like the monthly reminder's
 * card: the time is kept, and comes back with the switch.
 *
 * The time is a button that opens the time picker, the way a day's start and end are chosen on
 * the work schedule's card, and it is written the way those are: by the phone's own 24-hour
 * switch, so that the button and the picker's dial agree.
 *
 * Nothing is drawn until the settings have been read.
 */
@Composable
internal fun NothingRecordedCard(viewModel: NothingRecordedViewModel) {
    val state by viewModel.state.collectAsState()
    val shown = state ?: return
    val locale = LocalConfiguration.current.locales[0]
    val twentyFourHour = rememberTwentyFourHourClock()
    // Whether the picker is open. It belongs to the screen, not to the settings; saved, so that
    // turning the phone does not close the picker.
    var picking by rememberSaveable { mutableStateOf(false) }

    SectionCard(title = stringResource(R.string.settings_nothing_recorded_title)) {
        SwitchRow(
            label = stringResource(R.string.settings_nothing_recorded_enabled),
            checked = shown.enabled,
            onCheckedChange = viewModel::onEnabled,
        )
        Quiet(stringResource(R.string.settings_nothing_recorded_detail))
        if (shown.enabled) {
            TextButton(onClick = { picking = true }) {
                Text(
                    text =
                        stringResource(
                            R.string.settings_nothing_recorded_time,
                            formatClockTime(shown.checkAt, locale, twentyFourHour),
                        ),
                )
            }
            Quiet(stringResource(R.string.settings_nothing_recorded_time_detail))
        }
        if (shown.couldNotSave) {
            // The screen's own words for a change that was not stored, said here, where the
            // press was made, and not at the top of a long screen.
            StatusRow(
                label = stringResource(R.string.settings_could_not_save),
                status = RowStatus.PROBLEM,
            )
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
                viewModel.onTimePicked(hour, minute)
            },
            onDismiss = { picking = false },
        )
    }
}

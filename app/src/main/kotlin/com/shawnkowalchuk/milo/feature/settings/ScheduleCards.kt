package com.shawnkowalchuk.milo.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.component.ChoiceRow
import com.shawnkowalchuk.milo.core.designsystem.component.RowStatus
import com.shawnkowalchuk.milo.core.designsystem.component.SectionCard
import com.shawnkowalchuk.milo.core.designsystem.component.StatusRow
import com.shawnkowalchuk.milo.core.designsystem.component.SwitchRow
import com.shawnkowalchuk.milo.core.designsystem.component.TimeDialog
import com.shawnkowalchuk.milo.core.designsystem.component.rememberTwentyFourHourClock
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme
import com.shawnkowalchuk.milo.core.util.formatClockTime
import com.shawnkowalchuk.milo.core.util.formatDayOfWeek
import java.time.DayOfWeek
import java.util.Locale

// The two cards of the work schedule: the seven days with their hours, and what becomes of a
// trip that starts outside them.

/**
 * The seven days, Monday first. Each has a switch; a day that is switched on shows its start
 * and its end, each of which opens the time picker, and (while another tracked day has other
 * hours) a button that gives its hours to every tracked day. A time that is refused is said
 * under the day it was picked for.
 */
@Composable
internal fun ScheduleCard(state: SettingsUiState.Ready, actions: ScheduleActions) {
    val locale = LocalConfiguration.current.locales[0]
    // The two times of a day and the picker's dial go by the phone's own 24-hour switch, so
    // that the dial never has AM and PM while the button beside it has neither.
    val twentyFourHour = rememberTwentyFourHourClock()
    // Which time the picker is open for. It belongs to the screen, not to the settings; saved,
    // so that turning the phone does not close the picker.
    var pickingDay by rememberSaveable { mutableStateOf<DayOfWeek?>(null) }
    var pickingEnd by rememberSaveable { mutableStateOf(false) }
    // The day the picker last gave a time for, and how many times it has given one. A refusal
    // goes by them to tell a press from a card that is merely drawn again. Not saved on
    // purpose: after the phone is turned, the next thing drawn is not the answer to a press.
    var answeredDay by remember { mutableStateOf<DayOfWeek?>(null) }
    var answers by remember { mutableIntStateOf(0) }

    SectionCard(title = stringResource(R.string.settings_schedule_title)) {
        Quiet(stringResource(R.string.settings_schedule_intro))
        Quiet(stringResource(R.string.settings_schedule_applies))
        for (day in state.schedule) {
            DayRows(
                day = day,
                locale = locale,
                twentyFourHour = twentyFourHour,
                answer = answers.takeIf { answeredDay == day.day },
                onTracked = { actions.onDayTracked(day.day, it) },
                onPick = { end ->
                    pickingDay = day.day
                    pickingEnd = end
                },
                onCopy = { actions.onCopyHours(day.day) },
            )
        }
    }

    // Looked up again each time: the picker shows the day as it is stored now.
    val picked = state.schedule.firstOrNull { it.day == pickingDay } ?: return
    val shown = if (pickingEnd) picked.end else picked.start
    TimeDialog(
        title =
            stringResource(
                if (pickingEnd) {
                    R.string.settings_schedule_pick_end
                } else {
                    R.string.settings_schedule_pick_start
                },
                formatDayOfWeek(picked.day, locale),
            ),
        hour = shown.hour,
        minute = shown.minute,
        twelveHourClock = !twentyFourHour,
        confirmLabel = stringResource(R.string.action_ok),
        dismissLabel = stringResource(R.string.action_cancel),
        onConfirm = { hour, minute ->
            pickingDay = null
            answeredDay = picked.day
            answers++
            if (pickingEnd) {
                actions.onDayEnd(picked.day, hour, minute)
            } else {
                actions.onDayStart(picked.day, hour, minute)
            }
        },
        onDismiss = { pickingDay = null },
    )
}

/**
 * One day: its switch, and under it, while the day is tracked, its two times, the refusal of a
 * time that was just picked for it, and the copy button.
 *
 * @param answer see [HoursRefused].
 * @param onPick asks for the time picker: false for the day's start, true for its end.
 */
@Composable
private fun DayRows(
    day: ScheduleDay,
    locale: Locale,
    twentyFourHour: Boolean,
    answer: Int?,
    onTracked: (Boolean) -> Unit,
    onPick: (end: Boolean) -> Unit,
    onCopy: () -> Unit,
) {
    SwitchRow(
        label = formatDayOfWeek(day.day, locale),
        checked = day.tracked,
        onCheckedChange = onTracked,
    )
    // A day that is not tracked has no hours that matter, so none are shown. They are kept,
    // and come back with the switch.
    if (!day.tracked) return
    // Each button has half the line, so that the two stay side by side at a large font size.
    Row(modifier = Modifier.fillMaxWidth()) {
        TextButton(onClick = { onPick(false) }, modifier = Modifier.weight(1f)) {
            Text(
                text =
                    stringResource(
                        R.string.settings_schedule_start,
                        formatClockTime(day.start, locale, twentyFourHour),
                    ),
            )
        }
        TextButton(onClick = { onPick(true) }, modifier = Modifier.weight(1f)) {
            Text(
                text =
                    stringResource(
                        R.string.settings_schedule_end,
                        formatClockTime(day.end, locale, twentyFourHour),
                    ),
            )
        }
    }
    if (day.hoursRefused) HoursRefused(answer)
    if (day.canCopy) {
        EndButton(text = stringResource(R.string.settings_schedule_copy), onClick = onCopy)
    }
}

/**
 * Why the time that was picked for a day was not taken, directly under that day's two times.
 *
 * @param answer how many times the picker has given a time since the card was drawn, if the
 * last of them was for this day; null if it was for another day or there was none.
 */
@Composable
private fun HoursRefused(answer: Int?) {
    val line = remember { BringIntoViewRequester() }
    // The line comes up under the day's times. For a day low on the screen that is below the
    // edge, and the press then looks as if it had done nothing, so the screen moves until the
    // line can be read. It moves once for each time picked, a second refusal included, which
    // changes nothing else on the screen. A line that is only drawn again (the phone turned,
    // Settings opened again) has no answer, and leaves the screen where it is.
    LaunchedEffect(answer) {
        if (answer != null) {
            // The line is laid out in the frame that has just begun. One frame on it has its
            // place, and the screen can tell how far to move.
            withFrameNanos { }
            line.bringIntoView()
        }
    }
    StatusRow(
        label = stringResource(R.string.settings_schedule_hours_refused),
        status = RowStatus.PROBLEM,
        modifier = Modifier.bringIntoViewRequester(line),
    )
}

/** What becomes of a trip that starts outside the schedule: one of two, said in full. */
@Composable
internal fun OutsideScheduleCard(state: SettingsUiState.Ready, actions: ScheduleActions) {
    SectionCard(title = stringResource(R.string.settings_outside_title)) {
        // One group for a screen reader: "1 of 2", "2 of 2".
        Column(
            modifier = Modifier.selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(MiloTheme.spacing.small),
        ) {
            ChoiceRow(
                label = stringResource(R.string.settings_outside_personal),
                selected = !state.ignoreOutsideSchedule,
                onSelect = { actions.onIgnoreOutside(false) },
                supportingText = stringResource(R.string.settings_outside_personal_detail),
            )
            ChoiceRow(
                label = stringResource(R.string.settings_outside_ignore),
                selected = state.ignoreOutsideSchedule,
                onSelect = { actions.onIgnoreOutside(true) },
                supportingText =
                    stringResource(
                        R.string.settings_outside_ignore_detail,
                        stringResource(R.string.trips_show_left_out),
                    ),
            )
        }
    }
}

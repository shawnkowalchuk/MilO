package com.shawnkowalchuk.milo.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.component.RowStatus
import com.shawnkowalchuk.milo.core.designsystem.component.Segment
import com.shawnkowalchuk.milo.core.designsystem.component.SegmentedChoice
import com.shawnkowalchuk.milo.core.designsystem.component.Span
import com.shawnkowalchuk.milo.core.designsystem.component.SpanHandleWords
import com.shawnkowalchuk.milo.core.designsystem.component.SpanSlider
import com.shawnkowalchuk.milo.core.designsystem.component.StatusRow
import com.shawnkowalchuk.milo.core.designsystem.component.Tile
import com.shawnkowalchuk.milo.core.designsystem.component.TileButton
import com.shawnkowalchuk.milo.core.designsystem.component.TilePadding
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme
import com.shawnkowalchuk.milo.core.util.formatClockTime
import com.shawnkowalchuk.milo.core.util.formatDayOfWeek
import com.shawnkowalchuk.milo.core.util.formatHourMark
import java.util.Locale

// The smaller parts of the work schedule's tile: the hours written out, one work day with a
// slider of its own, the line for a time that was refused, and the tile for the trips outside
// the work hours.

/**
 * How the tile writes a time of day and the name of an hour under a slider, both counted in
 * minutes since midnight as the slider counts them.
 *
 * @param twentyFourHour the phone's own 24-hour switch, as for every time MilO writes.
 */
internal class HoursWords(val locale: Locale, val twentyFourHour: Boolean) {
    fun time(minute: Int): String = formatClockTime(timeAtMinute(minute), locale, twentyFourHour)

    fun mark(minute: Int): String = formatHourMark(hourOfMark(minute), locale, twentyFourHour)
}

/**
 * A start and an end written out, "8:00 AM – 4:30 PM". Each of the two times is a button that
 * opens the time picker for it, which is how a time is set to the minute. They wrap onto a
 * second line when a large font leaves no room for both.
 *
 * @param onPick false for the start, true for the end.
 * @param roomForAFinger true where something that can be pressed stands close above or below
 * the hours (a day's own slider): each time then takes up Android's 48 dp. False where the
 * room around the hours is free, as around the week's large figures: they are then exactly as
 * high as drawn, and a press near them still counts as a press on them.
 */
@Composable
internal fun HoursText(
    span: Span,
    words: HoursWords,
    style: TextStyle,
    onPick: (end: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    roomForAFinger: Boolean = false,
) {
    val start = words.time(span.start)
    val end = words.time(span.end)
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(MiloTheme.spacing.extraSmall),
        // The dash stands in the middle of the height of the two times beside it.
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        TimeButton(
            time = start,
            spoken = stringResource(R.string.settings_schedule_start, start),
            style = style,
            roomForAFinger = roomForAFinger,
            onClick = { onPick(false) },
        )
        // For the eye only: the two buttons say "Start" and "End".
        Text(
            text = stringResource(R.string.settings_schedule_dash),
            style = style,
            modifier = Modifier.clearAndSetSemantics {},
        )
        TimeButton(
            time = end,
            spoken = stringResource(R.string.settings_schedule_end, end),
            style = style,
            roomForAFinger = roomForAFinger,
            onClick = { onPick(true) },
        )
    }
}

/**
 * One time, written as the design writes it and pressed like a button.
 *
 * With [roomForAFinger] it takes up 48 dp, its words in the middle. Without, it is as high as
 * its words, and Compose lets a press that lands within 48 dp count as a press on it, which
 * holds only while nothing else that can be pressed stands in that room.
 */
@Composable
private fun TimeButton(
    time: String,
    spoken: String,
    style: TextStyle,
    roomForAFinger: Boolean,
    onClick: () -> Unit,
) {
    Text(
        text = time,
        style = style,
        modifier =
            Modifier
                .clickable(
                    onClickLabel = stringResource(R.string.settings_schedule_set_time),
                    role = Role.Button,
                    onClick = onClick,
                ).semantics { contentDescription = spoken }
                .then(
                    if (roomForAFinger) Modifier.minimumInteractiveComponentSize() else Modifier,
                ),
    )
}

/**
 * One work day by itself: its name with its hours at the end of the line, its slider, the
 * refusal of a time that was just picked for it, and (while another work day has other hours)
 * the button that gives its hours to every work day.
 *
 * @param last true for the last work day: its slider has the names of the hours under it.
 * @param answer see [HoursRefused].
 */
@Composable
internal fun DayHours(
    day: ScheduleDay,
    span: Span,
    last: Boolean,
    answer: Int?,
    sliding: HoursSliding,
    onCopy: () -> Unit,
) {
    val name = formatDayOfWeek(day.day, sliding.words.locale)
    Column(verticalArrangement = Arrangement.spacedBy(MiloTheme.spacing.extraSmall)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MiloTheme.spacing.rowGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = name,
                style = MiloTheme.textStyles.sentence,
                modifier = Modifier.weight(1f),
            )
            HoursText(
                span = span,
                words = sliding.words,
                style = MaterialTheme.typography.titleSmall,
                onPick = { end -> sliding.onPick(day.day, end) },
                // The day's slider stands right under its hours.
                roomForAFinger = true,
            )
        }
        SpanSlider(
            span = span,
            scale = sliding.scale,
            onChange = { sliding.onSlide(day.day, span, it) },
            onChangeFinished = sliding.onLetGo,
            startWords =
                SpanHandleWords(
                    name = stringResource(R.string.settings_schedule_start_handle_day, name),
                    value = sliding.words.time(span.start),
                ),
            endWords =
                SpanHandleWords(
                    name = stringResource(R.string.settings_schedule_end_handle_day, name),
                    value = sliding.words.time(span.end),
                ),
            markLabel = sliding.words::mark,
            showMarks = last,
        )
        if (day.hoursRefused) HoursRefused(answer)
        // Not while a handle is being moved: the button comes and goes with every step that
        // makes two days differ or agree, and would push the sliders under it up and down.
        if (day.canCopy && !sliding.moving) {
            TileButton(
                text = stringResource(R.string.settings_schedule_copy),
                onClick = onCopy,
                modifier = Modifier.align(Alignment.End),
            )
        }
    }
}

/**
 * Why the time that was picked was not taken, directly under the hours it was picked for.
 *
 * @param answer how many times the picker has given a time since the tile was drawn, if the
 * last of them was for these hours; null if it was for others or there was none.
 */
@Composable
internal fun HoursRefused(answer: Int?) {
    val line = remember { BringIntoViewRequester() }
    // The line comes up under the hours. For a day low on the screen that is below the edge,
    // and the press then looks as if it had done nothing, so the screen moves until the line
    // can be read. It moves once for each time picked, a second refusal included, which
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

/**
 * What becomes of a trip that starts outside the schedule: one of two, side by side as the
 * design draws them, and under them what the one in force does. The other one's sentence
 * comes up when it is pressed. A press is undone by pressing the first again, and nothing is
 * sorted by the choice until a trip ends.
 */
@Composable
internal fun OutsideHoursTile(state: SettingsUiState.Ready, actions: ScheduleActions) {
    val ignore = state.ignoreOutsideSchedule
    Tile(
        modifier = Modifier.fillMaxWidth(),
        padding = TilePadding.EVEN,
        gap = MiloTheme.spacing.tileGap,
    ) {
        TileHeading(stringResource(R.string.settings_outside_title))
        SegmentedChoice(
            segments =
                listOf(
                    Segment(
                        label = stringResource(R.string.settings_outside_personal),
                        selected = !ignore,
                        onSelect = { actions.onIgnoreOutside(false) },
                    ),
                    Segment(
                        label = stringResource(R.string.settings_outside_ignore),
                        selected = ignore,
                        onSelect = { actions.onIgnoreOutside(true) },
                    ),
                ),
        )
        Note(
            if (ignore) {
                stringResource(
                    R.string.settings_outside_ignore_detail,
                    stringResource(R.string.trips_show_left_out),
                )
            } else {
                stringResource(R.string.settings_outside_personal_detail)
            },
        )
    }
}

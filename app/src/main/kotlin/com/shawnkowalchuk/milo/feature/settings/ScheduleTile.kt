package com.shawnkowalchuk.milo.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.component.QuietExpander
import com.shawnkowalchuk.milo.core.designsystem.component.Span
import com.shawnkowalchuk.milo.core.designsystem.component.SpanHandleWords
import com.shawnkowalchuk.milo.core.designsystem.component.SpanScale
import com.shawnkowalchuk.milo.core.designsystem.component.SpanSlider
import com.shawnkowalchuk.milo.core.designsystem.component.Tile
import com.shawnkowalchuk.milo.core.designsystem.component.TilePadding
import com.shawnkowalchuk.milo.core.designsystem.component.ToggleButton
import com.shawnkowalchuk.milo.core.designsystem.component.ToggleButtonRow
import com.shawnkowalchuk.milo.core.designsystem.component.rememberTwentyFourHourClock
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme
import com.shawnkowalchuk.milo.core.util.formatDayOfWeek
import com.shawnkowalchuk.milo.core.util.formatHours
import com.shawnkowalchuk.milo.core.util.formatShortDayOfWeek
import java.time.DayOfWeek

// The tile of the work schedule, as the design draws it: seven day buttons, the hours written
// out, and a slider with two handles. Its smaller parts are in ScheduleParts.kt.

/**
 * The work schedule.
 *
 * **The seven days,** Monday first, each a button that is lime while the day is a work day. A
 * press switches the day; its hours are kept either way.
 *
 * **One slider for the week, while every work day has the same hours,** which is how the
 * design draws the schedule and how MilO starts out. The hours stand above it in large
 * figures, and under them how many days and hours a week that is. Moving a handle sets the
 * hours of all seven days at once, so a day that is switched on later has them too.
 *
 * **A slider for each work day, as soon as two of them differ,** or when the line "Set each day
 * by itself" under the slider is opened. The schedule is stored day by day, and a day can have
 * hours of its own; the tile then shows each work day's name, its hours and its slider, and
 * (while another work day has other hours) the button that gives its hours to every work day.
 * While two work days differ the line cannot be closed: the way back to one slider is to make
 * them agree, which that button does, and then to press the line.
 *
 * **Every time can also be set to the minute:** pressing a start or an end opens the time
 * picker, as before. A slider moves in quarters of an hour between 4 AM and 10 PM; hours that
 * were picked outside that, and are stored, are shown on a line of the whole day
 * ([hoursScale]). A picked time that is refused is said under the hours it was picked for.
 */
@Composable
internal fun ScheduleTile(state: SettingsUiState.Ready, actions: ScheduleActions) {
    val locale = LocalConfiguration.current.locales[0]
    // The times, the names under the slider and the picker's dial go by the phone's own
    // 24-hour switch, so that the dial never has AM and PM while the time beside it has neither.
    val words = HoursWords(locale, rememberTwentyFourHourClock())
    // Asked for with the line under the slider. It belongs to the screen, not to the settings.
    var eachDayAsked by rememberSaveable { mutableStateOf(false) }
    var aboutOpen by rememberSaveable { mutableStateOf(false) }
    val picker = rememberHoursPicker()
    val hold = rememberHoursHold()

    val eachDay = eachDayAsked || !state.week.daysAgree
    // Shown each by itself, the days stay so until the line is pressed, also once they agree
    // again. Otherwise the slider that makes the last day agree would be put away from under
    // the finger that moves it.
    LaunchedEffect(state.week.daysAgree) {
        if (!state.week.daysAgree) eachDayAsked = true
    }
    val workDays = state.schedule.filter { it.tracked }
    val weekStored = Span(state.week.start.minuteOfDay(), state.week.end.minuteOfDay())
    val shown =
        if (eachDay) {
            workDays.map { shownSpan(it.day, it.span(), hold.held) }
        } else {
            listOf(shownSpan(null, weekStored, hold.held))
        }
    // One line for every slider of the tile, and the same one from the moment a handle is
    // taken until it is let go.
    val scale = hold.held?.scale ?: hoursScale(shown)

    val sliding =
        HoursSliding(
            scale = scale,
            words = words,
            moving = hold.held != null,
            onSlide = { day, from, to ->
                val next = to.storable()
                hold.move(day, next, scale)
                // One handle moves at a time, so one of the two times changed.
                if (next.start != from.start) {
                    val start = timeAtMinute(next.start)
                    actions.onDayStart(day, start.hour, start.minute)
                } else if (next.end != from.end) {
                    val end = timeAtMinute(next.end)
                    actions.onDayEnd(day, end.hour, end.minute)
                }
            },
            onLetGo = hold::letGo,
            onPick = picker::open,
        )

    Tile(
        modifier = Modifier.fillMaxWidth(),
        padding = TilePadding.EVEN,
        gap = MiloTheme.spacing.medium,
    ) {
        TileHeading(stringResource(R.string.settings_schedule_label))
        ToggleButtonRow(
            buttons =
                state.schedule.map { day ->
                    ToggleButton(
                        label = formatShortDayOfWeek(day.day, locale),
                        spokenName = formatDayOfWeek(day.day, locale),
                        on = day.tracked,
                        onToggle = { actions.onDayTracked(day.day, it) },
                    )
                },
            onWords = stringResource(R.string.settings_schedule_day_on),
            offWords = stringResource(R.string.settings_schedule_day_off),
        )
        val summary = weekSummary(workDays.size, shownWeeklyMinutes(state.schedule, hold.held))
        if (eachDay) {
            SummaryLine(summary)
        } else {
            WeekHours(shown.single(), summary, state.week.hoursRefused, sliding, picker)
        }
        // One line for both cases, always in the same place. While two work days differ
        // their sliders cannot be put away, and the line is only their caption.
        QuietExpander(
            label =
                stringResource(
                    if (state.week.daysAgree) {
                        R.string.settings_schedule_each_day
                    } else {
                        R.string.settings_schedule_each_day_caption
                    },
                ),
            expanded = eachDay,
            onToggle = { eachDayAsked = !eachDayAsked },
            pressable = state.week.daysAgree,
        ) {
            EachDayHours(workDays, shown, sliding, picker, actions.onCopyHours)
        }
        QuietExpander(
            label = stringResource(R.string.settings_about_schedule),
            expanded = aboutOpen,
            onToggle = { aboutOpen = !aboutOpen },
            // A little more room above it: the line before it can be pressed too, and each
            // keeps 10 dp free around itself for the finger.
            modifier = Modifier.padding(top = MiloTheme.spacing.extraSmall),
        ) {
            Note(stringResource(R.string.settings_schedule_intro))
            Note(stringResource(R.string.settings_schedule_applies))
            Note(stringResource(R.string.settings_schedule_slider_note))
        }
    }
    HoursPickerDialog(picker, state, words, actions)
}

/** The whole week as the design draws it: its hours, what they add up to, and one slider. */
@Composable
private fun WeekHours(
    span: Span,
    summary: String,
    refused: Boolean,
    sliding: HoursSliding,
    picker: HoursPicker,
) {
    Column(verticalArrangement = Arrangement.spacedBy(MiloTheme.spacing.textGap)) {
        HoursText(
            span = span,
            words = sliding.words,
            // TODO(debt): Type.kt still says that nothing uses `spanFigure`. This does
            // (FINDINGS_LOG, 2026-10-07).
            style = MiloTheme.textStyles.spanFigure,
            onPick = { end -> sliding.onPick(null, end) },
        )
        SummaryLine(summary)
    }
    if (refused) HoursRefused(picker.answerFor(null))
    SpanSlider(
        span = span,
        scale = sliding.scale,
        onChange = { sliding.onSlide(null, span, it) },
        onChangeFinished = sliding.onLetGo,
        startWords =
            SpanHandleWords(
                name = stringResource(R.string.settings_schedule_start_handle),
                value = sliding.words.time(span.start),
            ),
        endWords =
            SpanHandleWords(
                name = stringResource(R.string.settings_schedule_end_handle),
                value = sliding.words.time(span.end),
            ),
        markLabel = sliding.words::mark,
    )
}

/**
 * Each work day by itself: every work day with its hours and its slider. Only the last slider
 * has the names of the hours under it; all stand on one line.
 *
 * @param spans what each of [workDays]' sliders shows, in the same order. While the week is
 * shown with one slider there is one span for all of them.
 */
@Composable
private fun EachDayHours(
    workDays: List<ScheduleDay>,
    spans: List<Span>,
    sliding: HoursSliding,
    picker: HoursPicker,
    onCopy: (DayOfWeek) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(MiloTheme.spacing.rowGap)) {
        workDays.forEachIndexed { index, day ->
            DayHours(
                day = day,
                span = spans.getOrElse(index) { spans.first() },
                last = index == workDays.lastIndex,
                answer = picker.answerFor(day.day),
                sliding = sliding,
                onCopy = { onCopy(day.day) },
            )
        }
    }
}

@Composable
private fun SummaryLine(text: String) {
    Text(
        text = text,
        style = MiloTheme.textStyles.tileLabel,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** "5 days · 42.5 h a week", or the sentence for a week without a work day. */
@Composable
private fun weekSummary(workDays: Int, weeklyMinutes: Int): String = if (workDays == 0) {
    stringResource(R.string.settings_schedule_no_days)
} else {
    pluralStringResource(
        R.plurals.settings_schedule_summary,
        workDays,
        workDays,
        formatHours(weeklyMinutes, LocalConfiguration.current.locales[0]),
    )
}

/**
 * What the sliders and the written hours of the tile share.
 *
 * @param scale the line every slider of the tile is drawn on.
 * @param moving true from the first move of a handle until a moment after it is let go.
 * @param onSlide a handle of a day's slider, or of the week's (a null day), was moved: what
 * the slider showed, and what it is to show now.
 * @param onLetGo the handle was let go.
 * @param onPick a start (false) or an end (true) was pressed: open the time picker for it.
 */
internal class HoursSliding(
    val scale: SpanScale,
    val words: HoursWords,
    val moving: Boolean,
    val onSlide: (day: DayOfWeek?, from: Span, to: Span) -> Unit,
    val onLetGo: () -> Unit,
    val onPick: (day: DayOfWeek?, end: Boolean) -> Unit,
)

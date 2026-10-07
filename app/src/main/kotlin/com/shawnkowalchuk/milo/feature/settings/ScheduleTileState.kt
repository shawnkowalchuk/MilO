package com.shawnkowalchuk.milo.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.component.Span
import com.shawnkowalchuk.milo.core.designsystem.component.SpanScale
import com.shawnkowalchuk.milo.core.designsystem.component.TimeDialog
import com.shawnkowalchuk.milo.core.util.formatDayOfWeek
import java.time.DayOfWeek
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// What the work schedule's tile keeps for itself, apart from the settings: the span a slider
// shows while its handle is moved, and which time the time picker is open for.

/**
 * How long a slider goes on showing the span its handle was let go at. Each move is stored as
 * it is made, and the stored hours come back to the screen a moment later; shown at once, the
 * handle would step back and forward again.
 */
private const val SHOWN_AFTER_LET_GO_MS = 500L

/** What the picker's saved state calls the whole week, which is no day of it. */
private const val THE_WEEK = -1

/**
 * The span a slider shows while its handle is moved ([HeldHours]): from the first move until a
 * moment after the handle is let go.
 */
@Stable
internal class HoursHold(private val scope: CoroutineScope) {
    var held: HeldHours? by mutableStateOf(null)
        private set

    /** Counts the moves, so that a handle taken again is not let go by the one before it. */
    private var moves = 0

    /** A handle of [day]'s slider, or of the week's, was moved to [span] on [scale]. */
    fun move(day: DayOfWeek?, span: Span, scale: SpanScale) {
        moves++
        held = HeldHours(day, span, held?.scale ?: scale)
    }

    /** The handle was let go: a moment later the sliders show what is stored again. */
    fun letGo() {
        val at = moves
        scope.launch {
            delay(SHOWN_AFTER_LET_GO_MS)
            if (moves == at) held = null
        }
    }
}

@Composable
internal fun rememberHoursHold(): HoursHold {
    val scope = rememberCoroutineScope()
    return remember(scope) { HoursHold(scope) }
}

/**
 * Which time the time picker is open for, and for which hours it last gave a time.
 *
 * The first is saved, so that turning the phone does not close the picker. The second is not,
 * on purpose: a refusal goes by it to tell a press from a tile that is merely drawn again, and
 * after the phone is turned the next thing drawn is not the answer to a press.
 */
@Stable
internal class HoursPicker(
    private val openFor: () -> Int?,
    private val setOpenFor: (Int?) -> Unit,
    private val openForEnd: () -> Boolean,
    private val setOpenForEnd: (Boolean) -> Unit,
) {
    private var answeredFor: Int? by mutableStateOf(null)
    private var answers by mutableIntStateOf(0)

    /** True while the picker is open. */
    val isOpen: Boolean get() = openFor() != null

    /** The day the picker is open for, or null for the whole week. Only while [isOpen]. */
    val day: DayOfWeek? get() = openFor()?.takeIf { it != THE_WEEK }?.let(DayOfWeek.entries::get)

    /** Whether it is open for an end, and not for a start. */
    val end: Boolean get() = openForEnd()

    /** Opens the picker for the start or the end of [day], or of the whole week. */
    fun open(day: DayOfWeek?, end: Boolean) {
        setOpenForEnd(end)
        setOpenFor(day?.ordinal ?: THE_WEEK)
    }

    /** Closes the picker with nothing chosen. */
    fun close() = setOpenFor(null)

    /** Closes the picker after it gave a time. */
    fun answered() {
        answeredFor = openFor()
        answers++
        close()
    }

    /**
     * How many times the picker has given a time since the tile was drawn, if the last of
     * them was for [day] (or for the week, with null); otherwise null.
     */
    fun answerFor(day: DayOfWeek?): Int? =
        answers.takeIf { answeredFor == (day?.ordinal ?: THE_WEEK) }
}

@Composable
internal fun rememberHoursPicker(): HoursPicker {
    var openFor by rememberSaveable { mutableStateOf<Int?>(null) }
    var openForEnd by rememberSaveable { mutableStateOf(false) }
    return remember {
        HoursPicker(
            openFor = { openFor },
            setOpenFor = { openFor = it },
            openForEnd = { openForEnd },
            setOpenForEnd = { openForEnd = it },
        )
    }
}

/**
 * The time picker, while it is open: Android's clock dial under a title that says whose start
 * or end it is. Nothing changes until its OK is pressed. It opens on the time as it is stored
 * now, looked up each time it is drawn.
 */
@Composable
internal fun HoursPickerDialog(
    picker: HoursPicker,
    state: SettingsUiState.Ready,
    words: HoursWords,
    actions: ScheduleActions,
) {
    if (!picker.isOpen) return
    val day = picker.day
    val stored = state.schedule.firstOrNull { it.day == day }
    val shown =
        when {
            stored == null -> if (picker.end) state.week.end else state.week.start
            picker.end -> stored.end
            else -> stored.start
        }
    val whose =
        if (day == null) {
            stringResource(R.string.settings_schedule_every_day)
        } else {
            formatDayOfWeek(day, words.locale)
        }
    TimeDialog(
        title =
            stringResource(
                if (picker.end) {
                    R.string.settings_schedule_pick_end
                } else {
                    R.string.settings_schedule_pick_start
                },
                whose,
            ),
        hour = shown.hour,
        minute = shown.minute,
        twelveHourClock = !words.twentyFourHour,
        confirmLabel = stringResource(R.string.action_ok),
        dismissLabel = stringResource(R.string.action_cancel),
        onConfirm = { hour, minute ->
            val end = picker.end
            picker.answered()
            if (end) actions.onDayEnd(day, hour, minute) else actions.onDayStart(day, hour, minute)
        },
        onDismiss = picker::close,
    )
}

package com.shawnkowalchuk.milo.feature.settings

import com.shawnkowalchuk.milo.core.designsystem.component.Span
import com.shawnkowalchuk.milo.core.designsystem.component.SpanScale
import java.time.DayOfWeek
import java.time.LocalTime

// How the work schedule's hours stand on the slider of the Settings screen: which line the
// slider draws, and how a place on it becomes a time of day and back. Pure, so it is tested
// without a phone. The slider works in whole minutes since midnight.

private const val MINUTES_PER_HOUR = 60
private const val MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR

/** A handle lands on a quarter of an hour, as in the design. */
private const val SLIDER_STEP_MINUTES = 15

/** A moved handle leaves at least half an hour between the start and the end, as in the design. */
private const val SHORTEST_HOURS_MINUTES = 30

private fun hours(vararg hours: Int): List<Int> = hours.map { it * MINUTES_PER_HOUR }

/** The line the design draws: 4 AM to 10 PM, with five of its hours named under it. */
val DRAWN_HOURS_SCALE: SpanScale =
    SpanScale(
        from = 4 * MINUTES_PER_HOUR,
        to = 22 * MINUTES_PER_HOUR,
        step = SLIDER_STEP_MINUTES,
        minimumSpan = SHORTEST_HOURS_MINUTES,
        marks = hours(4, 9, 13, 18, 22),
    )

/**
 * The whole day, midnight to midnight, for hours that reach outside the drawn line. A day's
 * hours may start at 00:00 and end as late as 23:59 (they are set to the minute with the time
 * picker), and the slider has to be able to show whatever is stored.
 */
val WHOLE_DAY_SCALE: SpanScale =
    SpanScale(
        from = 0,
        to = MINUTES_PER_DAY,
        step = SLIDER_STEP_MINUTES,
        minimumSpan = SHORTEST_HOURS_MINUTES,
        marks = hours(0, 6, 12, 18, 24),
    )

/**
 * The line for the hours that are on the screen: the drawn one while every one of them lies
 * inside it, and the whole day as soon as one starts before 4 AM or ends after 10 PM. All the
 * sliders of the tile share one line, so that the same time is at the same place in each.
 */
fun hoursScale(shown: Collection<Span>): SpanScale {
    val drawn = DRAWN_HOURS_SCALE
    return if (shown.all {
            it.start >= drawn.from && it.end <= drawn.to
        }
    ) {
        drawn
    } else {
        WHOLE_DAY_SCALE
    }
}

/** A time of day as the slider counts it: whole minutes since midnight. */
fun LocalTime.minuteOfDay(): Int = hour * MINUTES_PER_HOUR + minute

/**
 * The time of day at [minute] of the slider's line. The end of the whole day's line is
 * midnight, which no day's hours can end at: it is read as 23:59, the latest end there is.
 */
fun timeAtMinute(minute: Int): LocalTime {
    val within = minute.coerceIn(0, MINUTES_PER_DAY - 1)
    return LocalTime.of(within / MINUTES_PER_HOUR, within % MINUTES_PER_HOUR)
}

/**
 * The full hour that the mark at [minute] of the line names. The end of the whole day's line
 * is named as what it is, midnight.
 */
fun hourOfMark(minute: Int): LocalTime =
    LocalTime.of(minute / MINUTES_PER_HOUR % (MINUTES_PER_DAY / MINUTES_PER_HOUR), 0)

/**
 * [span] as it can be stored: a handle at the very end of the whole day's line stands on
 * midnight, and is the latest end there is, 23:59.
 */
fun Span.storable(): Span = copy(end = end.coerceAtMost(MINUTES_PER_DAY - 1))

/** The start and the end of a day of the schedule, as the slider counts them. */
fun ScheduleDay.span(): Span = Span(start.minuteOfDay(), end.minuteOfDay())

/**
 * A span that a slider is showing in place of what is stored: the one whose handle is being
 * moved, from the first move until the stored schedule has caught up.
 *
 * @param day the day whose slider it is, or null for the one slider of the whole week.
 * @param scale the line the sliders were drawn on when the handle was taken. It is kept until
 * the handle is let go: a line that changed under a moving handle would move the handle.
 */
data class HeldHours(val day: DayOfWeek?, val span: Span, val scale: SpanScale)

/**
 * The span the slider of [day] shows: the held one while its handle is being moved, otherwise
 * what is [stored]. The one slider of the whole week is asked for with a null [day], and a
 * span held there stands for every day.
 */
fun shownSpan(day: DayOfWeek?, stored: Span, held: HeldHours?): Span =
    if (held != null && (held.day == null || held.day == day)) held.span else stored

/**
 * How many minutes of work hours the week has as it is shown, a moving handle included, so
 * that the line under the hours counts along with the handle.
 */
fun shownWeeklyMinutes(days: List<ScheduleDay>, held: HeldHours?): Int = days
    .filter { it.tracked }
    .sumOf { day -> shownSpan(day.day, day.span(), held).let { it.end - it.start } }

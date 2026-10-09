package com.shawnkowalchuk.milo.platform.clock

import com.shawnkowalchuk.milo.core.clock.ClockNews
import kotlin.math.abs

// The clock's lines in the event log. English and plain, like the rest of the log: one line for
// each change of the phone's clock that MilO saw and did not follow at once, written when it is
// over, and one for a start of MilO that took a date which was set ahead.

private const val MS_PER_SECOND = 1000L
private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 3600L
private const val SECONDS_PER_DAY = 24 * SECONDS_PER_HOUR

/**
 * The line for [news], or null if it has none: a change that has only just been seen is not
 * written down until it is over, so that each change leaves one line and not two.
 *
 * It says what MilO saw and when it looked, and nothing it cannot know. A process that Android
 * had put to sleep sees the clock back only when it next looks, which can be long after.
 */
internal fun clockNewsText(news: ClockNews): String? = when (news) {
    is ClockNews.SetAside -> null

    is ClockNews.CameBack ->
        "The phone's ${setText(news.offsetMs)}. MilO saw it back ${spanText(news.lastedMs)} " +
            "later and kept its own time."

    is ClockNews.ChangedAgain ->
        "The phone's ${setText(news.offsetMs)}. MilO saw it changed again " +
            "${spanText(news.lastedMs)} later and kept its own time."

    is ClockNews.Followed -> {
        val where = if (news.offsetMs >= 0) "ahead of" else "behind"
        "The phone's clock is ${spanText(abs(news.offsetMs))} $where where it was and has " +
            "stayed so for ${spanText(news.heldMs)}. MilO now follows it."
    }

    // It does not say for how long: the process that took the date may be long gone, and the
    // one that writes this only knows that the phone's clock is back.
    is ClockNews.StartedAhead ->
        "MilO started while the phone's ${setText(news.aheadMs)}, and took the phone's time " +
            "when it came back."
}

/** "date was set 24 h 0 min ahead" or "clock was set 3 h 0 min back", after "the phone's". */
private fun setText(offsetMs: Long): String {
    // He sets the date: a change of a day or more is called by that name.
    val what = if (wholeSeconds(abs(offsetMs)) >= SECONDS_PER_DAY) "date" else "clock"
    val direction = if (offsetMs >= 0) "ahead" else "back"
    return "$what was set ${spanText(abs(offsetMs))} $direction"
}

/**
 * A length of time in a log line: "9 s", "10 min", "1 min 15 s", "24 h 0 min". Rounded to the
 * nearest second, so that a day set by hand, which comes out some milliseconds short, reads as
 * 24 hours.
 */
internal fun spanText(spanMs: Long): String {
    val seconds = wholeSeconds(spanMs)
    val hours = seconds / SECONDS_PER_HOUR
    val minutes = seconds % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
    val left = seconds % SECONDS_PER_MINUTE
    return when {
        hours > 0 -> "$hours h $minutes min"
        minutes > 0 && left == 0L -> "$minutes min"
        minutes > 0 -> "$minutes min $left s"
        else -> "$left s"
    }
}

private fun wholeSeconds(spanMs: Long): Long =
    (spanMs.coerceAtLeast(0) + MS_PER_SECOND / 2) / MS_PER_SECOND

/**
 * The line a process writes at its start on a phone that does not say which boot this is. It is
 * an `ERROR` line: the clock still works while a process lives, and not across two of them.
 */
internal const val NO_BOOT_NUMBER =
    "MilO's clock: this phone gives no boot number, so MilO's time cannot be kept from one " +
        "start of MilO to the next. A MilO that Android starts while the phone's date is set " +
        "ahead will go by that date until the phone's clock comes back."

/** What the event log calls the asking and the look that follow the phone's clock coming back. */
internal const val CLOCK_BACK = "the phone's clock is back"

/** ...those that follow a change of the phone's clock that held, and that MilO now follows. */
internal const val CLOCK_FOLLOWED = "the phone's clock was changed and is followed"

/** ...and those that follow a change of the phone's clock that MilO follows at once. */
internal const val CLOCK_SET = "the phone's clock was set"

/** ...and those that follow the phone's clock being set back by more than MilO follows. */
internal const val CLOCK_SET_BACK = "the phone's clock was set back and MilO keeps its own time"

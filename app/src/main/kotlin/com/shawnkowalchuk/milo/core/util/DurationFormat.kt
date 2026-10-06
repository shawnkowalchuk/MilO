package com.shawnkowalchuk.milo.core.util

import java.util.Locale

private const val MS_PER_MINUTE = 60_000L
private const val MINUTES_PER_HOUR = 60L
private const val SECONDS_PER_MINUTE = 60

/**
 * A length of time as it is shown: whole hours and the minutes left over. The words around the
 * two numbers ("1 h 5 min") are user-visible text and live in strings.xml.
 */
data class HoursAndMinutes(val hours: Long, val minutes: Long)

/**
 * Splits a length of time into whole hours and minutes, rounded down as a stopwatch counts them.
 *
 * A negative length reads as nothing. It happens: the phone's clock can be set back while a trip
 * is open, and a trip's stored start can be later than its end.
 */
fun wholeHoursAndMinutes(durationMs: Long): HoursAndMinutes {
    val wholeMinutes = durationMs.coerceAtLeast(0) / MS_PER_MINUTE
    return HoursAndMinutes(
        hours = wholeMinutes / MINUTES_PER_HOUR,
        minutes = wholeMinutes % MINUTES_PER_HOUR,
    )
}

/**
 * A number of seconds as the minutes figure shown to the user: 120 becomes "2", 150 becomes
 * "2.5". Whole minutes carry no decimal, so the usual values read as plain numbers.
 *
 * Only the number is returned, like `formatKilometres`: the unit is in strings.xml.
 *
 * @param locale decides the decimal separator.
 * @throws IllegalArgumentException if [seconds] is negative. A setting like that cannot be right.
 */
fun formatMinutes(seconds: Int, locale: Locale): String {
    require(seconds >= 0) { "A length of time cannot be negative, but was $seconds s" }
    return if (seconds % SECONDS_PER_MINUTE == 0) {
        String.format(locale, "%d", seconds / SECONDS_PER_MINUTE)
    } else {
        String.format(locale, "%.1f", seconds / SECONDS_PER_MINUTE.toDouble())
    }
}

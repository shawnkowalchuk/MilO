package com.shawnkowalchuk.milo.core.util

import java.time.ZoneId
import java.util.Locale

/**
 * A trip's start and end as two times of day, written the short way: where both are followed
 * by the same half of the day, it is said once, after the end. "5:30" and "5:41 PM", not
 * "5:30 PM" and "5:41 PM". Where they differ ("11:50 AM" and "12:10 PM"), where the language
 * writes the half of the day before the time, and on a 24-hour clock, both stand in full.
 *
 * The two are returned apart, because the dash between them is the screen's to write.
 *
 * It goes by what [formatTimeOfDay] writes, not by the clock: whatever follows the last digit
 * of each time is compared, so it holds for "PM", for "p.m." and for a language this was never
 * tried in.
 *
 * @param zone, [locale] and [twentyFourHour] as for [formatTimeOfDay].
 */
fun formatTimeSpan(
    startMs: Long,
    endMs: Long,
    zone: ZoneId,
    locale: Locale,
    twentyFourHour: Boolean,
): Pair<String, String> {
    val start = formatTimeOfDay(startMs, zone, locale, twentyFourHour)
    val end = formatTimeOfDay(endMs, zone, locale, twentyFourHour)
    val startDigits = start.digitsEnd()
    val half = start.substring(startDigits)
    val sameHalf = half.isNotBlank() && half == end.substring(end.digitsEnd())
    return (if (sameHalf) start.substring(0, startDigits) else start) to end
}

/** Where the time's own figures end: just past its last digit, or at its end if it has none. */
private fun String.digitsEnd(): Int {
    val last = indexOfLast { it.isDigit() }
    return if (last < 0) length else last + 1
}

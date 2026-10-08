package com.shawnkowalchuk.milo.core.util

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.chrono.IsoChronology
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/**
 * A plain 24-hour time, for a phone that is set to 24 hours in a language that writes AM and PM.
 * It is how Android itself writes the time on such a phone.
 */
private const val TWENTY_FOUR_HOUR_PATTERN = "HH:mm"

/** And the other way round: a phone set to AM and PM in a language that writes 24 hours. */
private const val TWELVE_HOUR_PATTERN = "h:mm a"

/**
 * Turns a stored time (wall-clock milliseconds since 1970) into the time of day shown to the
 * user: "08:14", or "8:14 a.m.".
 *
 * Times are stored as plain milliseconds and only given a time zone here, at the moment of
 * display, so a trip recorded before a clock change still shows the time it happened.
 *
 * @param zone and [locale] have no defaults on purpose, like `formatDistance`: the screen
 * passes the phone's own, and a test passes fixed ones.
 * @param twentyFourHour whether the phone is set to write times with 24 hours ("Use 24-hour
 * format" in Android's date and time settings). The phone's switch decides between "08:14" and
 * "8:14 a.m.", so that MilO's times read like the phone's own clock; the language decides how
 * either is spelled.
 */
fun formatTimeOfDay(epochMs: Long, zone: ZoneId, locale: Locale, twentyFourHour: Boolean): String =
    timeOfDayFormat(locale, twentyFourHour).withZone(zone).format(Instant.ofEpochMilli(epochMs))

/**
 * A time of day that belongs to no date, such as the start of a work day, in the same form as
 * [formatTimeOfDay]: "08:00", or "8:00 a.m.".
 *
 * @param twentyFourHour as for [formatTimeOfDay]. The time picker's dial is drawn by the same
 * switch, so a time is never entered on a dial that disagrees with the time printed beside it.
 */
fun formatClockTime(time: LocalTime, locale: Locale, twentyFourHour: Boolean): String =
    timeOfDayFormat(locale, twentyFourHour).format(time)

/**
 * The language's own short form wherever it already has the clock the phone is set to, as it
 * does on a phone whose 24-hour switch follows the language. Only where the two disagree is a
 * plain pattern used in its place.
 */
private fun timeOfDayFormat(locale: Locale, twentyFourHour: Boolean): DateTimeFormatter = when {
    usesTwelveHourClock(locale) != twentyFourHour ->
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)

    twentyFourHour -> DateTimeFormatter.ofPattern(TWENTY_FOUR_HOUR_PATTERN, locale)

    else -> DateTimeFormatter.ofPattern(TWELVE_HOUR_PATTERN, locale)
}

/**
 * Whether [locale] itself writes a short time of day on a 12-hour clock. It says what the
 * language does, not what the phone is set to: [formatTimeOfDay] is told that by its caller.
 */
internal fun usesTwelveHourClock(locale: Locale): Boolean {
    val pattern =
        DateTimeFormatterBuilder.getLocalizedDateTimePattern(
            null,
            FormatStyle.SHORT,
            IsoChronology.INSTANCE,
            locale,
        )
    // In a date and time pattern "h" and "K" are the hours that run to 12, "H" and "k" the ones
    // that run to 24. The hour letter is asked, not the AM or PM marker: some languages mark
    // the half of the day with another letter. Text between single quotes is written out as it
    // is (the "h" of French "8 h 14") and is left aside.
    return pattern.split('\'').filterIndexed { index, _ -> index % 2 == 0 }.any { part ->
        part.any { it == 'h' || it == 'K' }
    }
}

/** A day of the week by its full name: "Monday". */
fun formatDayOfWeek(day: DayOfWeek, locale: Locale): String =
    day.getDisplayName(TextStyle.FULL, locale)

/** The event log's time format: fixed, sortable, and exact to the second. */
private val LOG_TIME: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT)

/**
 * Turns a stored time into the form the event log shows: "2026-10-05 08:14:03", in [zone].
 *
 * The same form in every language, on purpose. The log is evidence for why a trip did or did not
 * start: its lines are compared with each other and with the truck's clock, to the second, so
 * the date and the 24-hour time are always written out in full.
 */
fun formatLogTime(epochMs: Long, zone: ZoneId): String =
    LOG_TIME.withZone(zone).format(Instant.ofEpochMilli(epochMs))

/**
 * A month as a heading: "October 2026". "LLLL" is the month's name standing alone, which in
 * some languages is spelled differently from the name inside a date.
 */
fun formatMonthAndYear(month: YearMonth, locale: Locale): String =
    DateTimeFormatter.ofPattern("LLLL yyyy", locale).format(month)

/**
 * A month by its name alone, for a place where the year goes without saying because the month
 * is this one or the last: "October".
 */
fun formatMonthName(month: YearMonth, locale: Locale): String =
    DateTimeFormatter.ofPattern("LLLL", locale).format(month)

/**
 * Today as the short line under the app's name: "Tue, Oct 6". The names of the weekday and the
 * month are the language's own short ones; their order is the design's, in every language.
 */
fun formatShortDay(date: LocalDate, locale: Locale): String =
    DateTimeFormatter.ofPattern("EEE, MMM d", locale).format(date)

/** A day as a heading, in the long form of the user's language: "Monday, 5 October 2026". */
fun formatDay(date: LocalDate, locale: Locale): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale).format(date)

/**
 * A day in the medium form of the user's language, with its year: "5 Oct 2026". For a place
 * too narrow for the long form, where the year still has to be said: the two days of a date
 * range.
 */
fun formatMediumDay(date: LocalDate, locale: Locale): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(date)

/**
 * A stored time as a date only, in the medium form of the user's language: "5 Oct 2026". Used
 * where the day matters and the time of day does not, such as the day a setting was confirmed.
 */
fun formatDate(epochMs: Long, zone: ZoneId, locale: Locale): String = DateTimeFormatter
    .ofLocalizedDate(FormatStyle.MEDIUM)
    .withLocale(locale)
    .withZone(zone)
    .format(Instant.ofEpochMilli(epochMs))

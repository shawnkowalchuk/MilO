package com.shawnkowalchuk.milo.core.util

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Turns a stored time (wall-clock milliseconds since 1970) into the time of day shown to the
 * user, in the short form of their language: "08:14", or "8:14 a.m.".
 *
 * Times are stored as plain milliseconds and only given a time zone here, at the moment of
 * display, so a trip recorded before a clock change still shows the time it happened.
 *
 * @param zone and [locale] have no defaults on purpose, like `formatKilometres`: the screen
 * passes the phone's own, and a test passes fixed ones.
 */
fun formatTimeOfDay(epochMs: Long, zone: ZoneId, locale: Locale): String = DateTimeFormatter
    .ofLocalizedTime(FormatStyle.SHORT)
    .withLocale(locale)
    .withZone(zone)
    .format(Instant.ofEpochMilli(epochMs))

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

/** A day as a heading, in the long form of the user's language: "Monday, 5 October 2026". */
fun formatDay(date: LocalDate, locale: Locale): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale).format(date)

/**
 * A stored time as a date only, in the medium form of the user's language: "5 Oct 2026". Used
 * where the day matters and the time of day does not, such as the day a setting was confirmed.
 */
fun formatDate(epochMs: Long, zone: ZoneId, locale: Locale): String = DateTimeFormatter
    .ofLocalizedDate(FormatStyle.MEDIUM)
    .withLocale(locale)
    .withZone(zone)
    .format(Instant.ofEpochMilli(epochMs))

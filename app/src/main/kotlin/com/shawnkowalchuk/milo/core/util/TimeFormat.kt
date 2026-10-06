package com.shawnkowalchuk.milo.core.util

import java.time.Instant
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

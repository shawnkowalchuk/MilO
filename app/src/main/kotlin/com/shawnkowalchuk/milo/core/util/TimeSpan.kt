package com.shawnkowalchuk.milo.core.util

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * A span of stored time: from [fromMs] up to, but not including, [untilMs]. Both are wall-clock
 * milliseconds since 1970, the form every stored time has.
 *
 * Half-open on purpose. The end of one month is the start of the next, so a trip that starts on
 * the stroke of midnight belongs to exactly one of them.
 */
data class TimeSpan(val fromMs: Long, val untilMs: Long)

/**
 * The span of time a calendar month covers in [zone]: from the first instant of its first day to
 * the first instant of the next month.
 *
 * The month is worked out in a time zone because "October" is not a fixed span of milliseconds:
 * it starts at local midnight, and a month in which the clocks change is an hour longer or
 * shorter than its days suggest. `atStartOfDay` also copes with a zone whose clocks jump over
 * midnight itself.
 */
fun monthSpan(month: YearMonth, zone: ZoneId): TimeSpan = TimeSpan(
    fromMs = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli(),
    untilMs = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli(),
)

/**
 * The span of time a calendar day covers in [zone]: from local midnight to the next local
 * midnight. A day on which the clocks change is an hour shorter or longer than 24 hours, which
 * is why the end is worked out from the next day and not by adding 24 hours.
 */
fun daySpan(day: LocalDate, zone: ZoneId): TimeSpan = TimeSpan(
    fromMs = day.atStartOfDay(zone).toInstant().toEpochMilli(),
    untilMs = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
)

/** The calendar day a stored time falls on in [zone]. */
fun localDateOf(epochMs: Long, zone: ZoneId): LocalDate =
    Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()

/** The calendar month a stored time falls in, in [zone]. */
fun monthOf(epochMs: Long, zone: ZoneId): YearMonth = YearMonth.from(localDateOf(epochMs, zone))

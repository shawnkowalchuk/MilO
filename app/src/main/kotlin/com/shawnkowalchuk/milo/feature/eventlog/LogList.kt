package com.shawnkowalchuk.milo.feature.eventlog

import com.shawnkowalchuk.milo.core.designsystem.component.TileRowPlace
import com.shawnkowalchuk.milo.core.designsystem.component.tileRowPlace
import com.shawnkowalchuk.milo.data.eventlog.EventLogEntry
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// How the Log screen lists the lines it has read: by day, each day's lines as one tile. Pure
// functions, so they are tested without a phone.

/**
 * One line of the log, as a row of its day's tile.
 *
 * @param place where it stands in that tile.
 */
data class LogDayLine(val entry: EventLogEntry, val place: TileRowPlace)

/**
 * The lines of one day that the screen has read, newest first: one tile of the list.
 *
 * @param isToday the day is not named above the tile ([logDays] says why).
 */
data class LogDay(val date: LocalDate, val isToday: Boolean, val lines: List<LogDayLine>)

/**
 * Lays the lines out as the screen lists them: in the order they were read (newest first), the
 * lines of one day as one tile, and above each tile the day it is.
 *
 * **Today's tile has no label,** as on the owner's drawing, which shows one day: a tile without
 * a day above it is today's. Every other day is named, so that a line's time is never read
 * without its date. A line dated in the future (the phone's clock was wrong) is named too.
 *
 * @param entries newest first, as the log is read.
 * @param zone the phone's time zone: a day is the phone's own day.
 */
fun logDays(entries: List<EventLogEntry>, zone: ZoneId, today: LocalDate): List<LogDay> {
    val days = mutableListOf<LogDay>()
    var from = 0
    while (from < entries.size) {
        val date = entries[from].date(zone)
        var until = from + 1
        while (until < entries.size && entries[until].date(zone) == date) until++
        val lines =
            (from until until).map { index ->
                LogDayLine(entries[index], tileRowPlace(index - from, until - from))
            }
        days += LogDay(date, isToday = date == today, lines = lines)
        from = until
    }
    return days
}

private fun EventLogEntry.date(zone: ZoneId): LocalDate =
    Instant.ofEpochMilli(atMs).atZone(zone).toLocalDate()

// The same forms as the event log's own lines and its text file use, in every language: the
// log is evidence, compared line by line and with the truck's clock.
private val LOG_CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT)
private val LOG_DAY: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE yyyy-MM-dd", Locale.ENGLISH)

/**
 * A line's time of day as the Log screen writes it beside the line: "08:14:03", to the second
 * and with 24 hours, in [zone]. The date stands above the day's tile ([formatLogDay]).
 */
fun formatLogClock(epochMs: Long, zone: ZoneId): String =
    LOG_CLOCK.withZone(zone).format(Instant.ofEpochMilli(epochMs))

/** A day as the Log screen names it above that day's lines: "Tue 2026-10-06". */
fun formatLogDay(date: LocalDate): String = LOG_DAY.format(date)

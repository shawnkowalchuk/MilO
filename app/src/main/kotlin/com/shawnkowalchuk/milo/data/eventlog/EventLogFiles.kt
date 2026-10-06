package com.shawnkowalchuk.milo.data.eventlog

import android.content.Context
import com.shawnkowalchuk.milo.core.util.formatLogTime
import com.shawnkowalchuk.milo.core.util.localDateOf
import com.shawnkowalchuk.milo.data.report.ReportFileStore
import com.shawnkowalchuk.milo.data.report.buildReportFileStore
import java.io.File
import java.io.IOException
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// The event log as a plain text file, for Shawn to send to whoever is helping him find out why
// a trip did not start, without plugging the phone into the Mac. The text is made by pure
// functions, so its form is tested without a phone.

private const val FILE_PREFIX = "MilO-log-"
private const val FILE_EXTENSION = ".txt"

/** What a line of an entry's detail is set in by, so that it reads as belonging to its line. */
private const val DETAIL_INDENT = "    "

/**
 * The file's name: "MilO-log-2026-10-06.txt", by the day it was written, in [zone]. One name
 * for a day, so a second share on the same day replaces the first file and they do not pile up.
 */
fun eventLogFileName(writtenAtMs: Long, zone: ZoneId): String =
    FILE_PREFIX + localDateOf(writtenAtMs, zone) + FILE_EXTENSION

/**
 * The first lines of the file: what it is, when it was written, which time zone its times are
 * in, and how much of the log it holds. Whoever reads the file was not there when it was made.
 */
fun eventLogFileHeader(writtenAtMs: Long, zone: ZoneId, lineCount: Int): String =
    "MilO event log\n" +
        "Written ${formatLogTime(writtenAtMs, zone)}. Every time is in $zone.\n" +
        "$lineCount lines, oldest first. MilO removes lines older than $EVENT_LOG_KEEP_DAYS " +
        "days, except the newest $EVENT_LOG_KEEP_NEWEST.\n\n"

/**
 * [entries] as text, one line each, in the order given: the time as the Log screen writes it,
 * the category by its stored name, and the message. A detail (the state before and after a
 * trigger, a stack trace) follows on lines of its own, set in.
 */
fun eventLogFileLines(entries: List<EventLogEntry>, zone: ZoneId): String = buildString {
    for (entry in entries) {
        append(formatLogTime(entry.atMs, zone))
        append("  ").append(entry.category.name)
        append("  ").append(entry.message).append('\n')
        entry.detail?.lines()?.forEach { append(DETAIL_INDENT).append(it).append('\n') }
    }
}

/**
 * Writes the event log to a text file that can be handed to another app.
 *
 * The file is written beside the report files, in the one folder MilO's file provider can hand
 * a file out of (`cache/reports/`), and like them it is complete or it is not there. Nothing
 * here sends it anywhere: the Log screen offers it to Android's share sheet, and Shawn chooses.
 *
 * **The file holds everything the log holds,** and the log is written for finding faults, not
 * for other eyes: the truck's Bluetooth address and name, those of the phone's other paired
 * devices, and every address Shawn typed or replaced on the edit screen, the one before and
 * the one after. The screen says so beside the button. No line of the log holds a GPS
 * position, and none may: with this file the log can leave the phone.
 */
class EventLogFiles(private val eventLog: EventLogRepository, private val files: ReportFileStore) {
    /**
     * Writes the whole log, oldest line first, whatever the Log screen is showing.
     *
     * @param nowMs wall-clock milliseconds, for the file's name and its first lines.
     * @throws IOException if the file cannot be written. No half-written file is left behind.
     */
    suspend fun write(nowMs: Long, zone: ZoneId): File = withContext(Dispatchers.IO) {
        // Counted first, for the file's first lines. A line that is written between the count
        // and the reading is in the file all the same, so the count can be one or two short.
        val lineCount = eventLog.count()
        files.writeInPieces(eventLogFileName(nowMs, zone)) { out ->
            out.write(eventLogFileHeader(nowMs, zone, lineCount).toByteArray(Charsets.UTF_8))
            eventLog.readAll { page ->
                out.write(eventLogFileLines(page, zone).toByteArray(Charsets.UTF_8))
            }
        }
    }
}

/** Builds [EventLogFiles] on the folder of the report files. Called once, by the `AppContainer`. */
fun buildEventLogFiles(context: Context, eventLog: EventLogRepository): EventLogFiles =
    EventLogFiles(eventLog, buildReportFileStore(context))

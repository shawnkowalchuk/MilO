package com.shawnkowalchuk.milo.data.transfer

import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogEntry

/** The lines of the note before the text of the log's line: four numbers. */
private const val NUMBER_LINES = 4

/**
 * The note an import leaves just before it replaces the trips, and takes away when its last
 * step is done.
 *
 * An import replaces the rows of two database files, one after the other, and nothing makes
 * the two one step. A process that is ended in between has stored the file's trips and still
 * holds the raw points of the trips that are gone, under the same trip ids. Nothing in either
 * database says so; this note does, and the next process start finishes the import from it
 * (`ImportRun.resume`).
 *
 * Whether the trips were replaced at all is not for the note to say, since it is written
 * before they are: that is said by the line the event log gets in the very transaction that
 * replaces them ([saidAtMs], [said]).
 *
 * @param highestTripId the highest id a trip had on the phone or has in the file. Every raw
 * point up to it belongs to a trip the import replaces; a trip that starts once the trips are
 * replaced is given a higher one.
 * @param safetyCopyAtMs when the safety copy of this import was written, which is its name.
 * @param saidAtMs the time, and [said] the text, of that line of the event log. One line.
 * @param tries how many process starts have set about finishing this import.
 */
data class ImportBegun(
    val highestTripId: Long,
    val safetyCopyAtMs: Long,
    val saidAtMs: Long,
    val said: String,
    val tries: Int = 0,
) {
    /** The note as its file holds it: the four numbers, one on a line, then the text. */
    fun asText(): String = "$highestTripId\n$safetyCopyAtMs\n$saidAtMs\n$tries\n$said"
}

/**
 * The line of the event log that [ImportBegun.saidAtMs] and [ImportBegun.said] name. A
 * `REPORT` line, like every line about an import (`TransferLogText.kt`).
 */
fun ImportBegun.logLine(): EventLogEntry =
    EventLogEntry(atMs = saidAtMs, category = EventCategory.REPORT, message = said)

/** The note [text] is, or null if it is not one. */
fun importBegunOf(text: String): ImportBegun? {
    val lines = text.split('\n', limit = NUMBER_LINES + 1)
    if (lines.size <= NUMBER_LINES) return null
    val numbers = lines.take(NUMBER_LINES).map { it.toLongOrNull() ?: return null }
    val tries = numbers[NUMBER_LINES - 1]
    if (tries !in 0..Int.MAX_VALUE) return null
    return ImportBegun(
        highestTripId = numbers[0],
        safetyCopyAtMs = numbers[1],
        saidAtMs = numbers[2],
        said = lines[NUMBER_LINES],
        tries = tries.toInt(),
    )
}

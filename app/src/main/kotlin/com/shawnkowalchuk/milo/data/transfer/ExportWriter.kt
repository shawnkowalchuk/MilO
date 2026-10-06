package com.shawnkowalchuk.milo.data.transfer

import com.shawnkowalchuk.milo.core.util.localDateOf
import com.shawnkowalchuk.milo.data.point.RawPoint
import com.shawnkowalchuk.milo.data.report.SentReport
import com.shawnkowalchuk.milo.data.settings.TransferredSettings
import com.shawnkowalchuk.milo.data.trip.Trip
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

private const val FILE_PREFIX = "MilO-export-"
private const val FILE_EXTENSION = ".json"

/**
 * The name an export is offered under in the file picker: "MilO-export-2026-10-06.json", by
 * the day it is written, in [zone]. Shawn can change it there, and the picker itself adds a
 * number to a name that is taken.
 */
fun exportFileName(writtenAtMs: Long, zone: ZoneId): String =
    FILE_PREFIX + localDateOf(writtenAtMs, zone) + FILE_EXTENSION

/**
 * Hands the raw points to [each], a few hundred at a time, in the order they were recorded.
 * A function and not a list: there can be a million of them.
 */
fun interface PointPages {
    suspend fun forEachPage(each: suspend (List<RawPoint>) -> Unit)
}

/**
 * Everything an export is made of, as it was read from storage at one moment.
 *
 * @param zone the phone's time zone, for the one time in the file that is written for a person.
 * @param settings null if the settings file could not be read. The export is written all the
 * same, without them: the trips are what must not be lost.
 * @param pointCount how many of the raw points [points] hands over belong to one of [trips],
 * or null to leave the points out of the file. A point of any other trip is passed over.
 */
class ExportSource(
    val exportedAtMs: Long,
    val zone: ZoneId,
    val appVersion: String,
    val databaseVersion: Int,
    val settings: TransferredSettings?,
    val sentReports: List<SentReport>,
    val trips: List<Trip>,
    val pointCount: Long?,
    val points: PointPages,
)

/**
 * The data changed under an export while it was being written. The file is not to be kept: it
 * is an I/O failure like any other, so whoever writes the file removes what was written.
 */
class ChangedWhileExportingException(message: String) : IOException(message)

/**
 * Writes the export document (`ExportFormat.kt`) to [out], one thing on a line, so that the
 * file can be read by a person and compared with another one line by line.
 *
 * It writes straight through: the points are fetched page by page while they are written, and
 * nothing but one page is ever held.
 *
 * A raw point whose trip is not among the trips of the file is not written. The file holds
 * closed trips only, and an import refuses a point of a trip the file does not hold; such a
 * point is the first fix of a trip that started while the file was being made, or one that
 * something left behind (`DataExport` counts them and says so).
 *
 * @return how many of each thing was written, which is also what the file says it holds.
 * @throws ChangedWhileExportingException if the points turn out not to be what was counted
 * before the first line was written. A file that says one thing and holds another must not be
 * kept.
 */
suspend fun writeExport(out: Appendable, source: ExportSource): ExportContents {
    val contents =
        ExportContents(
            trips = source.trips.size,
            sentReports = source.sentReports.size,
            points = source.pointCount,
            settings = source.settings != null,
        )
    val writtenAt = Instant.ofEpochMilli(source.exportedAtMs).atZone(source.zone)
    out.append("{\n")
    out.line("format", ExportJson.encodeToString(EXPORT_FORMAT))
    out.line("formatVersion", EXPORT_FORMAT_VERSION.toString())
    out.line("exportedAtMs", source.exportedAtMs.toString())
    out.line(
        "exportedAt",
        ExportJson.encodeToString(
            writtenAt.toOffsetDateTime().truncatedTo(ChronoUnit.SECONDS).toString(),
        ),
    )
    out.line("appVersion", ExportJson.encodeToString(source.appVersion))
    out.line("databaseVersion", source.databaseVersion.toString())
    out.line("about", ExportJson.encodeToString(EXPORT_ABOUT))
    out.line("contents", ExportJson.encodeToString(contents))
    out.line("settings", ExportJson.encodeToString(source.settings?.toExported()))
    out.list("sentReports", source.sentReports.map { ExportJson.encodeToString(it.toExported()) })
    out.list("trips", source.trips.map { ExportJson.encodeToString(it.toExported()) })
    out.line("pointColumns", ExportJson.encodeToString(EXPORT_POINT_COLUMNS))
    if (source.pointCount == null) {
        out.append("\"points\": null\n")
    } else {
        out.points(source, source.pointCount)
    }
    out.append("}\n")
    return contents
}

private fun Appendable.line(key: String, value: String) {
    append('"').append(key).append("\": ").append(value).append(",\n")
}

private fun Appendable.list(key: String, elements: List<String>) {
    append('"').append(key).append("\": [\n")
    elements.forEachIndexed { index, element ->
        append(element).append(if (index == elements.lastIndex) "\n" else ",\n")
    }
    append("],\n")
}

private suspend fun Appendable.points(source: ExportSource, expected: Long) {
    val tripIds = source.trips.mapTo(HashSet()) { it.id }
    var written = 0L
    append("\"points\": [\n")
    source.points.forEachPage { page ->
        for (point in page) {
            if (point.tripId !in tripIds) continue
            if (written > 0) append(",\n")
            append(point.toExportRow())
            written++
        }
    }
    if (written != expected) {
        throw ChangedWhileExportingException(
            "$expected raw points were counted and $written were read",
        )
    }
    append(if (written > 0) "\n]\n" else "]\n")
}

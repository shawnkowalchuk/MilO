package com.shawnkowalchuk.milo.data.transfer

import com.shawnkowalchuk.milo.data.point.RawPoint
import com.shawnkowalchuk.milo.data.report.SentReport
import com.shawnkowalchuk.milo.data.settings.TransferredSettings
import com.shawnkowalchuk.milo.data.trip.Trip
import java.io.Reader
import kotlinx.serialization.SerializationException

/** Why a file cannot be imported. Nothing on the phone has been touched when one is found. */
sealed interface ExportProblem {
    /** The file is not an export of MilO's: not JSON at all, or JSON of some other kind. */
    data object NotAnExport : ExportProblem

    /**
     * The file was written by a newer MilO, in a form this one does not know. It is refused as
     * a whole: what a newer form means cannot be guessed at.
     */
    data class NewerVersion(val version: Int) : ExportProblem

    /**
     * The file is an export, and something in it is not what MilO writes: it was cut short, a
     * part is missing, or a value cannot be right.
     *
     * @param why what was found and where, in plain English, for the event log.
     */
    data class Damaged(val why: String) : ExportProblem
}

/**
 * What a file holds, once every part of it has been read and checked.
 *
 * @param settings null if the file has none: the settings could not be read when it was made.
 * @param pointCount how many raw GPS points the file holds, or null if they were left out.
 */
data class CheckedExport(
    val exportedAtMs: Long,
    val appVersion: String,
    val settings: TransferredSettings?,
    val sentReports: List<SentReport>,
    val trips: List<Trip>,
    val pointCount: Long?,
)

sealed interface ExportReading {
    data class Good(val export: CheckedExport) : ExportReading

    data class Refused(val problem: ExportProblem) : ExportReading
}

/** How many raw points are handed on at a time. */
private const val POINT_BATCH = 500

private val REQUIRED_KEYS =
    setOf(
        "format",
        "formatVersion",
        "exportedAtMs",
        "exportedAt",
        "appVersion",
        "databaseVersion",
        "about",
        "contents",
        "settings",
        "sentReports",
        "trips",
        "pointColumns",
        "points",
    )

/**
 * Reads and checks a whole export file, and changes nothing anywhere.
 *
 * The file is gone through twice. First only for what it says it is, so that a file from a
 * newer MilO is called that and not "damaged" because one of its trips has a part this version
 * does not know. Then part by part: every trip, every sent report, the settings and every raw
 * point is read and held against the rules of `ExportChecks.kt`, and at the end the file is
 * held against itself: it holds as many of each thing as it says, no id twice, and no point
 * of a trip that is not in it.
 *
 * The trips, the sent reports and the settings are returned; they are a few thousand small
 * values at most. The raw points are not: there can be a million of them. They are counted, and
 * handed to [onPoints] a few hundred at a time if the caller wants them.
 *
 * @param open opens the file at its start. Called twice; each reader is closed here.
 * @param onPoints is handed the points in the order of the file. An import stores them with
 * it; a check of the file leaves it out.
 */
suspend fun readExport(
    open: () -> Reader,
    onPoints: (suspend (List<RawPoint>) -> Unit)? = null,
): ExportReading {
    saidToBe(open)?.let { return ExportReading.Refused(it) }
    return try {
        ExportReading.Good(open().use { ExportParts(JsonScanner(it), onPoints).read() })
    } catch (cutShort: BrokenJsonException) {
        ExportReading.Refused(ExportProblem.Damaged(cutShort.message.orEmpty()))
    } catch (wrong: DamagedExportException) {
        ExportReading.Refused(ExportProblem.Damaged(wrong.message.orEmpty()))
    }
}

/** Something in the file is not what MilO writes. The message says what and where. */
private class DamagedExportException(message: String) : Exception(message)

/**
 * The first pass: what the file says it is. Null if it says it is an export in a form this
 * MilO reads.
 */
private fun saidToBe(open: () -> Reader): ExportProblem? {
    var format: String? = null
    var version: String? = null
    try {
        open().use { reader ->
            val scanner = JsonScanner(reader)
            scanner.beginObject()
            // The two keys stand first in a file MilO wrote, so this usually reads two lines.
            while (format == null || version == null) {
                when (scanner.nextKey() ?: break) {
                    "format" -> format = scanner.value()
                    "formatVersion" -> version = scanner.value()
                    else -> scanner.skipValue()
                }
            }
        }
    } catch (notJson: BrokenJsonException) {
        // A file that names itself an export and then breaks off is an export that was cut
        // short, which the second pass says. Anything else was never one.
        if (format != "\"$EXPORT_FORMAT\"") return ExportProblem.NotAnExport
    }
    val number = version?.toIntOrNull()
    return when {
        format != "\"$EXPORT_FORMAT\"" -> ExportProblem.NotAnExport

        version == null -> ExportProblem.Damaged("It does not say which format version it is")

        number == null || number < 1 ->
            ExportProblem.Damaged("Its format version is $version, which is no version")

        number > EXPORT_FORMAT_VERSION -> ExportProblem.NewerVersion(number)

        else -> null
    }
}

/** The second pass: the parts of one document, read in whatever order they stand. */
private class ExportParts(
    private val scanner: JsonScanner,
    private val onPoints: (suspend (List<RawPoint>) -> Unit)?,
) {
    private val seen = mutableSetOf<String>()
    private var exportedAtMs = 0L
    private var appVersion = ""
    private var contents: ExportContents? = null
    private var settings: TransferredSettings? = null
    private val sentReports = mutableListOf<SentReport>()
    private val trips = mutableListOf<Trip>()
    private var pointCount: Long? = null
    private val tripsOfPoints = mutableSetOf<Long>()

    suspend fun read(): CheckedExport {
        scanner.beginObject()
        while (true) {
            val key = scanner.nextKey() ?: break
            if (!seen.add(key)) throw damaged("\"$key\" stands in it twice")
            readPart(key)
        }
        scanner.endDocument()
        (REQUIRED_KEYS - seen).firstOrNull()?.let { throw damaged("It has no \"$it\"") }
        heldAgainstItself(checkNotNull(contents))
        return CheckedExport(exportedAtMs, appVersion, settings, sentReports, trips, pointCount)
    }

    private suspend fun readPart(key: String) {
        when (key) {
            "exportedAtMs" -> exportedAtMs = decode<Long>(key).also(::mustBeATime)

            "appVersion" -> appVersion = decode(key)

            "contents" -> contents = decode(key)

            "settings" -> settings = decode<ExportedSettings?>(key)?.let(::checkedSettings)

            "sentReports" -> eachOf(key) { text, number -> sentReports += sentReport(text, number) }

            "trips" -> eachOf(key) { text, number -> trips += trip(text, number) }

            "points" -> readPoints()

            "pointColumns" ->
                if (decode<List<String>>(key) != EXPORT_POINT_COLUMNS) {
                    throw damaged("Its points have other columns than MilO writes")
                }

            // Already read by the first pass, or written for a person and not for MilO.
            "format", "formatVersion" -> scanner.skipValue()

            "exportedAt", "about" -> decode<String>(key)

            "databaseVersion" -> decode<Int>(key)

            else -> throw damaged("It has a part this MilO does not know, \"$key\"")
        }
    }

    private fun mustBeATime(ms: Long) {
        if (ms < 0) throw damaged("It says it was written before 1970")
    }

    private fun checkedSettings(read: ExportedSettings): TransferredSettings {
        read.problem()?.let { throw damaged("The settings: $it") }
        return read.toTransferred()
    }

    private fun sentReport(text: String, number: Int): SentReport {
        val read = decodeText<ExportedSentReport>(text, "Sent report number $number")
        read.problem()?.let { throw damaged("Sent report ${read.id}: $it") }
        return read.toSentReport()
    }

    private fun trip(text: String, number: Int): Trip {
        val read = decodeText<ExportedTrip>(text, "Trip number $number")
        read.problem()?.let { throw damaged("Trip ${read.id}: $it") }
        return read.toTrip()
    }

    private suspend fun readPoints() {
        if (!scanner.beginList()) return
        var count = 0L
        val batch = ArrayList<RawPoint>(POINT_BATCH)
        while (true) {
            val text = scanner.nextElement() ?: break
            count++
            when (val row = pointOfRow(text)) {
                is PointRow.Bad -> throw damaged("Point number $count: ${row.why}")

                is PointRow.Read -> {
                    tripsOfPoints += row.point.tripId
                    batch += row.point
                }
            }
            if (batch.size == POINT_BATCH) {
                onPoints?.invoke(batch.toList())
                batch.clear()
            }
        }
        if (batch.isNotEmpty()) onPoints?.invoke(batch.toList())
        pointCount = count
    }

    /** What only the whole file can show: it holds what it says, and it hangs together. */
    private fun heldAgainstItself(said: ExportContents) {
        val found = ExportContents(trips.size, sentReports.size, pointCount, settings != null)
        if (found != said) {
            throw damaged("It says it holds $said, and it holds $found: it is not all there")
        }
        if (trips.distinctBy { it.id }.size != trips.size) throw damaged("Two trips share an id")
        if (sentReports.distinctBy { it.id }.size != sentReports.size) {
            throw damaged("Two sent reports share an id")
        }
        (tripsOfPoints - trips.mapTo(mutableSetOf()) { it.id }).firstOrNull()?.let {
            throw damaged("It has GPS points of trip $it, and no such trip")
        }
    }

    private inline fun eachOf(key: String, take: (text: String, number: Int) -> Unit) {
        if (!scanner.beginList()) throw damaged("\"$key\" is not a list")
        var number = 0
        while (true) take(scanner.nextElement() ?: break, ++number)
    }

    private inline fun <reified T> decode(key: String): T = decodeText(scanner.value(), "\"$key\"")

    private inline fun <reified T> decodeText(text: String, what: String): T = try {
        ExportJson.decodeFromString<T>(text)
    } catch (unreadable: SerializationException) {
        // The library's first line says which key and what it found; the rest repeats the text.
        throw damaged("$what cannot be read: ${unreadable.message?.lineSequence()?.first()}")
    }

    private fun damaged(why: String) = DamagedExportException(why)
}

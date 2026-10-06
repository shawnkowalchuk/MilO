package com.shawnkowalchuk.milo.data.transfer

import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.data.MILO_DATABASE_VERSION
import com.shawnkowalchuk.milo.data.point.RawPoint
import com.shawnkowalchuk.milo.data.settings.SettingsStore
import com.shawnkowalchuk.milo.data.settings.TransferredSettings
import com.shawnkowalchuk.milo.data.settings.transferred
import java.io.IOException
import java.io.OutputStream
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** How many raw points are read from the table at a time while an export is written. */
private const val POINT_PAGE = 500

/**
 * Raw points that are stored for a trip that is not, and so are in no export.
 *
 * There should be none. They are what is left when the two databases have come apart: the
 * trips put back by Android over an installation that kept its points, or an import that could
 * not be finished. An export that stopped at them could not be made at all, and neither could
 * the safety copy every import begins with, so they are passed over and counted instead.
 *
 * @param points how many there are, and [tripIds] the trips they name, in order.
 */
data class PointsLeftOut(val points: Long, val tripIds: List<Long>)

/** What a request for an export led to. */
sealed interface ExportWritten {
    /** A trip is being recorded. Nothing was written: its end and its distance are not known. */
    data object TripInProgress : ExportWritten

    /**
     * The whole document was written.
     *
     * @param contents how many of each thing it holds.
     * @param leftOut the raw points that were passed over, or null if there were none (and
     * always when the points were not asked for).
     */
    data class Done(val contents: ExportContents, val leftOut: PointsLeftOut? = null) :
        ExportWritten
}

/**
 * Writes everything of Shawn's that MilO stores into one export document: every closed trip,
 * every report recorded as sent, the settings that mean the same on another phone, and, if
 * asked for, every raw GPS point.
 *
 * It only reads. Where the document goes is the caller's business: a file Shawn picked with
 * Android's file picker, or a safety copy inside MilO before an import.
 *
 * @param appVersion MilO's version as Android names it, for the file's first lines.
 * @param clock wall-clock milliseconds, and [zone] the phone's time zone.
 */
class DataExport(
    private val main: MainTransferDao,
    private val points: PointsTransferDao,
    private val settings: SettingsStore,
    private val appVersion: String,
    private val clock: () -> Long,
    private val zone: () -> ZoneId,
) {
    /**
     * @param out where the document is written. Left open: the caller opened it.
     * @param includePoints false leaves the raw GPS points out, for a small file.
     * @throws IOException if [out] cannot be written.
     * @throws ChangedWhileExportingException if the points changed while the file was written.
     * @throws IllegalStateException for a stored value that cannot be written as JSON at all.
     */
    suspend fun writeTo(out: OutputStream, includePoints: Boolean): ExportWritten =
        withContext(Dispatchers.IO) {
            val rows = main.rowsUnlessOpen(TripStatus.OPEN)
            if (rows == null) return@withContext ExportWritten.TripInProgress
            // The points that exist now. One recorded from here on has a higher id and is left
            // for the next export, so that the file holds exactly as many as it says.
            val newestPoint = if (includePoints) points.highestId() ?: 0L else null
            val tripIds = rows.trips.mapTo(HashSet()) { it.id }
            val (kept, strays) =
                newestPoint?.let { points.countByTripUpTo(it) }.orEmpty().partition {
                    it.tripId in tripIds
                }
            val source =
                ExportSource(
                    exportedAtMs = clock(),
                    zone = zone(),
                    appVersion = appVersion,
                    databaseVersion = MILO_DATABASE_VERSION,
                    settings = readableSettings(),
                    sentReports = rows.sentReports,
                    trips = rows.trips,
                    pointCount = newestPoint?.let { kept.sumOf { it.points } },
                    points = { each -> pointPages(upToId = newestPoint ?: 0L, each) },
                )
            val writer = out.bufferedWriter(Charsets.UTF_8)
            val contents = writeExport(writer, source)
            writer.flush()
            ExportWritten.Done(contents, leftOut(strays))
        }

    private fun leftOut(strays: List<TripPoints>): PointsLeftOut? =
        strays.takeIf { it.isNotEmpty() }?.let { found ->
            PointsLeftOut(found.sumOf { it.points }, found.map { it.tripId }.sorted())
        }

    /**
     * The settings to export, or null if the settings file cannot be read. The trips are
     * exported all the same: an export is what Shawn reaches for when something is wrong, and
     * the file then says that it holds no settings.
     */
    private suspend fun readableSettings(): TransferredSettings? = try {
        settings.current().transferred()
    } catch (unreadable: IOException) {
        null
    }

    private suspend fun pointPages(upToId: Long, each: suspend (List<RawPoint>) -> Unit) {
        var afterId = 0L
        while (true) {
            val page = points.readAfter(afterId, upToId, POINT_PAGE)
            if (page.isEmpty()) return
            each(page)
            afterId = page.last().id
        }
    }
}

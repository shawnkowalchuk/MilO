package com.shawnkowalchuk.milo.data.transfer

import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.data.eventlog.EventLogEntry
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** How much of Shawn's own data the phone holds now. The question before an import says it. */
data class StoredCounts(val trips: Int, val sentReports: Int)

/**
 * Reads an export file and replaces what MilO stores with what the file holds.
 *
 * **Nothing is replaced before the whole file has been checked** ([check]), and each database
 * is replaced in one transaction of its own, so a failure inside one leaves that database as
 * it was. The two cannot share a transaction: they are two files. The trips and the sent
 * reports go first ([replaceMain]), because they are the part that matters and the part whose
 * failure must leave everything untouched. The raw points follow ([replacePoints]); if that
 * fails, the points that are left belong to trips that are gone, and [removePoints] takes them
 * away, so that no trip is ever shown with another trip's track. The file can then be imported
 * once more.
 *
 * A process that is ended between the two transactions can do none of that. For that case the
 * line [replaceMain] writes into the event log says, in the same transaction, that the trips
 * were replaced ([wasReplaced]), and `ImportRun` leaves a note and finishes at the next start.
 *
 * The event log is not replaced: it is the record of what happened on this phone, and it gets
 * the line that says an import was made.
 */
class DataImport(private val main: MainTransferDao, private val points: PointsTransferDao) {
    /** Reads and checks the whole of [file], and changes nothing. */
    suspend fun check(file: File): ExportReading = withContext(Dispatchers.IO) {
        readExport(open = { file.bufferedReader(Charsets.UTF_8) })
    }

    /** How many trips and sent reports an import would replace. */
    suspend fun storedNow(): StoredCounts =
        StoredCounts(trips = main.countTrips(), sentReports = main.countSentReports())

    /**
     * The highest id a trip has on the phone or in [export]: what [replaceMain] will return,
     * known before it runs.
     */
    suspend fun highestTripIdWith(export: CheckedExport): Long =
        maxOf(main.highestTripId() ?: 0L, export.trips.maxOfOrNull { it.id } ?: 0L)

    /**
     * Replaces every trip and every sent report with those of [export], unless a trip is
     * being recorded at that moment.
     *
     * @param said the line the event log gets if, and only if, they were replaced.
     */
    suspend fun replaceMain(export: CheckedExport, said: EventLogEntry): MainReplacement =
        main.replaceUnlessOpen(export.trips, export.sentReports, TripStatus.OPEN, said)

    /** Whether the import that left [begun] came as far as replacing the trips. */
    suspend fun wasReplaced(begun: ImportBegun): Boolean =
        main.countLogLines(begun.saidAtMs, begun.said) > 0

    /**
     * Replaces the raw points of the trips that were replaced with the points of [file], which
     * is read once more for them: they are too many to have been kept from the check. A file
     * without points leaves the phone without any for those trips, since the ones it had were
     * of the trips that are gone.
     *
     * @param export what the check of [file] returned. If the file reads as anything else now,
     * nothing is stored.
     * @param highestTripId what [replaceMain] returned.
     * @return how many points were stored.
     * @throws IOException if the file cannot be read any more, or is no longer what was checked.
     */
    // TODO(debt): one transaction as large as the import leaves a write-ahead log of that size
    // beside points.db (points.db-wal), which SQLite reuses and never shrinks by itself. It
    // holds nothing that is not in the database, and the backup agent empties it the next
    // time Android collects a backup; until then it is disk space (FINDINGS_LOG, 2026-10-06).
    suspend fun replacePoints(file: File, export: CheckedExport, highestTripId: Long): Long =
        points.replaceUpToTrip(highestTripId) { store ->
            if (export.pointCount == null) return@replaceUpToTrip
            val read = readExport(open = { file.bufferedReader(Charsets.UTF_8) }, onPoints = store)
            if (read != ExportReading.Good(export)) {
                // Thrown inside the transaction, so the points stored so far are taken back.
                throw IOException("The export file is no longer what was checked: $read")
            }
        }

    /**
     * Removes the raw points of every trip up to [highestTripId]: the way out when
     * [replacePoints] has failed.
     *
     * @return how many were removed.
     */
    suspend fun removePoints(highestTripId: Long): Int = points.deleteUpToTrip(highestTripId)
}

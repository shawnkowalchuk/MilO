package com.shawnkowalchuk.milo.data.transfer

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Transaction
import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.data.eventlog.EventLogEntry
import com.shawnkowalchuk.milo.data.point.RawPoint
import com.shawnkowalchuk.milo.data.report.SentReport
import com.shawnkowalchuk.milo.data.trip.Trip

// The SQL of an export and an import: every row of a table read, and every row replaced. It
// is kept apart from the DAOs the rest of the app uses, because nothing else may ever empty a
// table. Only `DataExport` and `DataImport` call these. They add queries and no table, so the
// exported schema files are what they were.

/** The trips and the sent reports as they stood at one moment. */
class MainRows(val trips: List<Trip>, val sentReports: List<SentReport>)

/** What became of a replacement of the trips and the sent reports. */
sealed interface MainReplacement {
    /** A trip is being recorded. Nothing was changed. */
    data object TripInProgress : MainReplacement

    /**
     * Every trip and every sent report was replaced.
     *
     * @param highestTripId the highest id any trip had before or has now. A trip that starts
     * from here on is given a higher one, so every raw point up to this id belongs to a trip
     * that was replaced.
     */
    data class Done(val highestTripId: Long) : MainReplacement
}

/** The SQL for exporting and replacing the main database's two tables of Shawn's own data. */
@Dao
interface MainTransferDao {
    @Query("SELECT COUNT(*) FROM trips WHERE status = :status")
    suspend fun countTripsWith(status: TripStatus): Int

    @Query("SELECT COUNT(*) FROM trips")
    suspend fun countTrips(): Int

    @Query("SELECT COUNT(*) FROM sent_reports")
    suspend fun countSentReports(): Int

    @Query("SELECT * FROM trips ORDER BY id")
    suspend fun allTrips(): List<Trip>

    @Query("SELECT * FROM sent_reports ORDER BY id")
    suspend fun allSentReports(): List<SentReport>

    @Query("SELECT MAX(id) FROM trips")
    suspend fun highestTripId(): Long?

    @Query("DELETE FROM trips")
    suspend fun deleteTrips(): Int

    @Query("DELETE FROM sent_reports")
    suspend fun deleteSentReports(): Int

    @Insert
    suspend fun insertTrips(trips: List<Trip>)

    @Insert
    suspend fun insertSentReports(reports: List<SentReport>)

    @Insert
    suspend fun insertLogLine(line: EventLogEntry): Long

    @Query("SELECT COUNT(*) FROM event_log WHERE atMs = :atMs AND message = :message")
    suspend fun countLogLines(atMs: Long, message: String): Int

    /**
     * Every trip and every sent report, read in one transaction so that the two belong to the
     * same moment, or null while a trip is [open]: an export is not made of a trip that has no
     * end yet.
     */
    @Transaction
    suspend fun rowsUnlessOpen(open: TripStatus): MainRows? =
        if (countTripsWith(open) > 0) null else MainRows(allTrips(), allSentReports())

    /**
     * Replaces every trip and every sent report with the ones given, in one transaction: if
     * anything in it fails, the tables are as they were before.
     *
     * The look for a trip in progress is inside the transaction too. A trip that the truck
     * starts while the question on the screen is being answered is found here, and then
     * nothing is replaced; one that starts after this has finished is a new row with a new id.
     *
     * @param said a line for the event log, which is a table of the same database. It is
     * written in this transaction, so it is there exactly if the trips were replaced: the raw
     * points are in another file and follow later, and a process that is ended in between
     * leaves no other trace of how far it came (`ImportRun`).
     */
    @Transaction
    suspend fun replaceUnlessOpen(
        trips: List<Trip>,
        sentReports: List<SentReport>,
        open: TripStatus,
        said: EventLogEntry,
    ): MainReplacement {
        if (countTripsWith(open) > 0) return MainReplacement.TripInProgress
        val highestBefore = highestTripId() ?: 0L
        deleteSentReports()
        deleteTrips()
        insertTrips(trips)
        insertSentReports(sentReports)
        insertLogLine(said)
        return MainReplacement.Done(maxOf(highestBefore, trips.maxOfOrNull { it.id } ?: 0L))
    }
}

/** How many raw points are stored for one trip id. */
data class TripPoints(val tripId: Long, val points: Long)

/** Hands the raw points to store to [store], a few hundred at a time, in order. */
fun interface PointFeed {
    suspend fun into(store: suspend (List<RawPoint>) -> Unit)
}

/** The SQL for exporting and replacing the raw points. */
@Dao
interface PointsTransferDao {
    @Query("SELECT MAX(id) FROM raw_points")
    suspend fun highestId(): Long?

    /**
     * How many of the points up to [upToId] each trip id has. One row for a trip, so the
     * answer is small however many points there are.
     */
    @Query(
        "SELECT tripId, COUNT(*) AS points FROM raw_points WHERE id <= :upToId GROUP BY tripId",
    )
    suspend fun countByTripUpTo(upToId: Long): List<TripPoints>

    /** The next [limit] points after the one with [afterId], up to [upToId], in order. */
    @Query(
        "SELECT * FROM raw_points WHERE id > :afterId AND id <= :upToId ORDER BY id LIMIT :limit",
    )
    suspend fun readAfter(afterId: Long, upToId: Long, limit: Int): List<RawPoint>

    @Query("DELETE FROM raw_points WHERE tripId <= :tripId")
    suspend fun deleteUpToTrip(tripId: Long): Int

    @Insert
    suspend fun insert(points: List<RawPoint>)

    /**
     * Replaces the raw points of every trip up to [highestTripId] with the ones [feed] hands
     * over, in one transaction: if the feed or an insert fails, the points are as they were.
     * The points of a trip with a higher id, one that started after the trips were replaced,
     * are not touched.
     *
     * @return how many points were stored.
     */
    @Transaction
    suspend fun replaceUpToTrip(highestTripId: Long, feed: PointFeed): Long {
        deleteUpToTrip(highestTripId)
        var stored = 0L
        feed.into { batch ->
            // The ids the file's points had are not carried; the table numbers them afresh,
            // in this order, which is the order they were recorded in.
            insert(batch)
            stored += batch.size
        }
        return stored
    }
}

package com.shawnkowalchuk.milo.data.transfer

import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.data.eventlog.EventLogEntry
import com.shawnkowalchuk.milo.data.point.RawPoint
import com.shawnkowalchuk.milo.data.report.SentReport
import com.shawnkowalchuk.milo.data.trip.Trip
import kotlin.coroutines.cancellation.CancellationException

/**
 * What a stand-in throws where the process is to be ended, as Android ends it: the work stops
 * there, an open transaction is taken back, and nothing after it runs: no clean-up, no line in
 * the log, no word to the screen. Being cancelled is the one thing the code under test passes
 * on untouched, so it leaves exactly what a process that was killed leaves.
 */
class ProcessEnded : CancellationException("the process is ended here")

/**
 * The trips and the sent reports, held in memory, for the tests of an export and an import.
 * The two transactions are the interface's own code, so what is tested is what runs on the
 * phone; the stand-in adds what the database adds to a transaction: if anything in it fails,
 * the tables are as they were.
 */
class FakeMainTransferDao : MainTransferDao {
    var trips = listOf<Trip>()
    var sentReports = listOf<SentReport>()

    /** Set to make the next insert of sent reports fail once, as a full disk would. */
    var failNextInsert: Exception? = null

    /** Set to have a trip start just as the tables are about to be replaced. */
    var tripStartsBeforeReplacing: Trip? = null

    /** Set to have the process ended inside the transaction that replaces the tables, once. */
    var endProcessWhileReplacing = false

    /**
     * The lines this DAO wrote into the event log. On the phone the log is a table of the same
     * database; here it is kept apart from the stand-in for the log's own DAO.
     */
    var logLines = listOf<EventLogEntry>()

    override suspend fun countTripsWith(status: TripStatus): Int = trips.count {
        it.status == status
    }

    override suspend fun countTrips(): Int = trips.size

    override suspend fun countSentReports(): Int = sentReports.size

    override suspend fun allTrips(): List<Trip> = trips.sortedBy { it.id }

    override suspend fun allSentReports(): List<SentReport> = sentReports.sortedBy { it.id }

    override suspend fun highestTripId(): Long? = trips.maxOfOrNull { it.id }

    override suspend fun deleteTrips(): Int = trips.size.also { trips = emptyList() }

    override suspend fun deleteSentReports(): Int = sentReports.size.also {
        sentReports =
            emptyList()
    }

    override suspend fun insertTrips(trips: List<Trip>) {
        this.trips += trips
    }

    override suspend fun insertSentReports(reports: List<SentReport>) {
        failNextInsert?.let { failure ->
            failNextInsert = null
            throw failure
        }
        sentReports += reports
    }

    override suspend fun insertLogLine(line: EventLogEntry): Long {
        if (endProcessWhileReplacing) {
            endProcessWhileReplacing = false
            throw ProcessEnded()
        }
        logLines += line
        return logLines.size.toLong()
    }

    override suspend fun countLogLines(atMs: Long, message: String): Int =
        logLines.count { it.atMs == atMs && it.message == message }

    override suspend fun replaceUnlessOpen(
        trips: List<Trip>,
        sentReports: List<SentReport>,
        open: TripStatus,
        said: EventLogEntry,
    ): MainReplacement {
        tripStartsBeforeReplacing?.let { this.trips += it }
        val before = Triple(this.trips, this.sentReports, logLines)
        return try {
            super.replaceUnlessOpen(trips, sentReports, open, said)
        } catch (failure: Exception) {
            this.trips = before.first
            this.sentReports = before.second
            logLines = before.third
            throw failure
        }
    }
}

/** The raw points, held in memory, with the same promise for its one transaction. */
class FakePointsTransferDao : PointsTransferDao {
    var points = listOf<RawPoint>()

    /** Set to make the insert of the batch that holds this many points or more fail, once. */
    var failInsertOnceStoredAtLeast: Int? = null

    /**
     * Set to have a fix arrive just before the points are replaced: the first fix of a trip
     * that started after the trips were.
     */
    var arrivesBeforeReplacing: RawPoint? = null

    /** Set to have the process ended while the points are being replaced, once. */
    var endProcessWhileReplacing = false

    /** Set to have the process ended while points are being removed, once. */
    var endProcessWhileRemoving = false

    /** Set to make every removal of points fail, as a broken database would. */
    var failRemoving = false

    private var inserted = 1_000L

    override suspend fun highestId(): Long? = points.maxOfOrNull { it.id }

    override suspend fun countByTripUpTo(upToId: Long): List<TripPoints> = points
        .filter { it.id <= upToId }
        .groupBy { it.tripId }
        .map { (tripId, ofTrip) -> TripPoints(tripId, ofTrip.size.toLong()) }

    override suspend fun readAfter(afterId: Long, upToId: Long, limit: Int): List<RawPoint> = points
        .sortedBy { it.id }
        .filter { it.id > afterId && it.id <= upToId }
        .take(limit)

    override suspend fun deleteUpToTrip(tripId: Long): Int {
        if (failRemoving) throw IllegalStateException("the database is broken")
        if (endProcessWhileRemoving) {
            endProcessWhileRemoving = false
            throw ProcessEnded()
        }
        val going = points.filter { it.tripId <= tripId }
        points = points - going.toSet()
        return going.size
    }

    override suspend fun insert(points: List<RawPoint>) {
        failInsertOnceStoredAtLeast?.let { limit ->
            if (this.points.size + points.size >= limit) {
                failInsertOnceStoredAtLeast = null
                throw IllegalStateException("database or disk is full")
            }
        }
        // The table numbers the points itself, upwards, whatever id they are handed with.
        this.points += points.map { it.copy(id = ++inserted) }
    }

    override suspend fun replaceUpToTrip(highestTripId: Long, feed: PointFeed): Long {
        arrivesBeforeReplacing?.let { points += it }
        val before = points
        return try {
            val stored = super.replaceUpToTrip(highestTripId, feed)
            // After every point was handed over and before the transaction is closed.
            if (endProcessWhileReplacing) {
                endProcessWhileReplacing = false
                throw ProcessEnded()
            }
            stored
        } catch (failure: Exception) {
            points = before
            throw failure
        }
    }
}

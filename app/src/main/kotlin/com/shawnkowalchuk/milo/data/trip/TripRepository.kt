package com.shawnkowalchuk.milo.data.trip

import com.shawnkowalchuk.milo.core.trip.ClosedTrip
import com.shawnkowalchuk.milo.core.trip.TripStartCause
import com.shawnkowalchuk.milo.core.trip.TripStatus
import kotlinx.coroutines.flow.Flow

/**
 * The only way the rest of the app reads or writes trips.
 *
 * The writes to an open trip mirror the effects of the trip rules (`core/trip/TripEffect`) one
 * for one, and each is safe to repeat: starting twice gives the same trip, and changing a trip
 * that is no longer open changes nothing. The functions that change an open trip return false
 * in that case, so the caller can write the surprise to the event log.
 *
 * The one write that is not safe to repeat is [recordAddressLookup], the only write to a trip
 * that has closed: every call counts as a lookup of its own.
 */
class TripRepository(private val dao: TripDao) {
    /** The trip being recorded or waiting out its grace period, or null when idle. */
    suspend fun findOpenTrip(): Trip? = dao.findNewestWithStatus(TripStatus.OPEN)

    /** Finished trips, newest first. Discarded trips are left out. */
    fun observeFinishedTrips(): Flow<List<Trip>> = dao.observeWithStatus(TripStatus.FINISHED)

    /**
     * The trips that started in a span of time, newest first, and again each time one of them
     * changes. Open, finished and discarded trips are all included: the caller decides what to
     * show and what to count. The span is half-open, so a month is asked for as "from its first
     * instant up to the first instant of the next month" and no trip can fall in two months.
     */
    fun observeTripsStartedBetween(fromMs: Long, untilMs: Long): Flow<List<Trip>> {
        require(fromMs <= untilMs) { "The span ends ($untilMs) before it starts ($fromMs)" }
        return dao.observeStartedBetween(fromMs, untilMs)
    }

    /** Opens a trip, or returns the one that is already open. There is never a second open trip. */
    suspend fun startTrip(startedAtMs: Long, startedBy: TripStartCause, truckSeen: Boolean): Trip =
        dao.insertUnlessOneIsOpen(
            Trip(
                startedAtMs = startedAtMs,
                status = TripStatus.OPEN,
                startedBy = startedBy,
                truckSeen = truckSeen,
            ),
        )

    suspend fun markTruckSeen(tripId: Long): Boolean =
        dao.markTruckSeen(tripId, TripStatus.OPEN) == 1

    suspend fun startGrace(tripId: Long, startedAtMs: Long, deadlineMs: Long): Boolean =
        dao.setGrace(tripId, startedAtMs, deadlineMs, TripStatus.OPEN) == 1

    suspend fun cancelGrace(tripId: Long): Boolean =
        dao.setGrace(tripId, startedAtMs = null, deadlineMs = null, TripStatus.OPEN) == 1

    /**
     * Closes the trip with the result worked out by `core/trip/TripClosing`. A trip under the
     * minimum distance is marked discarded, not deleted: its row and its raw points stay.
     */
    suspend fun closeTrip(tripId: Long, closed: ClosedTrip): Boolean = dao.close(
        tripId = tripId,
        closedStatus = closed.status,
        endedAtMs = closed.endedAtMs,
        distanceMetres = closed.distance.metres,
        startLatitude = closed.start?.latitude,
        startLongitude = closed.start?.longitude,
        endLatitude = closed.end?.latitude,
        endLongitude = closed.end?.longitude,
        open = TripStatus.OPEN,
    ) == 1

    /**
     * The finished trips that still lack a start or an end address and have had fewer than
     * [maxAttempts] failed lookups, newest first. A discarded trip is never among them: it is
     * not looked up.
     */
    suspend fun findTripsLackingAddress(maxAttempts: Int): List<Trip> =
        dao.findLackingAddress(TripStatus.FINISHED, maxAttempts)

    /**
     * Stores the outcome of one address lookup on a finished trip. It writes the address
     * columns only: whatever happens here, the trip's times, distance and positions stay as
     * they were. An address that is already stored is never replaced.
     *
     * Call it once for each lookup, and never again to be sure: every call moves the time of
     * the last attempt to [atMs], and with [stillLacking] counts one more failed attempt.
     *
     * @param startAddress the start address that was found, or null if none was.
     * @param endAddress the end address that was found, or null if none was.
     * @param stillLacking true if the trip is still without one of its addresses after this
     * lookup. It is counted as a failed attempt.
     * @return false if the trip is not a finished one, in which case nothing was written.
     */
    suspend fun recordAddressLookup(
        tripId: Long,
        startAddress: String?,
        endAddress: String?,
        stillLacking: Boolean,
        atMs: Long,
    ): Boolean = dao.recordAddressLookup(
        tripId = tripId,
        startAddress = startAddress,
        endAddress = endAddress,
        failedAttempts = if (stillLacking) 1 else 0,
        atMs = atMs,
        finished = TripStatus.FINISHED,
    ) == 1
}

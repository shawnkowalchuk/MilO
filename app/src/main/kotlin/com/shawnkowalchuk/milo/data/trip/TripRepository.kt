package com.shawnkowalchuk.milo.data.trip

import com.shawnkowalchuk.milo.core.trip.ClosedTrip
import com.shawnkowalchuk.milo.core.trip.TripStartCause
import com.shawnkowalchuk.milo.core.trip.TripStatus
import kotlinx.coroutines.flow.Flow

/**
 * The only way the rest of the app reads or writes trips.
 *
 * The writes mirror the effects of the trip rules (`core/trip/TripEffect`) one for one, and each
 * is safe to repeat: starting twice gives the same trip, and changing a trip that is no longer
 * open changes nothing. The functions that change an open trip return false in that case, so
 * the caller can write the surprise to the event log.
 */
class TripRepository(private val dao: TripDao) {
    /** The trip being recorded or waiting out its grace period, or null when idle. */
    suspend fun findOpenTrip(): Trip? = dao.findNewestWithStatus(TripStatus.OPEN)

    /** Finished trips, newest first. Discarded trips are left out. */
    fun observeFinishedTrips(): Flow<List<Trip>> = dao.observeWithStatus(TripStatus.FINISHED)

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
}

package com.shawnkowalchuk.milo.platform.trip

import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.data.trip.Trip
import com.shawnkowalchuk.milo.data.trip.TripDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * The trips table, held in memory. It stands in for the database in the tests of the trip
 * controller and of the address lookup, and behaves like the real queries where they depend on
 * it: an update changes a row only if the row has the status the query names.
 */
class FakeTripDao : TripDao {
    val rows = mutableListOf<Trip>()

    /** Set to make the next insert fail once, as a full disk would. */
    var failNextInsert: Exception? = null

    /** Set to make the next write of an address lookup fail once, the same way. */
    var failNextAddressWrite: Exception? = null

    override suspend fun insert(trip: Trip): Long {
        failNextInsert?.let { failure ->
            failNextInsert = null
            throw failure
        }
        val id = rows.size + 1L
        rows += trip.copy(id = id)
        return id
    }

    override suspend fun findNewestWithStatus(status: TripStatus): Trip? =
        rows.lastOrNull { it.status == status }

    override fun observeWithStatus(status: TripStatus): Flow<List<Trip>> =
        flowOf(rows.filter { it.status == status })

    override fun observeStartedBetween(fromMs: Long, untilMs: Long): Flow<List<Trip>> =
        flowOf(rows.filter { it.startedAtMs in fromMs until untilMs }.reversed())

    override suspend fun markTruckSeen(tripId: Long, open: TripStatus): Int =
        change(tripId, open) { it.copy(truckSeen = true) }

    override suspend fun setGrace(
        tripId: Long,
        startedAtMs: Long?,
        deadlineMs: Long?,
        open: TripStatus,
    ): Int = change(tripId, open) {
        it.copy(graceStartedAtMs = startedAtMs, graceDeadlineMs = deadlineMs)
    }

    override suspend fun close(
        tripId: Long,
        closedStatus: TripStatus,
        endedAtMs: Long,
        distanceMetres: Double,
        startLatitude: Double?,
        startLongitude: Double?,
        endLatitude: Double?,
        endLongitude: Double?,
        open: TripStatus,
    ): Int = change(tripId, open) {
        it.copy(
            status = closedStatus,
            endedAtMs = endedAtMs,
            distanceMetres = distanceMetres,
            startLatitude = startLatitude,
            startLongitude = startLongitude,
            endLatitude = endLatitude,
            endLongitude = endLongitude,
            graceStartedAtMs = null,
            graceDeadlineMs = null,
        )
    }

    override suspend fun findLackingAddress(finished: TripStatus, maxAttempts: Int): List<Trip> =
        rows
            .filter { it.status == finished && it.addressAttempts < maxAttempts }
            .filter { it.startAddress == null || it.endAddress == null }
            .sortedByDescending { it.id }

    override suspend fun recordAddressLookup(
        tripId: Long,
        startAddress: String?,
        endAddress: String?,
        failedAttempts: Int,
        atMs: Long,
        finished: TripStatus,
    ): Int {
        failNextAddressWrite?.let { failure ->
            failNextAddressWrite = null
            throw failure
        }
        return change(tripId, finished) {
            it.copy(
                startAddress = it.startAddress ?: startAddress,
                endAddress = it.endAddress ?: endAddress,
                addressAttempts = it.addressAttempts + failedAttempts,
                addressLastAttemptAtMs = atMs,
            )
        }
    }

    /** Like the real queries: only a row with the expected status is changed. */
    private fun change(tripId: Long, status: TripStatus, update: (Trip) -> Trip): Int {
        val index = rows.indexOfFirst { it.id == tripId && it.status == status }
        if (index < 0) return 0
        rows[index] = update(rows[index])
        return 1
    }
}

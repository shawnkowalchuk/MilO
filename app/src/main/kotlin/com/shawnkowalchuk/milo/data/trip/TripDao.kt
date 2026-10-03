package com.shawnkowalchuk.milo.data.trip

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Transaction
import com.shawnkowalchuk.milo.core.trip.TripStatus
import kotlinx.coroutines.flow.Flow

/**
 * The SQL for the trips table. Only [TripRepository] calls it.
 *
 * Every update takes the "open" status as a parameter and matches on it, so a late or repeated
 * call can never alter a trip that has already been closed. Each returns the number of rows it
 * changed: 1, or 0 when the trip was not open.
 */
@Dao
interface TripDao {
    @Insert
    suspend fun insert(trip: Trip): Long

    @Query("SELECT * FROM trips WHERE status = :status ORDER BY id DESC LIMIT 1")
    suspend fun findNewestWithStatus(status: TripStatus): Trip?

    @Query("SELECT * FROM trips WHERE status = :status ORDER BY startedAtMs DESC")
    fun observeWithStatus(status: TripStatus): Flow<List<Trip>>

    /**
     * Inserts [trip] unless a trip is already open, in which case that one is returned. The check
     * and the insert share a transaction, so two triggers firing together cannot both insert.
     */
    @Transaction
    suspend fun insertUnlessOneIsOpen(trip: Trip): Trip {
        val alreadyOpen = findNewestWithStatus(TripStatus.OPEN)
        return alreadyOpen ?: trip.copy(id = insert(trip))
    }

    @Query("UPDATE trips SET truckSeen = 1 WHERE id = :tripId AND status = :open")
    suspend fun markTruckSeen(tripId: Long, open: TripStatus): Int

    /** Pass two times to start the grace period, two nulls to cancel it. */
    @Query(
        "UPDATE trips SET graceStartedAtMs = :startedAtMs, graceDeadlineMs = :deadlineMs " +
            "WHERE id = :tripId AND status = :open",
    )
    suspend fun setGrace(tripId: Long, startedAtMs: Long?, deadlineMs: Long?, open: TripStatus): Int

    @Query(
        "UPDATE trips SET status = :closedStatus, endedAtMs = :endedAtMs, " +
            "distanceMetres = :distanceMetres, " +
            "startLatitude = :startLatitude, startLongitude = :startLongitude, " +
            "endLatitude = :endLatitude, endLongitude = :endLongitude, " +
            "graceStartedAtMs = NULL, graceDeadlineMs = NULL " +
            "WHERE id = :tripId AND status = :open",
    )
    suspend fun close(
        tripId: Long,
        closedStatus: TripStatus,
        endedAtMs: Long,
        distanceMetres: Double,
        startLatitude: Double?,
        startLongitude: Double?,
        endLatitude: Double?,
        endLongitude: Double?,
        open: TripStatus,
    ): Int
}

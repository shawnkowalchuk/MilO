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
 * Every update of a trip in progress takes the "open" status as a parameter and matches on it,
 * so a late or repeated call can never alter a trip that has already been closed. Each returns
 * the number of rows it changed: 1, or 0 when the trip was not open. The two updates of a closed
 * trip match on a status the same way: [recordAddressLookup] on "finished", writing nothing but
 * the four address columns, and [changeStatus] on the status it is given, writing nothing but
 * the status.
 */
@Dao
interface TripDao {
    @Insert
    suspend fun insert(trip: Trip): Long

    @Query("SELECT * FROM trips WHERE status = :status ORDER BY id DESC LIMIT 1")
    suspend fun findNewestWithStatus(status: TripStatus): Trip?

    @Query("SELECT * FROM trips WHERE id = :tripId")
    suspend fun findById(tripId: Long): Trip?

    @Query("SELECT * FROM trips WHERE status = :status ORDER BY startedAtMs DESC")
    fun observeWithStatus(status: TripStatus): Flow<List<Trip>>

    /**
     * Every trip that started in the half-open range from [fromMs] up to, not including,
     * [untilMs], whatever its status, newest first. Read through the index on `startedAtMs`.
     */
    @Query(
        "SELECT * FROM trips WHERE startedAtMs >= :fromMs AND startedAtMs < :untilMs " +
            "ORDER BY startedAtMs DESC, id DESC",
    )
    fun observeStartedBetween(fromMs: Long, untilMs: Long): Flow<List<Trip>>

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

    /**
     * The trips with [finished] status that still lack an address and have had fewer than
     * [maxAttempts] failed lookups, newest first. Open, discarded and deleted trips are never
     * returned.
     */
    @Query(
        "SELECT * FROM trips WHERE status = :finished " +
            "AND (startAddress IS NULL OR endAddress IS NULL) " +
            "AND addressAttempts < :maxAttempts ORDER BY id DESC",
    )
    suspend fun findLackingAddress(finished: TripStatus, maxAttempts: Int): List<Trip>

    /**
     * Writes what one lookup found. An address that is already stored is kept, whatever is
     * passed, and a null leaves the column as it is. Nothing but the four address columns is
     * touched, and only on a trip with [finished] status.
     *
     * @param failedAttempts 1 if the lookup left an address of the trip missing, else 0.
     */
    @Query(
        "UPDATE trips SET startAddress = COALESCE(startAddress, :startAddress), " +
            "endAddress = COALESCE(endAddress, :endAddress), " +
            "addressAttempts = addressAttempts + :failedAttempts, " +
            "addressLastAttemptAtMs = :atMs " +
            "WHERE id = :tripId AND status = :finished",
    )
    suspend fun recordAddressLookup(
        tripId: Long,
        startAddress: String?,
        endAddress: String?,
        failedAttempts: Int,
        atMs: Long,
        finished: TripStatus,
    ): Int

    /**
     * Moves a trip from one status to another and changes nothing else on its row. A trip that
     * does not have the status [from] is left alone, so a second call, or a call for a trip
     * that is still open, changes nothing.
     */
    @Query("UPDATE trips SET status = :to WHERE id = :tripId AND status = :from")
    suspend fun changeStatus(tripId: Long, from: TripStatus, to: TripStatus): Int
}

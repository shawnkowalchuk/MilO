package com.shawnkowalchuk.milo.data.trip

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Transaction
import com.shawnkowalchuk.milo.core.schedule.TripCategory
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
 *
 * Business or Personal is written in three places and no other. [close] stores what the
 * schedule made of the trip in the same update that closes it. [sortUnsorted] does the same
 * for a closed trip that has no category yet, and for no other. [setCategoryByHand] is Shawn's
 * own choice for a finished trip, and the only one that marks the row as set by hand.
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
            "graceStartedAtMs = NULL, graceDeadlineMs = NULL, " +
            "category = :category, ranPastSchedule = :ranPastSchedule, " +
            "ignoredOutsideSchedule = :ignoredOutsideSchedule " +
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
        category: TripCategory?,
        ranPastSchedule: Boolean,
        ignoredOutsideSchedule: Boolean,
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

    /**
     * The closed trips that have never been sorted into Business or Personal, oldest first:
     * every status but [open], no category, and not set by hand. The same three conditions
     * guard [sortUnsorted], and `needsSorting` in `TripCategoryCatchUp.kt` is the rule in
     * Kotlin.
     */
    @Query(
        "SELECT * FROM trips WHERE status != :open AND category IS NULL " +
            "AND categorySetByHand = 0 ORDER BY id",
    )
    suspend fun findUnsorted(open: TripStatus): List<Trip>

    /**
     * Gives a closed trip that has no category yet the one the schedule makes of it. A trip
     * that has a category by now, or that Shawn has set by hand, is not matched, so the
     * catch-up can never undo a choice of his, however late its write arrives. Nothing but the
     * two columns is touched; the status above all is left alone.
     */
    @Query(
        "UPDATE trips SET category = :category, ranPastSchedule = :ranPastSchedule " +
            "WHERE id = :tripId AND status != :open AND category IS NULL " +
            "AND categorySetByHand = 0",
    )
    suspend fun sortUnsorted(
        tripId: Long,
        category: TripCategory,
        ranPastSchedule: Boolean,
        open: TripStatus,
    ): Int

    /**
     * Shawn's own choice of Business or Personal for a trip with [finished] status. It writes
     * the category and marks it as set by hand, and nothing else: the status is not touched, so
     * a kept trip marked Personal stays a kept trip whatever the setting for trips outside the
     * schedule says. A trip that already has [category] is not matched, so a second press
     * changes nothing.
     */
    @Query(
        "UPDATE trips SET category = :category, categorySetByHand = 1 " +
            "WHERE id = :tripId AND status = :finished " +
            "AND (category IS NULL OR category != :category)",
    )
    suspend fun setCategoryByHand(tripId: Long, category: TripCategory, finished: TripStatus): Int
}

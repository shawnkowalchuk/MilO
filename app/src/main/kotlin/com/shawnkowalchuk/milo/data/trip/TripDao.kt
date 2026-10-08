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
 * Business or Personal is written by [close], which stores what the schedule made of the trip
 * in the same update that closes it; by [sortUnsorted], which does the same for a closed trip
 * that has no category yet, and for no other; by [setCategoryByHand], Shawn's own choice for a
 * finished trip on the Trips screen; and by [writeByHand].
 *
 * [writeByHand] is the one write behind the edit form: an edit of a finished trip, and
 * "Restore recorded values". It is only ever called by [rewriteFinished], which reads the row
 * and writes it back inside one transaction. A trip that is typed in by hand is an [insert].
 */
@Dao
interface TripDao {
    @Insert
    suspend fun insert(trip: Trip): Long

    @Query("SELECT * FROM trips WHERE status = :status ORDER BY id DESC LIMIT 1")
    suspend fun findNewestWithStatus(status: TripStatus): Trip?

    @Query("SELECT * FROM trips WHERE id = :tripId")
    suspend fun findById(tripId: Long): Trip?

    /**
     * The latest end time among the trips that have closed, whatever became of them: every
     * status but [open]. Null if no trip has closed yet. A read only, of a column that exists.
     */
    @Query("SELECT MAX(endedAtMs) FROM trips WHERE status != :open")
    suspend fun findNewestEndedAtMs(open: TripStatus): Long?

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

    /** The open trip's vehicle, written once: a trip does not change vehicles (2026-10-08). */
    @Query(
        "UPDATE trips SET vehicleAddress = :address " +
            "WHERE id = :tripId AND status = :open AND vehicleAddress IS NULL",
    )
    suspend fun setVehicle(tripId: Long, address: String, open: TripStatus): Int

    /**
     * Gives every trip that was in the truck before MilO knew several vehicles the truck's
     * address: those it saw connected and those typed in by hand (`TripVehicleCatchUp`).
     */
    @Query(
        "UPDATE trips SET vehicleAddress = :address " +
            "WHERE vehicleAddress IS NULL AND (truckSeen = 1 OR addedByHand = 1)",
    )
    suspend fun fillVehicle(address: String): Int

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
     * The trips with [finished] status that still lack an address the lookup may fill in, and
     * have had fewer than [maxAttempts] failed lookups, newest first. Open, discarded and
     * deleted trips are never returned, and an address that is Shawn's own (typed, or emptied
     * by hand) does not count as lacking.
     */
    @Query(
        "SELECT * FROM trips WHERE status = :finished " +
            "AND ((startAddress IS NULL AND startAddressByHand = 0) " +
            "OR (endAddress IS NULL AND endAddressByHand = 0)) " +
            "AND addressAttempts < :maxAttempts ORDER BY id DESC",
    )
    suspend fun findLackingAddress(finished: TripStatus, maxAttempts: Int): List<Trip>

    /**
     * Writes what one lookup found. An address that is already stored is kept, whatever is
     * passed, and a null leaves the column as it is. An address that is Shawn's own is never
     * written to, even where he left it empty: the condition is in the update itself, so a
     * lookup that was under way while he saved the edit form cannot undo what he typed.
     * Nothing but the four address columns is touched, and only on a trip with [finished]
     * status.
     *
     * @param failedAttempts 1 if the lookup left an address of the trip missing, else 0.
     */
    @Query(
        "UPDATE trips SET " +
            "startAddress = CASE WHEN startAddressByHand = 1 THEN startAddress " +
            "ELSE COALESCE(startAddress, :startAddress) END, " +
            "endAddress = CASE WHEN endAddressByHand = 1 THEN endAddress " +
            "ELSE COALESCE(endAddress, :endAddress) END, " +
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
     * Removes a trip that is still open, row and all: the one delete of the table, for a drive
     * in another vehicle (`leftInAnotherVehicle`). A trip that has been closed is never removed.
     */
    @Query("DELETE FROM trips WHERE id = :tripId AND status = :open")
    suspend fun deleteOpen(tripId: Long, open: TripStatus): Int

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

    /**
     * Writes the figures of a trip with [finished] status that Shawn's own hand may change: its
     * times, distance and addresses, Business or Personal, and the marks and kept figures that
     * go with an edit. A trip in any other status is not matched, so one that is being recorded,
     * or was deleted while the form was open, is never written to.
     *
     * Called by [rewriteFinished] only, which works the values out from the row as it is read
     * in the same transaction.
     */
    @Query(
        "UPDATE trips SET startedAtMs = :startedAtMs, endedAtMs = :endedAtMs, " +
            "distanceMetres = :distanceMetres, " +
            "startAddress = :startAddress, endAddress = :endAddress, " +
            "startAddressByHand = :startAddressByHand, endAddressByHand = :endAddressByHand, " +
            "addressAttempts = :addressAttempts, " +
            "addressLastAttemptAtMs = :addressLastAttemptAtMs, " +
            "category = :category, categorySetByHand = :categorySetByHand, " +
            "ranPastSchedule = :ranPastSchedule, editedByHand = :editedByHand, " +
            "recordedStartedAtMs = :recordedStartedAtMs, " +
            "recordedEndedAtMs = :recordedEndedAtMs, " +
            "recordedDistanceMetres = :recordedDistanceMetres " +
            "WHERE id = :tripId AND status = :finished",
    )
    suspend fun writeByHand(
        tripId: Long,
        startedAtMs: Long,
        endedAtMs: Long?,
        distanceMetres: Double,
        startAddress: String?,
        endAddress: String?,
        startAddressByHand: Boolean,
        endAddressByHand: Boolean,
        addressAttempts: Int,
        addressLastAttemptAtMs: Long?,
        category: TripCategory?,
        categorySetByHand: Boolean,
        ranPastSchedule: Boolean,
        editedByHand: Boolean,
        recordedStartedAtMs: Long?,
        recordedEndedAtMs: Long?,
        recordedDistanceMetres: Double?,
        finished: TripStatus,
    ): Int

    /**
     * Makes one of Shawn's own changes to a finished trip: reads the row, asks [rewrite] what
     * it is to become, and stores that with [writeByHand]. The three share a transaction, so
     * the address lookup or the trip rules cannot write to the table in between, and what
     * [rewrite] saw is what is replaced.
     *
     * @param rewrite a pure rule from `TripEdit.kt`. Of what it answers, only the columns
     * [writeByHand] names are stored.
     */
    @Transaction
    suspend fun rewriteFinished(
        tripId: Long,
        finished: TripStatus,
        rewrite: TripRewrite,
    ): ByHandOutcome {
        val stored = findById(tripId)
        if (stored == null || stored.status != finished) return ByHandOutcome.Refused(stored)
        val wanted = rewrite.of(stored) ?: return ByHandOutcome.Refused(stored)
        if (wanted == stored) return ByHandOutcome.Unchanged(stored)
        val written =
            writeByHand(
                tripId = tripId,
                startedAtMs = wanted.startedAtMs,
                endedAtMs = wanted.endedAtMs,
                distanceMetres = wanted.distanceMetres,
                startAddress = wanted.startAddress,
                endAddress = wanted.endAddress,
                startAddressByHand = wanted.startAddressByHand,
                endAddressByHand = wanted.endAddressByHand,
                addressAttempts = wanted.addressAttempts,
                addressLastAttemptAtMs = wanted.addressLastAttemptAtMs,
                category = wanted.category,
                categorySetByHand = wanted.categorySetByHand,
                ranPastSchedule = wanted.ranPastSchedule,
                editedByHand = wanted.editedByHand,
                recordedStartedAtMs = wanted.recordedStartedAtMs,
                recordedEndedAtMs = wanted.recordedEndedAtMs,
                recordedDistanceMetres = wanted.recordedDistanceMetres,
                finished = finished,
            )
        // Read back, not assumed: the outcome carries what the row really holds now.
        val after = findById(tripId)
        return if (written == 1 && after != null) {
            ByHandOutcome.Done(before = stored, after = after)
        } else {
            ByHandOutcome.Refused(after)
        }
    }
}

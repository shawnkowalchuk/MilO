package com.shawnkowalchuk.milo.platform.trip

import com.shawnkowalchuk.milo.core.schedule.TripCategory
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

    /** Set to make the next change of a trip's status fail once, the same way. */
    var failNextStatusChange: Exception? = null

    /** Set to make the next write of Business or Personal to a closed trip fail once. */
    var failNextCategoryWrite: Exception? = null

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

    override suspend fun findById(tripId: Long): Trip? = rows.firstOrNull { it.id == tripId }

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
        category: TripCategory?,
        ranPastSchedule: Boolean,
        ignoredOutsideSchedule: Boolean,
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
            category = category,
            ranPastSchedule = ranPastSchedule,
            ignoredOutsideSchedule = ignoredOutsideSchedule,
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

    override suspend fun changeStatus(tripId: Long, from: TripStatus, to: TripStatus): Int {
        failNextStatusChange?.let { failure ->
            failNextStatusChange = null
            throw failure
        }
        return change(tripId, from) { it.copy(status = to) }
    }

    // The three conditions are written out here as the SQL has them, and not taken from
    // needsSorting(): a test compares the two, so that the rule in Kotlin and the query's
    // conditions cannot drift apart unnoticed.
    private fun unsorted(trip: Trip, open: TripStatus): Boolean =
        trip.status != open && trip.category == null && !trip.categorySetByHand

    override suspend fun findUnsorted(open: TripStatus): List<Trip> =
        rows.filter { unsorted(it, open) }.sortedBy { it.id }

    override suspend fun sortUnsorted(
        tripId: Long,
        category: TripCategory,
        ranPastSchedule: Boolean,
        open: TripStatus,
    ): Int {
        failNextCategoryWrite?.let { failure ->
            failNextCategoryWrite = null
            throw failure
        }
        return changeIf(tripId, { unsorted(it, open) }) {
            it.copy(category = category, ranPastSchedule = ranPastSchedule)
        }
    }

    override suspend fun setCategoryByHand(
        tripId: Long,
        category: TripCategory,
        finished: TripStatus,
    ): Int {
        failNextCategoryWrite?.let { failure ->
            failNextCategoryWrite = null
            throw failure
        }
        return changeIf(tripId, { it.status == finished && it.category != category }) {
            it.copy(category = category, categorySetByHand = true)
        }
    }

    /** Like the real queries: only a row with the expected status is changed. */
    private fun change(tripId: Long, status: TripStatus, update: (Trip) -> Trip): Int =
        changeIf(tripId, { it.status == status }, update)

    private fun changeIf(tripId: Long, matches: (Trip) -> Boolean, update: (Trip) -> Trip): Int {
        val index = rows.indexOfFirst { it.id == tripId && matches(it) }
        if (index < 0) return 0
        rows[index] = update(rows[index])
        return 1
    }
}

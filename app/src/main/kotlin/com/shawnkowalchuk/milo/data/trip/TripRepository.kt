package com.shawnkowalchuk.milo.data.trip

import com.shawnkowalchuk.milo.core.schedule.TripCategory
import com.shawnkowalchuk.milo.core.schedule.TripClassification
import com.shawnkowalchuk.milo.core.schedule.TripFiling
import com.shawnkowalchuk.milo.core.schedule.WorkSchedule
import com.shawnkowalchuk.milo.core.trip.ClosedTrip
import com.shawnkowalchuk.milo.core.trip.TripStartCause
import com.shawnkowalchuk.milo.core.trip.TripStatus
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow

/**
 * The only way the rest of the app reads or writes trips.
 *
 * The writes to an open trip mirror the effects of the trip rules (`core/trip/TripEffect`) one
 * for one, and each is safe to repeat: starting twice gives the same trip, and changing a trip
 * that is no longer open changes nothing. The functions that change an open trip return false
 * in that case, so the caller can write the surprise to the event log.
 *
 * Six writes are made to a trip that has closed. [recordAddressLookup] is the one write that
 * is not safe to repeat: every call counts as a lookup of its own. [correct] is Shawn's own
 * change to a trip (delete, restore, count after all); it changes the status and nothing else,
 * and a second call changes nothing. [sortUnsorted] gives a trip recorded before there was a
 * work schedule its Business or Personal, once. [setCategoryByHand] is Shawn's own choice of
 * the two, and nothing but another choice of his changes it afterwards. [editByHand] and
 * [restoreRecorded] are the edit form's: they change a finished trip's times, distance and
 * addresses, and put back what MilO recorded. A repeated one changes nothing.
 *
 * One more makes a row that was never open: [addByHand], a trip Shawn types in.
 */
class TripRepository(private val dao: TripDao) {
    /** The trip being recorded or waiting out its grace period, or null when idle. */
    suspend fun findOpenTrip(): Trip? = dao.findNewestWithStatus(TripStatus.OPEN)

    /** One trip by its id, whatever its status, or null if there is none. */
    suspend fun findTrip(tripId: Long): Trip? = dao.findById(tripId)

    /** Finished trips, newest first. Discarded and deleted trips are left out. */
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
     * Closes the trip with the result worked out by `core/trip/TripClosing`, and stores in the
     * same update what the work schedule made of it ([filing]), so a trip is never closed
     * without having been sorted. A trip under the minimum distance is marked discarded, not
     * deleted: its row and its raw points stay.
     *
     * A trip that the trip rules kept and [filing] says to ignore is stored as discarded too,
     * with everything that was measured, and marked as ignored. Nothing is lost: it is listed
     * with the discarded trips and can be counted after all.
     */
    suspend fun closeTrip(tripId: Long, closed: ClosedTrip, filing: TripFiling): Boolean =
        dao.close(
            tripId = tripId,
            closedStatus = if (filing.ignored) TripStatus.DISCARDED else closed.status,
            endedAtMs = closed.endedAtMs,
            distanceMetres = closed.distance.metres,
            startLatitude = closed.start?.latitude,
            startLongitude = closed.start?.longitude,
            endLatitude = closed.end?.latitude,
            endLongitude = closed.end?.longitude,
            category = filing.classification?.category,
            ranPastSchedule = filing.classification?.ranPastSchedule == true,
            ignoredOutsideSchedule = filing.ignored,
            open = TripStatus.OPEN,
        ) == 1

    /**
     * The finished trips that still lack a start or an end address and have had fewer than
     * [maxAttempts] failed lookups, newest first. A discarded or a deleted trip is never among
     * them: neither is looked up.
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

    /**
     * Makes one of Shawn's changes to a closed trip: deletes a finished one, restores a deleted
     * one, or counts a discarded one after all. Only the status is written. Whether the change
     * may be made is decided by the update itself, which matches on the status the change
     * starts from, so a trip that is still being recorded can never be deleted, whatever the
     * screen showed when the button was pressed.
     *
     * The trip's raw points are never touched: they are what a restored or counted trip can
     * still be recalculated from.
     */
    suspend fun correct(tripId: Long, correction: TripCorrection): TripCorrectionOutcome {
        val changed = dao.changeStatus(tripId, from = correction.from, to = correction.to) == 1
        val trip = dao.findById(tripId)
        return if (changed && trip != null) {
            TripCorrectionOutcome.Done(trip)
        } else {
            TripCorrectionOutcome.Refused(found = trip?.status)
        }
    }

    /**
     * The closed trips that have no Business or Personal yet, oldest first: the ones recorded
     * before MilO had a work schedule, whatever their status. A trip Shawn has set by hand is
     * never among them.
     */
    suspend fun findUnsortedTrips(): List<Trip> = dao.findUnsorted(TripStatus.OPEN)

    /**
     * Gives an unsorted closed trip what the schedule makes of it. Only the category and the
     * ran-past-schedule flag are written; the status is never changed here, so a trip is never
     * discarded after the fact.
     *
     * @return false if the trip was not an unsorted closed trip any more, for example because
     * Shawn marked it by hand in the meantime. Nothing was written then.
     */
    suspend fun sortUnsorted(tripId: Long, classification: TripClassification): Boolean =
        dao.sortUnsorted(
            tripId = tripId,
            category = classification.category,
            ranPastSchedule = classification.ranPastSchedule,
            open = TripStatus.OPEN,
        ) == 1

    /**
     * Shawn's own choice of Business or Personal for a finished trip. From then on the trip is
     * marked as set by hand. Whether the choice may be made is decided by the update itself,
     * like [correct]: the trip must be a finished one that does not have [category] already.
     */
    suspend fun setCategoryByHand(tripId: Long, category: TripCategory): CategoryChangeOutcome {
        val before = dao.findById(tripId)
        val changed = dao.setCategoryByHand(tripId, category, TripStatus.FINISHED) == 1
        val after = dao.findById(tripId)
        return if (changed && after != null) {
            CategoryChangeOutcome.Done(trip = after, was = before?.category)
        } else {
            CategoryChangeOutcome.Refused(found = after)
        }
    }

    /**
     * Stores one save of the edit form on a finished trip. What is written, and whether the
     * trip is marked as edited by it, is decided by `editedTrip` from the row as it is at this
     * moment; a trip that is not a finished one is refused by the write itself, like [correct].
     *
     * @param schedule the work schedule as it is now, or null if the settings cannot be read.
     * It is used only if the trip has to be sorted again (`refileTrip`).
     */
    suspend fun editByHand(
        tripId: Long,
        edit: TripEdit,
        schedule: WorkSchedule?,
        zone: ZoneId,
    ): ByHandOutcome = dao.rewriteFinished(tripId, TripStatus.FINISHED) { stored ->
        editedTrip(stored, edit, schedule, zone)
    }

    /**
     * Puts back what MilO recorded of an edited trip (`restoredTrip`): its times and its
     * distance, with any address Shawn typed handed back to the lookup. Refused for a trip
     * that has nothing to put back.
     */
    suspend fun restoreRecorded(
        tripId: Long,
        schedule: WorkSchedule?,
        zone: ZoneId,
    ): ByHandOutcome = dao.rewriteFinished(tripId, TripStatus.FINISHED) { stored ->
        restoredTrip(stored, schedule, zone)
    }

    /**
     * Stores a trip Shawn typed in, as a finished trip marked as added by hand
     * (`tripAddedByHand`). It was never open, so the rule that only one trip is open at a time
     * is not touched by it, and the trip rules never see it.
     *
     * @return the row as stored, with its id.
     */
    suspend fun addByHand(typed: TypedTrip, schedule: WorkSchedule?, zone: ZoneId): Trip {
        val trip = tripAddedByHand(typed, schedule, zone)
        return trip.copy(id = dao.insert(trip))
    }
}

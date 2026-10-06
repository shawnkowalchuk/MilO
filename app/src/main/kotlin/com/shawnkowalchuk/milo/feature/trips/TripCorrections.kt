package com.shawnkowalchuk.milo.feature.trips

import com.shawnkowalchuk.milo.core.schedule.TripCategory
import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.trip.CategoryChangeOutcome
import com.shawnkowalchuk.milo.data.trip.TripCorrection
import com.shawnkowalchuk.milo.data.trip.TripCorrectionOutcome
import com.shawnkowalchuk.milo.data.trip.TripRepository
import com.shawnkowalchuk.milo.data.trip.inWords
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt

/**
 * Makes Shawn's changes to closed trips (delete, restore, count after all, and mark as Business
 * or Personal) and writes each one to the event log. A month's total can only be trusted if
 * every change to it by hand can be read back, so there is exactly one line for every attempt:
 * made, refused, or failed.
 *
 * @param clock wall-clock milliseconds.
 */
class TripCorrections(
    private val trips: TripRepository,
    private val eventLog: EventLogRepository,
    private val clock: () -> Long,
) {
    /**
     * @return true if the change was made. False if it was refused (the trip was not in the
     * state the change starts from, for example still being recorded) or storage failed; the
     * event log says which, and nothing was changed.
     */
    suspend fun apply(tripId: Long, correction: TripCorrection): Boolean {
        val outcome =
            try {
                trips.correct(tripId, correction)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                // Storage failed, whatever it threw. Left alone, the exception would end the
                // process, and the trip service runs in it. If the log cannot be written either,
                // that does end it, on purpose: a failure must not vanish.
                val what = "Trip $tripId: ${correction.inWords()} failed in storage"
                eventLog.add(clock(), EventCategory.ERROR, what, failure.stackTraceToString())
                return false
            }
        eventLog.add(clock(), EventCategory.TRIP, correctionText(tripId, correction, outcome))
        return outcome is TripCorrectionOutcome.Done
    }

    /**
     * Marks a finished trip as [category] by Shawn's own choice. Only the category is written,
     * with the note that it was set by hand: the trip stays a finished, counted trip. That
     * holds for a trip marked Personal while trips outside the schedule are set to be ignored
     * too. That setting is applied once, when a trip is finalised, and Shawn has now dealt with
     * this one himself.
     *
     * @return true if the trip was marked, on the same terms as [apply].
     */
    suspend fun mark(tripId: Long, category: TripCategory): Boolean {
        val outcome =
            try {
                trips.setCategoryByHand(tripId, category)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                // Every kind of failure, for the reason given in apply().
                val what = "Trip $tripId: mark as ${category.inWords()} failed in storage"
                eventLog.add(clock(), EventCategory.ERROR, what, failure.stackTraceToString())
                return false
            }
        eventLog.add(clock(), EventCategory.TRIP, markedText(tripId, category, outcome))
        return outcome is CategoryChangeOutcome.Done
    }
}

/** The event-log line for one change by hand: what was asked for and what became of it. */
internal fun correctionText(
    tripId: Long,
    correction: TripCorrection,
    outcome: TripCorrectionOutcome,
): String = when (outcome) {
    is TripCorrectionOutcome.Done -> {
        val metres = outcome.trip.distanceMetres.roundToInt()
        val was = correction.from.inWords()
        when (correction) {
            TripCorrection.DELETE ->
                "Trip $tripId: deleted on the Trips screen ($metres m, was $was). It is no " +
                    "longer counted. Its row and its GPS points are kept, and Restore puts it back"

            TripCorrection.RESTORE ->
                "Trip $tripId: restored on the Trips screen ($metres m, was $was). It is " +
                    "counted again"

            TripCorrection.COUNT ->
                "Trip $tripId: counted on the Trips screen ($metres m, was $was). It is now a " +
                    "finished trip"
        }
    }

    is TripCorrectionOutcome.Refused -> {
        val why =
            when (outcome.found) {
                null -> "there is no such trip"
                TripStatus.OPEN -> "it is still being recorded"
                else -> "it is ${outcome.found.inWords()}, not ${correction.from.inWords()}"
            }
        "Trip $tripId: ${correction.inWords()} refused on the Trips screen: $why. Nothing changed"
    }
}

/** The event-log line for one marking by hand: what was asked for and what became of it. */
internal fun markedText(
    tripId: Long,
    category: TripCategory,
    outcome: CategoryChangeOutcome,
): String = when (outcome) {
    is CategoryChangeOutcome.Done -> {
        val metres = outcome.trip.distanceMetres.roundToInt()
        "Trip $tripId: marked ${category.inWords()} by hand on the Trips screen ($metres m, " +
            "was ${outcome.was.inWords()}). Nothing else about it changed, and the work " +
            "schedule no longer decides what it is"
    }

    is CategoryChangeOutcome.Refused -> {
        val found = outcome.found
        val why =
            when {
                found == null -> "there is no such trip"
                found.status == TripStatus.OPEN -> "it is still being recorded"
                found.status != TripStatus.FINISHED -> "it is ${found.status.inWords()}"
                else -> "it is ${found.category.inWords()} already"
            }
        "Trip $tripId: mark as ${category.inWords()} refused on the Trips screen: $why. " +
            "Nothing changed"
    }
}

private fun TripCorrection.inWords(): String = when (this) {
    TripCorrection.DELETE -> "delete"
    TripCorrection.RESTORE -> "restore"
    TripCorrection.COUNT -> "count this trip"
}

private fun TripStatus.inWords(): String = name.lowercase()

package com.shawnkowalchuk.milo.feature.trips

import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.trip.TripCorrection
import com.shawnkowalchuk.milo.data.trip.TripCorrectionOutcome
import com.shawnkowalchuk.milo.data.trip.TripRepository
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt

/**
 * Makes Shawn's changes to closed trips (delete, restore, count after all) and writes each one
 * to the event log. A month's total can only be trusted if every change to it by hand can be
 * read back, so there is exactly one line for every attempt: made, refused, or failed.
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

private fun TripCorrection.inWords(): String = when (this) {
    TripCorrection.DELETE -> "delete"
    TripCorrection.RESTORE -> "restore"
    TripCorrection.COUNT -> "count this trip"
}

private fun TripStatus.inWords(): String = name.lowercase()

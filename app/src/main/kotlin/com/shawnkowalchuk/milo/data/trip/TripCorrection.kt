package com.shawnkowalchuk.milo.data.trip

import com.shawnkowalchuk.milo.core.trip.TripStatus

/**
 * A change Shawn makes by hand to a trip that has closed. Each one moves a trip from exactly one
 * status to exactly one other and touches nothing else on the row, which is what makes every one
 * of them reversible: the times, the distance, the positions, the addresses and the raw points
 * stay as they were.
 *
 * None of them applies to an [TripStatus.OPEN] trip. A trip that is still being recorded belongs
 * to the trip rules, and is ended, not deleted.
 *
 * @param from the only status a trip may have for this change to be made.
 * @param to the status it has afterwards.
 */
enum class TripCorrection(val from: TripStatus, val to: TripStatus) {
    /** Takes a finished trip out of the list and the totals. The row is kept. */
    DELETE(from = TripStatus.FINISHED, to = TripStatus.DELETED),

    /** Puts a deleted trip back: it was a finished trip, and is one again. */
    RESTORE(from = TripStatus.DELETED, to = TripStatus.FINISHED),

    /**
     * Makes a discarded trip count after all. The distance thresholds are untested on the phone,
     * so a real trip can have been discarded wrongly.
     */
    COUNT(from = TripStatus.DISCARDED, to = TripStatus.FINISHED),
}

/**
 * The one change that can be offered for a trip of this status, or null for an open trip. It is
 * the change that starts from the status, which is the status the update in storage matches on
 * (`TripRepository.correct`). The Trips screen takes what it offers from here, so it cannot
 * offer a change that storage would refuse.
 */
fun TripStatus.correctionOffered(): TripCorrection? =
    TripCorrection.entries.firstOrNull { it.from == this }

/** What became of a request to correct a trip. */
sealed interface TripCorrectionOutcome {
    /** The change was made. [trip] is the row as it is now. */
    data class Done(val trip: Trip) : TripCorrectionOutcome

    /**
     * Nothing was changed: the trip was not in the status the change starts from.
     *
     * @param found the status it did have, or null if there is no such trip.
     */
    data class Refused(val found: TripStatus?) : TripCorrectionOutcome
}

package com.shawnkowalchuk.milo.data.trip

import com.shawnkowalchuk.milo.core.schedule.TripCategory
import com.shawnkowalchuk.milo.core.trip.TripStatus

// Business or Personal on a stored trip: what a row may say about it, and what Shawn may change
// it to. Pure functions, shared by the screens and checked against what storage accepts.

/**
 * Whether the trip is to be shown as having run past the schedule: the schedule made it a
 * Business trip that ended after its day's hours, and it is a Business trip now. The stored
 * flag outlives a change of category by hand, so that marking the trip Personal and Business
 * again brings the note back; this is the one place that decides whether it is said.
 */
val Trip.ranPastScheduleShown: Boolean
    get() = ranPastSchedule && category == TripCategory.BUSINESS

/**
 * What Shawn can mark a trip of this status and category as: for a finished trip, every
 * category it does not have, and for any other trip nothing. It is the rule the update in
 * storage matches on (`TripDao.setCategoryByHand`), so the Trips screen, which takes its
 * buttons from here, cannot offer a change that storage would refuse.
 *
 * A deleted or a discarded trip is restored or counted first. An open one has no category yet.
 */
fun categoriesOffered(status: TripStatus, category: TripCategory?): List<TripCategory> =
    when (status) {
        TripStatus.FINISHED -> TripCategory.entries.filter { it != category }
        else -> emptyList()
    }

/** What became of a request to mark a trip Business or Personal by hand. */
sealed interface CategoryChangeOutcome {
    /**
     * The trip was marked. [trip] is the row as it is now.
     *
     * @param was what it was saved as before, or null if it had not been sorted yet.
     */
    data class Done(val trip: Trip, val was: TripCategory?) : CategoryChangeOutcome

    /**
     * Nothing was changed: the trip is not a finished one, or it already has the category.
     *
     * @param found the trip as it is, or null if there is no such trip.
     */
    data class Refused(val found: Trip?) : CategoryChangeOutcome
}

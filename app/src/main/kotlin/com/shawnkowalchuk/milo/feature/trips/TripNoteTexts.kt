package com.shawnkowalchuk.milo.feature.trips

import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.text.categoryWordsRes
import com.shawnkowalchuk.milo.core.schedule.TripCategory
import com.shawnkowalchuk.milo.data.trip.TripCorrection

// Which words a trip's row uses for what the trip is saved as, for why it is not counted, and
// on its buttons. Only the choice is made here, so that it is tested without a phone: the words
// themselves are in strings.xml.

/**
 * The line that says Business or Personal, or null for the trip in progress, which is sorted
 * only when it ends. A deleted or a discarded trip says it too: that is what it will be if it
 * is restored or counted.
 */
internal fun TripLine.categoryNoteRes(): Int? = when (kind) {
    TripKind.IN_PROGRESS -> null
    else -> categoryWordsRes(category, ranPastSchedule)
}

/**
 * The words that say a listed trip is not in the total, or null for one that is. A trip that
 * was left out because of the setting for trips outside the work hours says so: "too short" is
 * not true of it.
 */
internal fun TripLine.leftOutNoteRes(): Int? = when (kind) {
    TripKind.DISCARDED ->
        if (ignored) R.string.trips_ignored_note else R.string.trips_discarded_note

    TripKind.DELETED -> R.string.trips_deleted_note

    TripKind.COUNTED, TripKind.IN_PROGRESS -> null
}

internal fun TripCorrection.labelRes(): Int = when (this) {
    TripCorrection.DELETE -> R.string.trips_action_delete
    TripCorrection.RESTORE -> R.string.trips_action_restore
    TripCorrection.COUNT -> R.string.trips_action_count
}

/** The button that marks a trip as this category. */
internal fun TripCategory.markLabelRes(): Int = when (this) {
    TripCategory.BUSINESS -> R.string.trips_action_mark_business
    TripCategory.PERSONAL -> R.string.trips_action_mark_personal
}

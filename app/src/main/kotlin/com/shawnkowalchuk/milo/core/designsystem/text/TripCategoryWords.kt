package com.shawnkowalchuk.milo.core.designsystem.text

import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.schedule.TripCategory

/**
 * Which words say what a closed trip is saved as: "Business", "Business · ran past schedule",
 * "Personal", or that it has not been sorted yet. Only the choice is made here, so that it is
 * tested without a phone; the words themselves are in strings.xml.
 *
 * In one place because the home screen and the Trips screen both list trips, and a trip must
 * read the same on both. That place is the design system and not `core/schedule/`, where the
 * rule lives: choosing a string resource is presentation, and the rules packages are plain
 * Kotlin that knows nothing of Android's resources.
 *
 * @param ranPastSchedule whether the note is to be shown. It is only ever said of a Business
 * trip, whatever is passed.
 */
fun categoryWordsRes(category: TripCategory?, ranPastSchedule: Boolean): Int = when (category) {
    TripCategory.BUSINESS ->
        if (ranPastSchedule) R.string.trip_business_ran_past else R.string.trip_business

    TripCategory.PERSONAL -> R.string.trip_personal

    null -> R.string.trip_unsorted
}

package com.shawnkowalchuk.milo.feature.trips

import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.text.categoryWordsRes
import com.shawnkowalchuk.milo.core.schedule.TripCategory
import com.shawnkowalchuk.milo.data.report.MonthSubmission
import com.shawnkowalchuk.milo.data.trip.ByHandMark
import com.shawnkowalchuk.milo.data.trip.TripCorrection
import java.time.LocalDate

// Which words the Trips screen uses: on a trip's grey line (what the trip is saved as, that
// its figures are typed, why it is not counted), on its buttons, in a day's heading, on the
// month's pill and for a month with nothing to list. Only the choice is made here, so that it
// is tested without a phone: the words themselves are in strings.xml and strings_trips.xml.

/**
 * The words that say Business or Personal, or null for the trip in progress, which is sorted
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

/**
 * The words that say a trip's figures are Shawn's own ("added by hand", "edited"), or null for
 * a trip that is as MilO recorded it. The trip in progress has none. The report for the
 * accountant marks the same trips, there with an asterisk and a legend.
 */
internal fun TripLine.byHandNoteRes(): Int? = when (mark.takeIf { kind != TripKind.IN_PROGRESS }) {
    ByHandMark.ADDED -> R.string.trips_note_added
    ByHandMark.EDITED -> R.string.trips_note_edited
    null -> null
}

/**
 * What follows a trip's times on its grey line, in the order it is written: what the trip is
 * saved as, then that its figures are typed, then why it is not counted. The row joins them
 * with a middle dot: "7:42 – 8:06 AM · Business · edited".
 */
internal fun TripLine.notesRes(): List<Int> =
    listOfNotNull(categoryNoteRes(), byHandNoteRes(), leftOutNoteRes())

/** One thing that can be done with a listed trip: one button under its row. */
internal sealed interface TripAction {
    /** Opens the edit screen for the trip. */
    data object Edit : TripAction

    /** Marks the trip as [category], whatever the work schedule made of it. */
    data class Mark(val category: TripCategory) : TripAction

    /** Deletes, restores or counts the trip. */
    data class Correct(val correction: TripCorrection) : TripAction
}

/**
 * The buttons under a trip's row, in the order they are drawn. What changes least comes
 * first, and Delete is the last.
 *
 * A finished trip has none until it is pressed. Then: Edit, the category it does not have
 * (both, for a trip that is not sorted yet), and Delete. A deleted or a discarded trip shows
 * its one button, Restore or Count this trip, straight away: those rows are only listed on
 * request, and the button is what they are looked at for. The trip in progress has none: it
 * is ended first.
 *
 * @param pressed whether this trip is the one whose buttons were brought up by a press.
 */
internal fun TripLine.actions(pressed: Boolean): List<TripAction> {
    val counted = kind == TripKind.COUNTED
    val shown = counted && pressed
    val change = kind.correction?.takeIf { !counted || shown }
    return buildList {
        if (shown) {
            if (editable) add(TripAction.Edit)
            markableAs.forEach { add(TripAction.Mark(it)) }
        }
        if (change != null) add(TripAction.Correct(change))
    }
}

/** The word on a button, as short as the design has it: "Edit", "Personal", "Delete". */
internal fun TripAction.wordsRes(): Int = when (this) {
    TripAction.Edit -> R.string.trips_button_edit

    is TripAction.Mark -> categoryWordsRes(category, ranPastSchedule = false)

    is TripAction.Correct ->
        when (correction) {
            TripCorrection.DELETE -> R.string.trips_button_delete
            TripCorrection.RESTORE -> R.string.trips_action_restore
            TripCorrection.COUNT -> R.string.trips_action_count
        }
}

/**
 * What a screen reader says the button is. The short word alone would not do: "Personal" on a
 * button says what the trip would become, not that pressing it marks the trip.
 */
internal fun TripAction.spokenRes(): Int = when (this) {
    TripAction.Edit -> R.string.trips_action_edit
    is TripAction.Mark -> category.markLabelRes()
    is TripAction.Correct -> correction.labelRes()
}

internal fun TripCorrection.labelRes(): Int = when (this) {
    TripCorrection.DELETE -> R.string.trips_action_delete
    TripCorrection.RESTORE -> R.string.trips_action_restore
    TripCorrection.COUNT -> R.string.trips_action_count
}

/** What marking a trip as this category is called. */
internal fun TripCategory.markLabelRes(): Int = when (this) {
    TripCategory.BUSINESS -> R.string.trips_action_mark_business
    TripCategory.PERSONAL -> R.string.trips_action_mark_personal
}

/**
 * What a day's heading says, as plain values. The words are put together by the screen.
 *
 * @param isToday the heading then starts with "Today".
 * @param sessionCount the day's counted trips, Business and Personal together.
 * @param businessTenths what its Business trips add up to, in tenths of a kilometre.
 * @param notCounted how many deleted and discarded trips the day lists. They are only listed
 * on request, and a closed day would otherwise hide where they are.
 */
internal data class DayHeading(
    val isToday: Boolean,
    val sessionCount: Int,
    val businessTenths: Long,
    val notCounted: Int,
)

/** The heading of this day, on a screen where today is [today]. */
internal fun TripDay.heading(today: LocalDate): DayHeading = DayHeading(
    isToday = date == today,
    sessionCount = sessionCount,
    businessTenths = businessTenths,
    notCounted = trips.count { it.kind != TripKind.COUNTED },
)

/**
 * The words on the month tile's pill: whether the month's report has been sent. Two short
 * ones, because a pill holds no more. When the report was sent, and whether it was sent
 * again, is the sentence under the month's figures (`submissionWords`).
 */
internal fun pillWordsRes(submission: MonthSubmission?): Int =
    if (submission == null) R.string.report_not_submitted else R.string.trips_pill_submitted

/**
 * The sentence for a month that lists no trip, or null for a month that lists one. A month
 * whose only trips are deleted or discarded ones that are not shown does have trips, so it
 * says "no counted trips"; the line under the switch says how many are hidden.
 */
internal fun MonthSummary.emptyWordsRes(): Int? = when {
    !isEmpty -> null
    hiddenLeftOut > 0 -> R.string.trips_empty_counted
    else -> R.string.trips_empty
}

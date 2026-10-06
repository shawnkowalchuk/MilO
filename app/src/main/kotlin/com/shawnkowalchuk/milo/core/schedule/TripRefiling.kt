package com.shawnkowalchuk.milo.core.schedule

import java.time.ZoneId

// What becomes of Business or Personal, and of "ran past schedule", when Shawn changes a closed
// trip's times by hand or types a missed trip in. A pure function, so that the form can show
// what a save will store and the save can store exactly that.
//
// Like the rest of this package it is asked only about a trip that has already been recorded
// or typed in. It has no say in whether a trip starts.

/**
 * How a closed trip is filed: what it is saved as, who decided that, and the note that goes
 * with a Business trip.
 *
 * @param category null for a trip that is not sorted.
 * @param categorySetByHand true once Shawn has chosen the category himself.
 * @param ranPastSchedule true if the schedule makes this a Business trip that ended after its
 * day's hours.
 */
data class TripFiled(
    val category: TripCategory?,
    val categorySetByHand: Boolean,
    val ranPastSchedule: Boolean,
)

/**
 * Files a trip again after a change by hand. The rule, in the order it is applied:
 *
 * 1. **A category Shawn picks is his.** If he chose one in the form ([chosen]), the trip is
 *    saved as that and marked as set by hand.
 * 2. **A category he set earlier stays.** A trip that is already set by hand keeps its
 *    category whatever happens to its times.
 * 3. **Otherwise a new start is sorted again.** Only the start decides Business or Personal
 *    (`classifyTrip`), so only a changed start re-sorts the trip, by the schedule as it is now.
 *    A changed end alone, or a changed distance or address, leaves the category as it was
 *    saved: a schedule that was changed since must not re-sort a trip whose start is the same.
 * 4. **"Ran past schedule" follows the times.** If the start or the end changed, the note is
 *    worked out again from the new times and the schedule as it is now, whoever chose the
 *    category: it is true only if the schedule makes the trip Business and it ends after that
 *    day's hours. If neither time changed, the stored note is kept, as it is when a trip is
 *    marked by hand on the Trips screen. The screens show the note only on a Business trip.
 *
 * A trip that is typed in new has nothing stored: it is filed with [stored] empty and both
 * times counted as changed, which makes rule 3 "sorted by its start" and rule 4 "worked out".
 *
 * @param stored how the trip is filed now.
 * @param startedAtMs and [endedAtMs] are the times the trip will have, wall-clock
 * milliseconds since 1970.
 * @param chosen the category Shawn pressed in the form, or null if he left it alone.
 * @param schedule the work schedule as it is now, or null if the settings cannot be read. A
 * trip that would have to be sorted again is then left unsorted (rule 3) and its note is
 * dropped (rule 4): the catch-up at the next process start sorts it, by Shawn's hours and not
 * by a guess.
 */
fun refileTrip(
    stored: TripFiled,
    startChanged: Boolean,
    endChanged: Boolean,
    startedAtMs: Long,
    endedAtMs: Long?,
    chosen: TripCategory?,
    schedule: WorkSchedule?,
    zone: ZoneId,
): TripFiled {
    val bySchedule = schedule?.let { classifyTrip(startedAtMs, endedAtMs, it, zone) }
    val byHand = chosen != null || stored.categorySetByHand
    val category =
        when {
            chosen != null -> chosen
            stored.categorySetByHand || !startChanged -> stored.category
            else -> bySchedule?.category
        }
    val timesChanged = startChanged || endChanged
    return TripFiled(
        category = category,
        categorySetByHand = byHand,
        ranPastSchedule =
            if (timesChanged) bySchedule?.ranPastSchedule == true else stored.ranPastSchedule,
    )
}

/** The filing of a trip that has none yet: one that is being typed in by hand. */
val NOT_FILED: TripFiled =
    TripFiled(category = null, categorySetByHand = false, ranPastSchedule = false)

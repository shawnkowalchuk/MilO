package com.shawnkowalchuk.milo.data.trip

import com.shawnkowalchuk.milo.core.schedule.TripCategory
import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.core.util.DistanceUnit
import com.shawnkowalchuk.milo.core.util.sumOfTenths

// Which stored trips count, and what a day or a month of them adds up to. Pure functions,
// shared by the Trips screen, the home screen and the Android Auto screen, so that the three
// can never count a trip differently.
//
// **Every total here is added up as the report for the accountant adds up:** each trip is
// rounded to a tenth of a kilometre, and the rounded figures are added (`sumOfTenths` in
// `core/util/DistanceFormat.kt`). So the rows of a day add up to the figure in its heading,
// the days to the month, and the month's Business figure is the total its report prints.
//
// Since 2026-10-07 the unit is asked for each time (`DistanceUnit`): in miles the same holds in
// tenths of a mile. Whoever asks says which unit the figure is to be printed in, and prints it
// in that unit; the widget's dollars alone always ask for kilometres, whatever is shown.

/**
 * Whether this trip is in a total: a finished trip, and nothing else. A trip in progress is not
 * counted until it ends, and a discarded or deleted one never is. A deleted trip that is
 * restored, or a discarded one that Shawn counts after all, is finished again and so counts.
 *
 * Business and Personal trips both count. They are added up apart ([CategoryTotals]), and it
 * is the Business trips that the report for the accountant lists (`data/report/`).
 */
val Trip.isCounted: Boolean get() = status == TripStatus.FINISHED

/**
 * A number of trips and what they add up to.
 *
 * @param tenths the total in tenths of the unit it was asked for in: the sum of the trips' own
 * figures, each rounded to a tenth first, which is how the report for the accountant adds up.
 */
data class Tally(val count: Int, val tenths: Long)

/**
 * Counted trips added up by what they are saved as.
 *
 * @param unsorted counted trips with no category yet. There are none in ordinary use: a trip
 * is sorted when it is finalised, and the ones recorded before there was a schedule are sorted
 * at the first process start. They are kept apart so that such a trip is never passed off as
 * Business or as Personal.
 */
data class CategoryTotals(val business: Tally, val personal: Tally, val unsorted: Tally) {
    /** Every counted trip, whatever it is saved as. */
    val count: Int get() = business.count + personal.count + unsorted.count
}

/**
 * Adds up the counted trips among [trips] by category. Trips that are not counted ([isCounted])
 * are in none of the three.
 *
 * @param unit the unit the three totals are in, and so the unit they must be printed in.
 */
fun categoryTotals(trips: List<Trip>, unit: DistanceUnit): CategoryTotals =
    totalsOf(trips.filter { it.isCounted }, unit, { it.category }, { it.distanceMetres })

private fun <T> totalsOf(
    counted: List<T>,
    unit: DistanceUnit,
    category: (T) -> TripCategory?,
    metres: (T) -> Double,
): CategoryTotals {
    fun tally(wanted: TripCategory?): Tally {
        val matching = counted.filter { category(it) == wanted }
        return Tally(matching.size, sumOfTenths(matching.map(metres), unit))
    }
    return CategoryTotals(
        business = tally(TripCategory.BUSINESS),
        personal = tally(TripCategory.PERSONAL),
        unsorted = tally(null),
    )
}

/**
 * One of today's finished trips, as the home screen lists it.
 *
 * @param endedAtMs null only for a row that storage should never produce: a finished trip
 * without an end.
 * @param category Business or Personal, or null for a trip that has not been sorted yet.
 * @param ranPastSchedule true for a Business trip that ended after its day's hours.
 */
data class TodaySession(
    val tripId: Long,
    val startedAtMs: Long,
    val endedAtMs: Long?,
    val distanceMetres: Double,
    val category: TripCategory? = null,
    val ranPastSchedule: Boolean = false,
) {
    /**
     * How long the trip lasted. Never negative: a trip's stored start can be later than its end
     * when the phone corrected its clock during the trip.
     */
    val driveTimeMs: Long get() = ((endedAtMs ?: startedAtMs) - startedAtMs).coerceAtLeast(0)
}

/**
 * Today's finished trips, newest first.
 *
 * The kilometres are added up as the report adds up, trip by trip as each is printed (see the
 * top of this file), so the rows of the Today card add up to the figures above them. The drive
 * time is added up in milliseconds and rounded once, when it is shown.
 *
 * [count], [totalTenths] and [driveTimeMs] are of every counted trip, Business and Personal
 * together; the Android Auto screen shows those. The home screen shows the split in [totals].
 *
 * It holds the trips, not their figures: the two functions that add up are told the unit each
 * time, so that today's trips read once can be shown in either.
 */
data class TodayTrips(val sessions: List<TodaySession>) {
    val count: Int get() = sessions.size

    /** Every counted trip of today in one figure, in tenths of [unit]. */
    fun totalTenths(unit: DistanceUnit): Long =
        sumOfTenths(sessions.map { it.distanceMetres }, unit)

    val driveTimeMs: Long get() = sessions.sumOf { it.driveTimeMs }

    /** Today's trips added up by what they are saved as, in tenths of [unit]. */
    fun totals(unit: DistanceUnit): CategoryTotals =
        totalsOf(sessions, unit, { it.category }, { it.distanceMetres })

    /** The time spent on today's Business trips. */
    val businessDriveTimeMs: Long
        get() = sessions.filter { it.category == TripCategory.BUSINESS }.sumOf { it.driveTimeMs }
}

/**
 * What counts as "today": the trips that started today and are counted ([isCounted]). The trip
 * in progress is left out until it ends; the screens show it apart.
 *
 * @param startedToday every trip that started today, whatever its status, in any order.
 */
fun todayTrips(startedToday: List<Trip>): TodayTrips = TodayTrips(
    startedToday
        .filter { it.isCounted }
        .sortedWith(compareByDescending<Trip> { it.startedAtMs }.thenByDescending { it.id })
        .map {
            TodaySession(
                tripId = it.id,
                startedAtMs = it.startedAtMs,
                endedAtMs = it.endedAtMs,
                distanceMetres = it.distanceMetres,
                category = it.category,
                ranPastSchedule = it.ranPastScheduleShown,
            )
        },
)

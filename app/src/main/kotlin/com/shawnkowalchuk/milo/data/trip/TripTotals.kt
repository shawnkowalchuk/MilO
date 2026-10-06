package com.shawnkowalchuk.milo.data.trip

import com.shawnkowalchuk.milo.core.schedule.TripCategory
import com.shawnkowalchuk.milo.core.trip.TripStatus

// Which stored trips count, and what a day or a month of them adds up to. Pure functions,
// shared by the Trips screen, the home screen and the Android Auto screen, so that the three
// can never count a trip differently.

/**
 * Whether this trip is in a total: a finished trip, and nothing else. A trip in progress is not
 * counted until it ends, and a discarded or deleted one never is. A deleted trip that is
 * restored, or a discarded one that Shawn counts after all, is finished again and so counts.
 *
 * Business and Personal trips both count. They are added up apart ([CategoryTotals]), and it
 * is the Business total that the report will be made of.
 */
val Trip.isCounted: Boolean get() = status == TripStatus.FINISHED

/** A number of trips and what they add up to, in metres, rounded only when it is shown. */
data class Tally(val count: Int, val metres: Double)

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
 */
fun categoryTotals(trips: List<Trip>): CategoryTotals =
    totalsOf(trips.filter { it.isCounted }, { it.category }, { it.distanceMetres })

private fun <T> totalsOf(
    counted: List<T>,
    category: (T) -> TripCategory?,
    metres: (T) -> Double,
): CategoryTotals {
    fun tally(wanted: TripCategory?): Tally {
        val matching = counted.filter { category(it) == wanted }
        return Tally(matching.size, matching.sumOf(metres))
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
 * The totals are added up in metres and milliseconds and rounded once, when they are shown, like
 * the month totals of the Trips screen, so the rounding of single trips never accumulates.
 *
 * [count], [totalMetres] and [driveTimeMs] are of every counted trip, Business and Personal
 * together; the Android Auto screen shows those. The home screen shows the split in [totals].
 */
data class TodayTrips(val sessions: List<TodaySession>) {
    val count: Int get() = sessions.size

    val totalMetres: Double get() = sessions.sumOf { it.distanceMetres }

    val driveTimeMs: Long get() = sessions.sumOf { it.driveTimeMs }

    /** Today's trips added up by what they are saved as. */
    val totals: CategoryTotals
        get() = totalsOf(sessions, { it.category }, { it.distanceMetres })

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

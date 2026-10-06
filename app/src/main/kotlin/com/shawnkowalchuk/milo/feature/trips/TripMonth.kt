package com.shawnkowalchuk.milo.feature.trips

import com.shawnkowalchuk.milo.core.schedule.TripCategory
import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.core.util.localDateOf
import com.shawnkowalchuk.milo.data.trip.CategoryTotals
import com.shawnkowalchuk.milo.data.trip.Trip
import com.shawnkowalchuk.milo.data.trip.TripCorrection
import com.shawnkowalchuk.milo.data.trip.categoriesOffered
import com.shawnkowalchuk.milo.data.trip.categoryTotals
import com.shawnkowalchuk.milo.data.trip.correctionOffered
import com.shawnkowalchuk.milo.data.trip.isCounted
import com.shawnkowalchuk.milo.data.trip.ranPastScheduleShown
import com.shawnkowalchuk.milo.platform.address.OpenTripStart
import com.shawnkowalchuk.milo.platform.address.TripPlace
import com.shawnkowalchuk.milo.platform.address.endPlace
import com.shawnkowalchuk.milo.platform.address.startPlace
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

// What the Trips screen makes of one month's trips: which month may be shown, what is counted,
// and how the trips are grouped. Pure functions, so they are tested without a phone.

/**
 * How a trip appears in the list.
 *
 * @param status the stored status a trip of this kind has.
 */
enum class TripKind(val status: TripStatus) {
    /** A finished trip. It is in the month's total, and it can be deleted. */
    COUNTED(TripStatus.FINISHED),

    /**
     * Still being recorded. Shown apart, and not in the total until it ends. It cannot be
     * deleted: it is ended first.
     */
    IN_PROGRESS(TripStatus.OPEN),

    /**
     * Discarded when it closed: under the minimum distance, or a start that was never confirmed.
     * Never in the total. Listed only on request, so that a real trip that was wrongly discarded
     * can be spotted, and counted after all.
     */
    DISCARDED(TripStatus.DISCARDED),

    /**
     * Deleted by Shawn. Never in the total. Listed only on request, beside the discarded ones,
     * so that it can be restored.
     */
    DELETED(TripStatus.DELETED),
    ;

    /**
     * The one change Shawn can make to such a trip, or null if there is none. It is not chosen
     * here: it is the change that starts from this kind's status, the rule storage goes by
     * (`data/trip/TripCorrection.kt`). Two lists of who may do what would drift apart.
     */
    val correction: TripCorrection? get() = status.correctionOffered()
}

/**
 * One trip as the list shows it.
 *
 * @param endedAtMs null while the trip is in progress.
 * @param distanceMetres null for a trip in progress whose running distance is not known.
 * @param from where it started, as far as that is known. Null for a discarded trip, whose
 * addresses are never looked up; for a trip in progress that has no position yet; and for a
 * deleted trip that had no start address stored when it was deleted.
 * @param to where it ended. Null for a trip in progress and a discarded one, and for a deleted
 * trip without a stored end address.
 * @param category Business or Personal. Null for a trip in progress, and for a closed trip
 * that has not been sorted yet.
 * @param ranPastSchedule true for a Business trip that ended after its day's hours.
 * @param ignored true for a discarded trip that was discarded only because it started outside
 * the work schedule while such trips were set to be ignored.
 * @param markableAs what Shawn can mark the trip as: for a counted trip, the categories it does
 * not have; for every other trip, nothing.
 */
data class TripLine(
    val id: Long,
    val startedAtMs: Long,
    val endedAtMs: Long?,
    val distanceMetres: Double?,
    val kind: TripKind,
    val from: TripPlace? = null,
    val to: TripPlace? = null,
    val category: TripCategory? = null,
    val ranPastSchedule: Boolean = false,
    val ignored: Boolean = false,
    val markableAs: List<TripCategory> = emptyList(),
)

/**
 * The trips that started on one calendar day, newest first.
 *
 * @param sessionCount how many counted trips the day has, Business and Personal together.
 * Deleted and discarded trips are not among them, listed or not.
 * @param businessMetres what the day's Business trips add up to.
 */
data class TripDay(
    val date: LocalDate,
    val trips: List<TripLine>,
    val sessionCount: Int,
    val businessMetres: Double,
)

/**
 * One month, ready to show.
 *
 * @param totals the finished trips, added up by what they are saved as. Added up in metres and
 * rounded once, when it is shown, so the rounding of single trips never accumulates.
 * @param inProgress the trip being recorded, if it started in this month.
 * @param days newest day first. Holds deleted and discarded trips only if they were asked for.
 * @param hiddenLeftOut how many deleted and discarded trips the month has that [days] leaves
 * out.
 */
data class MonthSummary(
    val totals: CategoryTotals,
    val inProgress: TripLine?,
    val days: List<TripDay>,
    val hiddenLeftOut: Int,
) {
    /** How many finished trips the month has, whatever they are saved as. */
    val tripCount: Int get() = totals.count

    /** True if the month has nothing at all to list. */
    val isEmpty: Boolean get() = inProgress == null && days.isEmpty()
}

/**
 * The month shown after stepping [months] away from [shown] (negative for back). It never goes
 * past [current]: a month that has not begun has no trips to show.
 */
fun stepMonth(shown: YearMonth, months: Long, current: YearMonth): YearMonth =
    minOf(shown.plusMonths(months), current)

/** Whether there is a later month to step to. */
fun canStepForward(shown: YearMonth, current: YearMonth): Boolean = shown < current

/**
 * Sums and groups the trips of one month.
 *
 * A trip belongs to the day it **started** on, in [zone]: a trip that runs past midnight stays
 * whole, under the day it began. The caller asks storage for the trips that started in the
 * month, so the same rule decides the month.
 *
 * What is counted is decided by `isCounted` (`data/trip/TripTotals.kt`), the rule the home
 * screen and the Android Auto screen use for today's totals, and what is Business and what
 * Personal by `categoryTotals` in the same file.
 *
 * @param trips every trip that started in the month, whatever its status, in any order.
 * @param showLeftOut whether the deleted and the discarded trips are listed. The totals are the
 * same either way.
 * @param liveTripId the trip the trip controller is recording right now, or null.
 * @param liveDistanceMetres that trip's running distance. The stored row holds 0 until the trip
 * closes, so the list shows this figure, and only for the trip it belongs to.
 * @param liveStart where the trip in progress started, from the address lookup. The stored row
 * has no position either until the trip closes. Used only for the trip it belongs to.
 */
fun monthSummary(
    trips: List<Trip>,
    zone: ZoneId,
    showLeftOut: Boolean,
    liveTripId: Long?,
    liveDistanceMetres: Double?,
    liveStart: OpenTripStart?,
): MonthSummary {
    val finished = trips.filter { it.isCounted }
    val leftOut =
        trips.mapNotNull { trip ->
            when (trip.status) {
                TripStatus.DISCARDED -> trip.toLine(TripKind.DISCARDED)
                TripStatus.DELETED -> trip.toLine(TripKind.DELETED)
                else -> null
            }
        }
    val listed =
        finished.map { it.toLine(TripKind.COUNTED) } + if (showLeftOut) leftOut else emptyList()
    val newestFirst = compareByDescending<TripLine> { it.startedAtMs }.thenByDescending { it.id }
    val finishedByDay = finished.groupBy { localDateOf(it.startedAtMs, zone) }
    return MonthSummary(
        totals = categoryTotals(finished),
        inProgress =
            trips.firstOrNull { it.status == TripStatus.OPEN }?.let { open ->
                TripLine(
                    id = open.id,
                    startedAtMs = open.startedAtMs,
                    endedAtMs = null,
                    distanceMetres = liveDistanceMetres.takeIf { open.id == liveTripId },
                    kind = TripKind.IN_PROGRESS,
                    from = liveStart?.takeIf { it.tripId == open.id }?.place,
                )
            },
        days =
            listed
                .groupBy { localDateOf(it.startedAtMs, zone) }
                .map { (date, lines) ->
                    val dayTotals = categoryTotals(finishedByDay[date].orEmpty())
                    TripDay(
                        date = date,
                        trips = lines.sortedWith(newestFirst),
                        sessionCount = dayTotals.count,
                        businessMetres = dayTotals.business.metres,
                    )
                }.sortedByDescending { it.date },
        hiddenLeftOut = if (showLeftOut) 0 else leftOut.size,
    )
}

private fun Trip.toLine(kind: TripKind): TripLine = TripLine(
    id = id,
    startedAtMs = startedAtMs,
    endedAtMs = endedAtMs,
    distanceMetres = distanceMetres,
    kind = kind,
    from = placeShown(kind, startPlace()),
    to = placeShown(kind, endPlace()),
    category = category,
    ranPastSchedule = ranPastScheduleShown,
    ignored = kind == TripKind.DISCARDED && ignoredOutsideSchedule,
    markableAs = categoriesOffered(status, category),
)

/**
 * What a row may say about one end of a trip. A finished trip says everything, "still looking"
 * included. A deleted trip shows an address it already had, which helps to recognise it, and
 * nothing else: it is not looked up while it is deleted, so "still looking" would not be true.
 * A discarded trip is never looked up, so its row says nothing about places.
 */
private fun placeShown(kind: TripKind, place: TripPlace): TripPlace? = when (kind) {
    TripKind.COUNTED -> place
    TripKind.DELETED -> place as? TripPlace.Known
    TripKind.DISCARDED, TripKind.IN_PROGRESS -> null
}

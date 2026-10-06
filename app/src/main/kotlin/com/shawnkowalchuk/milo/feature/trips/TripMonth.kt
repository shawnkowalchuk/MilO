package com.shawnkowalchuk.milo.feature.trips

import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.core.util.localDateOf
import com.shawnkowalchuk.milo.data.trip.Trip
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

// What the Trips screen makes of one month's trips: which month may be shown, what is counted,
// and how the trips are grouped. Pure functions, so they are tested without a phone.

/** How a trip appears in the list. */
enum class TripKind {
    /** A finished trip. It is in the month's total. */
    COUNTED,

    /** Still being recorded. Shown apart, and not in the total until it ends. */
    IN_PROGRESS,

    /**
     * Discarded when it closed: under the minimum distance, or a start that was never confirmed.
     * Never in the total. Listed only on request, so that a real trip that was wrongly discarded
     * can be spotted.
     */
    DISCARDED,
}

/**
 * One trip as the list shows it.
 *
 * @param endedAtMs null while the trip is in progress.
 * @param distanceMetres null for a trip in progress whose running distance is not known.
 */
data class TripLine(
    val id: Long,
    val startedAtMs: Long,
    val endedAtMs: Long?,
    val distanceMetres: Double?,
    val kind: TripKind,
)

/** The trips that started on one calendar day, newest first. */
data class TripDay(val date: LocalDate, val trips: List<TripLine>)

/**
 * One month, ready to show.
 *
 * @param totalMetres the distance of the finished trips. Added up in metres and rounded once,
 * when it is shown, so the rounding of single trips never accumulates.
 * @param tripCount how many finished trips that is.
 * @param inProgress the trip being recorded, if it started in this month.
 * @param days newest day first. Holds discarded trips only if they were asked for.
 * @param hiddenDiscarded how many discarded trips the month has that [days] leaves out.
 */
data class MonthSummary(
    val totalMetres: Double,
    val tripCount: Int,
    val inProgress: TripLine?,
    val days: List<TripDay>,
    val hiddenDiscarded: Int,
) {
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
 * @param trips every trip that started in the month, whatever its status, in any order.
 * @param liveTripId the trip the trip controller is recording right now, or null.
 * @param liveDistanceMetres that trip's running distance. The stored row holds 0 until the trip
 * closes, so the list shows this figure, and only for the trip it belongs to.
 */
fun monthSummary(
    trips: List<Trip>,
    zone: ZoneId,
    showDiscarded: Boolean,
    liveTripId: Long?,
    liveDistanceMetres: Double?,
): MonthSummary {
    val finished = trips.filter { it.status == TripStatus.FINISHED }
    val discarded = trips.filter { it.status == TripStatus.DISCARDED }
    val listed =
        finished.map { it.toLine(TripKind.COUNTED) } +
            if (showDiscarded) discarded.map { it.toLine(TripKind.DISCARDED) } else emptyList()
    val newestFirst = compareByDescending<TripLine> { it.startedAtMs }.thenByDescending { it.id }
    return MonthSummary(
        totalMetres = finished.sumOf { it.distanceMetres },
        tripCount = finished.size,
        inProgress =
            trips.firstOrNull { it.status == TripStatus.OPEN }?.let { open ->
                TripLine(
                    id = open.id,
                    startedAtMs = open.startedAtMs,
                    endedAtMs = null,
                    distanceMetres = liveDistanceMetres.takeIf { open.id == liveTripId },
                    kind = TripKind.IN_PROGRESS,
                )
            },
        days =
            listed
                .groupBy { localDateOf(it.startedAtMs, zone) }
                .map { (date, lines) -> TripDay(date, lines.sortedWith(newestFirst)) }
                .sortedByDescending { it.date },
        hiddenDiscarded = if (showDiscarded) 0 else discarded.size,
    )
}

private fun Trip.toLine(kind: TripKind): TripLine = TripLine(
    id = id,
    startedAtMs = startedAtMs,
    endedAtMs = endedAtMs,
    distanceMetres = distanceMetres,
    kind = kind,
)

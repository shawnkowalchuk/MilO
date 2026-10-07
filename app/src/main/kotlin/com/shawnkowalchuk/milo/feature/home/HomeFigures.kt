package com.shawnkowalchuk.milo.feature.home

import com.shawnkowalchuk.milo.core.designsystem.text.PlacesText
import com.shawnkowalchuk.milo.core.designsystem.text.routeText
import com.shawnkowalchuk.milo.core.schedule.TripCategory
import com.shawnkowalchuk.milo.core.util.daySpan
import com.shawnkowalchuk.milo.data.report.SentReport
import com.shawnkowalchuk.milo.data.settings.MiloSettings
import com.shawnkowalchuk.milo.data.trip.CategoryTotals
import com.shawnkowalchuk.milo.data.trip.TodayTrips
import com.shawnkowalchuk.milo.data.trip.Trip
import com.shawnkowalchuk.milo.data.trip.categoryTotals
import com.shawnkowalchuk.milo.data.trip.todayTrips
import com.shawnkowalchuk.milo.platform.address.OpenTripStart
import com.shawnkowalchuk.milo.platform.address.TripPlace
import com.shawnkowalchuk.milo.platform.address.endPlace
import com.shawnkowalchuk.milo.platform.address.startPlace
import com.shawnkowalchuk.milo.platform.reminder.ReminderStep
import com.shawnkowalchuk.milo.platform.reminder.judgeReminder
import com.shawnkowalchuk.milo.platform.reminder.reminderMoment
import com.shawnkowalchuk.milo.platform.trip.CurrentTrip
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

// What Home's tiles make of the stored trips, the sent reports and the settings. Pure
// functions, so each figure is tested without a phone. Nothing is counted here by a rule of
// Home's own: what counts, how it is added up and when a report is waiting are the rules the
// Trips screen, the report and the monthly reminder go by.

private const val PERCENT = 100L

/**
 * The month's tile.
 *
 * @param businessTenths what the month's Business trips add up to, in tenths of a kilometre:
 * the figure the Trips screen shows for the month, and the total its report prints.
 * @param businessPercent the Business share of all the month's counted kilometres, in whole
 * percent, or null while the month has no kilometres at all: a share of nothing is not a
 * number.
 */
internal data class MonthFigures(
    val month: YearMonth,
    val businessTenths: Long,
    val businessPercent: Int?,
)

/**
 * The Business share of [totals] in whole percent, or null if they add up to nothing.
 *
 * Rounded to the nearest percent, with one exception at each end: a month with any kilometre
 * that is not Business never reads "100%", and one with any Business kilometre never "0%".
 * Each of those two figures makes a claim that a rounded 99.6 or 0.4 does not support.
 */
internal fun businessPercent(totals: CategoryTotals): Int? {
    val business = totals.business.tenths
    val all = business + totals.personal.tenths + totals.unsorted.tenths
    if (all <= 0L) return null
    if (business <= 0L) return 0
    if (business >= all) return PERCENT.toInt()
    val nearest = (business * PERCENT * 2 + all) / (all * 2)
    return nearest.coerceIn(1L, PERCENT - 1).toInt()
}

/**
 * One of today's finished trips as Home writes it.
 *
 * @param places where it went, in the words the Trips screen uses for the same trip.
 * @param endedAtMs null only for a row that storage should never produce.
 * @param category Business or Personal, or null for a trip that has not been sorted yet.
 */
internal data class HomeTrip(
    val tripId: Long,
    val places: PlacesText,
    val startedAtMs: Long,
    val endedAtMs: Long?,
    val distanceMetres: Double,
    val category: TripCategory?,
    val ranPastSchedule: Boolean,
)

/**
 * Everything Home shows of the stored trips.
 *
 * @param date the day [today] and [rows] are of, and the month [month] is of: the day the
 * trips were read for. The screen shows them under that day only.
 * @param today today's finished trips, counted by the one rule (`todayTrips`).
 * @param rows the same trips, newest first, each with where it went.
 */
internal data class HomeTrips(
    val date: LocalDate,
    val today: TodayTrips,
    val rows: List<HomeTrip>,
    val month: MonthFigures,
) {
    /** The most recent finished trip of today, or null before the first one. */
    val lastTrip: HomeTrip? get() = rows.firstOrNull()
}

/**
 * Makes the tiles' figures from the trips of the month [date] is in.
 *
 * A trip belongs to the day and the month it **started** in, in [zone], as on the Trips screen.
 * Today's trips are picked out of the month's, so the two tiles are made from one reading and
 * cannot be a trip apart.
 *
 * @param monthTrips every trip that started in the month of [date], whatever its status.
 */
internal fun homeTrips(date: LocalDate, zone: ZoneId, monthTrips: List<Trip>): HomeTrips {
    val day = daySpan(date, zone)
    val startedToday =
        monthTrips.filter { it.startedAtMs >= day.fromMs && it.startedAtMs < day.untilMs }
    val today = todayTrips(startedToday)
    val stored = startedToday.associateBy { it.id }
    val totals = categoryTotals(monthTrips)
    return HomeTrips(
        date = date,
        today = today,
        rows =
            today.sessions.mapNotNull { session ->
                stored[session.tripId]?.let { trip ->
                    HomeTrip(
                        tripId = session.tripId,
                        places = routeText(trip.startPlace(), trip.endPlace()),
                        startedAtMs = session.startedAtMs,
                        endedAtMs = session.endedAtMs,
                        distanceMetres = session.distanceMetres,
                        category = session.category,
                        ranPastSchedule = session.ranPastSchedule,
                    )
                }
            },
        month =
            MonthFigures(
                month = YearMonth.from(date),
                businessTenths = totals.business.tenths,
                businessPercent = businessPercent(totals),
            ),
    )
}

/**
 * The month whose report is waiting to be sent, or null if none is: what the amber tile is
 * shown for.
 *
 * **It is the monthly reminder's own rule** ([judgeReminder], fed by [reminderMoment]): the
 * reminder is switched on, this month's reminder day has come, last month has a Business trip,
 * and no report for the whole of last month is recorded as sent. The one difference is on
 * purpose: the notification comes once a day, and the tile stays for as long as the report is
 * waiting, so "already shown today" does not take the tile away.
 *
 * @param stored null while the settings have not been read, or cannot be. Nothing is shown
 * then, as the reminder itself shows nothing.
 */
internal fun reportWaiting(
    nowMs: Long,
    zone: ZoneId,
    stored: MiloSettings?,
    sent: List<SentReport>,
    lastMonthTrips: List<Trip>,
): YearMonth? {
    if (stored == null) return null
    val verdict = judgeReminder(reminderMoment(nowMs, zone, stored, sent, lastMonthTrips))
    return verdict.month.takeIf { verdict.step != ReminderStep.WITHDRAW }
}

/**
 * Where the trip in progress started, if the address lookup already knows: the address alone.
 * Null while there is no trip, while the lookup has nothing for this trip (it keeps the last
 * trip's start after that trip ended), and while the address is still being looked up or was
 * not found. Home then writes no line, rather than a guess.
 */
internal fun startAddressOf(trip: CurrentTrip?, start: OpenTripStart?): String? {
    if (trip == null || start == null || start.tripId != trip.tripId) return null
    return (start.place as? TripPlace.Known)?.address
}

private const val MINUTE_MS = 60_000L
private const val SECOND_MS = 1_000L

/**
 * How long until a trip that has run for [elapsedMs] has run for another whole minute: when
 * the minutes shown for it next change. Never less than a second and never more than a
 * minute, also for a trip whose start lies in the future because the phone's clock was set
 * back while it was open.
 */
internal fun msUntilNextMinute(elapsedMs: Long): Long =
    (MINUTE_MS - Math.floorMod(elapsedMs, MINUTE_MS)).coerceIn(SECOND_MS, MINUTE_MS)

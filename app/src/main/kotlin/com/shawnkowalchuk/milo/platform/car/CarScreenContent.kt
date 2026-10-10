package com.shawnkowalchuk.milo.platform.car

import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.allowance.allowanceCents
import com.shawnkowalchuk.milo.core.allowance.formatWholeDollars
import com.shawnkowalchuk.milo.core.util.DistanceUnit
import com.shawnkowalchuk.milo.core.util.daySpan
import com.shawnkowalchuk.milo.core.util.formatDistance
import com.shawnkowalchuk.milo.core.util.formatMonthName
import com.shawnkowalchuk.milo.core.util.formatTenths
import com.shawnkowalchuk.milo.core.util.wholeHoursAndMinutes
import com.shawnkowalchuk.milo.data.trip.Trip
import com.shawnkowalchuk.milo.data.trip.categoryTotals
import com.shawnkowalchuk.milo.data.trip.todayTrips
import com.shawnkowalchuk.milo.platform.system.PreflightProblem
import com.shawnkowalchuk.milo.platform.trip.CurrentTrip
import com.shawnkowalchuk.milo.platform.trip.ParkedTruckWatch
import com.shawnkowalchuk.milo.platform.trip.StartFailure
import com.shawnkowalchuk.milo.platform.trip.TripActivity
import com.shawnkowalchuk.milo.platform.trip.TripTrigger
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale

// What the Android Auto screen shows, decided from the trip controller's state, the month's
// trips and the rate. Plain values and pure functions, so every state is tested
// without a car. Only the choice of words is made here: the words themselves are in strings.xml.
//
// Every figure is already rounded to what the screen prints (kilometres to one decimal, time to
// whole minutes). Two contents are therefore equal exactly when the screen would look the same,
// which is how the screen knows that there is nothing to redraw.

/**
 * The status line: the "This trip" row's words while no trip is open, and the start of them
 * while one is. The home-screen widget prints it too. Each constant is one sentence in
 * strings.xml.
 *
 * @param startRefused true for the lines that say the last attempt to start a trip was refused.
 */
enum class CarStatus(val textRes: Int, val startRefused: Boolean = false) {
    /** A trip is being recorded, and nothing says the truck is not connected. */
    RECORDING(R.string.car_status_recording),

    /**
     * A trip is being recorded while the trip rules believe the truck's Bluetooth is not
     * connected: a trip started by hand that the truck has not joined, or one that Android Auto
     * alone is holding open.
     */
    RECORDING_WITHOUT_TRUCK(R.string.car_status_recording_without_truck),

    /** The grace period: the truck has gone and the trip ends unless it comes back. */
    WAITING_FOR_TRUCK(R.string.car_status_waiting_for_truck),

    /**
     * No trip: the truck stood still, so its trip was ended, and it is still connected. MilO is
     * watching its position, and the next trip starts when it moves.
     */
    PARKED_WAITING(R.string.car_status_parked_waiting),

    /** The same, for so long that MilO has stopped watching: the button starts a trip. */
    PARKED_NOT_WATCHED(R.string.car_status_parked_not_watched),

    /** No trip, and nothing known that explains it (ended by hand, or not yet read). */
    NOT_RECORDING(R.string.car_status_not_recording),

    /** No trip, and the trip rules believe the truck's Bluetooth is not connected. */
    TRUCK_NOT_CONNECTED(R.string.car_status_truck_not_connected),

    /** No trip, and the setup checklist says a trip may not start by itself. */
    SETUP_INCOMPLETE(R.string.car_status_setup_incomplete),

    REFUSED_LOCATION_PERMISSION(R.string.car_status_refused_location_permission, true),
    REFUSED_BACKGROUND_LOCATION(R.string.car_status_refused_background_location, true),
    REFUSED_LOCATION_OFF(R.string.car_status_refused_location_off, true),
    REFUSED_BATTERY_RESTRICTED(R.string.car_status_refused_battery_restricted, true),
    REFUSED_BLUETOOTH_PERMISSION(R.string.car_status_refused_bluetooth_permission, true),

    /** The preflight passed and Android still refused the foreground service. */
    REFUSED_BY_ANDROID(R.string.car_status_refused_by_android, true),
}

/**
 * The screen's one button. It is the same manual override as the phone's Start trip / End trip
 * button and sends the same triggers, so a trip started here is the same kind of manual trip.
 * The labels are the phone's own strings, so the two surfaces cannot word it differently.
 *
 * @param source what the event log calls a press. The trip controller writes it into the
 * trigger's line, which is how every press on the car screen ends up in the log.
 */
enum class CarAction(val labelRes: Int, val trigger: TripTrigger, val source: String) {
    START_TRIP(R.string.home_start_trip, TripTrigger.MANUAL_START, "Android Auto Start button"),
    END_TRIP(R.string.home_end_trip, TripTrigger.MANUAL_END, "Android Auto End button"),
}

/**
 * The trip in progress as the "This trip" row prints it.
 *
 * @param kilometres the trip's distance, the number only, with one decimal, from
 * [formatDistance]. It is in the unit of the content it is part of ([CarScreenContent.unit]),
 * kilometres or miles: the name is from when kilometres were the only unit.
 * @param hours and [minutes] the time since the trip started, in whole minutes.
 */
data class TripFigures(val kilometres: String, val hours: Long, val minutes: Long)

/**
 * The "Business" row as it is printed (since 2026-10-09, Shawn: "it should have all the stuff
 * on the home screen less todays trips"): what Home's tiles show of today and of the month, with
 * the month's dollars. Until then the row was "Today" and counted every finished trip, Business
 * and Personal together.
 *
 * **The odometer is not in it.** It was, for an hour: with it the line was too long for the
 * smallest car display (800 by 480), wrapped, and the car put scroll arrows beside the rows,
 * which is what the two rows were made to end. The truck's own dashboard shows it.
 *
 * @param today and [month] today's and the month's Business distance, the numbers only, with
 * one decimal: the sum of the trips' own figures, each rounded to a tenth first, like every
 * total on the phone and on the report.
 * @param monthName the month's name in the phone's language: "October".
 * @param dollars the month's Business kilometres priced at the rate set in Settings, as the
 * widget and Home price them ("$289"), or null while the rate cannot be read.
 * @param unit the unit the two distances are in. Figures made in another unit than the
 * content's are not shown ([carScreenContent]).
 */
data class BusinessFigures(
    val today: String,
    val monthName: String,
    val month: String,
    val dollars: String?,
    val unit: DistanceUnit,
)

/**
 * What the "Business" row is made from.
 *
 * @param date today, and [zone] the phone's time zone: a trip belongs to the day and the month
 * it started in, as on the Trips screen.
 * @param monthTrips every trip that started in the month of [date], whatever its status.
 * @param centsPerKm the rate set in Settings, or null if the settings could not be read.
 */
class BusinessInput(
    val date: LocalDate,
    val zone: ZoneId,
    val monthTrips: List<Trip>,
    val centsPerKm: Int?,
)

/**
 * The "Business" row's figures. Nothing is counted by a rule of the car's own: today's trips
 * are picked and added up as Home's Today tile does (`todayTrips`, `categoryTotals`), the month
 * as Home's month tile and the report do, and the dollars as the widget does, always from
 * kilometres at the rate per kilometre.
 */
fun businessFigures(input: BusinessInput, locale: Locale, unit: DistanceUnit): BusinessFigures {
    val day = daySpan(input.date, input.zone)
    val startedToday =
        input.monthTrips.filter { it.startedAtMs >= day.fromMs && it.startedAtMs < day.untilMs }
    val monthInKilometres =
        categoryTotals(input.monthTrips, DistanceUnit.KILOMETRES).business.tenths
    return BusinessFigures(
        today = formatTenths(todayTrips(startedToday).totals(unit).business.tenths, locale),
        monthName = formatMonthName(YearMonth.from(input.date), locale),
        month = formatTenths(categoryTotals(input.monthTrips, unit).business.tenths, locale),
        dollars =
            input.centsPerKm?.let {
                formatWholeDollars(allowanceCents(monthInKilometres, it), locale)
            },
        unit = unit,
    )
}

/**
 * Everything the screen shows that can change. The two row titles and the header are not
 * here: they never change, which is what lets the car treat every redraw as a refresh.
 *
 * @param trip null when no trip is open.
 * @param business null while the month's trips have not been read, or could not be.
 * @param unit the unit the figures are in, and are to be written with (since 2026-10-07).
 * It is part of the content, so a change of the unit in Settings is a change that is drawn at
 * once, not one that waits like a trip's running figures.
 */
data class CarScreenContent(
    val status: CarStatus,
    val trip: TripFigures?,
    val business: BusinessFigures?,
    val action: CarAction,
    val unit: DistanceUnit,
)

/**
 * The screen's content for one moment.
 *
 * @param business the "Business" row's figures ([businessFigures]), or null if they are not
 * known. The home-screen widget, which shows the same status, trip and button, passes none.
 * @param setupNeedsAttention the home screen's rule (`needsAttention`): a required row of the
 * setup checklist is not in order.
 * @param locale decides the decimal separator of the distance figures.
 * @param unit the unit chosen in Settings: the figures are worked out and written in it.
 */
fun carScreenContent(
    activity: TripActivity,
    business: BusinessFigures?,
    setupNeedsAttention: Boolean,
    nowMs: Long,
    locale: Locale,
    unit: DistanceUnit,
): CarScreenContent {
    val trip = activity.trip
    return CarScreenContent(
        status = carStatus(activity, setupNeedsAttention),
        trip = trip?.let { tripFigures(it, nowMs, locale, unit) },
        // Right after the unit was changed in Settings the figures in hand are still in the
        // other one. They are not shown under this one's name: the row says "Not available".
        business = business?.takeIf { it.unit == unit },
        // One button, because exactly one of the two makes sense at any moment. End is offered
        // for as long as a trip is open, the grace period included, as on the phone.
        action = if (trip == null) CarAction.START_TRIP else CarAction.END_TRIP,
        unit = unit,
    )
}

/**
 * Whether [next] differs from this content only in the running figures of a trip that is open
 * in both. Such a change waits for the gap between refreshes; any other change (the status, the
 * button, a trip starting or ending, the Business row's figures) is drawn at once.
 */
fun CarScreenContent?.differsOnlyInTripFigures(next: CarScreenContent): Boolean =
    this != null && trip != null && next.trip != null && trip != next.trip &&
        copy(trip = next.trip) == next

/**
 * One line, so the most telling thing wins. An open trip comes first: while one is recording,
 * what stood in the way earlier no longer matters. With no trip, a truck that MilO is waiting
 * beside comes next: that is no failure, and the next trip starts by itself. Then a refused
 * start, which is the most exact explanation, then a setup that is not in order, then the truck.
 */
private fun carStatus(activity: TripActivity, setupNeedsAttention: Boolean): CarStatus {
    val trip = activity.trip
    val failure = activity.startFailure
    return when {
        trip != null && trip.waitingForTruck -> CarStatus.WAITING_FOR_TRUCK
        trip != null && activity.truckConnected == false -> CarStatus.RECORDING_WITHOUT_TRUCK
        trip != null -> CarStatus.RECORDING
        activity.parked == ParkedTruckWatch.WAITING_TO_MOVE -> CarStatus.PARKED_WAITING
        failure != null -> refusalStatus(failure)
        setupNeedsAttention -> CarStatus.SETUP_INCOMPLETE
        activity.parked == ParkedTruckWatch.NO_LONGER_WATCHED -> CarStatus.PARKED_NOT_WATCHED
        activity.truckConnected == false -> CarStatus.TRUCK_NOT_CONNECTED
        else -> CarStatus.NOT_RECORDING
    }
}

/**
 * The row has room for one reason, so the first thing the preflight found is named. The phone's
 * home screen lists them all.
 */
private fun refusalStatus(failure: StartFailure): CarStatus =
    when (failure.problems.firstOrNull()) {
        PreflightProblem.LOCATION_PERMISSION_MISSING -> CarStatus.REFUSED_LOCATION_PERMISSION
        PreflightProblem.BACKGROUND_LOCATION_MISSING -> CarStatus.REFUSED_BACKGROUND_LOCATION
        PreflightProblem.LOCATION_SWITCHED_OFF -> CarStatus.REFUSED_LOCATION_OFF
        PreflightProblem.BACKGROUND_RESTRICTED -> CarStatus.REFUSED_BATTERY_RESTRICTED
        PreflightProblem.BLUETOOTH_PERMISSION_MISSING -> CarStatus.REFUSED_BLUETOOTH_PERMISSION
        null -> CarStatus.REFUSED_BY_ANDROID
    }

private fun tripFigures(
    trip: CurrentTrip,
    nowMs: Long,
    locale: Locale,
    unit: DistanceUnit,
): TripFigures {
    // Whole minutes, rounded down, and never negative: the phone's clock can be set back while
    // a trip is open.
    val sinceStart = wholeHoursAndMinutes(nowMs - trip.startedAtMs)
    return TripFigures(
        kilometres = formatDistance(trip.distanceMetres, unit, locale),
        hours = sinceStart.hours,
        minutes = sinceStart.minutes,
    )
}

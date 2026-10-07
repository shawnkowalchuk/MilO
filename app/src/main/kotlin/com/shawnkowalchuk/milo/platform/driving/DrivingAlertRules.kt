package com.shawnkowalchuk.milo.platform.driving

import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.DetectedActivity
import com.shawnkowalchuk.milo.core.schedule.TripCategory
import com.shawnkowalchuk.milo.core.schedule.WorkSchedule
import com.shawnkowalchuk.milo.core.schedule.classifyTrip
import com.shawnkowalchuk.milo.platform.bluetooth.TruckReading
import java.time.ZoneId

// The rules of the driving alert: when the phone's report that Shawn is in a moving vehicle
// becomes a notification. Pure functions, so they are tested without a phone.
//
// Nothing here can start a trip, and nothing here is asked when one starts. The phone cannot
// tell the truck from a bus or a colleague's car, so driving detection only ever leads to a
// notification; a trip starts when Shawn taps it (ADR-002). Beside a parked, connected truck a
// report also turns GPS on again ([enteredVehicleAtMs]), and the fixes decide.

/**
 * An "entered a vehicle" report older than this says nothing about now, and is not acted on.
 *
 * Nothing promises that a report is fresh: each carries the time the phone noticed it, one
 * delivery can hold several, and MilO asks to be told again at every process start. Whether
 * Play services then hands over old ones has not been seen on the phone (device check DA-12).
 * Without a limit, opening MilO at a desk could bring up an alert about the morning's drive.
 * Five minutes is a judgement: a report is expected to be seconds old, and one that reaches a
 * process HyperOS had to wake first must not be thrown away for being a little late.
 */
const val MAX_DRIVING_REPORT_AGE_MS = 5 * 60_000L

/**
 * After an alert has been posted, no other is posted for this long. The phone can report
 * leaving a vehicle and entering one again at every stop of one drive, and each "left" takes
 * the alert away, so without the limit each "entered" would sound it anew. The time of the last
 * alert is kept in the settings file, so the limit holds across a restart of MilO's process.
 */
const val ALERT_QUIET_MS = 30 * 60_000L

/**
 * For this long after a trip has ended, a report of entering a vehicle shows no alert. The
 * phone takes a minute or more to notice a drive and can report it again, so a report that
 * comes just after a trip is most likely about the drive that trip recorded.
 */
const val AFTER_TRIP_QUIET_MS = 5 * 60_000L

private const val NANOS_PER_MILLI = 1_000_000L

/**
 * One report from the phone's driving detection about a vehicle.
 *
 * @param entered true for "is now in a vehicle", false for "has left it".
 * @param ageMs how long ago the phone noticed it, measured on the clock that counts from boot.
 */
data class VehicleReport(val entered: Boolean, val ageMs: Long)

/**
 * Reads one event of Play services' Activity Recognition as a [VehicleReport].
 *
 * @param activityType the event's `DetectedActivity` type. Only vehicles are asked for.
 * @param transitionType the event's `ActivityTransition` type: entering or leaving.
 * @param eventNanos when the phone noticed it, and [nowNanos] the present, both on the clock
 * that counts from boot, which never jumps.
 * @return null for anything that is not a vehicle being entered or left. It is dropped, not
 * guessed at.
 */
fun vehicleReport(
    activityType: Int,
    transitionType: Int,
    eventNanos: Long,
    nowNanos: Long,
): VehicleReport? {
    if (activityType != DetectedActivity.IN_VEHICLE) return null
    val entered =
        when (transitionType) {
            ActivityTransition.ACTIVITY_TRANSITION_ENTER -> true
            ActivityTransition.ACTIVITY_TRANSITION_EXIT -> false
            else -> return null
        }
    return VehicleReport(entered, ageMs = (nowNanos - eventNanos) / NANOS_PER_MILLI)
}

/**
 * When the phone noticed Shawn getting into a vehicle, on the wall clock, if that is the newest
 * of [reports] and no older than [MAX_DRIVING_REPORT_AGE_MS]; otherwise null. The trip
 * controller is told it: beside a parked, connected truck it turns GPS on again, and a trip
 * starts only if the fixes show the truck driving off (`parkedGpsUntilMs`).
 *
 * @param reports oldest first, as the phone handed them over.
 * @param nowMs the wall clock now. A report's age is measured on the clock that counts from
 * boot, so its time on the wall clock is worked out from the present.
 */
fun enteredVehicleAtMs(reports: List<VehicleReport>, nowMs: Long): Long? {
    val newest = reports.lastOrNull() ?: return null
    if (!newest.entered || newest.ageMs > MAX_DRIVING_REPORT_AGE_MS) return null
    return nowMs - newest.ageMs
}

/**
 * Everything the alert is decided from, as it was at one moment.
 *
 * @param reports what the phone reported, oldest first, as it hands them over. Only the last
 * one counts: the earlier ones describe a state the phone has already left.
 * @param alertEnabled the switch on the Settings screen.
 * @param tripInProgress whether a trip is being recorded right now.
 * @param truckPaired whether MilO knows a truck at all. Without one there is nothing a trip
 * could be for, and every ride in any vehicle would otherwise alert.
 * @param truck a fresh reading of the truck's Bluetooth connection.
 * @param truckNoLongerWatched true when the truck is connected and parked, and has stood for so
 * long that MilO has stopped watching it for movement (ADR-002, amendment 28). Nothing then
 * starts a trip when it drives off, although it reads as connected.
 * @param lastTripEndedAtMs when the newest closed trip ended, or null if there is none.
 * @param lastAlertAtMs when the alert was last posted, or null if it never was.
 * @param nowMs wall-clock milliseconds, read against [schedule] in [zone]. The two times above
 * are on the same clock.
 */
data class DrivingMoment(
    val reports: List<VehicleReport>,
    val alertEnabled: Boolean,
    val tripInProgress: Boolean,
    val truckPaired: Boolean,
    val truck: TruckReading.Answer,
    val lastTripEndedAtMs: Long?,
    val lastAlertAtMs: Long?,
    val nowMs: Long,
    val schedule: WorkSchedule,
    val zone: ZoneId,
    val truckNoLongerWatched: Boolean = false,
)

/** What is done with the alert's notification. */
enum class DrivingAlertStep {
    /** Post it, or post it again without a second sound if it is still showing. */
    SHOW,

    /** Take it away if it is showing: what it says is no longer true. */
    WITHDRAW,

    /** Leave it as it is: nothing was learned about now. */
    LEAVE,
}

/**
 * What [judgeDriving] made of a moment, and why. The reason goes to the event log, because a
 * missed alert has to be explainable afterwards.
 */
enum class DrivingVerdict(val step: DrivingAlertStep) {
    /** The phone handed over no report about a vehicle. */
    NOTHING_REPORTED(DrivingAlertStep.LEAVE),

    /** The newest report is "entered a vehicle", but from too long ago to act on. */
    REPORT_TOO_OLD(DrivingAlertStep.LEAVE),

    /** The phone says the drive is over, so "tap to start a trip" would be wrong. */
    LEFT_THE_VEHICLE(DrivingAlertStep.WITHDRAW),
    SWITCHED_OFF(DrivingAlertStep.WITHDRAW),
    TRIP_IN_PROGRESS(DrivingAlertStep.WITHDRAW),

    /** No truck is paired, so the vehicle cannot be one MilO logs trips for. */
    NO_TRUCK_PAIRED(DrivingAlertStep.WITHDRAW),

    /** The truck is connected: the truck's own triggers are responsible for the trip. */
    TRUCK_CONNECTED(DrivingAlertStep.WITHDRAW),

    /** A trip started now would be Personal, and the alert is for work drives only. */
    OUTSIDE_WORK_HOURS(DrivingAlertStep.WITHDRAW),

    /** A trip ended less than [AFTER_TRIP_QUIET_MS] ago: the report is taken to be about it. */
    TRIP_JUST_ENDED(DrivingAlertStep.WITHDRAW),

    /**
     * An alert was posted less than [ALERT_QUIET_MS] ago. Nothing is posted, and nothing is
     * taken away either: if that alert is still showing, what it says is still true.
     */
    ALERTED_RECENTLY(DrivingAlertStep.LEAVE),
    DRIVING_WITHOUT_THE_TRUCK(DrivingAlertStep.SHOW),

    /**
     * The phone could not say whether the truck is connected. No trip is being recorded, so the
     * alert is shown: one notification too many costs less than a drive that is not logged.
     */
    DRIVING_TRUCK_UNKNOWN(DrivingAlertStep.SHOW),

    /**
     * The truck is connected, but it stood for so long that MilO stopped watching it, and no
     * trip is being recorded. Its own triggers will not start one, so the alert is shown.
     */
    DRIVING_TRUCK_NOT_WATCHED(DrivingAlertStep.SHOW),
}

/**
 * Decides whether the driving alert is shown.
 *
 * It is shown when all of these hold: the newest report says Shawn has entered a vehicle, and
 * recently; the alert is switched on; no trip is being recorded; a truck is paired; the truck
 * is not known to be connected, or is connected but no longer watched by MilO
 * ([DrivingMoment.truckNoLongerWatched]); a trip that started now would be saved as Business by the work
 * schedule (`classifyTrip`, the same rule that sorts a finished trip, so the alert and the
 * Trips screen cannot disagree about what the work hours are); no trip ended in the last
 * [AFTER_TRIP_QUIET_MS]; and no alert was posted in the last [ALERT_QUIET_MS].
 *
 * A reading of the truck that came back "unknown" does not stop the alert. That is the opposite
 * of what the trip rules do with it, and deliberate: there, unknown must never start a trip;
 * here, the worst an unknown can cause is a notification.
 *
 * A recent "entered" report that fails one of the other tests withdraws an alert that is still
 * showing, so the notification always matches the newest decision. The one exception is the
 * limit on a second alert, which is asked last and leaves a showing alert where it is.
 */
fun judgeDriving(moment: DrivingMoment): DrivingVerdict {
    val newest = moment.reports.lastOrNull() ?: return DrivingVerdict.NOTHING_REPORTED
    val now = moment.nowMs
    val startedNow = classifyTrip(now, endedAtMs = null, moment.schedule, moment.zone)
    val connected = moment.truck == TruckReading.Answer.CONNECTED
    return when {
        !newest.entered -> DrivingVerdict.LEFT_THE_VEHICLE

        newest.ageMs > MAX_DRIVING_REPORT_AGE_MS -> DrivingVerdict.REPORT_TOO_OLD

        !moment.alertEnabled -> DrivingVerdict.SWITCHED_OFF

        moment.tripInProgress -> DrivingVerdict.TRIP_IN_PROGRESS

        !moment.truckPaired -> DrivingVerdict.NO_TRUCK_PAIRED

        connected && !moment.truckNoLongerWatched -> DrivingVerdict.TRUCK_CONNECTED

        startedNow.category != TripCategory.BUSINESS -> DrivingVerdict.OUTSIDE_WORK_HOURS

        happenedWithin(AFTER_TRIP_QUIET_MS, moment.lastTripEndedAtMs, now) ->
            DrivingVerdict.TRIP_JUST_ENDED

        happenedWithin(ALERT_QUIET_MS, moment.lastAlertAtMs, now) ->
            DrivingVerdict.ALERTED_RECENTLY

        moment.truck == TruckReading.Answer.UNKNOWN -> DrivingVerdict.DRIVING_TRUCK_UNKNOWN

        connected -> DrivingVerdict.DRIVING_TRUCK_NOT_WATCHED

        else -> DrivingVerdict.DRIVING_WITHOUT_THE_TRUCK
    }
}

/**
 * Whether something that happened at [atMs] is less than [spanMs] in the past at [nowMs].
 *
 * A time that lies in the future, which a phone clock that was set back leaves behind, does not
 * count as recent: it would otherwise keep the alert quiet until the clock had caught up.
 */
private fun happenedWithin(spanMs: Long, atMs: Long?, nowMs: Long): Boolean =
    atMs != null && nowMs - atMs in 0 until spanMs

/**
 * Whether MilO asks the phone to report driving at all.
 *
 * @param alertEnabled the switch on the Settings screen.
 * @param permissionGranted the Physical activity permission. Without it Play services refuses
 * the request, and reports nothing.
 */
data class DrivingWatch(val alertEnabled: Boolean, val permissionGranted: Boolean) {
    /** True if the phone's reports are wanted and can be had. */
    val wanted: Boolean get() = alertEnabled && permissionGranted
}

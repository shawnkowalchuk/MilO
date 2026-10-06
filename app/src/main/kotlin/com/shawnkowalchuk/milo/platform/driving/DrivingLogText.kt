package com.shawnkowalchuk.milo.platform.driving

import com.shawnkowalchuk.milo.core.schedule.classifyTrip
import com.shawnkowalchuk.milo.data.trip.classificationText
import com.shawnkowalchuk.milo.platform.bluetooth.TruckReading

// How the event log words the driving alert. Plain functions, so that what is logged can be
// read (and tested) in one place. Like the rest of the event log the lines are English only.
// Every report from the phone gets a line, acted on or not: whether driving detection reaches
// MilO on HyperOS at all, and how late, is only known from these lines.

private const val MILLIS_PER_SECOND = 1000L
private const val MILLIS_PER_MINUTE = 60_000L

/** "watching for driving (process start)", or why not. */
internal fun watchText(watch: DrivingWatch, source: String): String = when {
    !watch.alertEnabled ->
        "Driving alert: not watching for driving ($source): it is switched off in Settings"

    !watch.permissionGranted ->
        "Driving alert: not watching for driving ($source): " +
            "the Physical activity permission is not granted"

    else -> "Driving alert: watching for driving ($source)"
}

/** The line for a request the phone did not carry out. The reason goes in the detail. */
internal fun watchFailedText(watch: DrivingWatch, source: String): String = if (watch.wanted) {
    "Driving alert: the phone did not agree to report driving ($source). No alert can be shown"
} else {
    "Driving alert: the phone did not agree to stop reporting driving ($source). " +
        "Its reports are ignored"
}

/** "left a vehicle 95 s ago, then entered a vehicle 3 s ago". */
private fun reportsText(reports: List<VehicleReport>): String {
    if (reports.isEmpty()) return "a report that names no vehicle"
    return reports.joinToString(", then ") { report ->
        val what = if (report.entered) "entered a vehicle" else "left a vehicle"
        "$what ${report.ageMs / MILLIS_PER_SECOND} s ago"
    }
}

private fun DrivingVerdict.inWords(): String = when (this) {
    DrivingVerdict.NOTHING_REPORTED -> "Nothing done"

    DrivingVerdict.REPORT_TOO_OLD ->
        "Not acted on: the report is more than " +
            "${MAX_DRIVING_REPORT_AGE_MS / MILLIS_PER_MINUTE} min old and says nothing about now"

    DrivingVerdict.LEFT_THE_VEHICLE -> "The alert is withdrawn if it was showing: the drive is over"

    DrivingVerdict.SWITCHED_OFF -> "No alert: the driving alert is switched off in Settings"

    DrivingVerdict.TRIP_IN_PROGRESS -> "No alert: a trip is being recorded"

    DrivingVerdict.NO_TRUCK_PAIRED -> "No alert: no truck is paired"

    DrivingVerdict.TRUCK_CONNECTED -> "No alert: the truck is connected"

    DrivingVerdict.OUTSIDE_WORK_HOURS -> "No alert: it is outside the work hours"

    DrivingVerdict.TRIP_JUST_ENDED ->
        "No alert: a trip ended less than ${AFTER_TRIP_QUIET_MS / MILLIS_PER_MINUTE} min ago"

    DrivingVerdict.ALERTED_RECENTLY ->
        "No new alert: the last one was less than ${ALERT_QUIET_MS / MILLIS_PER_MINUTE} min ago"

    DrivingVerdict.DRIVING_WITHOUT_THE_TRUCK ->
        "Alert shown: no trip is being recorded and the truck is not connected"

    DrivingVerdict.DRIVING_TRUCK_UNKNOWN ->
        "Alert shown: no trip is being recorded, and the phone could not say whether the " +
            "truck is connected"
}

/**
 * The line for one delivery of reports and what was done about it.
 *
 * @param seen false if the alert was posted while notifications are off for MilO, or for this
 * kind of notification, so that nobody saw it.
 */
internal fun judgedText(
    reports: List<VehicleReport>,
    verdict: DrivingVerdict,
    seen: Boolean,
): String = buildString {
    append("Driving detection: ${reportsText(reports)}. ${verdict.inWords()}")
    if (verdict.step == DrivingAlertStep.SHOW && !seen) {
        append(". Notifications are off for MilO or for the driving alert, so nobody saw it")
    }
}

/**
 * Everything the decision went by, one fact to a line, for the detail of the log line. All of
 * it is written every time, whichever fact decided: a line that says "outside the work hours"
 * should also show whether the truck was connected.
 *
 * @param truckEvidence how the reading of the truck was reached.
 * @param notes what went wrong on the way, said first: the settings could not be read and the
 * defaults were used, or the time of the alert could not be stored.
 */
internal fun momentText(
    moment: DrivingMoment,
    truckEvidence: String,
    notes: List<String>,
): String {
    val startedNow = classifyTrip(moment.nowMs, endedAtMs = null, moment.schedule, moment.zone)
    val hours = classificationText(startedNow, moment.nowMs, moment.schedule, moment.zone)
    val truck =
        when (moment.truck) {
            TruckReading.Answer.CONNECTED -> "connected"
            TruckReading.Answer.NOT_CONNECTED -> "not connected"
            TruckReading.Answer.UNKNOWN -> "could not be read"
        }
    val facts =
        listOf(
            "The driving alert is switched ${if (moment.alertEnabled) "on" else "off"}.",
            "A trip is being recorded: ${if (moment.tripInProgress) "yes" else "no"}.",
            "A truck is paired: ${if (moment.truckPaired) "yes" else "no"}.",
            "The truck: $truck ($truckEvidence).",
            "A trip started now would be saved as $hours.",
            "The last trip ended: ${agoText(moment.lastTripEndedAtMs, moment.nowMs)}.",
            "The last alert: ${agoText(moment.lastAlertAtMs, moment.nowMs)}.",
        )
    return (notes + facts).joinToString("\n")
}

/** "12 min ago", in whole minutes that have passed: the two limits are counted in minutes. */
private fun agoText(atMs: Long?, nowMs: Long): String = when {
    atMs == null -> "never"
    atMs > nowMs -> "at a time the phone's clock has not reached yet"
    else -> "${(nowMs - atMs) / MILLIS_PER_MINUTE} min ago"
}

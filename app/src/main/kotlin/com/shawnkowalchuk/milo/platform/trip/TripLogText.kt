package com.shawnkowalchuk.milo.platform.trip

import com.shawnkowalchuk.milo.core.schedule.TripFiling
import com.shawnkowalchuk.milo.core.schedule.WorkSchedule
import com.shawnkowalchuk.milo.core.trip.ANOTHER_VEHICLE_WITHIN_METRES
import com.shawnkowalchuk.milo.core.trip.ClosedTrip
import com.shawnkowalchuk.milo.core.trip.TripEndReason
import com.shawnkowalchuk.milo.core.trip.TripEvent
import com.shawnkowalchuk.milo.core.trip.TripStartCause
import com.shawnkowalchuk.milo.core.trip.TripState
import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.core.trip.WaitingEnd
import com.shawnkowalchuk.milo.data.trip.classificationText
import com.shawnkowalchuk.milo.platform.bluetooth.TruckReading
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

// How the trip controller words its event-log lines. Plain functions, kept apart from the
// controller so that what is logged can be read (and tested) in one place. The event log is
// English only: it is evidence for diagnosing a missed trip, not a screen.

/** The state in words: what the trip is doing, and what is believed about the two connections. */
internal fun TripState.describe(): String {
    val open = trip
    val tripText =
        when {
            open == null -> "no trip"
            open.confirmByMs != null -> "trip open, waiting for the truck to be confirmed"
            open.grace != null -> "trip open, in its grace period"
            !open.truckSeen -> "trip open, truck not seen in it"
            else -> "trip open"
        }
    val truckText = if (truckConnected) "truck connected" else "truck not connected"
    val autoText =
        if (androidAutoConnected) "Android Auto connected" else "Android Auto not connected"
    val holdOffText = if (autoStartHeldOff) "; automatic start held off" else ""
    val parkedText =
        when (parked?.watching) {
            null -> ""
            true -> "; waiting for the parked truck to move"
            false -> "; the truck is parked and no longer watched"
        }
    return "$tripText; $truckText; $autoText$holdOffText$parkedText"
}

/** What an event told the trip rules, in words. */
internal fun TripEvent.describe(): String = when (this) {
    is TripEvent.TruckConnection ->
        if (connected) "the truck is connected" else "the truck is not connected"

    is TripEvent.TruckLinkConnected -> "a link to the truck was made"

    is TripEvent.TruckAppeared -> "the companion service says the truck appeared"

    is TripEvent.AndroidAutoConnection ->
        if (connected) "Android Auto is connected" else "Android Auto is not connected"

    is TripEvent.ManualStart -> "Start pressed, ${connectedText(truckConnected)}"

    is TripEvent.ManualEnd -> "End pressed, ${connectedText(truckConnected)}"

    is TripEvent.Moved -> "the truck moved"

    is TripEvent.MoveTakenBack -> "the movement last counted was one bad GPS fix"
}

private fun connectedText(truckConnected: Boolean): String =
    if (truckConnected) "truck connected" else "truck not connected"

/**
 * The line that records a closed trip: what became of it, why, and what it was measured from.
 *
 * @param ignored true if the trip rules kept the trip and it is stored as discarded all the
 * same, because it started outside the work schedule and such trips are set to be ignored.
 * @param parked for a trip the parked rule closed, when the truck last moved ([parkedText]).
 */
internal fun closedText(
    closed: ClosedTrip,
    reason: TripEndReason,
    storedFixes: Int,
    minimumTripDistanceMetres: Int,
    ignored: Boolean,
    parked: String? = null,
): String {
    val metres = closed.distance.metres.roundToInt()
    val outcome =
        when {
            ignored ->
                "discarded: it started outside the work schedule, and trips outside it are " +
                    "set to be ignored ($metres m). It can be counted on the Trips screen"

            closed.status != TripStatus.DISCARDED -> "finished, $metres m"

            reason == TripEndReason.FALSE_START ->
                "discarded as a false start: the truck's connection was never confirmed ($metres m)"

            else -> "discarded: $metres m is under the minimum of $minimumTripDistanceMetres m"
        }
    val distance = closed.distance
    val fixes =
        "$storedFixes fixes stored, ${distance.acceptedCount} used, " +
            "${distance.rejectedForAccuracy} too inaccurate, ${distance.rejectedAsJump} jumps"
    return "$outcome; ended by $reason${parked.orEmpty()}; $fixes"
}

/**
 * A trip removed for good as a drive in another vehicle (`leftInAnotherVehicle`): what it was
 * removed for, with the figures that decided it, so the log keeps what the trips table no longer
 * holds.
 */
internal fun anotherVehicleText(closed: ClosedTrip, startedAtMs: Long, zone: ZoneId): String {
    val metres = closed.distance.metres.roundToInt()
    val within = ANOTHER_VEHICLE_WITHIN_METRES.roundToInt()
    val started = TIME_OF_DAY.format(Instant.ofEpochMilli(startedAtMs).atZone(zone))
    val lost = TIME_OF_DAY.format(Instant.ofEpochMilli(closed.endedAtMs).atZone(zone))
    return "removed for good as a drive in another vehicle: it started at $started when the " +
        "parked truck seemed to drive off, and the truck's connection was lost for good at " +
        "$lost, $metres m on, under $within m. Its row and its points are deleted"
}

private const val MILLIS_PER_MINUTE = 60_000L
private val TIME_OF_DAY = DateTimeFormatter.ofPattern("HH:mm:ss")

/**
 * Why the parked rule closed a trip, to follow its reason in [closedText]: when the truck last
 * moved, which is where the trip was cut, and the limit it stood still for.
 */
internal fun parkedText(lastMovedAtMs: Long, parkedLimitMs: Long?, zone: ZoneId): String {
    val lastMoved = TIME_OF_DAY.format(Instant.ofEpochMilli(lastMovedAtMs).atZone(zone))
    val limit = parkedLimitMs?.let { " for ${it / MILLIS_PER_MINUTE} min" }.orEmpty()
    return " (the truck was parked: it last moved at $lastMoved and then stood still$limit)"
}

/**
 * The line for a trip that has started.
 *
 * @param fromParked true if the truck, connected and parked, moved again.
 * @param carriedOver how many points a trip that begins at the parked place was given: the
 * place and the fixes the watch kept. Null for a trip that begins where it was started.
 */
internal fun startedText(
    tripId: Long,
    startedBy: TripStartCause,
    fromParked: Boolean,
    carriedOver: Int?,
): String {
    val started = "Trip $tripId started by $startedBy"
    return when {
        fromParked ->
            "$started: it was connected and parked, and it moved. The trip starts where it " +
                "was parked ($carriedOver points carried over), with no trip-start sound"

        carriedOver != null ->
            "$started while MilO waited beside the parked truck. The trip starts where it " +
                "was parked ($carriedOver points carried over)"

        else -> started
    }
}

/** The line for the beginning of a wait beside the parked truck. */
internal const val WAITING_BEGAN =
    "Waiting for the truck to move: it is still connected. Its position is read at a low " +
        "rate, and a trip starts when it moves"

/**
 * The line for GPS going off beside the parked truck once the wait has lasted an hour
 * (`parkedGpsUntilMs`). The trip service writes it.
 */
internal const val PARKED_GPS_OFF =
    "GPS is off beside the parked truck, to spare the battery. The phone's motion sensor " +
        "watches instead: getting into a vehicle turns GPS on again"

private const val MILLIS_PER_SECOND = 1000L

/**
 * The line for a report of getting into a vehicle that turns GPS on beside the parked truck.
 *
 * @param ageMs how long before now the phone noticed it.
 * @param forMs how long from now GPS is read.
 */
internal fun gpsForDrivingText(ageMs: Long, forMs: Long): String {
    val minutes = (forMs + MILLIS_PER_MINUTE - 1) / MILLIS_PER_MINUTE
    return "The phone reports getting into a vehicle ${ageMs / MILLIS_PER_SECOND} s ago: the " +
        "parked truck's position is read for the next $minutes min, and a trip starts if it " +
        "drives off"
}

/** The line for the end of a wait beside the parked truck, with the reason. */
internal fun waitingEndedText(reason: WaitingEnd): String {
    val why =
        when (reason) {
            WaitingEnd.MOVED -> "it moved"

            WaitingEnd.TRUCK_DISCONNECTED -> "it is no longer connected. Nothing was recorded"

            WaitingEnd.NEW_LINK -> "a new link to it was reported, which starts a trip"

            WaitingEnd.START_PRESSED -> "Start was pressed"

            WaitingEnd.END_PRESSED -> "End was pressed"

            WaitingEnd.TIME_LIMIT ->
                "it has stood too long, and MilO has stopped watching it to spare the battery. " +
                    "The next trip starts when the truck reconnects, when MilO is opened or " +
                    "restarted, or with Start"

            WaitingEnd.TRIP_OPEN -> "a trip is open"
        }
    return "No longer waiting for the truck to move: $why"
}

/**
 * What the work schedule made of a trip that has just been closed, to follow [closedText] in
 * the same line: Business or Personal, and the day, the time and the hours it was judged by.
 *
 * @param kept false for a trip that is stored as discarded, by the trip rules or because it is
 * ignored. It is sorted all the same, for the day it is counted after all, but the line does
 * not say "saved".
 * @param schedule the schedule it was judged by, or null if the settings could not be read.
 */
internal fun filedText(
    filing: TripFiling,
    kept: Boolean,
    startedAtMs: Long,
    schedule: WorkSchedule?,
    zone: ZoneId,
): String {
    val classification = filing.classification
    if (classification == null || schedule == null) {
        return "not sorted into Business or Personal: the settings cannot be read. " +
            "It is sorted at a later start of MilO"
    }
    val verb = if (kept) "saved as" else "if it is counted, it is"
    return "$verb ${classificationText(classification, startedAtMs, schedule, zone)}"
}

/** The line for a start of the trip service that failed, at the preflight or in Android. */
internal fun couldNotStartText(request: StartRequest?, failure: StartFailure): String {
    val forWhat = request?.let { "${it.source}: ${it.trigger}" } ?: "a restart by Android"
    val why = failure.problems.joinToString().ifEmpty { "Android refused the foreground service" }
    return "Could not start recording for $forWhat: $why"
}

/** The source of the look at Android Auto's word that follows every poll. */
internal fun androidAutoRecheckText(believed: Boolean): String = if (believed) {
    "Android Auto, as last reported"
} else {
    "Android Auto has held the trip open alone for too long, taken for stuck"
}

/**
 * A reading of the truck taken at a process start, in words, with how it was reached.
 *
 * @param waitStands true if it could not be had and a stored wait beside the parked truck
 * carries on all the same (`storedWaitStandsUnread`).
 */
internal fun TruckReading.describe(waitStands: Boolean = false): String = when (answer) {
    TruckReading.Answer.CONNECTED -> "the truck is connected ($evidence)"

    TruckReading.Answer.NOT_CONNECTED -> "the truck is not connected ($evidence)"

    TruckReading.Answer.UNKNOWN -> {
        val unread = "the truck's connection could not be read ($evidence)"
        val then =
            if (waitStands) {
                "MilO was waiting beside the parked truck, and waits on until it can be read"
            } else {
                "so it counts as not connected"
            }
        "$unread, $then"
    }
}

/**
 * How a reading of the truck was reached, to follow what the trip rules were told: which
 * Bluetooth profile listed the truck, or why the phone could not say.
 */
internal fun TruckReading?.inWords(): String = when {
    this == null -> ""
    known -> " ($evidence)"
    else -> " (the truck's connection could not be read: $evidence)"
}

/**
 * The same for the reading a trigger took, which may have been set aside: a timer's reading of
 * "not connected" before any reading has shown the truck on its present link (amendment 18).
 */
internal fun Evidence.readingInWords(): String {
    if (!linkNotSeenYet) return reading.inWords()
    return " (it reads as not connected, ${reading?.evidence}, but no reading has shown it " +
        "connected since its link was made, so that proves nothing and what is believed stands)"
}

/**
 * The line for a trigger that told the trip rules nothing. Its reading of the truck could not
 * be had; or it said "not connected" before any reading had shown the truck connected on its
 * present link; or it is a poll's first "not connected", which is not yet believed.
 */
internal fun noNewsText(request: StartRequest, found: Evidence): String {
    val reading = found.reading
    val notConnected = "${request.source}: the truck reads as not connected${reading.inWords()}"
    return when {
        reading?.known == false ->
            "${request.source}: the truck's connection could not be read (${reading.evidence}). " +
                "Nothing changes"

        found.linkNotSeenYet ->
            "$notConnected. No reading has shown the truck connected since its link was made, " +
                "so this proves nothing"

        else -> "$notConnected. One reading alone proves nothing"
    }
}

/** Why a step that needs the trip service was not carried out: the service has to come up first. */
internal fun serviceNeededText(recording: Boolean): String = if (recording) {
    "Recording needs the trip service, which is not running"
} else {
    "Watching the parked truck needs the trip service, which is not running"
}

/** The line for a trip service that Android destroyed while it was needed. */
internal fun serviceLostText(recording: Boolean): String {
    val during = if (recording) "a trip is open" else "MilO is watching the parked truck"
    return "The trip service stopped while $during. Asking Android to start it again"
}

/** The line for what the controller holds in memory being dropped: nothing is doing the work. */
internal fun droppedText(recording: Boolean): String = if (recording) {
    "The open trip is not being recorded. The next trigger picks it up"
} else {
    "The parked truck is not being watched. The next trigger picks the wait up"
}

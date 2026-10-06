package com.shawnkowalchuk.milo.platform.trip

import com.shawnkowalchuk.milo.core.schedule.TripFiling
import com.shawnkowalchuk.milo.core.schedule.WorkSchedule
import com.shawnkowalchuk.milo.core.trip.ClosedTrip
import com.shawnkowalchuk.milo.core.trip.TripEndReason
import com.shawnkowalchuk.milo.core.trip.TripEvent
import com.shawnkowalchuk.milo.core.trip.TripState
import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.data.trip.classificationText
import com.shawnkowalchuk.milo.platform.bluetooth.TruckReading
import java.time.ZoneId
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
    return "$tripText; $truckText; $autoText$holdOffText"
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
}

private fun connectedText(truckConnected: Boolean): String =
    if (truckConnected) "truck connected" else "truck not connected"

/**
 * The line that records a closed trip: what became of it, why, and what it was measured from.
 *
 * @param ignored true if the trip rules kept the trip and it is stored as discarded all the
 * same, because it started outside the work schedule and such trips are set to be ignored.
 */
internal fun closedText(
    closed: ClosedTrip,
    reason: TripEndReason,
    storedFixes: Int,
    minimumTripDistanceMetres: Int,
    ignored: Boolean,
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
    return "$outcome; ended by $reason; $fixes"
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

/** A reading of the truck in words, with how it was reached. */
internal fun TruckReading.describe(): String = when (answer) {
    TruckReading.Answer.CONNECTED -> "the truck is connected ($evidence)"

    TruckReading.Answer.NOT_CONNECTED -> "the truck is not connected ($evidence)"

    TruckReading.Answer.UNKNOWN ->
        "the truck's connection could not be read ($evidence), so it counts as not connected"
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

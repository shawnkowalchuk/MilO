package com.shawnkowalchuk.milo.data.trip

import com.shawnkowalchuk.milo.core.trip.TripStatus
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

// How the event log words a change Shawn made by hand to a trip's figures. Plain functions, so
// that what is logged can be read (and tested) in one place. Like the rest of the event log the
// lines are English only.
//
// Every line names each value that changed, old and new. Once a trip is edited its row holds
// the new values, and the first edit keeps only what MilO recorded; whatever was typed in
// between exists nowhere but here. That is also why these lines, unlike those of the address
// lookup, spell addresses out: a typed address that is replaced would otherwise be gone
// without a trace. The event log stays on the phone.

// With the date, and to the second: an edit can move a trip to another day, and a recorded
// time is exact to the second while a typed one is on the whole minute.
private val DATE_AND_TIME: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT)

private fun time(epochMs: Long?, zone: ZoneId): String =
    epochMs?.let { DATE_AND_TIME.withZone(zone).format(Instant.ofEpochMilli(it)) } ?: "none"

private fun metres(distance: Double): String = "${distance.roundToInt()} m"

private fun address(text: String?): String = text?.let { "\"$it\"" } ?: "none"

/**
 * Every figure of a trip that differs between [before] and [after], each as "what: old → new".
 * Empty if none does. It compares the two rows and knows nothing of what was asked for, so a
 * value that a save or a restore changed cannot be left out of the log.
 */
internal fun changesInWords(before: Trip, after: Trip, zone: ZoneId): List<String> = buildList {
    if (before.startedAtMs != after.startedAtMs) {
        add("start ${time(before.startedAtMs, zone)} → ${time(after.startedAtMs, zone)}")
    }
    if (before.endedAtMs != after.endedAtMs) {
        add("end ${time(before.endedAtMs, zone)} → ${time(after.endedAtMs, zone)}")
    }
    if (before.distanceMetres != after.distanceMetres) {
        add("distance ${metres(before.distanceMetres)} → ${metres(after.distanceMetres)}")
    }
    if (before.startAddress != after.startAddress) {
        add("from ${address(before.startAddress)} → ${address(after.startAddress)}")
    }
    if (before.endAddress != after.endAddress) {
        add("to ${address(before.endAddress)} → ${address(after.endAddress)}")
    }
    val categoryChanged = before.category != after.category
    val nowByHand = after.categorySetByHand && !before.categorySetByHand
    if (categoryChanged || nowByHand) {
        val who = if (after.categorySetByHand) "set by hand" else "sorted again by the schedule"
        add("${before.category.inWords()} → ${after.category.inWords()} ($who)")
    }
    if (before.ranPastSchedule != after.ranPastSchedule) {
        add(if (after.ranPastSchedule) "now ran past schedule" else "no longer ran past schedule")
    }
}

/** The line for one save of the edit form. */
internal fun editText(tripId: Long, outcome: ByHandOutcome, zone: ZoneId): String = when (outcome) {
    is ByHandOutcome.Done -> {
        val changes = changesInWords(outcome.before, outcome.after, zone).joinToString("; ")
        val mark =
            when {
                outcome.after.addedByHand -> "It was added by hand and stays marked so"

                outcome.after.editedByHand && !outcome.before.editedByHand ->
                    "It is now marked as edited, and \"Restore recorded values\" puts back " +
                        "what MilO recorded"

                outcome.after.editedByHand -> "It was marked as edited already"

                else ->
                    "Its times, addresses and distance are as recorded, so it is not marked " +
                        "as edited"
            }
        "Trip $tripId: changed by hand on the edit screen: $changes (times in $zone). $mark"
    }

    is ByHandOutcome.Unchanged ->
        "Trip $tripId: saved on the edit screen with nothing changed. Nothing was written"

    is ByHandOutcome.Refused ->
        "Trip $tripId: edit refused: ${whyNotEditable(outcome.found)}. Nothing changed"
}

/** The line for one "Restore recorded values". */
internal fun restoreText(tripId: Long, outcome: ByHandOutcome, zone: ZoneId): String =
    when (outcome) {
        is ByHandOutcome.Done -> {
            val changes = changesInWords(outcome.before, outcome.after, zone)
            val what = if (changes.isEmpty()) "nothing differed" else changes.joinToString("; ")
            val lookedUp =
                if (outcome.before.startAddressByHand || outcome.before.endAddressByHand) {
                    " An address typed by hand was removed and is looked up again."
                } else {
                    ""
                }
            "Trip $tripId: recorded values restored on the edit screen: $what (times in " +
                "$zone). It is no longer marked as edited.$lookedUp"
        }

        is ByHandOutcome.Unchanged ->
            "Trip $tripId: restore recorded values changed nothing. Nothing was written"

        is ByHandOutcome.Refused -> {
            val found = outcome.found
            val why =
                if (found != null && found.canBeEdited) {
                    "it has no recorded values to put back"
                } else {
                    whyNotEditable(found)
                }
            "Trip $tripId: restore recorded values refused: $why. Nothing changed"
        }
    }

/** The line for a trip that was typed in by hand. */
internal fun addedText(trip: Trip, zone: ZoneId): String {
    val who = if (trip.categorySetByHand) "set by hand" else "sorted by the work schedule"
    return "Trip ${trip.id}: added by hand on the edit screen: ${time(trip.startedAtMs, zone)} " +
        "to ${time(trip.endedAtMs, zone)} ($zone), ${metres(trip.distanceMetres)}, from " +
        "${address(trip.startAddress)} to ${address(trip.endAddress)}, " +
        "${trip.category.inWords()} ($who). It has no GPS points, is marked as added by hand " +
        "and is counted like any finished trip"
}

private fun whyNotEditable(found: Trip?): String = when (found?.status) {
    null -> "there is no such trip"

    TripStatus.OPEN -> "it is still being recorded"

    // Not expected: a finished trip is what the write looks for.
    TripStatus.FINISHED -> "storage did not take the change"

    else -> "it is ${found.status.name.lowercase()}, not finished"
}

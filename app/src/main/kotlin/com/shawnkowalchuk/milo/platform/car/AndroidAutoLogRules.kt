package com.shawnkowalchuk.milo.platform.car

// What the app-wide watch on Android Auto makes of one report, and how it words its line of the
// event log. Plain functions with unit tests; nothing here knows Android.

/**
 * What the app-wide watch does about one report of Android Auto's connection.
 *
 * @param written whether a line goes to the event log.
 */
internal enum class AndroidAutoNote(val written: Boolean) {
    /** The first report since the watch began, with no trip being recorded. */
    FIRST_READING(written = true),

    /** Android Auto connected or disconnected, with no trip being recorded. */
    CHANGE(written = true),

    /**
     * A trip is being recorded. The trip service has a watch of its own for as long as it
     * records, and the trip controller writes what that one reports, so a line from here would
     * say the same thing twice.
     */
    LEFT_TO_THE_TRIP_SERVICE(written = false),

    /** The report says what the last one said. The library can repeat itself. */
    NOTHING_NEW(written = false),
}

/**
 * Decides whether a report of Android Auto's connection becomes a line of the event log.
 *
 * A line is written for the first report and for every change after it, and only while no trip
 * is being recorded. What a report said is remembered by the caller either way, so a connection
 * made during a trip and ended after it is still written as a change when it ends.
 *
 * @param lastKnown what the report before this one said, or null if this is the first.
 * @param connected what this report says.
 * @param tripBeingRecorded whether the trip controller shows a trip as being recorded.
 */
internal fun judgeAndroidAutoReport(
    lastKnown: Boolean?,
    connected: Boolean,
    tripBeingRecorded: Boolean,
): AndroidAutoNote = when {
    lastKnown == connected -> AndroidAutoNote.NOTHING_NEW
    tripBeingRecorded -> AndroidAutoNote.LEFT_TO_THE_TRIP_SERVICE
    lastKnown == null -> AndroidAutoNote.FIRST_READING
    else -> AndroidAutoNote.CHANGE
}

/**
 * The line for a report, or null if none is written.
 *
 * @param rawType the value `CarConnection` gave, as the trip service's lines name it too.
 */
internal fun androidAutoLogText(note: AndroidAutoNote, connected: Boolean, rawType: Int?): String? {
    val raw = "CarConnection reports ${rawType?.let { "type $it" } ?: "no type"}"
    return when (note) {
        AndroidAutoNote.FIRST_READING ->
            if (connected) {
                "Android Auto is connected ($raw). First reading since MilO's process " +
                    "started, so MilO did not see it connect. No trip is being recorded"
            } else {
                "Android Auto is not connected ($raw). First reading since MilO's process " +
                    "started. No trip is being recorded"
            }

        AndroidAutoNote.CHANGE ->
            if (connected) {
                "Android Auto connected ($raw). No trip is being recorded, and Android Auto " +
                    "does not start one"
            } else {
                "Android Auto disconnected ($raw). No trip is being recorded"
            }

        AndroidAutoNote.LEFT_TO_THE_TRIP_SERVICE, AndroidAutoNote.NOTHING_NEW -> null
    }
}

/** The `ERROR` line for a failure inside the watch. [doing] finishes the sentence. */
internal fun androidAutoLogFailedText(doing: String): String =
    "The watch on Android Auto outside trips failed while $doing"

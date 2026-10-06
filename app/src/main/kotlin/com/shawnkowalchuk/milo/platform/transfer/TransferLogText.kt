package com.shawnkowalchuk.milo.platform.transfer

import com.shawnkowalchuk.milo.data.transfer.CheckedExport
import com.shawnkowalchuk.milo.data.transfer.ExportContents
import com.shawnkowalchuk.milo.data.transfer.ExportProblem
import com.shawnkowalchuk.milo.data.transfer.PointsLeftOut

// The event log's lines about an export and an import. They are `REPORT` lines, like the lines
// about a report for the accountant: a file MilO made and handed over, or was handed. A
// category of their own would be a new stored constant, which no earlier build can read
// (ARCHITECTURE, section 6). In English, like every line of the log, and pure, so they are
// tested. None of them names a person, an address or a position.

private fun ExportContents.inWords(): String = "$trips trips, $sentReports sent reports, " +
    (points?.let { "$it raw GPS points" } ?: "no raw GPS points (they were left out)") +
    (if (settings) " and the settings" else ", and no settings: they could not be read")

/** How many trip ids a line names before it only counts the rest. */
private const val TRIP_IDS_NAMED = 10

private fun PointsLeftOut.inWords(): String {
    val named = tripIds.take(TRIP_IDS_NAMED).joinToString(", ")
    val more = tripIds.size - TRIP_IDS_NAMED
    return "$points raw GPS points that are stored for a trip this phone does not hold, or " +
        "that is not closed (trip ids: $named" + (if (more > 0) " and $more more" else "") + ")"
}

/** @param leftOut the raw points that were passed over, or null if there were none. */
internal fun exportedLine(contents: ExportContents, leftOut: PointsLeftOut? = null): String =
    "All data exported to a file that was picked: ${contents.inWords()}. Where the file is " +
        "kept is up to the app it was saved with" +
        leftOut?.let { ". Left out of the file: ${it.inWords()}" }.orEmpty()

/** What was being done when [this] failed, in words that finish "… failed while". */
internal fun TransferWork.inWords(): String = when (this) {
    TransferWork.EXPORTING -> "writing an export"
    TransferWork.READING_FILE -> "reading a file that was picked for an import"
    TransferWork.IMPORTING -> "importing"
}

internal fun exportFileLeftLine(): String =
    "No export was made, because a trip is being recorded, and the file that was made for it " +
        "could not be removed again: the app it was saved with would not remove it. That " +
        "file is not an export"

/** What was being done when an export failed, in words that finish "… failed while". */
internal fun exportFailureInWords(unfinishedRemoved: Boolean): String = "writing an export. " +
    if (unfinishedRemoved) {
        "The unfinished file was removed"
    } else {
        "The unfinished file could NOT be removed: it is not a whole export"
    }

internal fun importRefusedLine(problem: ExportProblem): String =
    "A file picked for an import was refused, and nothing was changed: " +
        when (problem) {
            ExportProblem.NotAnExport -> "it is not an export of MilO's"

            is ExportProblem.NewerVersion ->
                "it was written by a newer MilO, in format version ${problem.version}, which " +
                    "this MilO does not read"

            is ExportProblem.Damaged -> problem.why
        }

/**
 * The line that is written in the transaction that replaces the trips, and so is in the log
 * exactly if they were replaced (`ImportBegun`). One line: it is kept in a file as well.
 *
 * @param stored what the phone held before, by number of trips and of sent reports.
 * @param safetyCopy the name of the file that holds it now, and [leftOut] what that file
 * passed over, or null.
 */
internal fun importBegunLine(
    file: CheckedExport,
    stored: ImportOffer,
    safetyCopy: String,
    leftOut: PointsLeftOut?,
): String = "An import has replaced the ${stored.storedTrips} trips and " +
    "${stored.storedSentReports} sent reports this phone held with the ${file.trips.size} " +
    "trips and ${file.sentReports.size} sent reports of an export written by MilO " +
    "${file.appVersion}. Its settings and its raw GPS points are stored next. If no later " +
    "line says that the data was imported, MilO was ended before that was done, and finishes " +
    "it at its next start. What the phone held before is in the safety copy $safetyCopy, " +
    "inside MilO" + leftOut?.let { ". Left out of that copy: ${it.inWords()}" }.orEmpty()

/**
 * @param stored what the phone held before, by number of trips and of sent reports.
 * @param safetyCopy the name of the file that holds it now.
 */
internal fun importedLine(
    file: CheckedExport,
    done: ImportDone,
    stored: ImportOffer,
    safetyCopy: String,
): String = "Data imported from an export written by MilO ${file.appVersion}: ${done.trips} " +
    "trips and ${done.sentReports} sent reports replaced the ${stored.storedTrips} trips and " +
    "${stored.storedSentReports} sent reports this phone held. " +
    done.pointsAndSettingsInWords() +
    "What the phone held before is in the safety copy $safetyCopy, inside MilO"

private fun ImportDone.pointsAndSettingsInWords(): String = when (pointsTaken) {
    PointsTaken.TAKEN -> "$points raw GPS points were stored with them. "

    PointsTaken.NONE_IN_FILE ->
        "The file holds no raw GPS points, so the imported trips have none. "

    PointsTaken.NOT_STORED ->
        "The file's raw GPS points could NOT be stored (the line before this one says " +
            "why); the imported trips have none until the file is imported again. "
} +
    when (settings) {
        SettingsTaken.TAKEN -> "The file's settings are in force. "
        SettingsTaken.NONE_IN_FILE -> "The file holds no settings; the phone's own were kept. "
        SettingsTaken.NOT_STORED -> "The file's settings could NOT be stored. "
    }

// What a process start writes about an import that the process before it was ended in the
// middle of (`ImportResume`).

/** The steps that were left have been taken, from MilO's copy of the file. */
internal fun finishedLaterLine(file: CheckedExport, done: ImportDone, safetyCopy: String): String =
    "MilO was ended in the middle of an import, and has finished it at this start from its " +
        "copy of the file (an export written by MilO ${file.appVersion}): the ${done.trips} " +
        "trips and ${done.sentReports} sent reports were stored before MilO was ended. " +
        done.pointsAndSettingsInWords() +
        "What the phone held before the import is in the safety copy $safetyCopy, inside MilO"

internal fun cutOffBeforeReplacingLine(): String =
    "MilO was ended just as an import began, before the trips were replaced. Everything on " +
        "this phone is as it was. MilO's copy of the file and the safety copy written for " +
        "that import were removed; the file has to be imported again"

/** @param removed how many raw points of the trips that are gone were removed. */
internal fun cutOffPointsRemovedLine(removed: Int): String =
    "MilO was ended in the middle of an import, after the trips and the sent reports were " +
        "replaced and before the file's raw GPS points were all stored. Its copy of the file " +
        "could not be used to finish, so the $removed raw GPS points of the trips that are " +
        "gone were removed: the imported trips have none, and the file's settings may not " +
        "have been stored either. Importing the file again makes it whole"

internal fun cutOffLeftLine(): String =
    "MilO was ended in the middle of an import, and two later starts could not finish it. " +
        "It is left as it is: the trips and the sent reports are the file's, and raw GPS " +
        "points of the trips that are gone may still be stored under their trip ids. " +
        "Importing the file again puts that right"

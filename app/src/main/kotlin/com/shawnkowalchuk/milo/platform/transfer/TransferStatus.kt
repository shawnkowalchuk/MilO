package com.shawnkowalchuk.milo.platform.transfer

import com.shawnkowalchuk.milo.data.transfer.CheckedExport
import com.shawnkowalchuk.milo.data.transfer.ExportContents
import com.shawnkowalchuk.milo.data.transfer.ExportProblem
import com.shawnkowalchuk.milo.data.transfer.StoredCounts

// Where an export or an import stands, and what the last one came to: what [DataTransfer]
// publishes for the Settings screen. Plain values, so the screen's side is tested without a
// phone.

/** What is being done right now. Only one thing is, at any time. */
enum class TransferWork {
    /** The export file is being written. */
    EXPORTING,

    /** A picked file is being copied into MilO and checked from start to end. */
    READING_FILE,

    /** The phone's data is being replaced. */
    IMPORTING,
}

/** Why an export or an import was not made. */
sealed interface TransferRefusal {
    /** A trip is being recorded. Nothing was read, written or changed. */
    data object TripInProgress : TransferRefusal

    /** The export could not be written. The unfinished file was removed. */
    data object ExportNotWritten : TransferRefusal

    /** The export could not be written, and the unfinished file could not be removed either. */
    data object ExportLeftUnfinished : TransferRefusal

    /** The picked file could not be read at all. */
    data object FileNotRead : TransferRefusal

    /** The picked file is larger than any export. */
    data object FileTooLarge : TransferRefusal

    /** The picked file is not an export of MilO's. */
    data object NotAnExport : TransferRefusal

    /** The picked file was written by a newer MilO, in a form this one does not read. */
    data class NewerVersion(val version: Int) : TransferRefusal

    /** The picked file is an export that is cut short or has something wrong in it. */
    data object Damaged : TransferRefusal

    /** There is no safety copy to put back. */
    data object NoSafetyCopy : TransferRefusal

    /** The safety copy of the phone's own data could not be written, so nothing was replaced. */
    data object SafetyCopyNotWritten : TransferRefusal

    /** The trips could not be replaced. Everything on the phone is as it was. */
    data object NothingReplaced : TransferRefusal
}

/** What a problem with a file is called on the screen. */
fun ExportProblem.asRefusal(): TransferRefusal = when (this) {
    ExportProblem.NotAnExport -> TransferRefusal.NotAnExport
    is ExportProblem.NewerVersion -> TransferRefusal.NewerVersion(version)
    is ExportProblem.Damaged -> TransferRefusal.Damaged
}

/**
 * A file that was checked and can be imported, and what importing it would replace: what the
 * question "Replace everything on this phone?" says.
 *
 * @param points how many raw GPS points the file holds, or null if it holds none.
 * @param hasSettings false if the file holds no settings. The phone's own are then kept.
 * @param safetyCopyAtMs when the file was written, if it is one of MilO's own safety copies
 * from before an import, and null for a file that was picked.
 */
data class ImportOffer(
    val exportedAtMs: Long,
    val trips: Int,
    val sentReports: Int,
    val points: Long?,
    val hasSettings: Boolean,
    val storedTrips: Int,
    val storedSentReports: Int,
    val safetyCopyAtMs: Long?,
) {
    /** True if the file is one of MilO's own safety copies. */
    val fromSafetyCopy: Boolean get() = safetyCopyAtMs != null
}

internal fun importOffer(file: CheckedExport, stored: StoredCounts, safetyCopyAtMs: Long?) =
    ImportOffer(
        exportedAtMs = file.exportedAtMs,
        trips = file.trips.size,
        sentReports = file.sentReports.size,
        points = file.pointCount,
        hasSettings = file.settings != null,
        storedTrips = stored.trips,
        storedSentReports = stored.sentReports,
        safetyCopyAtMs = safetyCopyAtMs,
    )

/** What an import took from the file's settings. */
enum class SettingsTaken {
    /** The file's settings are in force. */
    TAKEN,

    /** The file holds no settings. The phone's own were kept. */
    NONE_IN_FILE,

    /** The file's settings could not be stored. The phone's own are still in force. */
    NOT_STORED,
}

/** What became of the raw GPS points in an import. */
enum class PointsTaken {
    /** The file's points are stored. */
    TAKEN,

    /** The file holds none. The points of the trips that were replaced are gone. */
    NONE_IN_FILE,

    /**
     * The file's points could not be stored. The phone now has none for the imported trips;
     * the trips themselves are whole, and the file can be imported again.
     */
    NOT_STORED,
}

/**
 * What an import did.
 *
 * @param truckNeedsPairing true if the truck that is stored now has to be paired on this phone
 * before a trip can start by itself.
 * @param afterCutOff true if MilO's process was ended in the middle of the import, and a later
 * start finished it.
 */
data class ImportDone(
    val trips: Int,
    val sentReports: Int,
    val points: Long,
    val pointsTaken: PointsTaken,
    val settings: SettingsTaken,
    val truckNeedsPairing: Boolean,
    val afterCutOff: Boolean = false,
)

/**
 * What became of an import that MilO's process was ended in the middle of, where a later
 * start could not simply finish it (`ImportResume`).
 */
enum class CutOffImport {
    /** It had replaced nothing yet. Everything on the phone is as it was before it. */
    NOTHING_REPLACED,

    /**
     * The trips and the sent reports are the file's. The raw points of the trips that are gone
     * were removed, and the file's are not stored; its settings may not be either.
     */
    POINTS_REMOVED,

    /**
     * It could not be put right. The trips and the sent reports are the file's, and raw points
     * of the trips that are gone may still be stored beside them.
     */
    NOT_FINISHED,
}

/** What the last export or import came to. */
sealed interface TransferOutcome {
    /**
     * @param pointsLeftOut how many raw points were passed over because the trip they belong
     * to is not stored (`PointsLeftOut`). None, on a phone where nothing has gone wrong.
     */
    data class Exported(val contents: ExportContents, val pointsLeftOut: Long = 0) :
        TransferOutcome

    data class Imported(val done: ImportDone) : TransferOutcome

    data class Refused(val why: TransferRefusal) : TransferOutcome

    data class CutOff(val what: CutOffImport) : TransferOutcome
}

/**
 * Where things stand.
 *
 * @param working what is being done, or null when nothing is.
 * @param offer a file that was checked and waits for Shawn's answer, or null.
 * @param outcome what the last export or import came to, until the next one is started.
 */
data class TransferStatus(
    val working: TransferWork? = null,
    val offer: ImportOffer? = null,
    val outcome: TransferOutcome? = null,
) {
    /** Whether a new export or import may be started: nothing is under way or waiting. */
    val idle: Boolean get() = working == null && offer == null
}

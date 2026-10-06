package com.shawnkowalchuk.milo.feature.settings

import com.shawnkowalchuk.milo.data.settings.LastExport
import com.shawnkowalchuk.milo.platform.transfer.CutOffImport
import com.shawnkowalchuk.milo.platform.transfer.ImportOffer
import com.shawnkowalchuk.milo.platform.transfer.PointsTaken
import com.shawnkowalchuk.milo.platform.transfer.SettingsTaken
import com.shawnkowalchuk.milo.platform.transfer.TransferOutcome
import com.shawnkowalchuk.milo.platform.transfer.TransferRefusal
import com.shawnkowalchuk.milo.platform.transfer.TransferStatus
import com.shawnkowalchuk.milo.platform.transfer.TransferWork
import java.time.ZoneId

// What the Settings screen's card for backup, export and import shows, and the functions that
// decide it. Pure, so they are tested without a phone.

/**
 * The card.
 *
 * @param lastExport when the last export was written, or null if none was, or if the settings
 * cannot be read.
 * @param includePoints the switch "Include the GPS points". On each time the screen is opened.
 * @param tripInProgress true while a trip is being recorded. Neither an export nor an import
 * is made then, and the buttons wait.
 * @param working what is being done right now, or null.
 * @param offer a file that was checked and that the question on the screen is about, or null.
 * @param lines what the last export or import came to, one sentence each, until the next.
 * @param safetyCopiesAtMs when MilO kept each of its safety copies, newest first. Each can be
 * put back, so that an import made after a wrong one does not put the first out of reach.
 * @param zone the phone's time zone, for the dates and times the card writes.
 */
data class DataCardState(
    val lastExport: LastExport? = null,
    val includePoints: Boolean = true,
    val tripInProgress: Boolean = false,
    val working: TransferWork? = null,
    val offer: ImportOffer? = null,
    val lines: List<OutcomeLine> = emptyList(),
    val safetyCopiesAtMs: List<Long> = emptyList(),
    val zone: ZoneId = ZoneId.systemDefault(),
) {
    /** Whether the buttons can be pressed: nothing is under way, asked or being recorded. */
    val canStart: Boolean get() = working == null && offer == null && !tripInProgress
}

/** How a sentence about an export or an import is to be read. */
enum class OutcomeTone {
    /** It was done. */
    DONE,

    /** It was not done, or not all of it. */
    PROBLEM,

    /** It was done, and something is left for Shawn to do. */
    TO_DO,
}

/** One sentence about what the last export or import came to. */
sealed interface OutcomeLine {
    val tone: OutcomeTone

    /** @param points null if the GPS points were left out. */
    data class Exported(val trips: Int, val sentReports: Int, val points: Long?) : OutcomeLine {
        override val tone = OutcomeTone.DONE
    }

    /** The export holds no settings: they could not be read. */
    data object ExportedWithoutSettings : OutcomeLine {
        override val tone = OutcomeTone.PROBLEM
    }

    /** The export passed over this many GPS points, which are stored for no trip. */
    data class PointsLeftOut(val points: Long) : OutcomeLine {
        override val tone = OutcomeTone.PROBLEM
    }

    /** MilO was closed in the middle of the import, and finished it at its next start. */
    data object FinishedAfterCutOff : OutcomeLine {
        override val tone = OutcomeTone.DONE
    }

    /** MilO was closed in the middle of the import, and this is what is left of it. */
    data class CutOff(val what: CutOffImport) : OutcomeLine {
        override val tone = OutcomeTone.PROBLEM
    }

    data class Imported(
        val trips: Int,
        val sentReports: Int,
        val points: Long,
        val pointsTaken: PointsTaken,
    ) : OutcomeLine {
        // The trips are in either way; points that could not be stored are still a failure.
        override val tone =
            if (pointsTaken == PointsTaken.NOT_STORED) OutcomeTone.PROBLEM else OutcomeTone.DONE
    }

    /** The file held no settings, so the phone's own were kept. */
    data object SettingsKept : OutcomeLine {
        override val tone = OutcomeTone.DONE
    }

    /** The file's settings could not be stored. */
    data object SettingsNotStored : OutcomeLine {
        override val tone = OutcomeTone.PROBLEM
    }

    /** The truck has to be paired again on this phone. */
    data object PairTruckAgain : OutcomeLine {
        override val tone = OutcomeTone.TO_DO
    }

    /** What the phone held before the import is kept, and can be put back. */
    data object SafetyCopyKept : OutcomeLine {
        override val tone = OutcomeTone.DONE
    }

    data class Refused(val why: TransferRefusal) : OutcomeLine {
        override val tone = OutcomeTone.PROBLEM
    }

    /** The phone could not show a file picker at all. */
    data object NoFilePicker : OutcomeLine {
        override val tone = OutcomeTone.PROBLEM
    }
}

/**
 * The sentences for what the last export or import came to: what was done first, then what
 * was not, then what is left to do.
 */
fun outcomeLines(outcome: TransferOutcome?): List<OutcomeLine> = when (outcome) {
    null -> emptyList()

    is TransferOutcome.Refused -> listOf(OutcomeLine.Refused(outcome.why))

    is TransferOutcome.CutOff -> listOf(OutcomeLine.CutOff(outcome.what))

    is TransferOutcome.Exported ->
        with(outcome.contents) {
            listOfNotNull(
                OutcomeLine.Exported(trips, sentReports, points),
                OutcomeLine.ExportedWithoutSettings.takeUnless { settings },
                OutcomeLine.PointsLeftOut(outcome.pointsLeftOut).takeIf { it.points > 0 },
            )
        }

    is TransferOutcome.Imported ->
        with(outcome.done) {
            listOfNotNull(
                OutcomeLine.FinishedAfterCutOff.takeIf { afterCutOff },
                OutcomeLine.Imported(trips, sentReports, points, pointsTaken),
                when (settings) {
                    SettingsTaken.TAKEN -> null
                    SettingsTaken.NONE_IN_FILE -> OutcomeLine.SettingsKept
                    SettingsTaken.NOT_STORED -> OutcomeLine.SettingsNotStored
                },
                OutcomeLine.PairTruckAgain.takeIf { truckNeedsPairing },
                OutcomeLine.SafetyCopyKept,
            )
        }
}

/**
 * The card for what is stored and what `DataTransfer` reports.
 *
 * @param noFilePicker true if the last press could not open a file picker. It is said in place
 * of an outcome, until the next press.
 */
fun dataCardState(
    lastExport: LastExport?,
    status: TransferStatus,
    tripInProgress: Boolean,
    includePoints: Boolean,
    safetyCopiesAtMs: List<Long>,
    noFilePicker: Boolean,
    zone: ZoneId,
): DataCardState = DataCardState(
    lastExport = lastExport,
    includePoints = includePoints,
    tripInProgress = tripInProgress,
    working = status.working,
    offer = status.offer,
    lines = if (noFilePicker) listOf(OutcomeLine.NoFilePicker) else outcomeLines(status.outcome),
    safetyCopiesAtMs = safetyCopiesAtMs,
    zone = zone,
)

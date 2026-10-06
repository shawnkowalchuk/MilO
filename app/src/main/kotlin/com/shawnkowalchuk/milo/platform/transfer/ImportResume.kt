package com.shawnkowalchuk.milo.platform.transfer

import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.transfer.CheckedExport
import com.shawnkowalchuk.milo.data.transfer.DataImport
import com.shawnkowalchuk.milo.data.transfer.ExportReading
import com.shawnkowalchuk.milo.data.transfer.ImportBegun
import com.shawnkowalchuk.milo.data.transfer.IncomingImport
import com.shawnkowalchuk.milo.data.transfer.SafetyCopies
import java.io.File
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/**
 * How many process starts set about finishing one import. The first takes the steps that were
 * left, from MilO's copy of the file; the second only removes the raw points of the trips that
 * are gone. After that the import is left as it is and the log says so: whatever ended two
 * processes in the middle of this must not go on ending every one that follows, because the
 * trip service runs in each of them.
 */
private const val MAX_TRIES = 2

/**
 * At a process start: deals with an import that the last process was ended in the middle of.
 *
 * `ImportRun` leaves a note before it replaces the trips and takes it away after its last
 * step. If the note is there now, the process that wrote it is gone, and one of three things
 * is true:
 *
 * - **The trips were not replaced.** The line that the replacing writes into the event log, in
 *   the same transaction, is not there. Everything is as it was: the note, the copy of the
 *   file and the safety copy written for it are removed.
 * - **The trips were replaced and the rest was not finished.** The phone holds the file's
 *   trips beside the raw points of the trips that are gone, under the same trip ids. The
 *   steps after the trips are taken again from MilO's copy of the file: the settings, the
 *   points, the lines in the log.
 * - **The same, and the copy cannot be used** (it is gone or no longer reads as it did, or a
 *   start has tried it before). The points of the trips that are gone are removed, so that
 *   no trip keeps another trip's track, and the file has to be imported once more.
 *
 * None of it touches the trips. A trip that started since has a higher id than any the import
 * knew, and its points are passed over, so this can run while a trip is being recorded.
 *
 * @param run the import's own steps, which are taken again.
 */
internal class ImportResume(
    private val run: ImportRun,
    private val import: DataImport,
    private val incoming: IncomingImport,
    private val safetyCopies: SafetyCopies,
    private val eventLog: EventLogRepository,
    private val failures: TransferFailures,
    private val clock: () -> Long,
) {
    /**
     * The note of an import that was cut off, or null if there is none. Without a note, a copy
     * of a picked file that is still there belongs to a question that was never answered, and
     * is removed: it can be tens of megabytes, and nothing else would ever remove it.
     *
     * @throws IOException if the files cannot be read or removed.
     */
    suspend fun waiting(): ImportBegun? {
        val begun =
            try {
                incoming.begun()
            } catch (unreadable: IOException) {
                // A note is written whole or not at all, so this is a damaged disk. Nothing
                // can be finished from it, and it must not be met again at every start.
                failures.report("reading the note of an import that had begun", unreadable)
                incoming.clearBegun()
                null
            }
        if (begun == null) incoming.clear()
        return begun
    }

    /**
     * Deals with the import that left [begun] and says what it came to.
     *
     * @throws IOException and whatever else storage throws. The note is then still there, and
     * the next start sets about it again, up to [MAX_TRIES] times.
     */
    suspend fun finish(begun: ImportBegun): TransferOutcome {
        // The log is asked by the first start only, which is the one that follows the import
        // and so finds its line among the newest. A later start may come when the log has
        // been trimmed, and a note that counts a try was written after the line was seen.
        val replaced = begun.tries > 0 || import.wasReplaced(begun)
        if (!replaced) return nothingWasReplaced(begun)
        if (begun.tries >= MAX_TRIES) return leftAsItIs()
        // On the disk before anything is tried, so that a start that is ended in the middle
        // of this is counted.
        incoming.noteBegun(begun.copy(tries = begun.tries + 1))
        val file = incoming.waiting()
        val checked = if (begun.tries == 0 && file != null) checkedAgain(file) else null
        val outcome =
            if (file != null && checked != null) {
                val copy = safetyCopies.nameOf(begun.safetyCopyAtMs)
                val done =
                    run.finish(file, checked, begun.highestTripId) { done ->
                        finishedLaterLine(checked, done, copy)
                    }
                TransferOutcome.Imported(done.copy(afterCutOff = true))
            } else {
                removeStalePoints(begun)
            }
        // Every safety copy stays: this start does not know whether the import was one that
        // put a copy back, and the next import makes room.
        run.tidyUp(makeRoom = false)
        return outcome
    }

    /** What MilO's copy of the file holds, or null if it does not read as a whole export now. */
    private suspend fun checkedAgain(file: File): CheckedExport? = try {
        (import.check(file) as? ExportReading.Good)?.export
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        failures.report("reading the kept copy of a file whose import was cut off", failure)
        null
    }

    private suspend fun nothingWasReplaced(begun: ImportBegun): TransferOutcome {
        incoming.clearBegun()
        incoming.clear()
        // It holds what the phone holds, and would only push an older copy out later.
        failures.contained("removing the safety copy of an import that replaced nothing") {
            safetyCopies.remove(begun.safetyCopyAtMs)
        }
        say(EventCategory.REPORT, cutOffBeforeReplacingLine())
        return TransferOutcome.CutOff(CutOffImport.NOTHING_REPLACED)
    }

    private suspend fun removeStalePoints(begun: ImportBegun): TransferOutcome {
        val removed = import.removePoints(begun.highestTripId)
        incoming.clearBegun()
        say(EventCategory.REPORT, cutOffPointsRemovedLine(removed))
        return TransferOutcome.CutOff(CutOffImport.POINTS_REMOVED)
    }

    private suspend fun leftAsItIs(): TransferOutcome {
        incoming.clearBegun()
        incoming.clear()
        say(EventCategory.ERROR, cutOffLeftLine())
        return TransferOutcome.CutOff(CutOffImport.NOT_FINISHED)
    }

    private suspend fun say(category: EventCategory, line: String) {
        failures.contained("writing into the event log what became of an import that was cut off") {
            eventLog.add(clock(), category, line)
        }
    }
}

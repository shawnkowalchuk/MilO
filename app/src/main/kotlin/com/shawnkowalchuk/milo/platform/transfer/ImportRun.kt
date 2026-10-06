package com.shawnkowalchuk.milo.platform.transfer

import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.settings.SettingsStore
import com.shawnkowalchuk.milo.data.settings.replaceTransferred
import com.shawnkowalchuk.milo.data.settings.transferred
import com.shawnkowalchuk.milo.data.transfer.CheckedExport
import com.shawnkowalchuk.milo.data.transfer.DataExport
import com.shawnkowalchuk.milo.data.transfer.DataImport
import com.shawnkowalchuk.milo.data.transfer.ExportWritten
import com.shawnkowalchuk.milo.data.transfer.ImportBegun
import com.shawnkowalchuk.milo.data.transfer.IncomingImport
import com.shawnkowalchuk.milo.data.transfer.MainReplacement
import com.shawnkowalchuk.milo.data.transfer.PointsLeftOut
import com.shawnkowalchuk.milo.data.transfer.SAFETY_COPIES_KEPT
import com.shawnkowalchuk.milo.data.transfer.SafetyCopies
import com.shawnkowalchuk.milo.data.transfer.SafetyCopy
import com.shawnkowalchuk.milo.data.transfer.logLine
import java.io.File
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/** What the event log calls the work that follows an import. */
private const val AFTER_IMPORT = "data imported"

/** A trip started while the safety copy was written. An I/O failure, so its file is removed. */
private class TripStartedException : IOException("A trip started while the safety copy was written")

/** What became of the raw points: see [PointsTaken]. */
private class PointsStep(val taken: PointsTaken, val stored: Long, val staleLeft: Boolean)

/**
 * One import, from Shawn's yes to the last line in the log.
 *
 * In this order, and the order is the point:
 *
 * 1. **Not while a trip is being recorded.** Asked here, and again inside the transaction that
 *    replaces the trips.
 * 2. **A safety copy first.** Everything the phone holds is written to a file inside MilO, a
 *    whole export with every raw point. If that fails, nothing is replaced.
 * 3. **A note that the import begins** (`ImportBegun`), beside the copy of the file.
 * 4. **The trips and the sent reports, in one transaction,** which also writes the line into
 *    the event log that says so. If it fails, the phone is exactly as it was.
 * 5. **The settings, in one write,** with the truck decided by `truckOnArrival`: an import
 *    never makes the phone believe it is paired.
 * 6. **The raw points, in one transaction of their own database.** If it fails, the points of
 *    the trips that are gone are removed, so that no imported trip shows another trip's track.
 * 7. **The note is taken away.**
 *
 * From step 5 on a failure does not undo the steps before it: the trips are the file's by
 * then, and what could not be done is said on the screen and in the log. Importing the same
 * file again makes it whole, and the safety copy holds what was there before.
 *
 * A process that is ended between steps 4 and 7 can say nothing. The note says it instead, and
 * the next process start takes steps 5 to 7 again from MilO's copy of the file
 * ([ImportResume]). Each of them can be taken twice.
 *
 * @param pairingHere asks Android which companion associations MilO holds on this phone.
 * @param afterImport what has to look at the new data and settings: the pairing check, the
 * address lookup, the sorting into Business and Personal, the reminder, the driving alert.
 */
internal class ImportRun(
    private val export: DataExport,
    private val import: DataImport,
    private val incoming: IncomingImport,
    private val safetyCopies: SafetyCopies,
    private val settings: SettingsStore,
    private val pairingHere: () -> PairingHere,
    private val tripInProgress: () -> Boolean,
    private val afterImport: (occasion: String) -> Unit,
    private val eventLog: EventLogRepository,
    private val failures: TransferFailures,
    private val clock: () -> Long,
) {
    /**
     * @param file MilO's own copy of the picked file, and [checked] what the check of it found.
     * @param asked what the question said, for the line in the log.
     */
    suspend fun run(file: File, checked: CheckedExport, asked: ImportOffer): TransferOutcome {
        val outcome = replaceEverything(file, checked, asked)
        // Putting a safety copy back never costs an older copy: the one this import wrote is
        // kept beside them all, and the one that was put back goes once the phone holds all
        // of it again. So "Put that data back", pressed for one copy after another, cannot
        // push out the copy of what the phone held before the first wrong import.
        tidyUp(
            putBack = asked.safetyCopyAtMs.takeIf { outcome.isWhole() },
            makeRoom = asked.safetyCopyAtMs == null,
        )
        return outcome
    }

    /**
     * What is removed when an import has come to its end, whatever it came to.
     *
     * @param putBack the safety copy to remove, or null for none.
     * @param makeRoom false leaves every other safety copy where it is; true keeps the newest
     * few only.
     */
    suspend fun tidyUp(putBack: Long? = null, makeRoom: Boolean) {
        failures.contained("removing the copy of the imported file") {
            // While the note is there, the copy is what the next start finishes from.
            if (incoming.begun() == null) incoming.clear()
        }
        failures.contained("removing older safety copies") {
            if (putBack != null) safetyCopies.remove(putBack)
            // With them goes what a failed safety copy left behind.
            safetyCopies.keepNewest(if (makeRoom) SAFETY_COPIES_KEPT else Int.MAX_VALUE)
        }
    }

    private suspend fun replaceEverything(
        file: File,
        checked: CheckedExport,
        asked: ImportOffer,
    ): TransferOutcome {
        if (tripInProgress()) return refused(TransferRefusal.TripInProgress)
        val (safetyCopy, leftOut) =
            try {
                writeSafetyCopy()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (tripStarted: TripStartedException) {
                return refused(TransferRefusal.TripInProgress)
            } catch (failure: Exception) {
                failures.report("writing the safety copy. Nothing was replaced", failure)
                return refused(TransferRefusal.SafetyCopyNotWritten)
            }
        val replaced =
            try {
                val begun =
                    ImportBegun(
                        highestTripId = import.highestTripIdWith(checked),
                        safetyCopyAtMs = safetyCopy.writtenAtMs,
                        saidAtMs = clock(),
                        said = importBegunLine(checked, asked, safetyCopy.file.name, leftOut),
                    )
                incoming.noteBegun(begun)
                import.replaceMain(checked, begun.logLine())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                failures.report("replacing the trips. Nothing was replaced", failure)
                forgetBeginning()
                return refused(TransferRefusal.NothingReplaced)
            }
        if (replaced !is MainReplacement.Done) {
            forgetBeginning()
            return refused(TransferRefusal.TripInProgress)
        }
        val done =
            finish(file, checked, replaced.highestTripId) { done ->
                importedLine(checked, done, asked, safetyCopy.file.name)
            }
        return TransferOutcome.Imported(done)
    }

    /**
     * Everything that follows the replacing of the trips: the settings, the raw points, the
     * note taken away, the lines in the log, and the rest of the app told. Also what a later
     * process start does again for an import that was cut off, which is why none of it minds
     * being done twice.
     *
     * @param highestTripId every raw point up to this trip id belongs to a trip that was
     * replaced.
     * @param line the line for the event log, for what was done.
     */
    suspend fun finish(
        file: File,
        checked: CheckedExport,
        highestTripId: Long,
        line: (ImportDone) -> String,
    ): ImportDone {
        // From here on the trips are the file's, and each step says for itself what it could
        // not do; the steps after it still run.
        val (settingsTaken, truck) = takeSettings(checked)
        val points = takePoints(file, checked, highestTripId)
        // The points are the file's now, or gone: nothing is left for a later start to do. If
        // not even the removing worked, the note stays, and the next start tries once more.
        // TODO(debt): "stored" here is safe from the end of the process, not from a power cut
        // in the seconds after it; the note would have to wait for a checkpoint of both
        // databases (FINDINGS_LOG, 2026-10-06).
        if (!points.staleLeft) {
            failures.contained("removing the note of an import that is done") {
                incoming.clearBegun()
            }
        }
        val done =
            ImportDone(
                trips = checked.trips.size,
                sentReports = checked.sentReports.size,
                points = points.stored,
                pointsTaken = points.taken,
                settings = settingsTaken,
                truckNeedsPairing = truck?.needsPairing == true,
            )
        failures.contained("writing the import into the event log") {
            eventLog.add(clock(), EventCategory.REPORT, line(done))
            if (truck != null) {
                val pairing = "After the import: ${truck.inWords()}"
                eventLog.add(clock(), EventCategory.PAIRING, pairing)
            }
        }
        afterImport(AFTER_IMPORT)
        return done
    }

    /** Everything the phone holds, with every raw point, as a file inside MilO. */
    private suspend fun writeSafetyCopy(): Pair<SafetyCopy, PointsLeftOut?> {
        var leftOut: PointsLeftOut? = null
        val copy =
            safetyCopies.write(clock()) { out ->
                val written = export.writeTo(out, includePoints = true)
                if (written !is ExportWritten.Done) throw TripStartedException()
                leftOut = written.leftOut
            }
        return copy to leftOut
    }

    /** Nothing was replaced, so there is nothing for a later start to finish. */
    private suspend fun forgetBeginning() {
        failures.contained("removing the note of an import that replaced nothing") {
            incoming.clearBegun()
        }
    }

    /**
     * Stores the file's settings, with the truck as [truckOnArrival] decides it.
     *
     * @return what was taken, and what became of the truck, or null if the stored truck was
     * not looked at.
     */
    private suspend fun takeSettings(checked: CheckedExport): Pair<SettingsTaken, TruckArrival?> {
        val arrived = checked.settings ?: return SettingsTaken.NONE_IN_FILE to null
        return try {
            val here = settings.current().transferred().truck
            val truck = truckOnArrival(arrived.truck, here, pairingHere())
            settings.replaceTransferred(arrived, truck.asChange())
            SettingsTaken.TAKEN to truck
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            // The settings file, or Android refusing to list its associations: either way
            // not one of the file's settings was written.
            failures.report("storing the imported settings. The phone's own are in force", failure)
            SettingsTaken.NOT_STORED to null
        }
    }

    private suspend fun takePoints(
        file: File,
        checked: CheckedExport,
        highestTripId: Long,
    ): PointsStep = try {
        val stored = import.replacePoints(file, checked, highestTripId)
        val taken = if (checked.pointCount == null) PointsTaken.NONE_IN_FILE else PointsTaken.TAKEN
        PointsStep(taken, stored, staleLeft = false)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        failures.report("storing the imported raw GPS points", failure)
        // The points that are still stored belong to the trips that were replaced.
        var removed = false
        failures.contained("removing the raw GPS points of the trips that were replaced") {
            import.removePoints(highestTripId)
            removed = true
        }
        PointsStep(PointsTaken.NOT_STORED, 0L, staleLeft = !removed)
    }

    private fun refused(why: TransferRefusal) = TransferOutcome.Refused(why)
}

/** Whether an import took everything the file holds, so that nothing of it is left to repeat. */
private fun TransferOutcome.isWhole(): Boolean =
    this is TransferOutcome.Imported && done.pointsTaken != PointsTaken.NOT_STORED

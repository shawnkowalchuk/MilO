package com.shawnkowalchuk.milo.platform.transfer

import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.settings.LastExport
import com.shawnkowalchuk.milo.data.settings.SettingsStore
import com.shawnkowalchuk.milo.data.settings.setLastExport
import com.shawnkowalchuk.milo.data.transfer.CheckedExport
import com.shawnkowalchuk.milo.data.transfer.DataExport
import com.shawnkowalchuk.milo.data.transfer.DataImport
import com.shawnkowalchuk.milo.data.transfer.ExportReading
import com.shawnkowalchuk.milo.data.transfer.ExportWritten
import com.shawnkowalchuk.milo.data.transfer.ImportBegun
import com.shawnkowalchuk.milo.data.transfer.ImportTooLargeException
import com.shawnkowalchuk.milo.data.transfer.IncomingImport
import com.shawnkowalchuk.milo.data.transfer.SafetyCopies
import com.shawnkowalchuk.milo.data.transfer.SafetyCopy
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The manual export and import of all of Shawn's data, from the Settings screen.
 *
 * **Export** writes one file to a place he picked with Android's file picker. It is the one
 * copy of his data that depends on nothing but itself, so it is whole or it is not there: an
 * export that fails removes what it wrote.
 *
 * **Import** replaces what the phone holds, and so it goes in steps that each can be said no
 * to. The picked file is copied into MilO and read from start to end ([offerImport]); only a
 * file with nothing wrong in it leads to the question ([TransferStatus.offer]); and only his
 * answer ([confirmImport]) starts the replacing, which first writes a safety copy of what the
 * phone holds (`ImportRun`).
 *
 * **Neither is made while a trip is being recorded.** A trip in progress has no end and no
 * distance yet, and replacing the trips under it would leave its row and its raw points behind.
 *
 * The work runs in the application scope and its state is kept here, not in a screen: leaving
 * the Settings screen neither stops an import half-way nor loses what it came to. What ends an
 * import half-way is the end of MilO's process, and the next start then finishes it
 * ([finishCutOffImport]).
 *
 * @param tripInProgress whether the trip controller shows a trip as being recorded. The stored
 * trips are asked once more inside the transaction that replaces them.
 * @param scope the application scope.
 */
class DataTransfer internal constructor(
    private val export: DataExport,
    private val import: DataImport,
    private val incoming: IncomingImport,
    private val safetyCopies: SafetyCopies,
    private val documents: PickedDocuments,
    private val settings: SettingsStore,
    private val tripInProgress: () -> Boolean,
    private val importRun: ImportRun,
    private val importResume: ImportResume,
    private val eventLog: EventLogRepository,
    private val failures: TransferFailures,
    private val clock: () -> Long,
    private val scope: CoroutineScope,
) {
    private val mutableStatus = MutableStateFlow(TransferStatus())

    /** Where an export or an import stands, and what the last one came to. */
    val status: StateFlow<TransferStatus> = mutableStatus.asStateFlow()

    /** The file the question on the screen is about, as it was checked. */
    @Volatile
    private var checked: Pair<File, CheckedExport>? = null

    /**
     * Writes everything to the file Shawn has just made with the file picker.
     *
     * @param uri the file's address, as the picker gave it.
     * @param includePoints false leaves the raw GPS points out.
     */
    fun exportTo(uri: String, includePoints: Boolean) {
        begin(TransferWork.EXPORTING, TransferRefusal.ExportNotWritten) {
            write(uri, includePoints)
        }
    }

    /** Takes the file Shawn picked, checks all of it, and asks before anything is replaced. */
    fun offerImport(uri: String) {
        begin(TransferWork.READING_FILE, TransferRefusal.FileNotRead) {
            offer(safetyCopyAtMs = null) { documents.openForReading(uri).use(incoming::take) }
        }
    }

    /**
     * The same for one of the safety copies: the way to undo an import.
     *
     * @param writtenAtMs which copy, by when it was written.
     */
    fun offerSafetyCopy(writtenAtMs: Long) {
        begin(TransferWork.READING_FILE, TransferRefusal.FileNotRead) {
            val copy = safetyCopies.all().firstOrNull { it.writtenAtMs == writtenAtMs }
            if (copy == null) {
                refused(TransferRefusal.NoSafetyCopy)
            } else {
                offer(copy.writtenAtMs) { copy.file.inputStream().use(incoming::take) }
            }
        }
    }

    /** Shawn's yes to the question: the phone's data is replaced with the file's. */
    fun confirmImport() {
        var offer: ImportOffer? = null
        mutableStatus.update { now ->
            offer = now.offer.takeIf { now.working == null }
            if (offer == null) now else TransferStatus(working = TransferWork.IMPORTING)
        }
        val asked = offer ?: return
        val picked = checked
        checked = null
        if (picked == null) {
            // The question stood without its file. That cannot happen as the two are set and
            // cleared today; if it ever does, the card must not be left waiting for good.
            mutableStatus.value = refused(TransferRefusal.FileNotRead)
            return
        }
        scope.launch(Dispatchers.IO) {
            val outcome = importRun.run(picked.first, picked.second, asked)
            mutableStatus.value = TransferStatus(outcome = outcome)
        }
    }

    /** Shawn's no: the copy of the picked file is removed, and nothing was changed. */
    fun declineImport() {
        var declined = false
        mutableStatus.update { now ->
            declined = now.offer != null && now.working == null
            if (declined) TransferStatus() else now
        }
        if (!declined) return
        checked = null
        scope.launch(Dispatchers.IO) {
            failures.contained("removing the copy of a file that was not imported") {
                incoming.clear()
            }
        }
    }

    /** Every safety copy there is, newest first. */
    suspend fun safetyCopies(): List<SafetyCopy> =
        withContext(Dispatchers.IO) { safetyCopies.all() }

    /**
     * At a process start: finishes an import that the last process was ended in the middle of
     * (`ImportResume`), and removes the copy of a file whose question was never answered. The
     * card shows it as an import under way, and then what it came to.
     *
     * Never throws, except for being cancelled: this runs at the start of the process the trip
     * service runs in.
     */
    suspend fun finishCutOffImport() = withContext(Dispatchers.IO) {
        var waiting: ImportBegun? = null
        failures.contained("looking for an import that was cut off") {
            waiting = importResume.waiting()
        }
        val begun = waiting ?: return@withContext
        if (!claim(TransferWork.IMPORTING)) return@withContext
        val outcome =
            try {
                importResume.finish(begun)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                failures.report("finishing an import that was cut off", failure)
                TransferOutcome.CutOff(CutOffImport.NOT_FINISHED)
            }
        mutableStatus.value = TransferStatus(outcome = outcome)
    }

    private suspend fun write(uri: String, includePoints: Boolean): TransferStatus {
        // The file picker has made the file already, empty. An export is whole or not there.
        if (tripInProgress()) return refusedForTrip(uri)
        val written =
            try {
                documents.openForWriting(uri).use { export.writeTo(it, includePoints) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                // Whatever went wrong (the disk, the app that holds the file, a trip that
                // started in between), a half-written export must not be left looking whole.
                val removed = documents.delete(uri)
                failures.report(exportFailureInWords(unfinishedRemoved = removed), failure)
                return refused(
                    if (removed) {
                        TransferRefusal.ExportNotWritten
                    } else {
                        TransferRefusal.ExportLeftUnfinished
                    },
                )
            }
        if (written !is ExportWritten.Done) return refusedForTrip(uri)
        // The file is whole and closed. What follows only records that, and its failure does
        // not make the export any less written.
        failures.contained("recording that an export was written") {
            val atMs = clock()
            val line = exportedLine(written.contents, written.leftOut)
            eventLog.add(atMs, EventCategory.REPORT, line)
            settings.setLastExport(LastExport(atMs, withPoints = includePoints))
        }
        val leftOut = written.leftOut?.points ?: 0L
        return TransferStatus(outcome = TransferOutcome.Exported(written.contents, leftOut))
    }

    /**
     * No export while a trip is being recorded, and the file the picker made for it is
     * removed again, or said to be no export if the app that holds it will not remove it.
     */
    private suspend fun refusedForTrip(uri: String): TransferStatus {
        if (documents.delete(uri)) return refused(TransferRefusal.TripInProgress)
        failures.contained("writing down an export file that could not be removed") {
            eventLog.add(clock(), EventCategory.REPORT, exportFileLeftLine())
        }
        return refused(TransferRefusal.ExportLeftUnfinished)
    }

    private suspend fun offer(safetyCopyAtMs: Long?, take: () -> File): TransferStatus {
        if (tripInProgress()) return refused(TransferRefusal.TripInProgress)
        val file =
            try {
                take()
            } catch (tooLarge: ImportTooLargeException) {
                return refused(TransferRefusal.FileTooLarge)
            }
        return when (val reading = import.check(file)) {
            is ExportReading.Refused -> {
                incoming.clear()
                eventLog.add(clock(), EventCategory.REPORT, importRefusedLine(reading.problem))
                refused(reading.problem.asRefusal())
            }

            is ExportReading.Good -> {
                checked = file to reading.export
                TransferStatus(
                    offer = importOffer(reading.export, import.storedNow(), safetyCopyAtMs),
                )
            }
        }
    }

    /**
     * Starts [work] unless something is under way or waits for an answer: one at a time. What
     * [work] returns is the next status.
     *
     * A failure is said on the screen and written to the log, whatever was thrown: left alone,
     * the exception would end the process, and the trip service runs in it.
     *
     * @param onFailure what the screen says if [work] throws.
     */
    private fun begin(
        doing: TransferWork,
        onFailure: TransferRefusal,
        work: suspend () -> TransferStatus,
    ) {
        if (!claim(doing)) return
        scope.launch(Dispatchers.IO) {
            mutableStatus.value =
                try {
                    work()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    failures.report(doing.inWords(), failure)
                    refused(onFailure)
                }
        }
    }

    /** Marks [doing] as under way, unless something is under way or waits for an answer. */
    private fun claim(doing: TransferWork): Boolean {
        var claimed = false
        mutableStatus.update { now ->
            claimed = now.idle
            if (claimed) TransferStatus(working = doing) else now
        }
        return claimed
    }

    private fun refused(why: TransferRefusal) =
        TransferStatus(outcome = TransferOutcome.Refused(why))
}

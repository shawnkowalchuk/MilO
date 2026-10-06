package com.shawnkowalchuk.milo.feature.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shawnkowalchuk.milo.core.report.MileageReport
import com.shawnkowalchuk.milo.core.util.localDateOf
import com.shawnkowalchuk.milo.data.settings.ReportHandOver
import com.shawnkowalchuk.milo.platform.report.ReportDocuments
import com.shawnkowalchuk.milo.platform.report.ReportFile
import com.shawnkowalchuk.milo.platform.report.ReportHandOff
import com.shawnkowalchuk.milo.platform.report.ReportTexts
import java.io.IOException
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** How long the state stays current after the screen stopped watching it (a rotation). */
private const val KEEP_WATCHING_MS = 5_000L

/**
 * The Report screen's link to the stored trips, the settings and the list of sent reports, and
 * to the classes that make the files.
 *
 * It keeps no copy of anything stored. What a report would hold is worked out from storage
 * every time something changes, and a file is made from exactly what the screen shows at the
 * moment its button is pressed.
 *
 * @param openedFor the month the Trips screen was showing: the period the screen opens with.
 * @param reading reads what a report is made from: the trips, the settings, what was sent.
 * @param documents makes the PDF and the CSV.
 * @param handOff builds the requests that hand a file to another app. The screen starts them.
 * @param texts the subject and the first lines of the email.
 * @param records keeps the report that waits for its answer, and writes what became of a
 * report to the list of sent reports and to the log.
 * @param onRecordedAsSent a report was recorded as sent. The app has the monthly reminder
 * looked at, so that a reminder for that month goes away at once. A plain function, like the
 * ones for navigation.
 * @param clock and [zone] are read again each time the screen comes to the front.
 */
class ReportViewModel(
    openedFor: YearMonth,
    reading: ReportReading,
    private val documents: ReportDocuments,
    private val handOff: ReportHandOff,
    private val texts: ReportTexts,
    private val records: ReportRecords,
    private val onRecordedAsSent: () -> Unit,
    private val clock: () -> Long,
    private val zone: () -> ZoneId,
) : ViewModel() {
    /** The PDF that was made last, and the report it was made of. */
    private data class Created(val report: MileageReport, val file: ReportFile)

    private val chosen = MutableStateFlow(opening(openedFor))
    private val created = MutableStateFlow<Created?>(null)
    private val passing = MutableStateFlow(ReportPassing())
    private var launches = 0

    /** True while the list of sent reports is being written to. Main thread only. */
    private var recording = false

    private val sources: StateFlow<ReportSources?> =
        reading
            .sources(chosen)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(KEEP_WATCHING_MS), null)

    val state: StateFlow<ReportUiState> =
        combine(sources, created, passing) { from, made, now ->
            val stored = from?.settings
            when {
                from == null -> ReportUiState.Reading

                stored == null -> ReportUiState.Unreadable

                else -> {
                    // The PDF can be opened only while it is of the report as it is now. A
                    // trip that ended since, or a changed name, makes it an old one.
                    val report = from.reportFor(ReportNeed.PDF)
                    val pages = made?.takeIf { it.report == report }?.file?.pageCount
                    reportUiState(
                        choice = from.chosen.choice,
                        today = from.chosen.today,
                        zone = from.chosen.zone,
                        selection = from.selection,
                        settings = stored,
                        sent = from.sent,
                        passing = now.copy(pdfPages = pages),
                    )
                }
            }
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(KEEP_WATCHING_MS),
            ReportUiState.Reading,
        )

    init {
        // Report files from earlier weeks. They are in the cache and can be made again.
        viewModelScope.launch {
            try {
                documents.removeOldFiles(clock())
            } catch (failure: IOException) {
                records.failed("Removing old report files", failure)
            }
        }
    }

    fun onKind(kind: PeriodKind) = choose { choice, _ -> choice.copy(kind = kind) }

    fun onPreviousMonth() = choose { choice, today -> choice.steppedMonth(-1, today) }

    fun onNextMonth() = choose { choice, today -> choice.steppedMonth(1, today) }

    fun onRangeFirst(day: LocalDate) = choose { choice, today -> choice.withRangeFirst(day, today) }

    fun onRangeLast(day: LocalDate) = choose { choice, today -> choice.withRangeLast(day, today) }

    /** Works out again which day is today; the period chosen stays where it is. */
    fun onCameToFront() {
        val now = opening(chosen.value.choice.month)
        chosen.update { it.copy(today = now.today, zone = now.zone) }
    }

    /** Makes the PDF of the report as the screen shows it, to be looked at. */
    fun onCreatePdf() = make(ReportNeed.PDF) { report ->
        val file = documents.createPdf(report)
        created.value = Created(report, file)
        records.created("PDF", report.period, report.tripCount, report.totalTenths)
        null
    }

    /**
     * Opens the PDF that was made, in whatever app the phone shows a PDF with.
     *
     * The file is in the cache, which Android empties by itself when the phone runs short of
     * room (seen on an emulator, where it was gone seconds after it was made). So it is looked
     * for first, and made again if it is no longer there or no longer the report on screen.
     */
    fun onOpenPdf() = make(ReportNeed.PDF) { report ->
        val made = created.value?.takeIf { it.report == report && documents.holds(it.file) }
        val file = made?.file ?: documents.createPdf(report)
        created.value = Created(report, file)
        ReportLaunch(++launches, LaunchKind.PDF_VIEWER, listOf(handOff.toView(file.file)))
    }

    /**
     * Makes a fresh PDF and has the email app opened with it. Nothing is recorded as sent
     * here: that is [onAnswer], when Shawn is back and says what he did.
     *
     * What is being handed over is stored before the email app is opened, so that the question
     * is still asked if MilO does not live to see him come back.
     *
     * One question at a time: while an earlier report still waits for its answer, nothing is
     * sent. The screen asks about that one first; sending now would forget it unanswered.
     */
    fun onSend() {
        if (sources.value?.settings?.reportHandOver != null) return
        make(ReportNeed.SENDING, ::handOver)
    }

    private suspend fun handOver(report: MileageReport): ReportLaunch? {
        // Checked by make(): sending needs the address.
        val address = sources.value?.settings?.accountantEmail ?: return null
        val file = documents.createPdf(report)
        created.value = Created(report, file)
        val handOver = ReportHandOver(report.period, report.tripCount, report.totalTenths, clock())
        records.awaitAnswerFor(handOver)
        return ReportLaunch(
            id = ++launches,
            kind = LaunchKind.EMAIL,
            intents =
                handOff.toAccountant(
                    pdf = file.file,
                    address = address,
                    subject = texts.subject(report),
                    body = texts.body(report),
                ),
            handOver = handOver,
        )
    }

    /** Makes the CSV of the same trips and offers it to Android's share sheet. */
    fun onExportCsv() = make(ReportNeed.NOTHING) { report ->
        val file = documents.createCsv(report)
        records.created("CSV", report.period, report.tripCount, report.totalTenths)
        val title = texts.subject(report)
        ReportLaunch(++launches, LaunchKind.SHARE, listOf(handOff.toShare(file.file, title)))
    }

    /**
     * The screen has tried to open the app a file was meant for.
     *
     * @param opened false if the phone has no app that takes it. A report that was to be
     * handed to the email app was then handed to nobody, and no question is owed for it.
     */
    fun onLaunched(launch: ReportLaunch, opened: Boolean) {
        val problem = launch.kind.problemIfNotOpened().takeUnless { opened }
        passing.update { now ->
            if (now.launch?.id == launch.id) now.copy(launch = null, problem = problem) else now
        }
        val handOver = launch.handOver ?: return
        viewModelScope.launch {
            if (opened) records.handedOver(handOver) else records.forgetAwaited()
        }
    }

    /** Shawn's answer to "Did you send it?" for the report that was handed to the email app. */
    fun onAnswer(sent: Boolean) {
        val report = sources.value?.settings?.reportHandOver ?: return
        record(ReportProblem.COULD_NOT_RECORD) {
            records.answered(report, sent).also { stored -> if (stored && sent) onRecordedAsSent() }
        }
    }

    /**
     * Removes a report from the list of sent reports, once the screen has asked and Shawn has
     * said yes: an "I sent it" that was a mistake. The list, and whether its month is
     * submitted, follow storage.
     */
    fun onRemoveSent(id: Long) = record(ReportProblem.COULD_NOT_REMOVE) { records.removed(id) }

    /**
     * One write to the list of sent reports at a time: a second tap while the first is being
     * stored does nothing.
     *
     * @param write answers whether it was stored. If not, [ifNot] is said on the screen.
     */
    private fun record(ifNot: ReportProblem, write: suspend () -> Boolean) {
        if (recording) return
        recording = true
        viewModelScope.launch {
            val stored = write()
            passing.update { it.copy(problem = ifNot.takeUnless { stored }) }
            recording = false
        }
    }

    /**
     * Runs one press that makes a file, one at a time, of the report as it is stored at that
     * moment. A press that lacks a setting it needs makes nothing: the screen says what is
     * missing. A file that cannot be made is said on the screen and written to the event log,
     * whatever was thrown: left alone, the exception would end the process, and the trip
     * service runs in it.
     *
     * @param file makes the file, and answers with the app to open for it, if any.
     */
    private fun make(need: ReportNeed, file: suspend (MileageReport) -> ReportLaunch?) {
        val from = sources.value ?: return
        val stored = from.settings ?: return
        if (passing.value.working) return
        if (need.missingIn(stored).isNotEmpty()) {
            passing.update { it.copy(refusals = it.refusals + 1, problem = null) }
            return
        }
        // Still reading the trips: there is nothing true to make a report of yet.
        val report = from.reportFor(need) ?: return
        passing.update { it.copy(working = true, problem = null) }
        viewModelScope.launch {
            var problem: ReportProblem? = null
            val launch =
                try {
                    file(report)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    problem = ReportProblem.COULD_NOT_CREATE
                    records.failed("Creating the report for ${report.period.inLogWords()}", failure)
                    null
                }
            passing.update { it.copy(working = false, problem = problem, launch = launch) }
        }
    }

    private fun choose(next: (ReportChoice, today: LocalDate) -> ReportChoice) {
        chosen.update { it.copy(choice = next(it.choice, it.today)) }
        // What a press said about the last period is not about this one.
        passing.update { it.copy(problem = null) }
    }

    private fun opening(month: YearMonth): ReportChosen {
        val zoneNow = zone()
        val today = localDateOf(clock(), zoneNow)
        return ReportChosen(openingChoice(month, today), today, zoneNow)
    }
}

private fun LaunchKind.problemIfNotOpened(): ReportProblem = when (this) {
    LaunchKind.EMAIL -> ReportProblem.NO_EMAIL_APP
    LaunchKind.PDF_VIEWER -> ReportProblem.NO_PDF_VIEWER
    LaunchKind.SHARE -> ReportProblem.NO_SHARE_APP
}

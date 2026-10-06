package com.shawnkowalchuk.milo.feature.report

import android.content.Intent
import com.shawnkowalchuk.milo.core.report.MileageReport
import com.shawnkowalchuk.milo.core.report.ReportPeriod
import com.shawnkowalchuk.milo.core.report.ReportRevision
import com.shawnkowalchuk.milo.core.report.ReportSender
import com.shawnkowalchuk.milo.core.report.reportDays
import com.shawnkowalchuk.milo.core.util.localDateOf
import com.shawnkowalchuk.milo.data.report.MonthSubmission
import com.shawnkowalchuk.milo.data.report.RemovalEffect
import com.shawnkowalchuk.milo.data.report.ReportSelection
import com.shawnkowalchuk.milo.data.report.SentEffect
import com.shawnkowalchuk.milo.data.report.SentReport
import com.shawnkowalchuk.milo.data.report.monthSubmission
import com.shawnkowalchuk.milo.data.report.nextRevision
import com.shawnkowalchuk.milo.data.report.period
import com.shawnkowalchuk.milo.data.report.removalEffect
import com.shawnkowalchuk.milo.data.report.sentEffect
import com.shawnkowalchuk.milo.data.report.sentFor
import com.shawnkowalchuk.milo.data.settings.MiloSettings
import com.shawnkowalchuk.milo.data.settings.MissingDetail
import com.shawnkowalchuk.milo.data.settings.ReportHandOver
import com.shawnkowalchuk.milo.data.settings.missingForPdf
import com.shawnkowalchuk.milo.data.settings.missingForSending
import java.time.LocalDate
import java.time.ZoneId

// What the Report screen shows, and the functions that decide it from the stored trips, the
// settings and the list of sent reports. Pure, so they are tested without a phone.

/**
 * What a report made now would hold.
 *
 * @param tenths its total, in tenths of a kilometre: the sum of the figures it prints.
 * @param markedCount how many of its trips were added or edited by hand.
 * @param withoutAddress how many of its trips lack a start or an end address.
 * @param personalLeftOut and [unsortedLeftOut] count the period's trips that are left off.
 */
data class ReportSummary(
    val tripCount: Int,
    val tenths: Long,
    val markedCount: Int,
    val withoutAddress: Int,
    val personalLeftOut: Int,
    val unsortedLeftOut: Int,
    val tripInProgress: Boolean,
)

/**
 * One line of the list of sent reports.
 *
 * @param removal what removing this report from the list would do, which the question says
 * before it is removed.
 */
data class SentLine(
    val id: Long,
    val period: ReportPeriod,
    val sentAtMs: Long,
    val tripCount: Int,
    val distanceMetres: Double,
    val revision: Int,
    val removal: RemovalEffect,
)

/**
 * That a report for the chosen period was sent before, so "Send to accountant" asks first.
 *
 * @param last the newest report sent for it.
 * @param revision the number the new report would carry.
 */
data class Resend(val last: SentLine, val revision: Int)

/** A press that did not do what it said, until the next press. */
enum class ReportProblem {
    /** The PDF or the CSV could not be written. */
    COULD_NOT_CREATE,

    /** The phone has no app that takes an email. */
    NO_EMAIL_APP,

    /** The phone has no app that shows a PDF. */
    NO_PDF_VIEWER,

    /** The phone has no app to share a file with. */
    NO_SHARE_APP,

    /** Shawn said he sent the report, and storage could not record it. */
    COULD_NOT_RECORD,

    /** A report was to be removed from the list of sent reports, and it was not. */
    COULD_NOT_REMOVE,
}

/** Which other app a file is being handed to. */
enum class LaunchKind { EMAIL, PDF_VIEWER, SHARE }

/**
 * Another app to open, which only the screen can do: it is opened on MilO's own activity.
 *
 * @param id tells one request from the next, so that each is carried out once.
 * @param intents the requests to try, in order, until one opens.
 * @param handOver for an email, what is being handed to the email app.
 */
data class ReportLaunch(
    val id: Int,
    val kind: LaunchKind,
    val intents: List<Intent>,
    val handOver: ReportHandOver? = null,
)

/** What the Report screen shows. */
sealed interface ReportUiState {
    /** The settings and the list of sent reports have not been read yet. */
    data object Reading : ReportUiState

    /** The settings file cannot be read, so no report can be made. */
    data object Unreadable : ReportUiState

    /**
     * @param today the last day a period may reach.
     * @param submission whether the chosen month has been submitted. It is about the month,
     * also while "A date range" is chosen, where the screen does not show it.
     * @param summary what the report would hold, or null while the trips are being read.
     * @param missing what stands in the way of sending the report. Shown as soon as the screen
     * is drawn, so that it is known before a button is pressed.
     * @param resend set if the chosen period was sent before: sending then asks first.
     * @param sendQuestions what "Send to accountant" asks before it sends, in order.
     * @param working true while a file is being made. The buttons wait.
     * @param pdfPages how many pages the PDF has that was made of exactly this report, or null
     * if there is none: then there is nothing to open.
     * @param refusals how many presses were refused for a missing setting. The screen moves to
     * the line that says what is missing each time it goes up.
     * @param launch another app for the screen to open, or null.
     * @param awaiting the report that was handed to the email app and not answered for yet:
     * what "Did you send it?" is asked about. It is stored, so it is there whenever the screen
     * is opened, for whichever period.
     * @param awaitingEffect what "I sent it" would do for [awaiting], which the question says
     * before it is answered. Null exactly when [awaiting] is.
     * @param sent every report that was sent, newest first.
     */
    data class Ready(
        val zone: ZoneId,
        val today: LocalDate,
        val choice: ReportChoice,
        val canStepForward: Boolean,
        val submission: MonthSubmission?,
        val summary: ReportSummary?,
        val missing: List<MissingDetail>,
        val accountantEmail: String?,
        val resend: Resend?,
        val sendQuestions: List<SendQuestion>,
        val working: Boolean,
        val pdfPages: Int?,
        val problem: ReportProblem?,
        val refusals: Int,
        val launch: ReportLaunch?,
        val awaiting: ReportHandOver?,
        val awaitingEffect: SentEffect?,
        val sent: List<SentLine>,
    ) : ReportUiState
}

/** What a report of [selection] would hold. */
fun summaryOf(selection: ReportSelection): ReportSummary = ReportSummary(
    tripCount = selection.trips.size,
    tenths = selection.trips.sumOf { it.tenths },
    markedCount = selection.trips.count { it.mark != null },
    withoutAddress = selection.withoutAddress,
    personalLeftOut = selection.personalLeftOut,
    unsortedLeftOut = selection.unsortedLeftOut,
    tripInProgress = selection.tripInProgress,
)

/**
 * The report for [period] as it would be made now.
 *
 * A period that was sent before makes this one a revision: it carries the next number and
 * names the day the newest report before it was sent. That holds for a PDF that is only made to
 * be looked at too, so that what he looks at is what would be sent.
 *
 * @param name printed as the sender. The caller decides what a report may be made without.
 * @param sent every report sent so far.
 * @param today the day the report is generated on, in [zone].
 */
fun mileageReport(
    period: ReportPeriod,
    selection: ReportSelection,
    name: String,
    settings: MiloSettings,
    sent: List<SentReport>,
    today: LocalDate,
    zone: ZoneId,
): MileageReport {
    val sentBefore = sentFor(period, sent)
    return MileageReport(
        sender = ReportSender(name, settings.reportCompany, settings.reportVehicle),
        period = period,
        generatedOn = today,
        revision =
            sentBefore.lastOrNull()?.let { last ->
                ReportRevision(nextRevision(sentBefore), localDateOf(last.sentAtMs, zone))
            },
        zone = zone,
        days = reportDays(selection.trips, zone),
    )
}

/** The question "Send to accountant" asks first, or null for a period never sent before. */
fun resendOf(period: ReportPeriod, sent: List<SentReport>): Resend? {
    val sentBefore = sentFor(period, sent)
    val last = sentBefore.lastOrNull() ?: return null
    return Resend(last = last.asLine(sent), revision = nextRevision(sentBefore))
}

/**
 * The screen for the chosen period.
 *
 * @param selection the period's trips, or null while they are being read.
 * @param passing what the screen shows that is not stored: see [ReportUiState.Ready].
 */
fun reportUiState(
    choice: ReportChoice,
    today: LocalDate,
    zone: ZoneId,
    selection: ReportSelection?,
    settings: MiloSettings,
    sent: List<SentReport>,
    passing: ReportPassing,
): ReportUiState.Ready = ReportUiState.Ready(
    zone = zone,
    today = today,
    choice = choice,
    canStepForward = choice.canStepForward(today),
    submission = monthSubmission(choice.month, sent),
    summary = selection?.let(::summaryOf),
    missing = settings.missingForSending(),
    accountantEmail = settings.accountantEmail,
    resend = resendOf(choice.period, sent),
    sendQuestions = sendQuestions(choice.period, today, sentFor(choice.period, sent).isNotEmpty()),
    working = passing.working,
    pdfPages = passing.pdfPages,
    problem = passing.problem,
    refusals = passing.refusals,
    launch = passing.launch,
    awaiting = settings.reportHandOver,
    awaitingEffect = settings.reportHandOver?.let { sentEffect(it.period, sent) },
    sent = sent.map { it.asLine(sent) },
)

/**
 * What the screen shows that is not stored anywhere.
 *
 * @param pdfPages the pages of the PDF made of the report as it is now, or null.
 */
data class ReportPassing(
    val working: Boolean = false,
    val pdfPages: Int? = null,
    val problem: ReportProblem? = null,
    val refusals: Int = 0,
    val launch: ReportLaunch? = null,
)

/** What a press needs of the settings before it may make a file. */
enum class ReportNeed {
    /** A CSV: nothing. It prints no name and goes wherever Shawn shares it. */
    NOTHING,

    /** A PDF to look at: the name that is printed on it. */
    PDF,

    /** A PDF to send: the name, and the address it is sent to. */
    SENDING,
    ;

    /** What of it [settings] lack. */
    fun missingIn(settings: MiloSettings): List<MissingDetail> = when (this) {
        NOTHING -> emptyList()
        PDF -> settings.missingForPdf()
        SENDING -> settings.missingForSending()
    }
}

private fun SentReport.asLine(sent: List<SentReport>): SentLine = SentLine(
    id = id,
    period = period,
    sentAtMs = sentAtMs,
    tripCount = tripCount,
    distanceMetres = distanceMetres,
    revision = revision,
    removal = removalEffect(this, sent),
)

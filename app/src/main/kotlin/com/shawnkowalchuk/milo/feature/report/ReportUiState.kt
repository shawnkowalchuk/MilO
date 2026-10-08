package com.shawnkowalchuk.milo.feature.report

import android.content.Intent
import com.shawnkowalchuk.milo.core.report.ReportPeriod
import com.shawnkowalchuk.milo.core.util.DistanceUnit
import com.shawnkowalchuk.milo.data.report.MonthSubmission
import com.shawnkowalchuk.milo.data.report.RemovalEffect
import com.shawnkowalchuk.milo.data.report.ReportSelection
import com.shawnkowalchuk.milo.data.report.SentEffect
import com.shawnkowalchuk.milo.data.report.SentReport
import com.shawnkowalchuk.milo.data.report.monthSubmission
import com.shawnkowalchuk.milo.data.report.sentEffect
import com.shawnkowalchuk.milo.data.report.sentFor
import com.shawnkowalchuk.milo.data.settings.MiloSettings
import com.shawnkowalchuk.milo.data.settings.MissingDetail
import com.shawnkowalchuk.milo.data.settings.ReportHandOver
import com.shawnkowalchuk.milo.data.settings.missingForSending
import java.time.LocalDate
import java.time.ZoneId

// What the Report screen shows, and the functions that decide it from the stored trips, the
// settings and the list of sent reports. Pure, so they are tested without a phone.

/**
 * What a report made now would hold.
 *
 * @param tenths its total, in tenths of [unit]: the sum of the figures it prints.
 * @param unit the unit the report would be printed in: the one chosen in Settings.
 * @param markedCount how many of its trips were added or edited by hand.
 * @param withoutAddress how many of its trips lack a start or an end address.
 * @param personalLeftOut and [unsortedLeftOut] count the period's trips that are left off.
 */
data class ReportSummary(
    val tripCount: Int,
    val tenths: Long,
    val unit: DistanceUnit,
    val markedCount: Int,
    val withoutAddress: Int,
    val personalLeftOut: Int,
    val unsortedLeftOut: Int,
    val tripInProgress: Boolean,
)

/**
 * One line of the list of sent reports.
 *
 * @param distanceMetres the total the report printed, and [unit] the unit it was printed in.
 * The line is written in that unit for good: it is a record of what the report said, and does
 * not follow a later change of the unit in Settings.
 * @param removal what removing this report from the list would do, which the question says
 * before it is removed.
 */
data class SentLine(
    val id: Long,
    val period: ReportPeriod,
    val sentAtMs: Long,
    val tripCount: Int,
    val distanceMetres: Double,
    val unit: DistanceUnit,
    val revision: Int,
    val removal: RemovalEffect,
)

/**
 * That a report for the chosen period was sent before, so "Email the report" asks first.
 *
 * @param last the newest report sent for it.
 * @param revision the number the new report would carry.
 */
data class Resend(val last: SentLine, val revision: Int)

/**
 * Who the report is from, as far as the settings say: what its heading prints. The name is
 * null while it is not set, and a report then cannot be made; a company or a vehicle that is
 * not set is left out of the report, and of the screen.
 */
data class SenderDetails(val name: String?, val company: String?, val vehicle: String?)

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
     * @param unit the unit chosen in Settings: what a report made now is printed in.
     * @param submission whether the chosen month has been submitted. It is about the month,
     * also while "A date range" is chosen, where the screen does not show it.
     * @param status what the Status tile says about the chosen period, month or range.
     * @param summary what the report would hold, or null while the trips are being read.
     * @param missing what stands in the way of sending the report. Shown as soon as the screen
     * is drawn, so that it is known before a button is pressed.
     * @param sender the name, company and vehicle the report prints, as far as they are set.
     * @param resend set if the chosen period was sent before: sending then asks first.
     * @param sendQuestions what "Email the report" asks before it sends, in order.
     * @param ifSent what recording a report for the chosen period as sent would do now. The
     * question before "Mark as sent" says it.
     * @param working true while a file is being made. The buttons wait.
     * @param pdfName what the PDF of this report is called, made or not, or null while no PDF
     * can be made: the trips are being read, or the name it prints is not set.
     * @param pdfPages how many pages the PDF has that was made of exactly this report, or null
     * if none was made yet.
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
        val unit: DistanceUnit,
        val choice: ReportChoice,
        val canStepForward: Boolean,
        val submission: MonthSubmission?,
        val status: ReportStatus,
        val summary: ReportSummary?,
        val missing: List<MissingDetail>,
        val accountantEmail: String?,
        val sender: SenderDetails,
        val resend: Resend?,
        val sendQuestions: List<SendQuestion>,
        val ifSent: SentEffect,
        val working: Boolean,
        val pdfName: String?,
        val pdfPages: Int?,
        val problem: ReportProblem?,
        val refusals: Int,
        val launch: ReportLaunch?,
        val awaiting: ReportHandOver?,
        val awaitingEffect: SentEffect?,
        val sent: List<SentLine>,
    ) : ReportUiState
}

/** What a report of [selection] would hold, printed in [unit]. */
fun summaryOf(selection: ReportSelection, unit: DistanceUnit): ReportSummary = ReportSummary(
    tripCount = selection.trips.size,
    tenths = selection.totalTenths(unit),
    unit = unit,
    markedCount = selection.trips.count { it.mark != null },
    withoutAddress = selection.withoutAddress,
    personalLeftOut = selection.personalLeftOut,
    unsortedLeftOut = selection.unsortedLeftOut,
    tripInProgress = selection.tripInProgress,
)

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
    unit = settings.distanceUnit,
    choice = choice,
    canStepForward = choice.canStepForward(today),
    submission = monthSubmission(choice.month, sent),
    status = reportStatus(choice.period, today, zone, selection?.trips?.size, settings, sent),
    summary = selection?.let { summaryOf(it, settings.distanceUnit) },
    missing = settings.missingForSending(),
    accountantEmail = settings.accountantEmail,
    sender = SenderDetails(settings.reportName, settings.reportCompany, settings.reportVehicle),
    resend = resendOf(choice.period, sent),
    sendQuestions = sendQuestions(choice.period, today, sentFor(choice.period, sent).isNotEmpty()),
    ifSent = sentEffect(choice.period, sent),
    working = passing.working,
    pdfName = passing.pdfName,
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
 * @param pdfName what the PDF of the report as it is now is called, or null while none can be
 * made.
 * @param pdfPages the pages of the PDF made of the report as it is now, or null.
 */
data class ReportPassing(
    val working: Boolean = false,
    val pdfName: String? = null,
    val pdfPages: Int? = null,
    val problem: ReportProblem? = null,
    val refusals: Int = 0,
    val launch: ReportLaunch? = null,
)

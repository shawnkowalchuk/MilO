package com.shawnkowalchuk.milo.feature.report

import com.shawnkowalchuk.milo.core.report.MileageReport
import com.shawnkowalchuk.milo.core.report.ReportPeriod
import com.shawnkowalchuk.milo.core.report.ReportRevision
import com.shawnkowalchuk.milo.core.report.ReportSender
import com.shawnkowalchuk.milo.core.report.reportDays
import com.shawnkowalchuk.milo.core.util.localDateOf
import com.shawnkowalchuk.milo.data.report.ReportSelection
import com.shawnkowalchuk.milo.data.report.SentReport
import com.shawnkowalchuk.milo.data.report.nextRevision
import com.shawnkowalchuk.milo.data.report.period
import com.shawnkowalchuk.milo.data.report.removalEffect
import com.shawnkowalchuk.milo.data.report.sentFor
import com.shawnkowalchuk.milo.data.settings.MiloSettings
import com.shawnkowalchuk.milo.data.settings.MissingDetail
import com.shawnkowalchuk.milo.data.settings.missingForPdf
import com.shawnkowalchuk.milo.data.settings.missingForSending
import java.time.LocalDate
import java.time.ZoneId

// The report a press on the Report screen would make, and what each kind of press needs of the
// settings first. Pure functions, so they are tested without a phone. They were part of
// ReportUiState.kt until that file reached its size limit (ENGINEERING_STANDARDS section 3).

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

/** The question "Email the report" asks first, or null for a period never sent before. */
fun resendOf(period: ReportPeriod, sent: List<SentReport>): Resend? {
    val sentBefore = sentFor(period, sent)
    val last = sentBefore.lastOrNull() ?: return null
    return Resend(last = last.asLine(sent), revision = nextRevision(sentBefore))
}

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

internal fun SentReport.asLine(sent: List<SentReport>): SentLine = SentLine(
    id = id,
    period = period,
    sentAtMs = sentAtMs,
    tripCount = tripCount,
    distanceMetres = distanceMetres,
    revision = revision,
    removal = removalEffect(this, sent),
)

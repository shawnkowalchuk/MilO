package com.shawnkowalchuk.milo.feature.report

import com.shawnkowalchuk.milo.core.report.ReportPeriod
import com.shawnkowalchuk.milo.core.report.formatTenths
import com.shawnkowalchuk.milo.core.report.metresOfTenths
import com.shawnkowalchuk.milo.core.report.tenthsOfAKilometre
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.report.SentReport
import com.shawnkowalchuk.milo.data.report.SentReportKind
import com.shawnkowalchuk.milo.data.report.SentReportRepository
import com.shawnkowalchuk.milo.data.report.period
import com.shawnkowalchuk.milo.data.settings.ReportHandOver
import com.shawnkowalchuk.milo.data.settings.SettingsStore
import java.io.IOException
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException

/**
 * Records what became of a report: the report that waits for its answer (in the settings
 * file), the list of sent reports, and the event log. A month shows as submitted because of a
 * row written here, so every step that leads to one, or does not, leaves a line: made, handed
 * to the email app, and what Shawn answered.
 *
 * @param settings where the report that was handed to the email app is kept until answered.
 * @param clock wall-clock milliseconds.
 */
class ReportRecords(
    private val sent: SentReportRepository,
    private val settings: SettingsStore,
    private val eventLog: EventLogRepository,
    private val clock: () -> Long,
) {
    /**
     * Stores the report that is about to be handed to the email app, so that "Did you send
     * it?" is still asked if MilO does not live to see Shawn come back.
     *
     * @throws IOException if the settings file cannot be written. Nothing is handed over then.
     */
    suspend fun awaitAnswerFor(report: ReportHandOver) {
        settings.setReportHandOver(report)
    }

    /**
     * Forgets the report that waited for its answer: it has been answered, or the email app
     * did not open after all.
     *
     * @return false if the settings file could not be written; the event log then says why.
     */
    suspend fun forgetAwaited(): Boolean = try {
        settings.setReportHandOver(null)
        true
    } catch (failure: IOException) {
        failed("Forgetting the report that was handed to the email app", failure)
        false
    }

    /**
     * Shawn's answer to "Did you send it?".
     *
     * The waiting report is forgotten before anything is recorded. A question that outlived
     * its answer would be asked again, and a second "I sent it" would record a revision that
     * was never sent; a question that is lost only means the report has to be sent again.
     *
     * @return false if the answer could not be stored; the event log then says why.
     */
    suspend fun answered(report: ReportHandOver, sent: Boolean): Boolean = when {
        !forgetAwaited() -> false

        sent -> answeredSent(report) != null

        else -> {
            answeredNotSent(report)
            true
        }
    }

    /** A file was made and not sent anywhere yet: a PDF to look at, or a CSV. */
    suspend fun created(what: String, period: ReportPeriod, tripCount: Int, tenths: Long) {
        log("$what for ${period.inLogWords()} created: ${figures(tripCount, tenths)}")
    }

    /** The email app was opened with the report. Nothing is known about the email itself. */
    suspend fun handedOver(report: ReportHandOver) {
        log(
            "Report for ${report.period.inLogWords()} handed to the email app: " +
                "${figures(report.tripCount, report.tenths)}. Android does not say whether " +
                "the email is sent, so that is asked when MilO is in front again",
        )
    }

    /**
     * Shawn said he sent it. The report is added to the list of sent reports, which for a
     * whole month is what marks the month as submitted. It is recorded as sent at the moment
     * the email app was opened with it, not at the moment of the answer: the question can
     * wait for days, and the email did not.
     *
     * @return the row as stored, or null if storage failed; the event log then says why, and
     * nothing was recorded.
     */
    suspend fun answeredSent(report: ReportHandOver): SentReport? {
        val stored =
            try {
                sent.recordSent(
                    period = report.period,
                    sentAtMs = report.atMs,
                    tripCount = report.tripCount,
                    distanceMetres = metresOfTenths(report.tenths),
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                // Storage failed, whatever it threw. Left alone, the exception would end the
                // process, and the trip service runs in it.
                failed("Recording the report for ${report.period.inLogWords()} as sent", failure)
                return null
            }
        // After the write and outside the guard around it: a report that was recorded is never
        // reported as failed because its log line could not be written, which would have it
        // recorded a second time, as a revision that was never sent.
        log(sentText(stored))
        return stored
    }

    /** Shawn said he did not send it. Nothing is recorded but this line. */
    suspend fun answeredNotSent(report: ReportHandOver) {
        log(
            "Report for ${report.period.inLogWords()}: answered \"not sent\" after the email " +
                "app. Nothing was recorded",
        )
    }

    /** Something about a report failed and was caught. */
    suspend fun failed(what: String, failure: Exception) {
        eventLog.add(clock(), EventCategory.ERROR, "$what failed", failure.stackTraceToString())
    }

    private suspend fun log(line: String) {
        eventLog.add(clock(), EventCategory.REPORT, line)
    }
}

/** The event-log line for a report that was recorded as sent. */
internal fun sentText(stored: SentReport): String {
    val which =
        if (stored.revision == 0) {
            "the first report for this period"
        } else {
            "revision ${stored.revision}"
        }
    val effect =
        when {
            stored.kind == SentReportKind.RANGE -> "A date range marks no month as submitted"
            stored.revision == 0 -> "The month is now marked as submitted"
            else -> "The month was marked as submitted before, and stays so"
        }
    val total = figures(stored.tripCount, tenthsOfAKilometre(stored.distanceMetres))
    return "Report for ${stored.period.inLogWords()} recorded as sent, on Shawn's word: " +
        "$which, $total. $effect"
}

/**
 * A period as the event log writes it: "2026-10" for a month, "2026-10-05 to 2026-10-18" for a
 * range. The same in every language, like the rest of the log.
 */
internal fun ReportPeriod.inLogWords(): String = when (this) {
    is ReportPeriod.Month -> month.toString()
    is ReportPeriod.Range -> "$firstDay to $lastDay"
}

private fun figures(tripCount: Int, tenths: Long): String =
    "$tripCount trips, ${formatTenths(tenths, Locale.ROOT)} km"

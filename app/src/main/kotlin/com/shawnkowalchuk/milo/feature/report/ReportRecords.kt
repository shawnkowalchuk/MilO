package com.shawnkowalchuk.milo.feature.report

import com.shawnkowalchuk.milo.core.report.ReportPeriod
import com.shawnkowalchuk.milo.core.util.DistanceUnit
import com.shawnkowalchuk.milo.core.util.formatTenths
import com.shawnkowalchuk.milo.core.util.metresOfTenths
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.report.RemovedReport
import com.shawnkowalchuk.milo.data.report.SentReport
import com.shawnkowalchuk.milo.data.report.SentReportKind
import com.shawnkowalchuk.milo.data.report.SentReportRepository
import com.shawnkowalchuk.milo.data.report.period
import com.shawnkowalchuk.milo.data.report.printedTenths
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

    /**
     * A file was made and not sent anywhere yet: a PDF to look at, or a CSV.
     *
     * @param tenths the total the file prints, in tenths of [unit].
     */
    suspend fun created(
        what: String,
        period: ReportPeriod,
        tripCount: Int,
        tenths: Long,
        unit: DistanceUnit,
    ) {
        log("$what for ${period.inLogWords()} created: ${figures(tripCount, tenths, unit)}")
    }

    /** The email app was opened with the report. Nothing is known about the email itself. */
    suspend fun handedOver(report: ReportHandOver) {
        log(
            "Report for ${report.period.inLogWords()} handed to the email app: " +
                "${figures(report.tripCount, report.tenths, report.unit)}. Android does not " +
                "say whether " +
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
    suspend fun answeredSent(report: ReportHandOver): SentReport? = recordSent(report, ::sentText)

    /**
     * Shawn pressed "Mark as sent", and said yes to its question: the report as the screen
     * shows it is added to the list of sent reports without the email app having been opened.
     * It is for a report he sent some other way, and for one he answered "Not sent" for by
     * mistake. It is recorded as sent now, and does for a month or a range exactly what "I
     * sent it" does; only the line in the event log says that it was marked by hand.
     *
     * @param tripCount and [tenths] are what the report lists and adds up to at this moment,
     * the total in tenths of [unit], the unit the report is in.
     * @return the row as stored, or null if storage failed; the event log then says why, and
     * nothing was recorded.
     */
    suspend fun markedSent(
        period: ReportPeriod,
        tripCount: Int,
        tenths: Long,
        unit: DistanceUnit,
    ): SentReport? =
        recordSent(ReportHandOver(period, tripCount, tenths, clock(), unit), ::markedText)

    /**
     * Adds [report] to the list of sent reports, as sent at its own time.
     *
     * @param line the event-log line for the row as it was stored.
     */
    private suspend fun recordSent(
        report: ReportHandOver,
        line: (SentReport) -> String,
    ): SentReport? {
        val stored =
            try {
                sent.recordSent(
                    period = report.period,
                    sentAtMs = report.atMs,
                    tripCount = report.tripCount,
                    // The printed total itself, in metres, and the unit it was printed in: the
                    // row reads back as exactly this figure (`printedTenths`).
                    distanceMetres = metresOfTenths(report.tenths, report.unit),
                    unit = report.unit,
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
        log(line(stored))
        return stored
    }

    /** Shawn said he did not send it. Nothing is recorded but this line. */
    suspend fun answeredNotSent(report: ReportHandOver) {
        log(
            "Report for ${report.period.inLogWords()}: answered \"not sent\" after the email " +
                "app. Nothing was recorded",
        )
    }

    /**
     * Shawn removed a report from the list of sent reports, because it was recorded by mistake.
     * The row is deleted and nothing else is written; a month whose only report it was is "not
     * submitted" again by that alone. One line goes to the event log.
     *
     * @return false if nothing was removed: storage failed, or the list no longer holds the
     * report. The event log says which.
     */
    suspend fun removed(id: Long): Boolean {
        val removed =
            try {
                sent.remove(id)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                // Storage failed, whatever it threw. Left alone, the exception would end the
                // process, and the trip service runs in it.
                failed("Removing a report from the list of sent reports", failure)
                return false
            }
        if (removed == null) {
            log(
                "Remove refused on the Report screen: the list of sent reports holds no " +
                    "report with the id $id. Nothing changed",
            )
            return false
        }
        log(removedText(removed))
        return true
    }

    /** Something about a report failed and was caught. */
    suspend fun failed(what: String, failure: Exception) {
        eventLog.add(clock(), EventCategory.ERROR, "$what failed", failure.stackTraceToString())
    }

    private suspend fun log(line: String) {
        eventLog.add(clock(), EventCategory.REPORT, line)
    }
}

/** The event-log line for a report that was recorded as sent after the email app. */
internal fun sentText(stored: SentReport): String =
    "Report for ${stored.period.inLogWords()} recorded as sent, on Shawn's word: " +
        whatWasRecorded(stored)

/** The event-log line for a report that was marked as sent by hand, with no email app. */
internal fun markedText(stored: SentReport): String =
    "Report for ${stored.period.inLogWords()} marked as sent by hand on the Report screen, " +
        "without the email app: " + whatWasRecorded(stored)

/** Which report of its period a stored row is, its figures, and what it did to the month. */
private fun whatWasRecorded(stored: SentReport): String {
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
    val total = figures(stored.tripCount, stored.printedTenths, stored.distanceUnit)
    return "$which, $total. $effect"
}

/** The event-log line for a report that was removed from the list of sent reports. */
internal fun removedText(removed: RemovedReport): String {
    val report = removed.report
    val which =
        if (report.revision == 0) {
            "the first report for this period"
        } else {
            "revision ${report.revision}"
        }
    val left = removed.leftForPeriod
    val effect =
        when {
            report.kind == SentReportKind.RANGE -> "A date range marked no month as submitted"

            left.isEmpty() -> "The month is no longer marked as submitted"

            else ->
                "The month stays marked as submitted: ${left.size} other report(s) for it " +
                    "are still in the list (revision " +
                    "${left.map { it.revision }.sorted().joinToString(", ")}), and keep " +
                    "their numbers"
        }
    val total = figures(report.tripCount, report.printedTenths, report.distanceUnit)
    return "Sent report removed from the list by hand, on the Report screen: " +
        "${report.period.inLogWords()}, $which, $total. $effect. No trip and no email was " +
        "touched"
}

/**
 * A period as the event log writes it: "2026-10" for a month, "2026-10-05 to 2026-10-18" for a
 * range. The same in every language, like the rest of the log.
 */
internal fun ReportPeriod.inLogWords(): String = when (this) {
    is ReportPeriod.Month -> month.toString()
    is ReportPeriod.Range -> "$firstDay to $lastDay"
}

/**
 * A report's figures as the event log writes them, the total with the unit it was printed in:
 * "12 trips, 345.6 km", "12 trips, 214.8 mi". The same in every language, like the rest of the
 * log. A line names the unit of its own figure, so a line in kilometres and one in miles can
 * stand in one log without either being read as the other.
 */
private fun figures(tripCount: Int, tenths: Long, unit: DistanceUnit): String {
    val word =
        when (unit) {
            DistanceUnit.KILOMETRES -> "km"
            DistanceUnit.MILES -> "mi"
        }
    return "$tripCount trips, ${formatTenths(tenths, Locale.ROOT)} $word"
}

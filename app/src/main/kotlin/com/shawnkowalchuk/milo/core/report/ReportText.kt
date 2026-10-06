package com.shawnkowalchuk.milo.core.report

import com.shawnkowalchuk.milo.core.util.formatDay
import com.shawnkowalchuk.milo.core.util.formatTenths
import com.shawnkowalchuk.milo.core.util.formatTimeOfDay
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

// Every word and figure of the PDF, worked out before anything is measured or drawn. Pure
// Kotlin: the words come in as plain strings, which the caller reads from the string resources.

/** The mark a trip carries on the report when it was added or edited by hand. */
const val REPORT_MARK = "*"

/**
 * The words of the PDF. Each one is user-visible text, so none is written in this package.
 *
 * @param revisionNote a format with two places: the revision's number and the day the report
 * it replaces was sent.
 * @param periodRange how the two dates of a range are joined: "%1$s to %2$s".
 * @param dayContinued a format with one place, the day: its heading on a following page.
 * @param total a format with one place, the period.
 * @param tripCount how many trips the total is of, already worded for this report's number
 * of trips: one trip and five trips are written differently, and which way is the language's
 * to say, so the caller asks the string resources.
 * @param footer a format with two places, the sender's name and the period.
 * @param page a format with two places: this page's number and the number of pages.
 */
data class ReportWords(
    val title: String,
    val name: String,
    val company: String,
    val vehicle: String,
    val period: String,
    val generated: String,
    val businessOnly: String,
    val revisionNote: String,
    val periodRange: String,
    val columnStart: String,
    val columnEnd: String,
    val columnFrom: String,
    val columnTo: String,
    val columnKm: String,
    val dayContinued: String,
    val subtotal: String,
    val total: String,
    val tripCount: String,
    val noTrips: String,
    val noAddress: String,
    val legend: String,
    val signature: String,
    val signatureDate: String,
    val footer: String,
    val page: String,
)

/**
 * How dates, times of day and figures are written on the report: the way the phone writes them,
 * so the report reads like the Trips screen it was made from.
 *
 * @param twentyFourHour whether the phone is set to write times with 24 hours.
 */
data class ReportFormat(val locale: Locale, val twentyFourHour: Boolean)

/** One trip as it is printed. */
data class PrintedRow(
    val start: String,
    val end: String,
    val from: String,
    val to: String,
    val km: String,
    val marked: Boolean,
)

/** One day as it is printed: its heading, its trips and its subtotal. */
data class PrintedDay(
    val heading: String,
    val continuedHeading: String,
    val rows: List<PrintedRow>,
    val subtotalKm: String,
)

/**
 * The whole report as text.
 *
 * @param fields the lines of the heading, each a label and its value: name, company, vehicle,
 * period and the day it was generated. A field that is not set is left out.
 * @param notes the lines under the heading: that only Business trips are listed, and, for a
 * revision, what it replaces.
 * @param emptyNote said in place of the days when the period has no trip, else null.
 * @param legend what the asterisk means, or null when no trip carries one.
 */
data class PrintedReport(
    val words: ReportWords,
    val fields: List<Pair<String, String>>,
    val notes: List<String>,
    val days: List<PrintedDay>,
    val emptyNote: String?,
    val totalLabel: String,
    val totalKm: String,
    val tripCount: String,
    val legend: String?,
    val footer: String,
)

/** Turns [report] into the words and figures that are printed. */
fun printedReport(report: MileageReport, words: ReportWords, format: ReportFormat): PrintedReport {
    val locale = format.locale
    val longDate = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale)
    val period = periodInWords(report.period, locale, words.periodRange)
    val sender = report.sender
    return PrintedReport(
        words = words,
        fields =
            listOfNotNull(
                words.name to sender.name,
                sender.company?.let { words.company to it },
                sender.vehicle?.let { words.vehicle to it },
                words.period to period,
                words.generated to longDate.format(report.generatedOn),
            ),
        notes =
            listOfNotNull(
                words.businessOnly,
                report.revision?.let {
                    val replaced = longDate.format(it.replacesSentOn)
                    String.format(locale, words.revisionNote, it.number, replaced)
                },
            ),
        days = report.days.map { it.printed(report.zone, words, format) },
        emptyNote = words.noTrips.takeIf { report.days.isEmpty() },
        totalLabel = String.format(locale, words.total, period),
        totalKm = formatTenths(report.totalTenths, locale),
        tripCount = words.tripCount,
        legend = words.legend.takeIf { report.markedCount > 0 },
        footer = String.format(locale, words.footer, sender.name, period),
    )
}

private fun ReportDay.printed(zone: ZoneId, words: ReportWords, format: ReportFormat): PrintedDay {
    val heading = formatDay(date, format.locale)
    return PrintedDay(
        heading = heading,
        continuedHeading = String.format(format.locale, words.dayContinued, heading),
        rows =
            trips.map { trip ->
                PrintedRow(
                    start = timeOfDay(trip.startedAtMs, zone, format),
                    end = trip.endedAtMs?.let { timeOfDay(it, zone, format) }.orEmpty(),
                    // An address that is missing is said in words. A position is never printed
                    // in its place: coordinates mean nothing to whoever reads the report.
                    from = trip.from ?: words.noAddress,
                    to = trip.to ?: words.noAddress,
                    km = formatTenths(trip.tenths, format.locale),
                    marked = trip.mark != null,
                )
            },
        subtotalKm = formatTenths(tenths, format.locale),
    )
}

private fun timeOfDay(epochMs: Long, zone: ZoneId, format: ReportFormat): String =
    formatTimeOfDay(epochMs, zone, format.locale, format.twentyFourHour)

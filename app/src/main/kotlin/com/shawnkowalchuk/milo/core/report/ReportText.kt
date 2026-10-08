package com.shawnkowalchuk.milo.core.report

import com.shawnkowalchuk.milo.core.odometer.OdometerFigure
import com.shawnkowalchuk.milo.core.odometer.OdometerSpan
import com.shawnkowalchuk.milo.core.odometer.formatOdometer
import com.shawnkowalchuk.milo.core.util.DistanceUnit
import com.shawnkowalchuk.milo.core.util.formatDay
import com.shawnkowalchuk.milo.core.util.formatMediumDay
import com.shawnkowalchuk.milo.core.util.formatTenths
import com.shawnkowalchuk.milo.core.util.formatTimeOfDay
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

// Every word and figure of the PDF, worked out before anything is measured or drawn. Pure
// Kotlin: the words come in as plain strings, which the caller reads from the string resources.

/** The mark a trip carries on the report when it was added or edited by hand. */
const val REPORT_MARK = "*"

/** Between a trip's label and its vehicle on the line under its addresses. */
private const val DETAIL_JOINER = " · "

/**
 * The words of the PDF. Each one is user-visible text, so none is written in this package.
 *
 * The words that name a unit ([businessOnly], [columnKm], [total], [odometerKm],
 * [odometerEstimated]) are handed in already written for the report's unit, kilometres or
 * miles: this package prints them and knows neither word.
 *
 * @param appName and [appMark] are the app's name and the initial on its mark, as the screens
 * write them at the top.
 * @param generatedOn a format with one place, the day the report was made.
 * @param revisionNote a format with two places: the revision's number and the day the report
 * it replaces was sent.
 * @param periodRange how the two dates of a range are joined: "%1$s to %2$s".
 * @param business and [personal] name the two tiles of figures at the top.
 * @param dayContinued a format with one place, the day: its heading on a following page.
 * @param total a format with one place, the period.
 * @param tripCount how many trips the total is of, already worded for this report's number
 * of trips: one trip and five trips are written differently, and which way is the language's
 * to say, so the caller asks the string resources.
 * @param personalTripCount the same for the period's Personal trips.
 * @param columnKm the title of the distance column, which is the unit's short word ("km",
 * "mi"). It also stands after the two large figures at the top.
 * @param odometerOn a format with one place, the day: the label of an odometer figure.
 * @param odometerKm a format with one place, the figure: an odometer reading as typed.
 * @param odometerEstimated the same for a figure MilO worked out, which carries "est.".
 * @param odometerNote what "est." means, under the heading when a figure carries it.
 * @param odometerOfVehicle a format with two places, the vehicle's name and the label of one
 * of its odometer figures: for a report with more than one vehicle's odometer (2026-10-08).
 * @param footer a format with two places, the sender's name and the period.
 * @param page a format with two places: this page's number and the number of pages.
 */
data class ReportWords(
    val appName: String,
    val appMark: String,
    val title: String,
    val name: String,
    val company: String,
    val vehicle: String,
    val generatedOn: String,
    val businessOnly: String,
    val revisionNote: String,
    val periodRange: String,
    val business: String,
    val personal: String,
    val columnStart: String,
    val columnEnd: String,
    val columnFrom: String,
    val columnTo: String,
    val columnKm: String,
    val dayContinued: String,
    val subtotal: String,
    val total: String,
    val tripCount: String,
    val personalTripCount: String,
    val noTrips: String,
    val noAddress: String,
    val legend: String,
    val signature: String,
    val signatureDate: String,
    val odometerOn: String,
    val odometerKm: String,
    val odometerEstimated: String,
    val odometerNote: String,
    val odometerOfVehicle: String,
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

/**
 * One trip as it is printed.
 *
 * @param detail the line under its addresses (since 2026-10-08): its label, its purpose, and
 * the vehicle it was in where the report has several, joined by a dot. Null with neither.
 */
data class PrintedRow(
    val start: String,
    val end: String,
    val from: String,
    val to: String,
    val km: String,
    val marked: Boolean,
    val detail: String? = null,
)

/**
 * One tile of figures at the top of the report, as it is printed: "Business", "231.4", "21
 * business trips".
 */
data class PrintedTally(val label: String, val km: String, val trips: String)

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
 * @param period the period in words, which the top of the report names in large type.
 * @param generated the line under it: the day the report was made.
 * @param business the Business trips' total, which the report lists, and [personal] the
 * Personal trips', which it does not.
 * @param fields who the report is from, each a label and its value: name, company and
 * vehicle. One that is not set is left out.
 * @param odometer the odometer at the start and the end of the period, each a label and its
 * value, on a tile of their own; empty while no reading has been typed in. Two lines for each
 * vehicle that has a reading, each label naming its vehicle where there are several.
 * @param notes the lines under the heading: that only Business trips are listed, and, for a
 * revision, what it replaces.
 * @param emptyNote said in place of the days when the period has no trip, else null.
 * @param legend what the asterisk means, or null when no trip carries one.
 */
data class PrintedReport(
    val words: ReportWords,
    val period: String,
    val generated: String,
    val business: PrintedTally,
    val personal: PrintedTally,
    val fields: List<Pair<String, String>>,
    val odometer: List<Pair<String, String>>,
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
    val totalKm = formatTenths(report.totalTenths, locale)
    return PrintedReport(
        words = words,
        period = period,
        generated = String.format(locale, words.generatedOn, longDate.format(report.generatedOn)),
        business = PrintedTally(words.business, totalKm, words.tripCount),
        personal =
            PrintedTally(
                words.personal,
                formatTenths(report.personal.tenths, locale),
                words.personalTripCount,
            ),
        fields =
            listOfNotNull(
                words.name to sender.name,
                sender.company?.let { words.company to it },
                sender.vehicle?.let { words.vehicle to it },
            ),
        odometer =
            report.odometers.flatMap { odometer ->
                val name = odometer.vehicle.takeIf { report.odometers.size > 1 }
                listOf(
                    odometer.span.start.printed(report.period.firstDay, words, locale, name),
                    odometer.span.end.printed(report.period.lastDay, words, locale, name),
                )
            },
        notes =
            listOfNotNull(
                words.businessOnly,
                report.revision?.let {
                    val replaced = longDate.format(it.replacesSentOn)
                    String.format(locale, words.revisionNote, it.number, replaced)
                },
                words.odometerNote.takeIf { report.odometers.any { it.span.anyEstimated } },
            ),
        days =
            report.days.map {
                it.printed(report.zone, report.unit, words, format, report.severalVehicles)
            },
        emptyNote = words.noTrips.takeIf { report.days.isEmpty() },
        totalLabel = String.format(locale, words.total, period),
        totalKm = totalKm,
        tripCount = words.tripCount,
        legend = words.legend.takeIf { report.markedCount > 0 },
        footer = String.format(locale, words.footer, sender.name, period),
    )
}

private fun ReportDay.printed(
    zone: ZoneId,
    unit: DistanceUnit,
    words: ReportWords,
    format: ReportFormat,
    severalVehicles: Boolean,
): PrintedDay {
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
                    km = formatTenths(trip.tenths(unit), format.locale),
                    marked = trip.mark != null,
                    detail =
                        listOfNotNull(trip.label, trip.vehicle.takeIf { severalVehicles })
                            .joinToString(DETAIL_JOINER)
                            .ifEmpty { null },
                )
            },
        subtotalKm = formatTenths(tenths(unit), format.locale),
    )
}

/**
 * One odometer figure as it is printed: its label with the day, and the figure.
 *
 * @param vehicle the vehicle's name, where the report has more than one vehicle's odometer.
 */
private fun OdometerFigure.printed(
    day: LocalDate,
    words: ReportWords,
    locale: Locale,
    vehicle: String? = null,
): Pair<String, String> {
    val figure = formatOdometer(value, locale)
    val written = if (estimated) words.odometerEstimated else words.odometerKm
    val label = String.format(locale, words.odometerOn, formatMediumDay(day, locale))
    val named = if (vehicle == null) label else String.format(locale, words.odometerOfVehicle, vehicle, label)
    return named to String.format(locale, written, figure)
}

private val OdometerSpan.anyEstimated: Boolean get() = start.estimated || end.estimated

private fun timeOfDay(epochMs: Long, zone: ZoneId, format: ReportFormat): String =
    formatTimeOfDay(epochMs, zone, format.locale, format.twentyFourHour)

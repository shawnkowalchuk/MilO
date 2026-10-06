package com.shawnkowalchuk.milo.core.report

import com.shawnkowalchuk.milo.core.util.formatTenths
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// The CSV export: the trips of a report, one row each, for a spreadsheet. Pure Kotlin.

/**
 * The words of the CSV: the titles of its seven columns, and what the last column says for a
 * trip that was added or edited by hand. User-visible text, so the caller reads them from the
 * string resources.
 */
data class CsvWords(
    val date: String,
    val start: String,
    val end: String,
    val from: String,
    val to: String,
    val km: String,
    val byHand: String,
    val added: String,
    val edited: String,
)

// A spreadsheet has to read these the same way whatever language the phone is set to, so the
// date is written year first, the times on a 24-hour clock, and the kilometres with a dot.
private val CSV_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT)
private val CSV_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)

/** Lines end the way the CSV standard (RFC 4180) says, which every spreadsheet reads. */
private const val LINE_END = "\r\n"

/** A cell that starts with one of these is run as a formula by a spreadsheet. */
private const val FORMULA_STARTS = "=+-@\t\r"

/**
 * The trips of [report] as CSV text: a row of column titles, then one row for each trip, oldest
 * first, with the facts the PDF prints for it and, in the last column, whether it was added or
 * edited by hand.
 *
 * It differs from the PDF where a spreadsheet needs it to: each row carries its own date, the
 * formats are fixed (see above), a missing address is an empty cell where the PDF writes it
 * out in words, and there are no subtotals, which would be counted twice by whoever sums the
 * column. The kilometres are the figures the PDF prints, so the column adds up to its total.
 */
fun reportCsv(report: MileageReport, words: CsvWords): String {
    val header =
        listOf(words.date, words.start, words.end, words.from, words.to, words.km, words.byHand)
    val rows =
        report.days.flatMap { it.trips }.map { trip ->
            listOf(
                CSV_DATE.format(trip.startedAtMs.at(report.zone)),
                CSV_TIME.format(trip.startedAtMs.at(report.zone)),
                trip.endedAtMs?.let { CSV_TIME.format(it.at(report.zone)) }.orEmpty(),
                trip.from.orEmpty(),
                trip.to.orEmpty(),
                formatTenths(trip.tenths, Locale.ROOT),
                when (trip.mark) {
                    ReportMark.ADDED -> words.added
                    ReportMark.EDITED -> words.edited
                    null -> ""
                },
            )
        }
    return (listOf(header) + rows).joinToString(separator = "") { row ->
        row.joinToString(separator = ",", postfix = LINE_END, transform = ::csvCell)
    }
}

private fun Long.at(zone: ZoneId) = Instant.ofEpochMilli(this).atZone(zone)

/**
 * One cell as it is written. A cell that holds a comma, a quotation mark or a line break is put
 * in quotation marks, with every quotation mark inside it doubled: "48 Main St, Leduc" would
 * otherwise be read as two cells.
 *
 * A cell that starts like a formula gets an apostrophe in front, which makes a spreadsheet show
 * it as the text it is. No address starts that way; a typed one could.
 */
internal fun csvCell(text: String): String {
    val safe = if (text.isNotEmpty() && text[0] in FORMULA_STARTS) "'$text" else text
    val mustQuote = safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
    return if (mustQuote) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
}

package com.shawnkowalchuk.milo.core.report

import java.util.Locale

// What a report is called outside MilO: the subject of the email it is sent with, and the name
// of the file that is attached. Pure Kotlin.

/** A name in a file name is cut here: the period and the extension must stay readable. */
private const val MAX_NAME_IN_FILE_NAME = 40

/**
 * The words of the email's subject.
 *
 * @param subject a format with two places, the period and the sender's name:
 * "Mileage - %1$s - %2$s".
 * @param revision a format with two places, the subject and the revision's number, for a
 * report that replaces one already sent.
 * @param periodRange how the two dates of a range are joined: "%1$s to %2$s".
 */
data class SubjectWords(val subject: String, val revision: String, val periodRange: String)

/**
 * The subject of the email: "Mileage - October 2026 - Shawn Kowalchuk" for a month, and for a
 * date range the range in words in the month's place. A report that replaces one already sent
 * says so at the end, so that the two emails can be told apart in an inbox.
 *
 * @param revision 0 for the first report of its period.
 */
fun reportSubject(
    period: ReportPeriod,
    name: String,
    revision: Int,
    words: SubjectWords,
    locale: Locale,
): String {
    val periodWords = periodInWords(period, locale, words.periodRange)
    val subject = String.format(locale, words.subject, periodWords, name)
    return if (revision > 0) String.format(locale, words.revision, subject, revision) else subject
}

/**
 * The name of the report's file, without a folder: "Mileage-2026-10-Shawn-Kowalchuk.pdf" for a
 * month, "Mileage-2026-10-05-to-2026-10-18-Shawn-Kowalchuk.pdf" for a range, with "-rev1"
 * before the dot for a report that replaces one already sent.
 *
 * The period is written with digits and in the same form in every language, so that a folder
 * of these files sorts by date. The sender's name is in it because the accountant receives the
 * same report from other people; only its letters and digits are kept, since the name is typed
 * freely and a file name may not hold a slash.
 *
 * @param prefix the first word of the name. It is user-visible text ("Mileage").
 * @param extension without the dot: "pdf" or "csv".
 */
fun reportFileName(
    period: ReportPeriod,
    name: String,
    revision: Int,
    prefix: String,
    extension: String,
): String {
    val days =
        when (period) {
            is ReportPeriod.Month -> period.month.toString()
            is ReportPeriod.Range -> "${period.firstDay}-to-${period.lastDay}"
        }
    val parts =
        listOf(
            fileNamePart(prefix),
            days,
            fileNamePart(name).take(MAX_NAME_IN_FILE_NAME).trim('-'),
            if (revision > 0) "rev$revision" else "",
        )
    return parts.filter { it.isNotEmpty() }.joinToString(separator = "-") + "." + extension
}

/** [text] with every run of anything but letters and digits turned into one hyphen. */
private fun fileNamePart(text: String): String = text
    .map { if (it.isLetterOrDigit()) it else '-' }
    .joinToString(separator = "")
    .replace(Regex("-+"), "-")
    .trim('-')

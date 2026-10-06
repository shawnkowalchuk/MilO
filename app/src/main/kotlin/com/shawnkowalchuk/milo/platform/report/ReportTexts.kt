package com.shawnkowalchuk.milo.platform.report

import android.content.Context
import android.text.format.DateFormat
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.report.CsvWords
import com.shawnkowalchuk.milo.core.report.MileageReport
import com.shawnkowalchuk.milo.core.report.ReportFormat
import com.shawnkowalchuk.milo.core.report.ReportWords
import com.shawnkowalchuk.milo.core.report.SubjectWords
import com.shawnkowalchuk.milo.core.report.formatTenths
import com.shawnkowalchuk.milo.core.report.periodInWords
import com.shawnkowalchuk.milo.core.report.reportSubject

/**
 * The words of the report, read from the string resources, and how this phone writes dates and
 * times. The rules that put a report together (`core/report/`) are pure Kotlin and know no
 * resources, so they are handed their words from here.
 *
 * Everything is read at the moment it is asked for, so a report made after the phone's language
 * or its 24-hour switch was changed is written the new way.
 */
class ReportTexts(context: Context) {
    private val appContext = context.applicationContext

    /**
     * How the phone writes dates, figures and times of day: its language, and its own "Use
     * 24-hour format" switch, which the screens go by as well, so a trip's times read the same
     * on the report as on the Trips screen.
     */
    fun format(): ReportFormat = ReportFormat(
        locale = appContext.resources.configuration.locales[0],
        twentyFourHour = DateFormat.is24HourFormat(appContext),
    )

    /** The words of the PDF, for a report that lists [tripCount] trips. */
    fun words(tripCount: Int): ReportWords = ReportWords(
        title = text(R.string.report_pdf_title),
        name = text(R.string.report_pdf_name),
        company = text(R.string.report_pdf_company),
        vehicle = text(R.string.report_pdf_vehicle),
        period = text(R.string.report_pdf_period),
        generated = text(R.string.report_pdf_generated),
        businessOnly = text(R.string.report_pdf_business_only),
        revisionNote = text(R.string.report_pdf_revision),
        periodRange = text(R.string.report_period_range_words),
        columnStart = text(R.string.report_pdf_column_start),
        columnEnd = text(R.string.report_pdf_column_end),
        columnFrom = text(R.string.report_pdf_column_from),
        columnTo = text(R.string.report_pdf_column_to),
        columnKm = text(R.string.report_pdf_column_km),
        dayContinued = text(R.string.report_pdf_day_continued),
        subtotal = text(R.string.report_pdf_subtotal),
        total = text(R.string.report_pdf_total),
        tripCount =
            appContext.resources.getQuantityString(
                R.plurals.report_pdf_trip_count,
                tripCount,
                tripCount,
            ),
        noTrips = text(R.string.report_pdf_no_trips),
        noAddress = text(R.string.report_pdf_no_address),
        legend = text(R.string.report_pdf_legend),
        signature = text(R.string.report_pdf_signature),
        signatureDate = text(R.string.report_pdf_signature_date),
        footer = text(R.string.report_pdf_footer),
        page = text(R.string.report_pdf_page),
    )

    /** The column titles of the CSV, and its words for a trip added or edited by hand. */
    fun csvWords(): CsvWords = CsvWords(
        date = text(R.string.report_csv_date),
        start = text(R.string.report_pdf_column_start),
        end = text(R.string.report_pdf_column_end),
        from = text(R.string.report_pdf_column_from),
        to = text(R.string.report_pdf_column_to),
        km = text(R.string.report_pdf_column_km),
        byHand = text(R.string.report_csv_by_hand),
        added = text(R.string.report_csv_added),
        edited = text(R.string.report_csv_edited),
    )

    /** The first word of a report file's name. */
    fun filePrefix(): String = text(R.string.report_file_prefix)

    /** The subject of the email [report] is sent with. */
    fun subject(report: MileageReport): String = reportSubject(
        period = report.period,
        name = report.sender.name,
        revision = report.revision?.number ?: 0,
        words =
            SubjectWords(
                subject = text(R.string.report_email_subject),
                revision = text(R.string.report_email_subject_revision),
                periodRange = text(R.string.report_period_range_words),
            ),
        locale = format().locale,
    )

    /** The few lines the email starts with. Shawn can change them before he sends it. */
    fun body(report: MileageReport): String {
        val locale = format().locale
        return appContext.resources.getQuantityString(
            R.plurals.report_email_body,
            report.tripCount,
            periodInWords(report.period, locale, text(R.string.report_period_range_words)),
            formatTenths(report.totalTenths, locale),
            report.tripCount,
            report.sender.name,
        )
    }

    private fun text(id: Int): String = appContext.getString(id)
}

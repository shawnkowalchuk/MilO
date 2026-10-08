package com.shawnkowalchuk.milo.feature.report

import android.content.Intent
import com.shawnkowalchuk.milo.core.report.MileageReport
import com.shawnkowalchuk.milo.platform.report.ReportDocuments
import com.shawnkowalchuk.milo.platform.report.ReportFile
import com.shawnkowalchuk.milo.platform.report.ReportHandOff
import com.shawnkowalchuk.milo.platform.report.ReportTexts
import java.io.IOException

/** A PDF that was made, and the report it was made of. */
internal data class CreatedPdf(val report: MileageReport, val file: ReportFile)

/**
 * Makes the files a press on the Report screen asks for, and builds the requests that hand
 * them to another app. The screen starts those requests; nothing here opens an app or records
 * anything as sent. It was part of `ReportViewModel` until that file reached its size limit
 * (ENGINEERING_STANDARDS section 3).
 *
 * @param documents makes the PDF and the CSV.
 * @param handOff builds the requests for the email app, a PDF viewer and the share sheet.
 * @param texts the subject and the first lines of the email.
 * @param records where a file that was made leaves its line in the event log.
 */
internal class ReportFiles(
    private val documents: ReportDocuments,
    private val handOff: ReportHandOff,
    private val texts: ReportTexts,
    private val records: ReportRecords,
) {
    /** What the PDF of [report] is called, whether or not it has been made. */
    fun pdfName(report: MileageReport): String = documents.pdfName(report)

    /**
     * The PDF of [report], to look at or to save: [made] if that is of exactly this report and
     * its file is still there, and otherwise a new one.
     *
     * The file is in the cache, which Android empties by itself when the phone runs short of
     * room (seen on an emulator, where it was gone seconds after it was made). So it is looked
     * for first, and made again if it is no longer there or no longer the report on screen.
     */
    suspend fun pdf(report: MileageReport, made: CreatedPdf?): CreatedPdf {
        if (made != null && made.report == report && documents.holds(made.file)) return made
        val file = documents.createPdf(report)
        created("PDF", report)
        return CreatedPdf(report, file)
    }

    /** A PDF of [report] made at this moment, for the email: what is sent is never an old file. */
    suspend fun freshPdf(report: MileageReport): CreatedPdf =
        CreatedPdf(report, documents.createPdf(report))

    /** Opens [pdf] in whatever app the phone shows a PDF with. */
    fun toView(pdf: CreatedPdf): List<Intent> = listOf(handOff.toView(pdf.file.file))

    /** An email to [address] with [pdf] attached, as the requests to try in order. */
    fun toAccountant(pdf: CreatedPdf, address: String): List<Intent> = handOff.toAccountant(
        pdf = pdf.file.file,
        address = address,
        subject = texts.subject(pdf.report),
        body = texts.body(pdf.report),
    )

    /** Makes the CSV of [report] and offers it to Android's share sheet. */
    suspend fun csvToShare(report: MileageReport): List<Intent> {
        val file = documents.createCsv(report)
        created("CSV", report)
        return listOf(handOff.toShare(file.file, texts.subject(report)))
    }

    /**
     * Makes the CSV of the same trips as [pdf] and offers the two files together to Android's
     * share sheet, to be saved wherever Shawn chooses.
     *
     * The CSV is never a revision, here as when it is exported alone: it is the period's trips
     * as they are now and replaces nothing that was sent (`ReportSources.reportFor`).
     */
    suspend fun bothToShare(pdf: CreatedPdf): List<Intent> {
        val report = pdf.report.copy(revision = null)
        val csv = documents.createCsv(report)
        created("CSV", report)
        return listOf(handOff.toShareBoth(pdf.file.file, csv.file, texts.subject(pdf.report)))
    }

    /** The event-log line for a file that was made, with the report's total in its own unit. */
    private suspend fun created(what: String, report: MileageReport) {
        records.created(what, report.period, report.tripCount, report.totalTenths, report.unit)
    }

    /** Removes the report files of earlier weeks. They are in the cache and can be made again. */
    suspend fun removeOld(nowMs: Long) {
        try {
            documents.removeOldFiles(nowMs)
        } catch (failure: IOException) {
            records.failed("Removing old report files", failure)
        }
    }
}

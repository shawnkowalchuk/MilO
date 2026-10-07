package com.shawnkowalchuk.milo.platform.report

import android.content.Context
import android.graphics.Typeface
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.report.MileageReport
import com.shawnkowalchuk.milo.core.report.layoutReport
import com.shawnkowalchuk.milo.core.report.printedReport
import com.shawnkowalchuk.milo.core.report.reportCsv
import com.shawnkowalchuk.milo.core.report.reportFileName
import com.shawnkowalchuk.milo.data.report.KEEP_REPORT_FILES_MS
import com.shawnkowalchuk.milo.data.report.ReportFileStore
import com.shawnkowalchuk.milo.data.report.buildReportFileStore
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val PDF_EXTENSION = "pdf"
private const val CSV_EXTENSION = "csv"

/**
 * Tells a spreadsheet that the file is UTF-8. Without it Excel reads the file in the PC's old
 * character set, and "Montréal" comes out as "MontrÃ©al".
 */
private const val UTF8_MARK = "\uFEFF"

/**
 * A report file that was made.
 *
 * @param pageCount how many pages the PDF has. 0 for a CSV.
 */
data class ReportFile(val file: File, val pageCount: Int)

/**
 * Makes the two files of a report: the PDF and the CSV. Each is made from the report it is
 * handed and from nothing else, so what a screen showed as the report's content is what the
 * file holds.
 *
 * @param texts the report's words, and how the phone writes dates and times.
 * @param files where the files are kept: the app's cache.
 * @param typeface reads the app's typeface, which the PDF is written in.
 */
class ReportDocuments(
    private val texts: ReportTexts,
    private val files: ReportFileStore,
    private val typeface: () -> Typeface,
) {
    // Android's PdfDocument is not thread safe, and making a PDF is real work that must stay
    // off the main thread. One file at a time, on a background thread.
    private val oneAtATime = Dispatchers.IO.limitedParallelism(1)

    /**
     * Makes the PDF: lays the report out (the pure pass in `core/report/`), draws the pages,
     * and writes the file. A PDF made before for the same period is replaced.
     *
     * @throws IOException if the file cannot be written. No half-written file is left behind.
     */
    suspend fun createPdf(report: MileageReport): ReportFile = withContext(oneAtATime) {
        val format = texts.format()
        val words = texts.words(report.tripCount, report.personal.tripCount)
        val printed = printedReport(report, words, format)
        val paints = ReportPaints(typeface())
        val pages = layoutReport(printed, paints, format.locale)
        val file = files.write(fileName(report, PDF_EXTENSION)) { out ->
            writeReportPdf(pages, paints, out)
        }
        ReportFile(file, pages.size)
    }

    /**
     * What the PDF of [report] is called, whether or not it has been made yet: the name
     * [createPdf] gives its file. The Report screen shows it before anything is created.
     */
    fun pdfName(report: MileageReport): String = fileName(report, PDF_EXTENSION)

    /**
     * Makes the CSV of the same trips.
     *
     * @throws IOException if the file cannot be written.
     */
    suspend fun createCsv(report: MileageReport): ReportFile = withContext(oneAtATime) {
        val text = UTF8_MARK + reportCsv(report, texts.csvWords())
        val file = files.write(fileName(report, CSV_EXTENSION)) { out ->
            out.write(text.toByteArray(Charsets.UTF_8))
        }
        ReportFile(file, pageCount = 0)
    }

    /**
     * Whether a file that was made is still there. Android empties an app's cache by itself
     * when the phone runs short of room, so a report file can be gone before it is opened.
     */
    suspend fun holds(made: ReportFile): Boolean = withContext(oneAtATime) {
        files.holds(made.file)
    }

    /**
     * Removes the report files that are more than a week old. They are in the cache and can be
     * made again; this only keeps them from piling up.
     *
     * @throws IOException if a file cannot be removed.
     */
    suspend fun removeOldFiles(nowMs: Long): Int = withContext(oneAtATime) {
        files.removeOld(beforeMs = nowMs - KEEP_REPORT_FILES_MS)
    }

    private fun fileName(report: MileageReport, extension: String): String = reportFileName(
        period = report.period,
        name = report.sender.name,
        revision = report.revision?.number ?: 0,
        prefix = texts.filePrefix(),
        extension = extension,
    )
}

/** Builds [ReportDocuments] on the report files in the app's cache, in the app's typeface. */
fun buildReportDocuments(context: Context, texts: ReportTexts): ReportDocuments {
    val appContext = context.applicationContext
    return ReportDocuments(
        texts = texts,
        files = buildReportFileStore(appContext),
        typeface = { appContext.resources.getFont(R.font.sora) },
    )
}

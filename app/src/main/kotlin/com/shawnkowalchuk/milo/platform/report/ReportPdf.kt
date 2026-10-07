package com.shawnkowalchuk.milo.platform.report

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.shawnkowalchuk.milo.core.report.PageItem
import com.shawnkowalchuk.milo.core.report.REPORT_PAGE_HEIGHT
import com.shawnkowalchuk.milo.core.report.REPORT_PAGE_WIDTH
import com.shawnkowalchuk.milo.core.report.ReportPage
import com.shawnkowalchuk.milo.core.report.ReportTextStyle
import com.shawnkowalchuk.milo.core.report.TextMeasure
import java.io.OutputStream

// The second of the two passes that make the PDF: drawing. Everything was placed by the layout
// pass (`core/report/ReportLayout.kt`); this file only carries it out with Android's own
// PdfDocument. There is no third-party PDF library (ADR-001).
//
// A PDF page is measured in points, and so is everything here: a text size of 10 is a 10 pt
// font, whatever the phone's screen density or font scale. The colours are the layout pass's
// (`ReportInk`): ink on white paper, four of them the app's own.

/**
 * The paints the report is drawn with, one for each kind of text, and the answer to "how wide
 * is this text?" for the layout pass. Measuring with the very paint that draws is what makes a
 * line that was measured to fit a column fit it on the page.
 *
 * Not thread safe, like the PdfDocument it draws into: one report, one thread.
 *
 * @param sora the app's typeface, `res/font/sora.ttf`. It is a variable font, one file that
 * holds every weight, so each paint tells it how heavy to draw itself, as the screens do
 * (`Type.kt`); without that, every text would come out at the file's default weight.
 */
internal class ReportPaints(sora: Typeface) : TextMeasure {
    private val text: Map<ReportTextStyle, Paint> =
        ReportTextStyle.entries.associateWith { style ->
            // Without hinting and with sub-pixel positions, a text's width does not depend on
            // the size it is drawn at, so what is measured here is what the PDF shows at any
            // zoom and on paper.
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG or Paint.LINEAR_TEXT_FLAG)
                .apply {
                    textSize = style.size
                    typeface = sora
                    setFontVariationSettings("'wght' ${style.weight}")
                }
        }

    private val rule = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    override fun width(text: String, style: ReportTextStyle): Float =
        this.text.getValue(style).measureText(text)

    fun draw(item: PageItem, canvas: Canvas) {
        when (item) {
            is PageItem.Text -> {
                val paint = text.getValue(item.style)
                paint.color = item.ink.argb
                paint.textAlign = if (item.rightAligned) Paint.Align.RIGHT else Paint.Align.LEFT
                canvas.drawText(item.text, item.x, item.baseline, paint)
            }

            is PageItem.Rule -> {
                // Always a thickness of its own: a hairline (0) is one device pixel wide and
                // can print almost invisibly.
                rule.color = item.ink.argb
                rule.strokeWidth = item.thickness
                canvas.drawLine(item.fromX, item.y, item.toX, item.y, rule)
            }

            is PageItem.Box -> {
                fill.color = item.ink.argb
                canvas.drawRoundRect(
                    item.left,
                    item.top,
                    item.right,
                    item.bottom,
                    item.radius,
                    item.radius,
                    fill,
                )
            }
        }
    }
}

/**
 * Draws [pages] into a PDF and writes it to [out]. The stream is left open: it is the caller's.
 *
 * PdfDocument holds every page in memory until it is closed, and is closed here whatever
 * happens. It is not thread safe, so this runs from start to end on the one thread it is called
 * on, which must not be the main thread: the writing at the end is real work.
 *
 * @throws java.io.IOException if the PDF cannot be written to [out].
 */
internal fun writeReportPdf(pages: List<ReportPage>, paints: ReportPaints, out: OutputStream) {
    val document = PdfDocument()
    try {
        for (page in pages) {
            // US Letter in points. No content rectangle is set: the margins are the layout
            // pass's own, so a coordinate there is a coordinate on the paper here.
            val info =
                PdfDocument.PageInfo
                    .Builder(REPORT_PAGE_WIDTH, REPORT_PAGE_HEIGHT, page.number)
                    .create()
            val drawn = document.startPage(info)
            for (item in page.items) paints.draw(item, drawn.canvas)
            document.finishPage(drawn)
        }
        document.writeTo(out)
    } finally {
        document.close()
    }
}

package com.shawnkowalchuk.milo.core.report

import java.util.Locale

// The first of the two passes that make the PDF: where everything goes, and on which page.
// Android's PdfDocument only records what is drawn and cannot go back to a page it has
// finished, so "Page 2 of 5", a heading repeated on the next page and a row kept whole all have
// to be decided before the first page is drawn. Pure Kotlin, so it is tested on its own.

/** The least room between the two halves of the footer. */
private const val FOOTER_GAP = 16f

/** The app's mark at the start of the footer, small, and the room after it. */
private const val FOOTER_MARK_SIZE = 9f
private const val FOOTER_MARK_RADIUS = 2.5f
private const val FOOTER_MARK_GAP = 5f

/** How far a capital's middle stands above its baseline, as a share of the font size. */
private const val CAP_MIDDLE = 0.36f

private const val ELLIPSIS = "…"

/** The pages being filled, and how far down the present one is. */
private class Pages {
    val pages = mutableListOf<MutableList<PageItem>>(mutableListOf())
    var y = CONTENT_TOP
        private set

    /** True once a day, or a part of one, stands on the present page. */
    var holdsADay = false

    val spaceLeft: Float get() = CONTENT_BOTTOM - y

    /** True while nothing at all stands on the present page. */
    val atTop: Boolean get() = y == CONTENT_TOP

    fun place(block: Block) {
        pages.last() += block.items.map { it.movedDown(y) }
        y += block.height
    }

    fun gap(height: Float) {
        y += height
    }

    fun newPage() {
        pages += mutableListOf<PageItem>()
        y = CONTENT_TOP
        holdsADay = false
    }
}

/**
 * Lays the report out on US Letter pages.
 *
 * The rules, in the order they are applied:
 * - **A page is filled before the next is begun** (Shawn's choice of 2026-10-08: "Fill the
 *   page, continue the day"). A day starts in whatever room the page has left; if it does not
 *   fit, it is split between two of its trips and goes on at the top of the next page under its
 *   heading, repeated with "(continued)". Until then a day that fitted on a page was moved to
 *   the next one whole, which could leave half a page empty and the day looking missing.
 * - **The column titles stand once on each page,** above its first day, not under every day.
 * - **A heading is never left alone:** a day's heading, and the column titles above it, are
 *   always followed by at least one of its trips on the same page.
 * - **A trip is never cut,** however many lines its addresses wrap to, and a day's subtotal
 *   stands on the same page as the day's last trip.
 * - **The total, the legend and the signature line stay together.**
 * - Every page ends with the same line: who and which period on the left, "Page n of N" on
 *   the right.
 *
 * @param measure how wide a text comes out in a style. The drawing pass passes the paint it
 * draws with.
 * @return the pages in order, at least one.
 */
fun layoutReport(report: PrintedReport, measure: TextMeasure, locale: Locale): List<ReportPage> {
    val columns = columnsFor(report, measure)
    val pages = Pages()
    pages.place(headingBlock(report, measure))
    report.emptyNote?.let { pages.place(emptyBlock(it)) }
    for (day in report.days) pages.placeDay(day, report.words, columns, measure)

    val closing = closingBlock(report, columns, measure)
    if (closing.height > pages.spaceLeft && !pages.atTop) pages.newPage()
    pages.place(closing)

    val count = pages.pages.size
    return pages.pages.mapIndexed { index, items ->
        val number = index + 1
        ReportPage(number, items + footer(report, number, count, measure, locale))
    }
}

private fun Pages.placeDay(
    day: PrintedDay,
    words: ReportWords,
    columns: Columns,
    measure: TextMeasure,
) {
    val heading = dayHeadingBlock(day.heading)
    val titles = columnTitlesBlock(words, columns)
    val rows = day.rows.map { rowBlock(it, columns, measure) }
    val subtotal = subtotalBlock(day, words, columns)

    // What a part of the day needs above its first trip: its heading, and the column titles
    // while the page has none yet.
    fun head(): Float = heading.height + if (holdsADay) 0f else titles.height

    // What a trip needs to be placed: the day's last trip takes the subtotal along.
    fun needed(index: Int): Float =
        rows[index].height + if (index == rows.lastIndex) subtotal.height else 0f

    var next = 0
    while (next < rows.size) {
        // Not even the heading and one trip fit here. On a page with nothing on it there is no
        // better place to go, so the row is placed all the same, never looped over.
        if (head() + needed(next) > spaceLeft && !atTop) newPage()
        if (!holdsADay) place(titles)
        place(if (next == 0) heading else dayHeadingBlock(day.continuedHeading))
        holdsADay = true
        do {
            place(rows[next])
            next++
        } while (next < rows.size && needed(next) <= spaceLeft)
    }
    place(subtotal)
    gap(DAY_GAP)
}

/**
 * The line at the bottom of page [number] of [count]: the app's mark, small, then whose report
 * and which period on the left, the page on the right. A name and a period too long to stand
 * beside the page number are cut short, with an ellipsis, so the two never run into each other.
 */
private fun footer(
    report: PrintedReport,
    number: Int,
    count: Int,
    measure: TextMeasure,
    locale: Locale,
): List<PageItem> {
    val style = ReportTextStyle.FOOTER
    val markStyle = ReportTextStyle.FOOTER_MARK
    val quiet = ReportInk.QUIET
    val page = String.format(locale, report.words.page, number, count)
    val textX = CONTENT_LEFT + FOOTER_MARK_SIZE + FOOTER_MARK_GAP
    val room = CONTENT_RIGHT - textX - measure.width(page, style) - FOOTER_GAP
    // The mark's middle is level with the middle of the words' capitals.
    val middle = FOOTER_BASELINE - style.size * CAP_MIDDLE
    val mark = report.words.appMark
    return listOf(
        PageItem.Box(
            CONTENT_LEFT,
            middle - FOOTER_MARK_SIZE / 2,
            CONTENT_LEFT + FOOTER_MARK_SIZE,
            middle + FOOTER_MARK_SIZE / 2,
            FOOTER_MARK_RADIUS,
            ReportInk.ACCENT,
        ),
        PageItem.Text(
            mark,
            CONTENT_LEFT + (FOOTER_MARK_SIZE - measure.width(mark, markStyle)) / 2,
            middle + markStyle.size * CAP_MIDDLE,
            markStyle,
        ),
        PageItem.Text(
            cutToFit(report.footer, room, style, measure),
            textX,
            FOOTER_BASELINE,
            style,
            ink = quiet,
        ),
        PageItem.Text(
            page,
            CONTENT_RIGHT,
            FOOTER_BASELINE,
            style,
            rightAligned = true,
            ink = quiet,
        ),
    )
}

/** [text] as it is if it is no wider than [maxWidth], and otherwise its start and an ellipsis. */
internal fun cutToFit(
    text: String,
    maxWidth: Float,
    style: ReportTextStyle,
    measure: TextMeasure,
): String {
    if (measure.width(text, style) <= maxWidth) return text
    var kept = text
    while (kept.isNotEmpty() && measure.width(kept + ELLIPSIS, style) > maxWidth) {
        // Never half of a character that is stored as two.
        kept = kept.dropLast(if (kept.length > 1 && kept.last().isLowSurrogate()) 2 else 1)
    }
    return kept.trimEnd() + ELLIPSIS
}

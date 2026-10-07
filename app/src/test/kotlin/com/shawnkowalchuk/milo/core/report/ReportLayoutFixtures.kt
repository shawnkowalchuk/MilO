package com.shawnkowalchuk.milo.core.report

import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

// What the tests of the layout and of the page breaks share: reports of any size whose trips
// can be found again on the page, and the rules that hold for every one of them.

internal val DAY_ONE: LocalDate = LocalDate.of(2026, 10, 1)

/** Lays [report] out with text measured by plain arithmetic ([MEASURE]). */
internal fun layout(report: MileageReport): List<ReportPage> =
    layoutReport(printedReport(report, WORDS, FORMAT), MEASURE, Locale.CANADA)

/** Each trip's From address says which day and which row it is, so it can be found again. */
internal fun fromOf(day: Int, row: Int): String = "F${day + 1}-${row + 1}"

/** The start time of a day's row: ten minutes after the row before. */
internal fun startOf(row: Int): String = "%02d:%02d".format(row / 6, row % 6 * 10)

/** A report with one day for each number, holding that many trips, from 1 October on. */
internal fun reportOf(vararg tripsPerDay: Int): MileageReport = report(
    tripsPerDay.flatMapIndexed { day, count ->
        List(count) { row ->
            trip(DAY_ONE.plusDays(day.toLong()), startOf(row), 1, from = fromOf(day, row), to = "T")
        }
    },
)

internal val ReportPage.texts: List<PageItem.Text>
    get() = items.filterIsInstance<PageItem.Text>()

/** True for the line at the bottom of every page: its words, and the small mark before them. */
internal val PageItem.Text.inFooter: Boolean
    get() = style == ReportTextStyle.FOOTER || style == ReportTextStyle.FOOTER_MARK

internal fun ReportPage.has(text: String): Boolean = texts.any { it.text == text }

internal fun ReportPage.find(text: String): PageItem.Text = texts.first { it.text == text }

/** The trips on the page, from the top down, each by its From address. */
internal val ReportPage.rows: List<PageItem.Text>
    get() = texts.filter { it.text.matches(Regex("F\\d+-\\d+")) }.sortedBy { it.baseline }

internal val ReportPage.headings: List<PageItem.Text>
    get() = texts.filter { it.style == ReportTextStyle.DAY }.sortedBy { it.baseline }

/** The number of the one page [text] is on. */
internal fun pageOf(pages: List<ReportPage>, text: String): Int =
    pages.single { it.has(text) }.number

/** Where a line of text ends, measured as the layout measured it. */
internal fun PageItem.Text.end(): Float = if (rightAligned) x else x + MEASURE.width(text, style)

/**
 * What must hold for every report made by [reportOf], however its days fall on the pages: every
 * trip is there once and in order, nothing is outside the margins, no heading is left alone,
 * no two rows share a line, and every subtotal stands under its day's last trip on that trip's
 * page.
 */
internal fun assertSound(pages: List<ReportPage>, tripsPerDay: List<Int>) {
    val expected = tripsPerDay.flatMapIndexed { day, count -> List(count) { fromOf(day, it) } }
    assertEquals(expected, pages.flatMap { page -> page.rows.map { it.text } })

    for (page in pages) {
        for (text in page.texts.filterNot { it.inFooter }) {
            val where = "\"${text.text}\" on page ${page.number}"
            assertTrue(where, text.baseline > CONTENT_TOP && text.baseline <= CONTENT_BOTTOM)
            val start = if (text.rightAligned) {
                text.x - MEASURE.width(
                    text.text,
                    text.style,
                )
            } else {
                text.x
            }
            assertTrue(where, start >= CONTENT_LEFT - 0.01f && text.end() <= CONTENT_RIGHT + 0.01f)
        }
        for (heading in page.headings) {
            val below = page.rows.filter { it.baseline > heading.baseline }
            assertTrue("\"${heading.text}\" stands alone on page ${page.number}", below.any())
        }
        val baselines = page.rows.map { it.baseline }
        assertEquals("Rows overlap on page ${page.number}", baselines.distinct(), baselines)
    }

    val subtotals = pages.sumOf { page -> page.texts.count { it.text == WORDS.subtotal } }
    assertEquals(tripsPerDay.size, subtotals)
    tripsPerDay.forEachIndexed { day, count ->
        val page = pages.single { it.has(fromOf(day, count - 1)) }
        val last = page.find(fromOf(day, count - 1))
        assertTrue(
            "Day ${day + 1}'s subtotal is not under its last trip on page ${page.number}",
            page.texts.any { it.text == WORDS.subtotal && it.baseline > last.baseline },
        )
    }
}

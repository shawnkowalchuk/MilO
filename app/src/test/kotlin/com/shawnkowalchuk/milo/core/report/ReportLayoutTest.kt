package com.shawnkowalchuk.milo.core.report

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where the report's words stand on a page: the columns, the wrapping of long addresses, and
 * the figures under each other. (Where the pages break is `ReportPaginationTest`.)
 */
class ReportLayoutTest {
    private val cell = ReportTextStyle.CELL

    @Test
    fun `a short report is one page that holds everything`() {
        val page = layout(reportOf(2, 1)).single()

        val top = listOf("MilO", "Mileage report", "Sam Driver", "October 2026")
        for (text in top + "Generated October 6, 2026") {
            assertTrue(text, page.has(text))
        }
        assertTrue(page.has("Thursday, October 1, 2026"))
        assertTrue(page.has("Friday, October 2, 2026"))
        // The column titles once on the page, above the first day; a subtotal for each day,
        // and one total.
        assertEquals(1, page.texts.count { it.text == "Start" })
        assertTrue(page.find("Start").baseline < page.find("Thursday, October 1, 2026").baseline)
        assertEquals(2, page.texts.count { it.text == "Subtotal" })
        assertTrue(page.has("Total business kilometres for October 2026"))
        assertTrue(page.has("Signature"))
        assertTrue(page.has("Date"))
        assertTrue(page.has("Page 1 of 1"))
        assertSound(listOf(page), listOf(2, 1))
    }

    @Test
    fun `a trip's five entries stand under their column titles`() {
        val page = layout(reportOf(1)).single()

        val row = page.find(fromOf(0, 0)).baseline
        fun entry(text: String) = page.texts.single { it.text == text && it.baseline == row }
        assertEquals(page.find("Start").x, entry("00:00").x, 0f)
        assertEquals(page.find("End").x, entry("00:01").x, 0f)
        assertEquals(page.find("To").x, entry("T").x, 0f)
        assertEquals(page.kmTitle().x, entry("12.3").x, 0f)
        // From left to right, each entry ends before the next column starts.
        val inOrder = listOf("00:00", "00:01", fromOf(0, 0), "T").map(::entry)
        inOrder.zipWithNext { left, right -> assertTrue(left.text, left.end() < right.x) }
    }

    @Test
    fun `a long address wraps inside its column, and its row grows to hold it`() {
        val long = "10103 104 Avenue Northwest Suite 1200 Commerce Place Tower Edmonton Alberta"
        val trips =
            listOf(
                trip(DAY_ONE, "08:00", from = long, to = "T"),
                trip(DAY_ONE, "09:00", from = "After", to = "T"),
            )
        val page = layout(report(trips)).single()

        val fromX = page.find("From").x
        val toX = page.find("To").x
        val lines =
            page.texts
                .filter { it.style == cell && it.x == fromX && it.text != "After" }
                .sortedBy { it.baseline }
        assertTrue("Wrapped to ${lines.size} lines", lines.size >= 2)
        // Nothing was lost or cut: the lines put together are the address.
        assertEquals(long, lines.joinToString(" ") { it.text })
        for (line in lines) assertTrue("\"${line.text}\" runs into the To column", line.end() < toX)
        // One line under the other, and the next trip under all of them.
        val height = lines.last().baseline - lines.first().baseline
        assertEquals(cell.leading * (lines.size - 1), height, 0.01f)
        assertTrue(page.find("After").baseline > lines.last().baseline)
    }

    @Test
    fun `both addresses of a trip wrap, each in its own column`() {
        val from = "Unit 14 Building C Northside Industrial Park 12 Shop Road Edmonton"
        val to = "Loading Dock 3 rear entrance 48 Main Street South Leduc Alberta Canada"
        val page = layout(report(listOf(trip(DAY_ONE, "08:00", from = from, to = to)))).single()

        val fromX = page.find("From").x
        val toX = page.find("To").x
        fun column(x: Float) =
            page.texts.filter { it.style == cell && it.x == x }.sortedBy { it.baseline }
        assertEquals(from, column(fromX).joinToString(" ") { it.text })
        assertEquals(to, column(toX).joinToString(" ") { it.text })
        // The two start on the same line, and the subtotal is under the longer of them.
        assertEquals(column(fromX).first().baseline, column(toX).first().baseline, 0f)
        val lowest = maxOf(column(fromX).last().baseline, column(toX).last().baseline)
        assertTrue(page.find("Subtotal").baseline > lowest)
    }

    @Test
    fun `a word too long for its column is cut where the line is full, and nothing is lost`() {
        val word = "Llanfairpwllgwyngyllgogerychwyrndrobwllllantysiliogogogoch-Northwest"
        val page = layout(report(listOf(trip(DAY_ONE, "08:00", from = "F1-1", to = word)))).single()

        val toX = page.find("To").x
        val lines = page.texts.filter { it.style == cell && it.x == toX }.sortedBy { it.baseline }
        assertTrue(lines.size >= 2)
        assertEquals(word, lines.joinToString("") { it.text })
        for (line in lines) assertTrue(line.text, line.end() <= CONTENT_RIGHT)
    }

    @Test
    fun `wrapping breaks at spaces where it can, and always ends`() {
        val style = ReportTextStyle.FOOTER
        // Four points a letter at this size: ten letters to a line of 40 points.
        assertEquals(
            listOf("one two", "three four"),
            wrap("one two three four", 40f, style, MEASURE),
        )
        assertEquals(listOf("fits"), wrap("fits", 40f, style, MEASURE))
        assertEquals(listOf(""), wrap("", 40f, style, MEASURE))
        assertEquals(listOf("a b"), wrap("  a   b ", 40f, style, MEASURE))
        assertEquals(listOf("abcd", "efgh", "ij"), wrap("abcdefghij", 16f, style, MEASURE))
        // A long word starts a line of its own, and what is left of it shares its last line
        // with the words after it.
        assertEquals(
            listOf("ab", "abcd", "efgh", "ij k"),
            wrap("ab abcdefghij k", 16f, style, MEASURE),
        )
        // A column too narrow for one letter still ends: a letter to a line.
        assertEquals(listOf("a", "b", "c"), wrap("abc", 1f, style, MEASURE))
    }

    @Test
    fun `a character stored as two is never cut in half`() {
        val style = ReportTextStyle.FOOTER
        val truck = "🚚"

        val lines = wrap("ab$truck$truck", 12f, style, MEASURE)

        assertEquals("ab$truck$truck", lines.joinToString(""))
        for (line in lines) {
            assertFalse(line, Character.isLowSurrogate(line.first()))
            assertFalse(line, Character.isHighSurrogate(line.last()))
        }
    }

    @Test
    fun `the kilometres end at one edge, and a marked trip has its asterisk after them`() {
        val trips =
            listOf(
                trip(DAY_ONE, "08:00", from = "F1-1", to = "T", metres = 1_200.0),
                trip(DAY_ONE, "09:00", from = "F1-2", to = "T", metres = 123_400.0)
                    .copy(mark = ReportMark.EDITED),
            )
        val page = layout(report(trips)).single()

        val short = page.find("1.2")
        val long = page.find("123.4")
        val subtotal = page.texts.single { it.text == "124.6" && it.style == ReportTextStyle.SUM }
        val total = page.texts.single { it.text == "124.6" && it.style == ReportTextStyle.TOTAL }
        for (figure in listOf(short, long, subtotal, total, page.kmTitle())) {
            assertTrue(figure.text, figure.rightAligned)
            assertEquals(figure.text, short.x, figure.x, 0f)
        }
        val mark = page.find(REPORT_MARK)
        assertEquals(long.baseline, mark.baseline, 0f)
        assertTrue(mark.x > long.x)
        assertTrue(mark.end() <= CONTENT_RIGHT + 0.01f)
        assertTrue(page.has(WORDS.legend))
    }

    @Test
    fun `a report without a marked trip has no asterisk and no legend`() {
        val page = layout(reportOf(3)).single()

        assertFalse(page.has(REPORT_MARK))
        assertFalse(page.has(WORDS.legend))
    }

    @Test
    fun `times written with AM and PM get the room they need`() {
        val twelveHour = ReportFormat(Locale.CANADA, twentyFourHour = false)
        val trips = listOf(trip(DAY_ONE, "22:45", minutes = 30, from = "F1-1", to = "T"))
        val printed = printedReport(report(trips), WORDS, twelveHour)
        val page = layoutReport(printed, MEASURE, Locale.CANADA).single()

        val row = printed.days.single().rows.single()
        assertTrue(page.find(row.start).end() < page.find(row.end).x)
        assertTrue(page.find(row.end).end() < page.find("F1-1").x)
    }

    @Test
    fun `a long vehicle description wraps under its label, inside its column`() {
        val vehicle = "2019 Ford F-150 XLT SuperCrew four wheel drive, white, " +
            "Alberta plate ABC-1234, unit 17 of the Northside Electric service fleet"
        val sender = SENDER.copy(vehicle = vehicle)
        val page = layout(report(listOf(trip(DAY_ONE, "08:00")), sender = sender)).single()

        val label = page.find("Vehicle")
        val lines =
            page.texts
                .filter { it.style == ReportTextStyle.VALUE && it.x == label.x }
                .sortedBy { it.baseline }
        assertTrue(lines.size >= 2)
        assertEquals(vehicle, lines.joinToString(" ") { it.text })
        assertTrue(lines.first().baseline > label.baseline)
        for (line in lines) assertTrue(line.text, line.end() <= CONTENT_RIGHT)
        // The name and the company stand beside it, in columns of their own, on its first line.
        val name = page.find("Sam Driver")
        assertEquals(lines.first().baseline, name.baseline, 0f)
        assertTrue(name.end() < page.find("Northside Electric Ltd.").x)
        assertTrue(page.find("Northside Electric Ltd.").end() < label.x)
        // The days start under all of it.
        assertTrue(page.find("Thursday, October 1, 2026").baseline > lines.last().baseline)
    }

    @Test
    fun `the top tile holds the app's mark and name, the period and the day it was made`() {
        val page = layout(reportOf(1)).single()

        val tile = page.items.filterIsInstance<PageItem.Box>().first()
        assertEquals(ReportInk.DARK, tile.ink)
        assertEquals(CONTENT_TOP, tile.top, 0f)
        for (text in listOf(
            "MilO",
            "Mileage report",
            "October 2026",
            "Generated October 6, 2026",
        )) {
            val line = page.find(text)
            assertTrue(text, line.baseline > tile.top && line.baseline < tile.bottom)
        }
        // The mark is the accent's square with the initial in it, at the tile's start.
        val mark = page.items.filterIsInstance<PageItem.Box>()[1]
        assertEquals(ReportInk.ACCENT, mark.ink)
        val initial = page.texts.first { it.style == ReportTextStyle.MARK }
        assertEquals("M", initial.text)
        assertTrue(initial.x > mark.left && initial.end() < mark.right)
        assertTrue(page.find("MilO").x > mark.right)
        // The period ends where the tile's words end, on the right.
        assertTrue(page.find("October 2026").rightAligned)
    }

    @Test
    fun `Business and Personal stand side by side, Business on the accent`() {
        val page = layout(reportOf(2)).single()

        val business = page.find("Business")
        val personal = page.find("Personal")
        assertEquals(business.baseline, personal.baseline, 0f)
        assertTrue(business.x < personal.x)
        val tiles = page.items.filterIsInstance<PageItem.Box>()
        fun tileOf(text: PageItem.Text) = tiles.single {
            text.x > it.left && text.x < it.right &&
                text.baseline in it.top..it.bottom
        }
        assertEquals(ReportInk.ACCENT, tileOf(business).ink)
        assertEquals(ReportInk.PANEL, tileOf(personal).ink)
        // Each with its kilometres in large figures, and its trips.
        val figures = page.texts.filter { it.style == ReportTextStyle.FIGURE }.map { it.text }
        assertEquals(listOf("24.6", "18.2"), figures)
        assertTrue(page.has("3 business trips"))
        assertTrue(page.has("2 personal trips"))
    }

    @Test
    fun `a period too long for one line takes more on the top tile, which grows to hold them`() {
        // A language that joins the two days of a range with more words than English does.
        val words = WORDS.copy(periodRange = "from %1\$s up to and including %2\$s")
        val range = ReportPeriod.Range(DAY_ONE, DAY_ONE.plusDays(17))
        val printed = printedReport(report(listOf(trip(DAY_ONE, "08:00")), range), words, FORMAT)
        val page = layoutReport(printed, MEASURE, Locale.CANADA).single()

        val tile = page.items.filterIsInstance<PageItem.Box>().first()
        val period = page.texts.filter { it.style == ReportTextStyle.PERIOD }
        assertTrue(period.size >= 2)
        assertEquals(
            "from October 1, 2026 up to and including October 18, 2026",
            period.joinToString(" ") { it.text },
        )
        val generated = page.find("Generated October 6, 2026")
        assertTrue(generated.baseline > period.last().baseline && generated.baseline < tile.bottom)
        for (line in period) {
            val start = line.x - MEASURE.width(line.text, line.style)
            assertTrue(line.text, start > page.find("Mileage report").end())
        }
    }

    /** The column title of the kilometres: the first, where a page has more than one day. */
    private fun ReportPage.kmTitle(): PageItem.Text =
        texts.first { it.text == "km" && it.style == ReportTextStyle.COLUMN }
}

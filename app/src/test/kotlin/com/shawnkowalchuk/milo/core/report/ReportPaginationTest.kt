package com.shawnkowalchuk.milo.core.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where the pages break: a day kept whole, a long day split and carried on under its heading,
 * nothing orphaned, and the page numbers. Text is measured by plain arithmetic here, so no
 * Android is needed; the phone measures with its own fonts and the same rules apply.
 */
class ReportPaginationTest {
    @Test
    fun `the first page starts with the app's mark and name, and no later page repeats them`() {
        val pages = layout(reportOf(100))

        val first = pages.first()
        val name = first.find("MilO")
        val initial = first.texts.first { it.style == ReportTextStyle.MARK }
        assertTrue(first.texts.filterNot { it.inFooter }.none { it.baseline < name.baseline - 6 })
        assertTrue(initial.x < name.x)
        assertTrue(first.has("Mileage report"))
        val again = listOf("MilO", "Mileage report", "Name", "Business", "Personal")
        assertTrue(pages.drop(1).none { page -> again.any { page.has(it) } })
    }

    @Test
    fun `every page's line at the bottom starts with the app's small mark`() {
        val pages = layout(reportOf(100))

        for (page in pages) {
            val mark = page.texts.single { it.style == ReportTextStyle.FOOTER_MARK }
            assertEquals("M", mark.text)
            val whose = page.texts.single { it.style == ReportTextStyle.FOOTER && !it.rightAligned }
            assertTrue(mark.end() < whose.x)
            val square = page.items.filterIsInstance<PageItem.Box>().single {
                it.top >
                    CONTENT_BOTTOM
            }
            assertEquals(ReportInk.ACCENT, square.ink)
            assertTrue(mark.x > square.left && mark.end() < square.right)
        }
    }

    @Test
    fun `every page says which page of how many it is, and whose report`() {
        val pages = layout(reportOf(100, 40, 3))

        assertTrue(pages.size >= 3)
        pages.forEachIndexed { index, page ->
            assertEquals(index + 1, page.number)
            val footer = page.find("Page ${index + 1} of ${pages.size}")
            assertEquals(FOOTER_BASELINE, footer.baseline, 0f)
            assertTrue(footer.rightAligned)
            assertEquals(CONTENT_RIGHT, footer.x, 0f)
            assertEquals(FOOTER_BASELINE, page.find("Sam Driver · October 2026").baseline, 0f)
        }
    }

    @Test
    fun `a name too long for the footer is cut short, and never runs into the page number`() {
        val longName = "Bartholomew Montgomery-Featherstonehaugh of the Northside Electric " +
            "Company and Sons, Electrical and Mechanical Contractors Limited"
        val range = ReportPeriod.Range(DAY_ONE, DAY_ONE.plusDays(17))
        val sender = SENDER.copy(name = longName)
        val page = layout(report(listOf(trip(DAY_ONE, "08:00")), range, sender)).single()

        val number = page.find("Page 1 of 1")
        val whose = page.texts.single { it.style == ReportTextStyle.FOOTER && !it.rightAligned }
        assertTrue(whose.text, whose.text.startsWith("Bartholomew") && whose.text.endsWith("…"))
        val numberStart = number.x - MEASURE.width(number.text, number.style)
        assertTrue(whose.end() < numberStart)
        // The heading still has the name whole, wrapped under its label.
        val name = page.find("Name")
        val lines = page.texts.filter { it.style == ReportTextStyle.VALUE && it.x == name.x }
        assertEquals(longName, lines.sortedBy { it.baseline }.joinToString(" ") { it.text })
        // And a footer that fits is left as it is.
        val style = ReportTextStyle.FOOTER
        assertEquals("Sam Driver", cutToFit("Sam Driver", 40f, style, MEASURE))
        assertEquals("Sam Dr…", cutToFit("Sam Driver", 28f, style, MEASURE))
        assertEquals("…", cutToFit("Sam Driver", 2f, style, MEASURE))
    }

    @Test
    fun `a day that does not fit in what is left of the page starts the next one, whole`() {
        // Under the heading and two days, page 1 has room left for a heading and a couple of
        // trips, but not for the third day's twelve. The day is not started there and split:
        // it moves to page 2 in one piece.
        val sizes = listOf(6, 4, 12, 12)
        val pages = layout(reportOf(6, 4, 12, 12))

        assertSound(pages, sizes)
        assertTrue(pages.none { page -> page.texts.any { it.text.endsWith("(continued)") } })
        sizes.forEachIndexed { day, count ->
            val first = pageOf(pages, fromOf(day, 0))
            assertEquals("Day ${day + 1} is split", first, pageOf(pages, fromOf(day, count - 1)))
        }
        assertEquals(1, pageOf(pages, fromOf(1, 0)))
        assertEquals(2, pageOf(pages, fromOf(2, 0)))
        // The room that was left would have held its heading and its first trips.
        val lastOnPage1 = pages[0].texts.filterNot { it.inFooter }
        val roomLeft = CONTENT_BOTTOM - lastOnPage1.maxOf { it.baseline }
        assertTrue("Only $roomLeft points were left", roomLeft > 4 * ReportTextStyle.CELL.leading)
        // It starts the page: its heading is the first thing on it.
        val heading = pages[1].find("Saturday, October 3, 2026")
        assertTrue(pages[1].texts.none { it.baseline < heading.baseline })
    }

    @Test
    fun `a day longer than a page goes on under its heading, repeated, and the column titles`() {
        val pages = layout(reportOf(100))

        assertSound(pages, listOf(100))
        assertTrue(pages.size >= 3)
        val heading = "Thursday, October 1, 2026"
        assertTrue(pages.first().has(heading))
        for (page in pages.drop(1).filter { it.rows.isNotEmpty() }) {
            val continued = page.find("$heading (continued)")
            // At the top of the page, with the column titles between it and the first trip.
            assertTrue(page.texts.none { it.baseline < continued.baseline })
            val titles = page.find("Start")
            val firstRow = page.rows.first()
            assertTrue(continued.baseline < titles.baseline && titles.baseline < firstRow.baseline)
            assertFalse(page.has(heading))
        }
        // One subtotal for the whole day, on the page its last trip is on.
        assertEquals(1, pages.sumOf { page -> page.texts.count { it.text == "Subtotal" } })
    }

    @Test
    fun `a long day that starts under another day is split where the page ends`() {
        // A short day, then one that no page could hold whole: it starts on page 1, under the
        // first day, and is not moved to a page of its own.
        val pages = layout(reportOf(3, 80))

        assertSound(pages, listOf(3, 80))
        assertEquals(1, pageOf(pages, fromOf(1, 0)))
        assertTrue(pages[1].has("Friday, October 2, 2026 (continued)"))
    }

    @Test
    fun `a first day too long for the room under the title is split, not sent to page 2`() {
        // It would fit on a page of its own, but page 1 holds nothing but the title yet, and a
        // first page with a title and no trip is worse than a day in two parts.
        val pages = layout(reportOf(36))

        assertSound(pages, listOf(36))
        assertEquals(2, pages.size)
        assertEquals(1, pageOf(pages, fromOf(0, 0)))
        assertTrue(pages[1].has("Thursday, October 1, 2026 (continued)"))
    }

    @Test
    fun `no heading is left alone and no subtotal without its last trip, whatever the lengths`() {
        // Every length of a first day from 1 to 60 trips moves the page breaks of the days
        // after it by one row at a time, through every position a break can have.
        for (first in 1..60) {
            val pages = layout(reportOf(first, 9, 14, 45, 2, 30))
            assertSound(pages, listOf(first, 9, 14, 45, 2, 30))
        }
    }

    @Test
    fun `a day's last trip is not left on a page its subtotal does not fit on`() {
        // One of these lengths ends a page exactly: the last trip then moves on with the
        // subtotal, under the repeated heading, so the two are never parted.
        for (count in 30..80) {
            val pages = layout(reportOf(count))
            assertSound(pages, listOf(count))
            val last = pages.single { it.has(fromOf(0, count - 1)) }
            assertTrue("$count trips", last.has("Subtotal"))
        }
    }

    @Test
    fun `the total, the legend and the signature line stay on one page`() {
        // From a page that is nearly full to one that is over: the end of the report moves to
        // the next page in one piece, never line by line.
        for (count in 20..45) {
            val trips =
                List(count) { row ->
                    val mark = ReportMark.ADDED.takeIf { row == 0 }
                    trip(DAY_ONE, startOf(row), 1, from = fromOf(0, row), to = "T", mark = mark)
                }
            val pages = layout(report(trips))

            val closing = pages.single { it.has("Total business kilometres for October 2026") }
            for (text in listOf(WORDS.legend, "Signature", "Date", WORDS.tripCount)) {
                assertTrue("$count trips: $text", closing.has(text))
            }
            assertEquals(pages.last().number, closing.number)
            // The signature has a rule to sign on, and one for the date beside it.
            val words = closing.find("Signature").baseline
            val rules = closing.items.filterIsInstance<PageItem.Rule>()
            assertEquals(2, rules.count { it.y < words && it.y > words - 12 })
        }
    }

    @Test
    fun `a period without trips is one page that says so`() {
        val page = layout(report(emptyList())).single()

        assertTrue(page.has("No business trips in this period."))
        assertTrue(page.has("Total business kilometres for October 2026"))
        assertTrue(page.has("0.0"))
        assertTrue(page.has("Signature"))
        assertTrue(page.has("Page 1 of 1"))
        assertFalse(page.has("Start"))
        assertFalse(page.has("Subtotal"))
    }

    @Test
    fun `every rule has a thickness of its own, and stays inside the margins`() {
        val rules = layout(reportOf(100, 3)).flatMap { it.items.filterIsInstance<PageItem.Rule>() }

        assertTrue(rules.isNotEmpty())
        // Never a hairline: thickness 0 is one device pixel and can print invisibly.
        assertTrue(rules.all { it.thickness >= 0.5f })
        assertTrue(rules.all { it.fromX >= CONTENT_LEFT && it.toX <= CONTENT_RIGHT })
        for (rule in rules) {
            assertTrue("$rule", rule.fromX < rule.toX)
            assertTrue("$rule", rule.y >= CONTENT_TOP && rule.y <= CONTENT_BOTTOM)
        }
    }
}

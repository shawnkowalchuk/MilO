package com.shawnkowalchuk.milo.core.report

import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

/** The email's subject and the attached file's name. */
class ReportNamesTest {
    private val words =
        SubjectWords(
            subject = "Mileage - %1\$s - %2\$s",
            revision = "%1\$s - Revision %2\$d",
            periodRange = "%1\$s to %2\$s",
        )
    private val month = ReportPeriod.Month(OCTOBER)
    private val range = ReportPeriod.Range(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 18))

    private fun subject(period: ReportPeriod, revision: Int = 0): String =
        reportSubject(period, "Sam Driver", revision, words, Locale.CANADA)

    @Test
    fun `a month's subject is Mileage, the month and year, and the name`() {
        assertEquals("Mileage - October 2026 - Sam Driver", subject(month))
    }

    @Test
    fun `a date range's subject has the range in words where the month would stand`() {
        assertEquals(
            "Mileage - October 5, 2026 to October 18, 2026 - Sam Driver",
            subject(range),
        )
    }

    @Test
    fun `a range of one day is one date in the subject`() {
        val day = LocalDate.of(2026, 10, 5)

        assertEquals(
            "Mileage - October 5, 2026 - Sam Driver",
            subject(ReportPeriod.Range(day, day)),
        )
    }

    @Test
    fun `a revision says so at the end of the subject, with its number`() {
        assertEquals("Mileage - October 2026 - Sam Driver - Revision 1", subject(month, 1))
        assertEquals(
            "Mileage - October 5, 2026 to October 18, 2026 - Sam Driver - Revision 3",
            subject(range, 3),
        )
    }

    @Test
    fun `the subject carries the name as it was typed`() {
        val subject = reportSubject(month, "Zoë O'Neil-Smith", 0, words, Locale.CANADA)

        assertEquals("Mileage - October 2026 - Zoë O'Neil-Smith", subject)
    }

    @Test
    fun `a month's file is named by year and month, and by who it is from`() {
        assertEquals(
            "Mileage-2026-10-Sam-Driver.pdf",
            reportFileName(month, "Sam Driver", 0, "Mileage", "pdf"),
        )
        assertEquals(
            "Mileage-2026-10-Sam-Driver.csv",
            reportFileName(month, "Sam Driver", 0, "Mileage", "csv"),
        )
    }

    @Test
    fun `a range's file is named by its two days`() {
        assertEquals(
            "Mileage-2026-10-05-to-2026-10-18-Sam-Driver.pdf",
            reportFileName(range, "Sam Driver", 0, "Mileage", "pdf"),
        )
    }

    @Test
    fun `a revision's file says which revision it is`() {
        assertEquals(
            "Mileage-2026-10-Sam-Driver-rev2.pdf",
            reportFileName(month, "Sam Driver", 2, "Mileage", "pdf"),
        )
    }

    @Test
    fun `only the letters and digits of a name reach the file name`() {
        // A slash would make a folder of it, and dots a file that hides or climbs.
        assertEquals(
            "Mileage-2026-10-Sam-Driver-Jr.pdf",
            reportFileName(month, "  Sam/Driver, Jr.  ", 0, "Mileage", "pdf"),
        )
        assertEquals(
            "Mileage-2026-10-etc-passwd.pdf",
            reportFileName(month, "../../etc/passwd", 0, "Mileage", "pdf"),
        )
        assertEquals(
            "Mileage-2026-10-Zoë-O-Neil.pdf",
            reportFileName(month, "Zoë O'Neil", 0, "Mileage", "pdf"),
        )
    }

    @Test
    fun `a name with nothing to keep, or no name, leaves the file named by its period`() {
        assertEquals("Mileage-2026-10.csv", reportFileName(month, "", 0, "Mileage", "csv"))
        assertEquals("Mileage-2026-10.pdf", reportFileName(month, "?!", 0, "Mileage", "pdf"))
    }

    @Test
    fun `a very long name is cut, and the file name still ends properly`() {
        val name = reportFileName(month, "A".repeat(39) + " " + "B".repeat(30), 1, "Mileage", "pdf")

        assertEquals("Mileage-2026-10-${"A".repeat(39)}-rev1.pdf", name)
    }
}

package com.shawnkowalchuk.milo.core.report

import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The words and figures of the PDF, before anything is measured or drawn. */
class ReportTextTest {
    private val monday = LocalDate.of(2026, 10, 5)

    private fun printed(report: MileageReport, format: ReportFormat = FORMAT): PrintedReport =
        printedReport(report, WORDS, format)

    @Test
    fun `the heading names who, what vehicle, which period and when it was made`() {
        val printed = printed(report(listOf(trip(monday, "08:14"))))

        assertEquals(
            listOf(
                "Name" to "Sam Driver",
                "Company" to "Northside Electric Ltd.",
                "Vehicle" to "Ford F-150, ABC-123",
            ),
            printed.fields,
        )
        assertEquals("October 2026", printed.period)
        assertEquals("Generated October 6, 2026", printed.generated)
        assertEquals(listOf("Business trips only. Distances are in kilometres."), printed.notes)
    }

    @Test
    fun `the top shows the Business and the Personal kilometres, each with its trips`() {
        val trips = listOf(trip(monday, "08:14", metres = 12_340.0), trip(monday, "17:02"))

        val printed = printed(report(trips, personal = PersonalDriving(2, tenths = 182)))

        assertEquals(PrintedTally("Business", "24.6", "3 business trips"), printed.business)
        assertEquals(PrintedTally("Personal", "18.2", "2 personal trips"), printed.personal)
        // The Business figure is the total the report ends with.
        assertEquals(printed.totalKm, printed.business.km)
    }

    @Test
    fun `a period without Personal trips says so in figures`() {
        val printed =
            printed(report(listOf(trip(monday, "08:14")), personal = PersonalDriving(0, 0)))

        assertEquals("0.0", printed.personal.km)
    }

    @Test
    fun `a company or a vehicle that is not set is left off, label and all`() {
        val alone = ReportSender("Sam Driver", company = null, vehicle = null)

        val printed = printed(report(listOf(trip(monday, "08:14")), sender = alone))

        assertEquals(listOf("Name"), printed.fields.map { it.first })
    }

    @Test
    fun `a date range is written out in the heading, the total and the footer`() {
        val range = ReportPeriod.Range(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 18))

        val printed = printed(report(listOf(trip(monday, "08:14")), period = range))

        val words = "October 5, 2026 to October 18, 2026"
        assertEquals(words, printed.period)
        assertEquals("Total business kilometres for $words", printed.totalLabel)
        assertEquals("Sam Driver · $words", printed.footer)
    }

    @Test
    fun `a revision says which one it is and what it replaces`() {
        val revision = ReportRevision(number = 2, replacesSentOn = LocalDate.of(2026, 11, 3))

        val printed = printed(report(listOf(trip(monday, "08:14")), revision = revision))

        assertEquals(
            "Revision 2. It replaces the report sent on November 3, 2026.",
            printed.notes.last(),
        )
        assertEquals(2, printed.notes.size)
    }

    @Test
    fun `a trip is printed with its two times, its two addresses and its kilometres`() {
        val printed = printed(report(listOf(trip(monday, "08:14", minutes = 25))))

        val day = printed.days.single()
        assertEquals("Monday, October 5, 2026", day.heading)
        assertEquals("Monday, October 5, 2026 (continued)", day.continuedHeading)
        assertEquals(
            PrintedRow(
                "08:14",
                "08:39",
                "12 Shop Rd, Edmonton",
                "48 Main St, Leduc",
                "12.3",
                false,
            ),
            day.rows.single(),
        )
        assertEquals("12.3", day.subtotalKm)
        assertEquals("12.3", printed.totalKm)
        assertEquals("Total business kilometres for October 2026", printed.totalLabel)
    }

    @Test
    fun `the times are written the way the phone writes them`() {
        val twelveHour = ReportFormat(Locale.CANADA, twentyFourHour = false)
        val afternoon = report(listOf(trip(monday, "16:05", minutes = 30)))

        val row = printed(afternoon, twelveHour).days.single().rows.single()

        // Whatever marks the half of the day in this language, it is not the 24-hour "16:05".
        assertTrue(row.start, row.start.startsWith("4:05"))
        assertTrue(row.end, row.end.startsWith("4:35"))
        assertEquals("16:05", printed(afternoon).days.single().rows.single().start)
    }

    @Test
    fun `a missing address is said in words, and never as coordinates`() {
        val nowhere = trip(monday, "08:14", from = null, to = null)
        val half = trip(monday, "09:00", to = null)

        val rows = printed(report(listOf(nowhere, half))).days.single().rows

        assertEquals("No address recorded", rows[0].from)
        assertEquals("No address recorded", rows[0].to)
        assertEquals("12 Shop Rd, Edmonton", rows[1].from)
        assertEquals("No address recorded", rows[1].to)
    }

    @Test
    fun `a trip added or edited by hand is marked, and only then is there a legend`() {
        val recorded = trip(monday, "08:00")
        val added = trip(monday, "09:00", mark = ReportMark.ADDED)
        val edited = trip(monday, "10:00", mark = ReportMark.EDITED)

        val marked = printed(report(listOf(recorded, added, edited)))
        val editedOnly = printed(report(listOf(recorded, edited)))
        val plain = printed(report(listOf(recorded)))

        assertEquals(listOf(false, true, true), marked.days.single().rows.map { it.marked })
        val legend = "* This trip was added by hand, or changed by hand after it was recorded."
        assertEquals(legend, marked.legend)
        // There is one legend for both marks, so its words have to be true of a recorded trip
        // that was edited as well: it must not say that a marked trip was not recorded.
        assertEquals(legend, editedOnly.legend)
        assertNull(plain.legend)
    }

    @Test
    fun `a period without trips says so, and its total is zero`() {
        val printed = printed(report(emptyList()))

        assertEquals("No business trips in this period.", printed.emptyNote)
        assertEquals(emptyList<PrintedDay>(), printed.days)
        assertEquals("0.0", printed.totalKm)
        assertNull(printed(report(listOf(trip(monday, "08:00")))).emptyNote)
    }

    @Test
    fun `a trip that storage left without an end has an empty end, not a made-up one`() {
        val endless = trip(monday, "08:00").copy(endedAtMs = null)

        assertEquals("", printed(report(listOf(endless))).days.single().rows.single().end)
    }

    @Test
    fun `the figures follow the language, like the dates`() {
        val german = ReportFormat(Locale.GERMANY, twentyFourHour = true)

        val printed = printed(report(listOf(trip(monday, "08:14"))), german)

        assertEquals("12,3", printed.days.single().rows.single().km)
        assertEquals("12,3", printed.totalKm)
    }
}

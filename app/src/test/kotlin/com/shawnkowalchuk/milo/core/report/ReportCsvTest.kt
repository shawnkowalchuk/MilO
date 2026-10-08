package com.shawnkowalchuk.milo.core.report

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/** The CSV export: one row per trip, readable by a spreadsheet in any language. */
class ReportCsvTest {
    private val monday = LocalDate.of(2026, 10, 5)
    private val tuesday = LocalDate.of(2026, 10, 6)
    private val words =
        CsvWords(
            "Date",
            "Start",
            "End",
            "From",
            "To",
            "Purpose",
            "Vehicle",
            "km",
            "By hand",
            "added",
            "edited",
        )
    private val header = "Date,Start,End,From,To,Purpose,Vehicle,km,By hand"

    private fun lines(vararg trips: ReportTrip): List<String> =
        reportCsv(report(trips.toList()), words).split("\r\n")

    @Test
    fun `there is a row of titles, then one row for each trip, oldest first`() {
        val csv =
            reportCsv(
                report(
                    listOf(
                        trip(tuesday, "13:00", from = "Shop", to = "Site 7", metres = 23_449.0),
                        trip(monday, "08:14", minutes = 25, from = "Shop", to = "Yard"),
                    ),
                ),
                words,
            )

        assertEquals(
            "$header\r\n" +
                "2026-10-05,08:14,08:39,Shop,Yard,,,12.3,\r\n" +
                "2026-10-06,13:00,13:20,Shop,Site 7,,,23.4,\r\n",
            csv,
        )
    }

    @Test
    fun `an address with a comma is put in quotation marks, so it stays one cell`() {
        val row = lines(trip(monday, "08:14"))[1]

        assertEquals(
            "2026-10-05,08:14,08:34,\"12 Shop Rd, Edmonton\",\"48 Main St, Leduc\",,,12.3,",
            row,
        )
    }

    @Test
    fun `a quotation mark inside an address is doubled`() {
        val row = lines(
            trip(monday, "08:14", from = "The \"Old\" Yard", to = "Bay 4, \"B\" side"),
        )[1]

        assertEquals(
            "2026-10-05,08:14,08:34,\"The \"\"Old\"\" Yard\",\"Bay 4, \"\"B\"\" side\",,,12.3,",
            row,
        )
    }

    @Test
    fun `a line break inside an address stays inside its cell`() {
        assertEquals("\"Unit 4\nShop Rd\"", csvCell("Unit 4\nShop Rd"))
        assertEquals("\"Unit 4\r\nShop Rd\"", csvCell("Unit 4\r\nShop Rd"))
    }

    @Test
    fun `a plain cell is written as it is`() {
        assertEquals("Shop", csvCell("Shop"))
        assertEquals("Site 7", csvCell("Site 7"))
        assertEquals("", csvCell(""))
        assertEquals("L'Anse au Clair", csvCell("L'Anse au Clair"))
    }

    @Test
    fun `a cell that would be run as a formula is made plain text`() {
        assertEquals("'=SUM(A1:A9)", csvCell("=SUM(A1:A9)"))
        assertEquals("'+1 Shop Rd", csvCell("+1 Shop Rd"))
        assertEquals("'-5 Yard", csvCell("-5 Yard"))
        assertEquals("'@home", csvCell("@home"))
        // And still quoted where it holds a comma.
        assertEquals("\"'=A1, B2\"", csvCell("=A1, B2"))
        // A sign further in is no formula.
        assertEquals("5-7 Shop Rd", csvCell("5-7 Shop Rd"))
    }

    @Test
    fun `the last column says whether a trip was added or edited by hand`() {
        val rows =
            lines(
                trip(monday, "08:00", from = "A", to = "B"),
                trip(monday, "09:00", from = "A", to = "B", mark = ReportMark.ADDED),
                trip(monday, "10:00", from = "A", to = "B", mark = ReportMark.EDITED),
            )

        assertEquals("2026-10-05,08:00,08:20,A,B,,,12.3,", rows[1])
        assertEquals("2026-10-05,09:00,09:20,A,B,,,12.3,added", rows[2])
        assertEquals("2026-10-05,10:00,10:20,A,B,,,12.3,edited", rows[3])
    }

    @Test
    fun `a trip's label and its vehicle have columns of their own`() {
        val labelled =
            trip(
                monday,
                "08:00",
                from = "A",
                to = "B",
            ).copy(label = "Work, site 4", vehicle = "Van")

        assertEquals("2026-10-05,08:00,08:20,A,B,\"Work, site 4\",Van,12.3,", lines(labelled)[1])
    }

    @Test
    fun `a missing address is an empty cell, and a missing end an empty time`() {
        val nowhere = trip(monday, "08:00", from = null, to = null).copy(endedAtMs = null)

        assertEquals("2026-10-05,08:00,,,,,,12.3,", lines(nowhere)[1])
    }

    @Test
    fun `the kilometres are the figures the PDF prints, with a dot`() {
        val rows =
            lines(
                trip(monday, "08:00", from = "A", to = "B", metres = 1_040.0),
                trip(monday, "09:00", from = "A", to = "B", metres = 1_250.0),
                trip(monday, "10:00", from = "A", to = "B", metres = 0.0),
            )

        assertEquals(listOf("1.0", "1.3", "0.0"), rows.subList(1, 4).map { it.split(",")[7] })
    }

    @Test
    fun `a trip past midnight has its start's date, and the time it ended`() {
        val late = trip(monday, "23:40", minutes = 45, from = "A", to = "B")

        assertEquals("2026-10-05,23:40,00:25,A,B,,,12.3,", lines(late)[1])
    }

    @Test
    fun `a period without trips is the row of titles alone`() {
        assertEquals("$header\r\n", reportCsv(report(emptyList()), words))
    }

    @Test
    fun `every line ends the way the CSV standard says, the last one too`() {
        val csv = reportCsv(report(listOf(trip(monday, "08:00"), trip(tuesday, "08:00"))), words)

        assertEquals(3, csv.split("\r\n").count { it.isNotEmpty() })
        assertEquals(true, csv.endsWith("\r\n"))
        assertEquals(false, csv.replace("\r\n", "").contains('\n'))
    }
}

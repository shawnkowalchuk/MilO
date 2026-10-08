package com.shawnkowalchuk.milo.core.report

import com.shawnkowalchuk.milo.core.odometer.OdometerFigure
import com.shawnkowalchuk.milo.core.odometer.OdometerReading
import com.shawnkowalchuk.milo.core.odometer.OdometerSpan
import com.shawnkowalchuk.milo.core.util.DistanceUnit
import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The report for the accountant with miles chosen in Settings (Shawn's answer of 2026-10-07:
 * "Follow the setting"). Its distances and totals are in miles, the unit is named in every
 * heading, and the rows still add up to the totals printed under them: each trip is rounded
 * once to a tenth of a mile, and every total is the sum of those figures.
 *
 * The same trips in kilometres are what they always were: `ReportTextTest`, `ReportCsvTest`
 * and `MileageReportTest` hold that, unchanged.
 */
class ReportInMilesTest {
    private val monday = LocalDate.of(2026, 10, 5)
    private val tuesday = LocalDate.of(2026, 10, 6)
    private val miles = DistanceUnit.MILES

    /** The words as `ReportTexts` hands them in for miles: the unit's words, and nothing else. */
    private val milesWords =
        WORDS.copy(
            businessOnly = "Business trips only. Distances are in miles.",
            columnKm = "mi",
            total = "Total business miles for %1\$s",
            odometerKm = "%1\$s mi",
            odometerEstimated = "%1\$s mi est.",
        )

    /** Distances that round awkwardly: 0.3 km, 12.35 km, 100.05 km, and 0.35 mi typed in miles. */
    private val trips =
        listOf(
            trip(monday, "08:00", metres = 300.0),
            trip(monday, "09:00", metres = 12_350.0),
            trip(monday, "13:00", metres = 100_050.0),
            trip(tuesday, "08:00", metres = 23_449.0),
            trip(tuesday, "09:00", metres = 563.2704),
            trip(tuesday, "10:00", metres = 1_040.0),
            trip(tuesday, "11:00", metres = 1_040.0),
            trip(tuesday, "12:00", metres = 1_040.0),
        )

    private fun inMiles(personal: PersonalDriving = PERSONAL) =
        report(trips, personal = personal, unit = miles)

    @Test
    fun `every trip is printed in miles, rounded once to a tenth of a mile`() {
        val printed = printedReport(inMiles(), milesWords, FORMAT)

        assertEquals(
            listOf(listOf("0.2", "7.7", "62.2"), listOf("14.6", "0.4", "0.6", "0.6", "0.6")),
            printed.days.map { day -> day.rows.map { it.km } },
        )
    }

    @Test
    fun `the rows add up to each day's subtotal, and the subtotals to the total`() {
        val printed = printedReport(inMiles(), milesWords, FORMAT)

        for (day in printed.days) {
            val byHand = day.rows.sumOf { it.km.toBigDecimal() }

            assertEquals(day.heading, byHand.toPlainString(), day.subtotalKm)
        }
        assertEquals(listOf("70.1", "16.8"), printed.days.map { it.subtotalKm })
        assertEquals("86.9", printed.totalKm)
        assertEquals(
            printed.totalKm,
            printed.days.sumOf { it.subtotalKm.toBigDecimal() }.toPlainString(),
        )
        // The figure at the top is the total the report ends with.
        assertEquals(printed.totalKm, printed.business.km)
        assertEquals(869L, inMiles().totalTenths)
    }

    @Test
    fun `the unit is named wherever a figure stands under a heading`() {
        val printed = printedReport(inMiles(), milesWords, FORMAT)

        assertEquals("Total business miles for October 2026", printed.totalLabel)
        assertEquals("Business trips only. Distances are in miles.", printed.notes.first())
        // The column's title, which also stands after the two figures at the top.
        assertEquals("mi", printed.words.columnKm)
        // Nothing the report prints says kilometres.
        val everyWord = printed.notes + printed.totalLabel + printed.words.columnKm
        assertFalse(everyWord.any { it.contains("km") || it.contains("kilomet") })
    }

    @Test
    fun `the same trips in kilometres print the figures they always printed`() {
        val printed = printedReport(report(trips), WORDS, FORMAT)

        assertEquals(
            listOf(listOf("0.3", "12.4", "100.1"), listOf("23.4", "0.6", "1.0", "1.0", "1.0")),
            printed.days.map { day -> day.rows.map { it.km } },
        )
        assertEquals(listOf("112.8", "27.0"), printed.days.map { it.subtotalKm })
        assertEquals("139.8", printed.totalKm)
        assertEquals("Total business kilometres for October 2026", printed.totalLabel)
        assertEquals(DistanceUnit.KILOMETRES, report(trips).unit)
    }

    @Test
    fun `the Personal figure at the top is the one it was handed, in the report's unit`() {
        val printed = printedReport(inMiles(PersonalDriving(2, tenths = 113)), milesWords, FORMAT)

        assertEquals(PrintedTally("Personal", "11.3", "2 personal trips"), printed.personal)
    }

    @Test
    fun `the odometer is printed in miles, an estimate marked`() {
        val reading = OdometerReading(edmonton("2026-10-01T07:00"), 74_500, miles)
        val start = OdometerFigure(74_500, miles, reading, drivenTenths = 0, estimated = false)
        val end = OdometerFigure(74_587, miles, reading, drivenTenths = 869, estimated = true)

        val printed =
            printedReport(inMiles().copy(odometer = OdometerSpan(start, end)), milesWords, FORMAT)

        assertEquals(listOf("74,500 mi", "74,587 mi est."), printed.odometer.map { it.second })
    }

    // ---- The CSV ----------------------------------------------------------------------------------

    private val csvWords =
        CsvWords("Date", "Start", "End", "From", "To", "mi", "By hand", "added", "edited")

    @Test
    fun `the CSV names miles in its column title and writes them with a dot`() {
        val lines = reportCsv(inMiles(), csvWords).split("\r\n")

        assertEquals("Date,Start,End,From,To,mi,By hand", lines.first())
        assertEquals(
            listOf("0.2", "7.7", "62.2", "14.6", "0.4", "0.6", "0.6", "0.6"),
            lines.drop(1).dropLast(1).map { it.split(",").dropLast(1).last() },
        )
        // A dot whatever the phone's language is: a spreadsheet must read the column as numbers.
        assertTrue(lines.drop(1).none { it.contains("0,2") || it.contains("62,2") })
    }

    @Test
    fun `the CSV's column adds up to the total the PDF prints, in miles as in kilometres`() {
        for (unit in DistanceUnit.entries) {
            val report = report(trips, unit = unit)
            val words = if (unit == miles) milesWords else WORDS
            val column =
                reportCsv(report, csvWords)
                    .split("\r\n")
                    .drop(1)
                    .dropLast(1)
                    .sumOf { it.split(",").dropLast(1).last().toBigDecimal() }

            assertEquals(
                "$unit",
                printedReport(report, words, FORMAT).totalKm,
                column.toPlainString(),
            )
        }
    }

    @Test
    fun `the figures of a report in miles follow the language on the PDF, like the dates`() {
        val german = ReportFormat(Locale.GERMANY, twentyFourHour = true)

        val printed = printedReport(inMiles(), milesWords, german)

        assertEquals("86,9", printed.totalKm)
        assertEquals("62,2", printed.days.first().rows.last().km)
    }
}

package com.shawnkowalchuk.milo.feature.report

import com.shawnkowalchuk.milo.core.report.ReportPeriod
import com.shawnkowalchuk.milo.core.report.ReportRevision
import com.shawnkowalchuk.milo.core.report.ReportTrip
import com.shawnkowalchuk.milo.core.report.reportFileName
import com.shawnkowalchuk.milo.data.report.ReportSelection
import com.shawnkowalchuk.milo.data.report.SentReport
import com.shawnkowalchuk.milo.data.report.SentReportKind
import com.shawnkowalchuk.milo.data.settings.MiloSettings
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Which report a press would make, from what is stored at that moment. */
class ReportSourcesTest {
    private val zone = ZoneId.of("America/Edmonton")
    private val today = LocalDate.of(2026, 10, 6)
    private val september = YearMonth.of(2026, 9)
    private val trip = ReportTrip(1_789_000_000_000, 1_789_001_200_000, "Shop", "Site 7", 12_340.0)
    private val selection = ReportSelection(listOf(trip), 0, 0, 0, tripInProgress = false)
    private val named = MiloSettings(reportName = "Sam Driver", reportCompany = "Northside")

    private fun sources(
        settings: MiloSettings? = named,
        selection: ReportSelection? = this.selection,
        choice: ReportChoice = openingChoice(september, today),
        sent: List<SentReport> = emptyList(),
    ) = ReportSources(ReportChosen(choice, today, zone), selection, settings, sent)

    @Test
    fun `the report is of the period on screen, made today, in the phone's zone`() {
        val report = sources().reportFor(ReportNeed.PDF)

        assertEquals(ReportPeriod.Month(september), report?.period)
        assertEquals(today, report?.generatedOn)
        assertEquals(zone, report?.zone)
        assertEquals("Sam Driver", report?.sender?.name)
        assertEquals("Northside", report?.sender?.company)
        assertEquals(1, report?.tripCount)
    }

    @Test
    fun `a date range on screen makes a report of that range`() {
        val range =
            openingChoice(september, today)
                .copy(kind = PeriodKind.RANGE)
                .withRangeLast(LocalDate.of(2026, 9, 4), today)

        val report = sources(choice = range).reportFor(ReportNeed.SENDING)

        assertEquals(
            ReportPeriod.Range(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 4)),
            report?.period,
        )
    }

    @Test
    fun `without a name there is no PDF to make, to look at or to send`() {
        val unnamed = sources(settings = MiloSettings())

        assertNull(unnamed.reportFor(ReportNeed.PDF))
        assertNull(unnamed.reportFor(ReportNeed.SENDING))
    }

    @Test
    fun `a CSV is made without a name, and with it once there is one`() {
        val unnamed = sources(settings = MiloSettings()).reportFor(ReportNeed.NOTHING)
        val withName = sources().reportFor(ReportNeed.NOTHING)

        assertEquals("", unnamed?.sender?.name)
        assertEquals(1, unnamed?.tripCount)
        assertEquals("Sam Driver", withName?.sender?.name)
    }

    @Test
    fun `a CSV of a period that was sent before is no revision, and the PDF of it is`() {
        val sentOn = LocalDate.of(2026, 10, 2)
        val septemberSent =
            SentReport(
                id = 1,
                kind = SentReportKind.MONTH,
                firstDay = september.atDay(1).toEpochDay(),
                lastDay = september.atEndOfMonth().toEpochDay(),
                sentAtMs = sentOn.atTime(10, 0).atZone(zone).toInstant().toEpochMilli(),
                tripCount = 1,
                distanceMetres = 12_300.0,
                revision = 0,
            )
        val afterSending = sources(sent = listOf(septemberSent))

        val csv = requireNotNull(afterSending.reportFor(ReportNeed.NOTHING))
        val pdf = afterSending.reportFor(ReportNeed.PDF)
        val toSend = afterSending.reportFor(ReportNeed.SENDING)

        // The CSV's file is named like the report that was sent, not like one still to come.
        assertNull(csv.revision)
        val csvRevision = csv.revision?.number ?: 0
        assertEquals(
            "Mileage-2026-09-Sam-Driver.csv",
            reportFileName(csv.period, csv.sender.name, csvRevision, "Mileage", "csv"),
        )
        assertEquals(ReportRevision(1, sentOn), pdf?.revision)
        assertEquals(ReportRevision(1, sentOn), toSend?.revision)
        // The same trips either way.
        assertEquals(pdf?.days, csv.days)
    }

    @Test
    fun `nothing is made while the trips are being read, or the settings cannot be`() {
        for (need in ReportNeed.entries) {
            assertNull(sources(selection = null).reportFor(need))
            assertNull(sources(settings = null).reportFor(need))
        }
    }
}

package com.shawnkowalchuk.milo.feature.report

import com.shawnkowalchuk.milo.core.report.ReportPeriod
import com.shawnkowalchuk.milo.core.report.ReportTrip
import com.shawnkowalchuk.milo.data.report.ReportSelection
import com.shawnkowalchuk.milo.data.report.SentEffect
import com.shawnkowalchuk.milo.data.report.SentReport
import com.shawnkowalchuk.milo.data.report.kind
import com.shawnkowalchuk.milo.data.settings.MiloSettings
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the tiles of the Report screen say since it was laid out as the owner's design draws
 * it: the status and its line about the reminder, who the report is from, the PDF's name, and
 * what "Mark as sent" would do.
 */
class ReportTilesTest {
    private val zone = ZoneId.of("America/Edmonton")

    /** The reminder's day is the 1st unless a test says otherwise, so October's has come. */
    private val today = LocalDate.of(2026, 10, 7)
    private val september = YearMonth.of(2026, 9)
    private val lastMonth = ReportPeriod.Month(september)
    private val thisMonth = ReportPeriod.Month(YearMonth.of(2026, 10))
    private val range = ReportPeriod.Range(LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 18))
    private val settings =
        MiloSettings(
            reportName = "Sam Driver",
            reportCompany = "Northside Electric Ltd.",
            reportVehicle = "Ford F-150",
            accountantEmail = "accounts@example.ca",
        )

    private fun at(text: String): Long =
        LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()

    private fun sent(period: ReportPeriod, revision: Int, sentAt: String, id: Long = 1) =
        SentReport(
            id = id,
            kind = period.kind,
            firstDay = period.firstDay.toEpochDay(),
            lastDay = period.lastDay.toEpochDay(),
            sentAtMs = at(sentAt),
            tripCount = 21,
            distanceMetres = 231_400.0,
            revision = revision,
        )

    private fun status(
        period: ReportPeriod = lastMonth,
        on: LocalDate = today,
        businessTrips: Int? = 21,
        stored: MiloSettings = settings,
        sent: List<SentReport> = emptyList(),
    ) = reportStatus(period, on, zone, businessTrips, stored, sent)

    @Test
    fun `last month's report is not sent, and the reminder is running for it`() {
        assertEquals(ReportStatus.NotSent(reminding = true), status())
    }

    @Test
    fun `the reminder is not said to run while one of its own conditions fails`() {
        val quiet = ReportStatus.NotSent(reminding = false)

        // Switched off in Settings.
        assertEquals(quiet, status(stored = settings.copy(reminderEnabled = false)))
        // This month's reminder day has not come: it is the 10th, and today is the 7th.
        assertEquals(quiet, status(stored = settings.copy(reminderDay = 10)))
        assertEquals(
            ReportStatus.NotSent(reminding = true),
            status(stored = settings.copy(reminderDay = 7)),
        )
        // Nothing to send: the month has no Business trip.
        assertEquals(quiet, status(businessTrips = 0))
        // Not known yet: the trips are still being read, so nothing is claimed.
        assertEquals(quiet, status(businessTrips = null))
    }

    @Test
    fun `the reminder is about last month only`() {
        val quiet = ReportStatus.NotSent(reminding = false)

        // The month that is still running, and a month further back, are never reminded of.
        assertEquals(quiet, status(period = thisMonth))
        assertEquals(quiet, status(period = ReportPeriod.Month(YearMonth.of(2026, 8))))
        // On 1 January, last month is December of the year before.
        val december = ReportPeriod.Month(YearMonth.of(2026, 12))
        assertEquals(
            ReportStatus.NotSent(reminding = true),
            status(period = december, on = LocalDate.of(2027, 1, 1)),
        )
    }

    @Test
    fun `a month that was sent says so, with its first and its newest report`() {
        val first = sent(lastMonth, 0, "2026-10-02T09:30", id = 1)
        val again = sent(lastMonth, 1, "2026-10-05T09:30", id = 2)

        val once = status(sent = listOf(first)) as ReportStatus.MonthSent
        val twice = status(sent = listOf(again, first)) as ReportStatus.MonthSent

        assertEquals(first, once.submission.first)
        assertFalse(once.submission.sentAgain)
        assertEquals(first, twice.submission.first)
        assertEquals(again, twice.submission.latest)
        assertTrue(twice.submission.sentAgain)
    }

    @Test
    fun `a date range is never reminded of, and a sent one names its newest report`() {
        assertEquals(ReportStatus.NotSent(reminding = false), status(period = range))

        val first = sent(range, 0, "2026-09-20T09:30", id = 1)
        val again = sent(range, 1, "2026-09-25T09:30", id = 2).copy(tripCount = 6)
        assertEquals(
            ReportStatus.RangeSent(at("2026-09-25T09:30"), tripCount = 6),
            status(period = range, sent = listOf(again, first)),
        )
        // A range that covers other days was not sent, and the month's own report is not it.
        val other = ReportPeriod.Range(LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 19))
        val reports = listOf(first, sent(lastMonth, 0, "2026-10-02T09:30", id = 3))
        assertEquals(
            ReportStatus.NotSent(reminding = false),
            status(period = other, sent = reports),
        )
    }

    @Test
    fun `a range sent for a month's days does not stop the month's reminder`() {
        val wholeMonth = ReportPeriod.Range(september.atDay(1), september.atEndOfMonth())

        val reports = listOf(sent(wholeMonth, 0, "2026-10-02T09:30"))

        assertEquals(ReportStatus.NotSent(reminding = true), status(sent = reports))
    }

    private val selection =
        ReportSelection(
            trips = listOf(ReportTrip(at("2026-09-01T08:00"), null, "Shop", "Site", 23_100.0)),
            personalLeftOut = 0,
            unsortedLeftOut = 0,
            withoutAddress = 0,
            tripInProgress = false,
            personalTenths = 0,
        )

    private fun state(
        choice: ReportChoice = openingChoice(september, today),
        stored: MiloSettings = settings,
        sent: List<SentReport> = emptyList(),
        passing: ReportPassing = ReportPassing(),
    ) = reportUiState(choice, today, zone, selection, stored, sent, passing)

    @Test
    fun `the screen's status is the one the list and the reminder give`() {
        assertEquals(ReportStatus.NotSent(reminding = true), state().status)
        assertEquals(
            ReportStatus.NotSent(reminding = false),
            state(stored = settings.copy(reminderEnabled = false)).status,
        )
        val asRange = openingChoice(september, today).copy(kind = PeriodKind.RANGE)
        assertEquals(ReportStatus.NotSent(reminding = false), state(choice = asRange).status)
    }

    @Test
    fun `the sender is what the settings hold, with nothing made up for what is not set`() {
        assertEquals(
            SenderDetails("Sam Driver", "Northside Electric Ltd.", "Ford F-150"),
            state().sender,
        )
        assertEquals(SenderDetails(null, null, null), state(stored = MiloSettings()).sender)
    }

    @Test
    fun `the PDF's name is carried to the screen, and is absent while no PDF can be made`() {
        assertNull(state().pdfName)
        assertEquals(
            "Mileage-2026-09-Sam-Driver.pdf",
            state(passing = ReportPassing(pdfName = "Mileage-2026-09-Sam-Driver.pdf")).pdfName,
        )
    }

    @Test
    fun `marking as sent would do what I sent it does for the period on screen`() {
        val first = sent(lastMonth, 0, "2026-10-02T09:30")
        val asRange = openingChoice(september, today).copy(kind = PeriodKind.RANGE)

        assertEquals(SentEffect.MarksMonth, state().ifSent)
        assertEquals(
            SentEffect.RevisesMonth(revision = 1, firstSentAtMs = at("2026-10-02T09:30")),
            state(sent = listOf(first)).ifSent,
        )
        assertEquals(SentEffect.ListsRange, state(choice = asRange).ifSent)
    }

    @Test
    fun `a file name may be broken after each hyphen, and keeps every letter`() {
        val name = "Mileage-2026-09-Sam-Driver.pdf"

        val drawn = breakableAfterHyphens(name)

        assertEquals(name, drawn.replace("\u200B", ""))
        assertEquals(4, drawn.count { it == '\u200B' })
        assertTrue(drawn.contains("Sam-\u200BDriver.pdf"))
        assertEquals("Report.pdf", breakableAfterHyphens("Report.pdf"))
    }
}

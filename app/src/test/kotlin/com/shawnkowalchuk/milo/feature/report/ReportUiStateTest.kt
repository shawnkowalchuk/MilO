package com.shawnkowalchuk.milo.feature.report

import com.shawnkowalchuk.milo.core.report.PersonalDriving
import com.shawnkowalchuk.milo.core.report.ReportMark
import com.shawnkowalchuk.milo.core.report.ReportPeriod
import com.shawnkowalchuk.milo.core.report.ReportRevision
import com.shawnkowalchuk.milo.core.report.ReportSender
import com.shawnkowalchuk.milo.core.report.ReportTrip
import com.shawnkowalchuk.milo.data.report.ReportSelection
import com.shawnkowalchuk.milo.data.report.SentEffect
import com.shawnkowalchuk.milo.data.report.SentReport
import com.shawnkowalchuk.milo.data.report.kind
import com.shawnkowalchuk.milo.data.settings.MiloSettings
import com.shawnkowalchuk.milo.data.settings.MissingDetail
import com.shawnkowalchuk.milo.data.settings.ReportHandOver
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** What the Report screen shows, and the report it would make, from what is stored. */
class ReportUiStateTest {
    private val zone = ZoneId.of("America/Edmonton")
    private val today = LocalDate.of(2026, 10, 6)
    private val october = ReportPeriod.Month(YearMonth.of(2026, 10))
    private val range = ReportPeriod.Range(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 3))
    private val settings =
        MiloSettings(
            reportName = "Sam Driver",
            reportCompany = "Northside Electric Ltd.",
            reportVehicle = "Ford F-150",
            accountantEmail = "accounts@example.ca",
        )

    private fun at(text: String): Long =
        LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()

    private fun trip(start: String, metres: Double, mark: ReportMark? = null): ReportTrip {
        val startedAtMs = at(start)
        return ReportTrip(startedAtMs, startedAtMs + 1_200_000, "Shop", "Site 7", metres, mark)
    }

    private val selection =
        ReportSelection(
            trips =
                listOf(
                    trip("2026-10-01T08:00", 1_040.0),
                    trip("2026-10-01T09:00", 1_040.0, ReportMark.EDITED),
                    trip("2026-10-02T08:00", 23_449.0, ReportMark.ADDED),
                ),
            personalLeftOut = 2,
            unsortedLeftOut = 1,
            withoutAddress = 1,
            tripInProgress = true,
            personalTenths = 341,
        )

    private fun sent(period: ReportPeriod, revision: Int, sentAt: String, id: Long = 1) =
        SentReport(
            id = id,
            kind = period.kind,
            firstDay = period.firstDay.toEpochDay(),
            lastDay = period.lastDay.toEpochDay(),
            sentAtMs = at(sentAt),
            tripCount = 31,
            distanceMetres = 412_300.0,
            revision = revision,
        )

    private fun state(
        choice: ReportChoice = openingChoice(YearMonth.of(2026, 10), today),
        selection: ReportSelection? = this.selection,
        settings: MiloSettings = this.settings,
        sent: List<SentReport> = emptyList(),
        passing: ReportPassing = ReportPassing(),
    ) = reportUiState(choice, today, zone, selection, settings, sent, passing)

    @Test
    fun `the summary adds up the figures the report prints`() {
        val summary = summaryOf(selection)

        // 1.0 + 1.0 + 23.4, where the metres would round to 25.5.
        assertEquals(254, summary.tenths)
        assertEquals(3, summary.tripCount)
        assertEquals(2, summary.markedCount)
        assertEquals(1, summary.withoutAddress)
        assertEquals(2, summary.personalLeftOut)
        assertEquals(1, summary.unsortedLeftOut)
        assertEquals(true, summary.tripInProgress)
    }

    @Test
    fun `the summary is what the report made of the same trips holds`() {
        val report =
            mileageReport(october, selection, "Sam Driver", settings, emptyList(), today, zone)
        val summary = summaryOf(selection)

        assertEquals(report.totalTenths, summary.tenths)
        assertEquals(report.tripCount, summary.tripCount)
        assertEquals(report.markedCount, summary.markedCount)
    }

    @Test
    fun `the report carries the settings, the period, the day it is made and its days`() {
        val report =
            mileageReport(october, selection, "Sam Driver", settings, emptyList(), today, zone)

        assertEquals(
            ReportSender("Sam Driver", "Northside Electric Ltd.", "Ford F-150"),
            report.sender,
        )
        assertEquals(october, report.period)
        assertEquals(today, report.generatedOn)
        assertEquals(zone, report.zone)
        assertNull(report.revision)
        val dates = report.days.map { it.date }
        assertEquals(listOf(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 2)), dates)
        assertEquals(listOf(20L, 234L), report.days.map { it.tenths })
        // The Personal trips are not listed; their count and kilometres are carried along.
        assertEquals(PersonalDriving(tripCount = 2, tenths = 341), report.personal)
    }

    @Test
    fun `a report for a period that was sent before is the next revision of it`() {
        val before =
            listOf(
                sent(october, 1, "2026-11-09T10:00", id = 3),
                sent(range, 0, "2026-11-05T10:00", id = 2),
                sent(october, 0, "2026-11-02T23:30", id = 1),
            )

        val report = mileageReport(october, selection, "Sam Driver", settings, before, today, zone)
        val first =
            mileageReport(october, selection, "Sam Driver", settings, before.drop(1), today, zone)
        val other =
            mileageReport(
                ReportPeriod.Month(YearMonth.of(2026, 9)),
                selection,
                "Sam Driver",
                settings,
                before,
                today,
                zone,
            )

        // It replaces the newest report before it, named by the day that one was sent, in the
        // phone's time zone.
        assertEquals(ReportRevision(2, LocalDate.of(2026, 11, 9)), report.revision)
        assertEquals(ReportRevision(1, LocalDate.of(2026, 11, 2)), first.revision)
        assertNull(other.revision)
    }

    @Test
    fun `sending a period again asks first, and says what was sent and which revision is next`() {
        val before =
            listOf(sent(october, 1, "2026-11-09T10:00", 3), sent(october, 0, "2026-11-02T10:00", 1))

        val resend = resendOf(october, before)

        assertEquals(2, resend?.revision)
        assertEquals(3L, resend?.last?.id)
        assertEquals(at("2026-11-09T10:00"), resend?.last?.sentAtMs)
        assertEquals(31, resend?.last?.tripCount)
        assertNull(resendOf(range, before))
        assertNull(resendOf(october, emptyList()))
    }

    @Test
    fun `a date range that was sent before asks too, and the month of the same days does not`() {
        val wholeMonthAsRange = ReportPeriod.Range(october.firstDay, october.lastDay)
        val before = listOf(sent(wholeMonthAsRange, 0, "2026-11-02T10:00"))

        assertEquals(1, resendOf(wholeMonthAsRange, before)?.revision)
        assertNull(resendOf(october, before))
    }

    @Test
    fun `the screen says the month is submitted only for a report of the whole month`() {
        val rangeOnly = state(sent = listOf(sent(range, 0, "2026-11-02T10:00")))
        val month = state(sent = listOf(sent(october, 0, "2026-11-02T10:00")))

        assertNull(rangeOnly.submission)
        assertEquals(at("2026-11-02T10:00"), month.submission?.first?.sentAtMs)
        assertEquals(0, month.submission?.revisions)
        // Both are in the list of sent reports.
        assertEquals(1, rangeOnly.sent.size)
        assertEquals(range, rangeOnly.sent.single().period)
    }

    @Test
    fun `what is missing for sending is said before any button is pressed`() {
        assertEquals(emptyList<MissingDetail>(), state().missing)
        assertEquals(
            listOf(MissingDetail.NAME, MissingDetail.ACCOUNTANT_EMAIL),
            state(settings = MiloSettings()).missing,
        )
        assertEquals(
            listOf(MissingDetail.ACCOUNTANT_EMAIL),
            state(settings = settings.copy(accountantEmail = null)).missing,
        )
        assertNull(state(settings = settings.copy(accountantEmail = null)).accountantEmail)
    }

    @Test
    fun `each press needs only what it uses`() {
        val unnamed = settings.copy(reportName = null)
        val unaddressed = settings.copy(accountantEmail = null)

        // A CSV prints no name and goes wherever it is shared.
        assertEquals(emptyList<MissingDetail>(), ReportNeed.NOTHING.missingIn(MiloSettings()))
        // A PDF to look at needs the name it prints, and not the address.
        assertEquals(listOf(MissingDetail.NAME), ReportNeed.PDF.missingIn(unnamed))
        assertEquals(emptyList<MissingDetail>(), ReportNeed.PDF.missingIn(unaddressed))
        // Sending needs both.
        assertEquals(
            listOf(MissingDetail.ACCOUNTANT_EMAIL),
            ReportNeed.SENDING.missingIn(unaddressed),
        )
        assertEquals(listOf(MissingDetail.NAME), ReportNeed.SENDING.missingIn(unnamed))
    }

    @Test
    fun `while the trips are being read there is no summary, and nothing made up`() {
        val reading = state(selection = null)

        assertNull(reading.summary)
        assertEquals(openingChoice(YearMonth.of(2026, 10), today), reading.choice)
    }

    @Test
    fun `the screen carries what a press left behind, and the stepping of the month`() {
        val passing =
            ReportPassing(
                working = true,
                pdfPages = 3,
                problem = ReportProblem.NO_EMAIL_APP,
                refusals = 2,
            )
        val september = openingChoice(YearMonth.of(2026, 9), today)

        val now = state(passing = passing)
        val earlier = state(choice = september)

        assertEquals(true, now.working)
        assertEquals(3, now.pdfPages)
        assertEquals(ReportProblem.NO_EMAIL_APP, now.problem)
        assertEquals(2, now.refusals)
        assertEquals(false, now.canStepForward)
        assertEquals(true, earlier.canStepForward)
        assertEquals(today, now.today)
    }

    @Test
    fun `the list of sent reports keeps the order storage hands it in, newest first`() {
        val before =
            listOf(
                sent(october, 1, "2026-11-09T10:00", id = 3),
                sent(range, 0, "2026-11-05T10:00", id = 2),
                sent(october, 0, "2026-11-02T10:00", id = 1),
            )

        val lines = state(sent = before).sent

        assertEquals(listOf(3L, 2L, 1L), lines.map { it.id })
        assertEquals(listOf(1, 0, 0), lines.map { it.revision })
        assertEquals(listOf(october, range, october), lines.map { it.period })
        assertEquals(412_300.0, lines.first().distanceMetres, 0.0)
    }

    @Test
    fun `a report handed to the email app is still owed its answer, whatever is chosen`() {
        val handOver = ReportHandOver(range, tripCount = 4, tenths = 268, at("2026-10-04T17:05"))
        val waiting = settings.copy(reportHandOver = handOver)
        val august = openingChoice(YearMonth.of(2026, 8), today)

        // The question is about the report that was handed over, not the period on screen.
        assertEquals(handOver, state(settings = waiting).awaiting)
        assertEquals(handOver, state(choice = august, settings = waiting).awaiting)
        assertNull(state().awaiting)
        // Nothing is submitted, and nothing is in the list, until it is answered.
        assertNull(state(settings = waiting).submission)
        assertEquals(emptyList<SentLine>(), state(settings = waiting).sent)
    }

    @Test
    fun `the question says what I sent it would do for the report that was handed over`() {
        val september = ReportPeriod.Month(YearMonth.of(2026, 9))
        val handOver = ReportHandOver(september, 4, tenths = 268, at("2026-10-06T09:00"))
        val waiting = settings.copy(reportHandOver = handOver)
        val before = listOf(sent(september, 0, "2026-10-02T10:00"))

        // It is about the month that was handed over, while October is on screen.
        assertEquals(SentEffect.MarksMonth, state(settings = waiting).awaitingEffect)
        // A month that was sent before is not promised a new date: it keeps its first one.
        assertEquals(
            SentEffect.RevisesMonth(revision = 1, firstSentAtMs = at("2026-10-02T10:00")),
            state(settings = waiting, sent = before).awaitingEffect,
        )
        assertNull(state(sent = before).awaitingEffect)
    }
}

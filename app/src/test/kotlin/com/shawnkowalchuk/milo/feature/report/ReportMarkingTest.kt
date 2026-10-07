package com.shawnkowalchuk.milo.feature.report

import com.shawnkowalchuk.milo.core.report.ReportPeriod
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.report.FakeSentReportDao
import com.shawnkowalchuk.milo.data.report.SentReport
import com.shawnkowalchuk.milo.data.report.SentReportKind
import com.shawnkowalchuk.milo.data.report.SentReportRepository
import com.shawnkowalchuk.milo.data.report.monthSubmission
import com.shawnkowalchuk.milo.data.settings.ReportHandOver
import com.shawnkowalchuk.milo.data.settings.SettingsStore
import com.shawnkowalchuk.milo.platform.trip.FakeEventLogDao
import com.shawnkowalchuk.milo.platform.trip.FakeSettingsFile
import java.io.IOException
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * What "Mark as sent" records, against the stand-in table: the same row "I sent it" writes,
 * as sent at the moment of the press, and a line in the event log that says it was marked by
 * hand and not answered after the email app.
 */
class ReportMarkingTest {
    private val dao = FakeSentReportDao()
    private val log = FakeEventLogDao()
    private val nowMs = 1_791_300_000_000L
    private val settings = SettingsStore(FakeSettingsFile())
    private val records =
        ReportRecords(SentReportRepository(dao), settings, EventLogRepository(log)) { nowMs }
    private val september = YearMonth.of(2026, 9)
    private val month = ReportPeriod.Month(september)
    private val range = ReportPeriod.Range(LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 18))

    private fun logged(category: EventCategory): List<String> =
        log.entries.filter { it.category == category }.map { it.message }

    @Test
    fun `a month marked by hand is submitted from that moment, with the figures on screen`() =
        runTest {
            val stored = records.markedSent(month, tripCount = 21, tenths = 2314)

            val row = dao.rows.single()
            assertEquals(row, stored)
            assertEquals(SentReportKind.MONTH, row.kind)
            assertEquals(nowMs, row.sentAtMs)
            assertEquals(21, row.tripCount)
            assertEquals(231_400.0, row.distanceMetres, 0.0)
            assertEquals(0, row.revision)
            assertEquals(row, monthSubmission(september, dao.rows)?.first)
            assertEquals(
                listOf(
                    "Report for 2026-09 marked as sent by hand on the Report screen, without " +
                        "the email app: the first report for this period, 21 trips, 231.4 km. " +
                        "The month is now marked as submitted",
                ),
                logged(EventCategory.REPORT),
            )
        }

    @Test
    fun `marking a month that was sent before records a revision, and its first day stays`() =
        runTest {
            val handedOverAtMs = nowMs - 3 * 24 * 60 * 60_000L
            val first = records.answeredSent(ReportHandOver(month, 20, 2200, handedOverAtMs))

            val second = records.markedSent(month, tripCount = 21, tenths = 2314)

            assertEquals(1, second?.revision)
            val submission = monthSubmission(september, dao.rows)
            assertEquals(first, submission?.first)
            assertEquals(handedOverAtMs, submission?.first?.sentAtMs)
            assertEquals(second, submission?.latest)
            assertEquals(
                "Report for 2026-09 marked as sent by hand on the Report screen, without the " +
                    "email app: revision 1, 21 trips, 231.4 km. The month was marked as " +
                    "submitted before, and stays so",
                logged(EventCategory.REPORT).last(),
            )
        }

    @Test
    fun `a date range marked by hand is listed and marks no month`() = runTest {
        val stored = records.markedSent(range, tripCount = 6, tenths = 713)

        assertEquals(SentReportKind.RANGE, stored?.kind)
        assertNull(monthSubmission(september, dao.rows))
        assertEquals(
            listOf(
                "Report for 2026-09-14 to 2026-09-18 marked as sent by hand on the Report " +
                    "screen, without the email app: the first report for this period, 6 " +
                    "trips, 71.3 km. A date range marks no month as submitted",
            ),
            logged(EventCategory.REPORT),
        )
    }

    @Test
    fun `marking leaves a report that waits for its answer where it is`() = runTest {
        val waiting = ReportHandOver(range, tripCount = 6, tenths = 713, atMs = nowMs - 60_000)
        records.awaitAnswerFor(waiting)

        records.markedSent(month, tripCount = 21, tenths = 2314)

        // The question about the email is still owed: marking is not an answer to it.
        assertEquals(waiting, settings.current().reportHandOver)
    }

    @Test
    fun `a failure of storage marks nothing, says so, and does not end the process`() = runTest {
        dao.failNextInsert = IOException("disk full")

        val stored = records.markedSent(month, tripCount = 21, tenths = 2314)

        assertNull(stored)
        assertEquals(emptyList<SentReport>(), dao.rows)
        assertEquals(emptyList<String>(), logged(EventCategory.REPORT))
        assertEquals(
            listOf("Recording the report for 2026-09 as sent failed"),
            logged(EventCategory.ERROR),
        )
        // The next press records the first report: the failed one left no number behind.
        assertEquals(0, records.markedSent(month, tripCount = 21, tenths = 2314)?.revision)
    }
}

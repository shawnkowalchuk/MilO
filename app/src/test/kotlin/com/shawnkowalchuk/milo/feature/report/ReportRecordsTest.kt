package com.shawnkowalchuk.milo.feature.report

import com.shawnkowalchuk.milo.core.report.ReportPeriod
import com.shawnkowalchuk.milo.core.util.DistanceUnit
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.report.FakeSentReportDao
import com.shawnkowalchuk.milo.data.report.SentReport
import com.shawnkowalchuk.milo.data.report.SentReportKind
import com.shawnkowalchuk.milo.data.report.SentReportRepository
import com.shawnkowalchuk.milo.data.report.monthSubmission
import com.shawnkowalchuk.milo.data.report.printedTenths
import com.shawnkowalchuk.milo.data.settings.ReportHandOver
import com.shawnkowalchuk.milo.data.settings.SettingsStore
import com.shawnkowalchuk.milo.data.settings.setDistanceUnit
import com.shawnkowalchuk.milo.platform.trip.FakeEventLogDao
import com.shawnkowalchuk.milo.platform.trip.FakeSettingsFile
import com.shawnkowalchuk.milo.platform.trip.UnreadableSettingsFile
import java.io.IOException
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What is recorded when Shawn answers "Did you send it?", against the stand-in table: the row
 * that makes a month submitted, and the line each step leaves in the event log.
 */
class ReportRecordsTest {
    private val dao = FakeSentReportDao()
    private val log = FakeEventLogDao()
    private val nowMs = 1_791_300_000_000L
    private val settings = SettingsStore(FakeSettingsFile())
    private val records =
        ReportRecords(SentReportRepository(dao), settings, EventLogRepository(log)) { nowMs }
    private val october = YearMonth.of(2026, 10)
    private val handedOverAtMs = nowMs - 3 * 24 * 60 * 60_000L
    private val month =
        ReportHandOver(
            ReportPeriod.Month(october),
            tripCount = 31,
            tenths = 4123,
            handedOverAtMs,
            DistanceUnit.KILOMETRES,
        )
    private val range =
        ReportHandOver(
            ReportPeriod.Range(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 18)),
            tripCount = 12,
            tenths = 1500,
            atMs = handedOverAtMs,
            unit = DistanceUnit.KILOMETRES,
        )

    private fun logged(category: EventCategory): List<String> =
        log.entries.filter { it.category == category }.map { it.message }

    @Test
    fun `handing a report to the email app records nothing as sent`() = runTest {
        records.handedOver(month)

        assertEquals(emptyList<SentReport>(), dao.rows)
        assertNull(monthSubmission(october, dao.rows))
        assertEquals(
            listOf(
                "Report for 2026-10 handed to the email app: 31 trips, 412.3 km. Android does " +
                    "not say whether the email is sent, so that is asked when MilO is in front again",
            ),
            logged(EventCategory.REPORT),
        )
    }

    @Test
    fun `the month is submitted only when he says he sent it, with the day and the figures`() =
        runTest {
            val stored = records.answeredSent(month)

            assertNotNull(stored)
            val row = dao.rows.single()
            assertEquals(SentReportKind.MONTH, row.kind)
            // The day the email app was opened with it, though he answers three days later.
            assertEquals(handedOverAtMs, row.sentAtMs)
            assertEquals(31, row.tripCount)
            // The total the report printed, as metres.
            assertEquals(412_300.0, row.distanceMetres, 0.0)
            assertEquals(0, row.revision)
            assertEquals(row, monthSubmission(october, dao.rows)?.first)
            assertEquals(
                listOf(
                    "Report for 2026-10 recorded as sent, on Shawn's word: the first report for " +
                        "this period, 31 trips, 412.3 km. The month is now marked as submitted",
                ),
                logged(EventCategory.REPORT),
            )
        }

    @Test
    fun `answering not sent records nothing but a line`() = runTest {
        records.answeredNotSent(month)

        assertEquals(emptyList<SentReport>(), dao.rows)
        assertNull(monthSubmission(october, dao.rows))
        assertEquals(
            listOf(
                "Report for 2026-10: answered \"not sent\" after the email app. Nothing was recorded",
            ),
            logged(EventCategory.REPORT),
        )
    }

    @Test
    fun `a date range is kept in the list and marks no month`() = runTest {
        val stored = records.answeredSent(range)

        assertEquals(SentReportKind.RANGE, stored?.kind)
        assertEquals(1, dao.rows.size)
        assertNull(monthSubmission(october, dao.rows))
        assertEquals(
            listOf(
                "Report for 2026-10-05 to 2026-10-18 recorded as sent, on Shawn's word: the " +
                    "first report for this period, 12 trips, 150.0 km. A date range marks no " +
                    "month as submitted",
            ),
            logged(EventCategory.REPORT),
        )
    }

    @Test
    fun `sending a month again is recorded as a revision, and the first report stays`() = runTest {
        val first = records.answeredSent(month)
        val second = records.answeredSent(month.copy(tripCount = 32, tenths = 4201))

        assertEquals(0, first?.revision)
        assertEquals(1, second?.revision)
        assertEquals(2, dao.rows.size)
        val submission = monthSubmission(october, dao.rows)
        assertEquals(first, submission?.first)
        assertEquals(second, submission?.latest)
        assertEquals(1, submission?.revisions)
        assertTrue(logged(EventCategory.REPORT).last().contains("revision 1, 32 trips, 420.1 km"))
    }

    @Test
    fun `a failure of storage records nothing, says so, and does not end the process`() = runTest {
        dao.failNextInsert = IOException("disk full")

        val stored = records.answeredSent(month)

        assertNull(stored)
        assertEquals(emptyList<SentReport>(), dao.rows)
        assertEquals(emptyList<String>(), logged(EventCategory.REPORT))
        assertEquals(
            listOf("Recording the report for 2026-10 as sent failed"),
            logged(EventCategory.ERROR),
        )
        assertTrue(log.entries.single().detail.orEmpty().contains("disk full"))
        // The next answer is recorded as the first report: the failed one left no number behind.
        assertEquals(0, records.answeredSent(month)?.revision)
    }

    @Test
    fun `a file that was made leaves a line, and names no one and no address`() = runTest {
        records.created("PDF", month.period, 31, 4123, DistanceUnit.KILOMETRES)
        records.created("CSV", range.period, 12, 1500, DistanceUnit.KILOMETRES)

        assertEquals(
            listOf(
                "PDF for 2026-10 created: 31 trips, 412.3 km",
                "CSV for 2026-10-05 to 2026-10-18 created: 12 trips, 150.0 km",
            ),
            logged(EventCategory.REPORT),
        )
    }

    // ---- A report in miles (2026-10-07) ----------------------------------------------------------

    /** The same month as a report printed in miles: 31 trips, 256.2 mi. */
    private val monthInMiles = month.copy(tenths = 2_562, unit = DistanceUnit.MILES)

    @Test
    fun `a report sent in miles is recorded as the miles it printed, whatever is chosen later`() =
        runTest {
            // He switches back to kilometres before he answers. The report did not change.
            settings.setDistanceUnit(DistanceUnit.KILOMETRES)

            val stored = checkNotNull(records.answeredSent(monthInMiles))

            assertEquals(DistanceUnit.MILES, stored.distanceUnit)
            assertEquals(2_562L, stored.printedTenths)
            assertEquals(31, stored.tripCount)
            // In metres like every stored distance: 256.2 times 1 609.344.
            assertEquals(412_313.9328, stored.distanceMetres, 0.0)
            // The month is submitted by it exactly as by a report in kilometres.
            assertEquals(stored, monthSubmission(october, dao.rows)?.first)
            assertEquals(
                listOf(
                    "Report for 2026-10 recorded as sent, on Shawn's word: the first report for " +
                        "this period, 31 trips, 256.2 mi. The month is now marked as submitted",
                ),
                logged(EventCategory.REPORT),
            )
        }

    @Test
    fun `every line about a report in miles names miles, beside lines that name kilometres`() =
        runTest {
            records.created("PDF", month.period, 31, 2_562, DistanceUnit.MILES)
            records.handedOver(monthInMiles)
            records.markedSent(month.period, 31, 2_562, DistanceUnit.MILES)
            records.removed(dao.rows.single().id)
            records.created("CSV", month.period, 31, 4_123, DistanceUnit.KILOMETRES)

            val lines = logged(EventCategory.REPORT)
            assertEquals(5, lines.size)
            for (line in lines.take(4)) {
                assertTrue(line, line.contains("31 trips, 256.2 mi"))
                assertFalse(line, line.contains(" km"))
            }
            assertEquals("CSV for 2026-10 created: 31 trips, 412.3 km", lines.last())
        }

    @Test
    fun `the log names a period the same way in every language`() {
        assertEquals("2026-10", month.period.inLogWords())
        assertEquals("2026-10-05 to 2026-10-18", range.period.inLogWords())
    }

    @Test
    fun `a report that is handed over waits for its answer, and an answer ends the wait`() =
        runTest {
            records.awaitAnswerFor(month)
            assertEquals(month, settings.current().reportHandOver)

            assertTrue(records.answered(month, sent = true))

            assertNull(settings.current().reportHandOver)
            assertEquals(1, dao.rows.size)
        }

    @Test
    fun `not sent is an answer too, it ends the wait and records nothing as sent`() = runTest {
        records.awaitAnswerFor(month)

        assertTrue(records.answered(month, sent = false))

        assertNull(settings.current().reportHandOver)
        assertEquals(emptyList<SentReport>(), dao.rows)
        assertNull(monthSubmission(october, dao.rows))
    }

    @Test
    fun `a report that was handed to nobody is forgotten without a word`() = runTest {
        records.awaitAnswerFor(range)

        assertTrue(records.forgetAwaited())

        assertNull(settings.current().reportHandOver)
        assertEquals(emptyList<SentReport>(), dao.rows)
        assertEquals(0, log.entries.size)
    }

    @Test
    fun `if the wait cannot be ended, nothing is recorded as sent`() = runTest {
        // A settings file that cannot be written: the question would be asked again, so an
        // answer that was recorded now could be recorded twice.
        val stuck =
            ReportRecords(
                SentReportRepository(dao),
                SettingsStore(UnreadableSettingsFile),
                EventLogRepository(log),
            ) { nowMs }

        assertFalse(stuck.answered(month, sent = true))

        assertEquals(emptyList<SentReport>(), dao.rows)
        assertEquals(emptyList<String>(), logged(EventCategory.REPORT))
        assertEquals(
            listOf("Forgetting the report that was handed to the email app failed"),
            logged(EventCategory.ERROR),
        )
    }

    @Test
    fun `if storage fails after the wait has ended, the answer is lost and said to be`() = runTest {
        records.awaitAnswerFor(month)
        dao.failNextInsert = IOException("disk full")

        assertFalse(records.answered(month, sent = true))

        // Nothing is recorded, and the question is not asked again: sending again is the way.
        assertEquals(emptyList<SentReport>(), dao.rows)
        assertNull(settings.current().reportHandOver)
        assertEquals(1, logged(EventCategory.ERROR).size)
    }
}

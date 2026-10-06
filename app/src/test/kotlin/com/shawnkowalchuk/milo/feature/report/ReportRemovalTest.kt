package com.shawnkowalchuk.milo.feature.report

import com.shawnkowalchuk.milo.core.report.ReportPeriod
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.report.FakeSentReportDao
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Removing a report from the list of sent reports, against the stand-in table: what is deleted,
 * what becomes of the month, and the one line each removal leaves in the event log.
 */
class ReportRemovalTest {
    private val dao = FakeSentReportDao()
    private val log = FakeEventLogDao()
    private val nowMs = 1_791_300_000_000L
    private val records =
        ReportRecords(
            SentReportRepository(dao),
            SettingsStore(FakeSettingsFile()),
            EventLogRepository(log),
        ) { nowMs }
    private val october = YearMonth.of(2026, 10)
    private val month = ReportHandOver(ReportPeriod.Month(october), 31, 4123, nowMs - 60_000)
    private val range =
        ReportHandOver(
            ReportPeriod.Range(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 18)),
            tripCount = 12,
            tenths = 1500,
            atMs = nowMs - 60_000,
        )

    private fun logged(category: EventCategory): List<String> =
        log.entries.filter { it.category == category }.map { it.message }

    @Test
    fun `removing a month's only report takes the month back to not submitted, with one line`() =
        runTest {
            val stored = records.answeredSent(month)!!
            log.entries.clear()

            assertTrue(records.removed(stored.id))

            assertTrue(dao.rows.isEmpty())
            assertNull(monthSubmission(october, dao.rows))
            assertEquals(
                listOf(
                    "Sent report removed from the list by hand, on the Report screen: 2026-10, " +
                        "the first report for this period, 31 trips, 412.3 km. The month is no " +
                        "longer marked as submitted. No trip and no email was touched",
                ),
                log.entries.map { it.message },
            )
            assertEquals(EventCategory.REPORT, log.entries.single().category)
        }

    @Test
    fun `removing the first of two leaves the month submitted by the revision, number kept`() =
        runTest {
            val first = records.answeredSent(month)!!
            val second = records.answeredSent(month.copy(tripCount = 32, tenths = 4201))!!
            log.entries.clear()

            assertTrue(records.removed(first.id))

            assertEquals(listOf(second), dao.rows)
            assertEquals(1, dao.rows.single().revision)
            assertEquals(second, monthSubmission(october, dao.rows)?.first)
            assertEquals(
                listOf(
                    "Sent report removed from the list by hand, on the Report screen: 2026-10, " +
                        "the first report for this period, 31 trips, 412.3 km. The month stays " +
                        "marked as submitted: 1 other report(s) for it are still in the list " +
                        "(revision 1), and keep their numbers. No trip and no email was touched",
                ),
                logged(EventCategory.REPORT),
            )
        }

    @Test
    fun `removing a revision says which, and leaves the first report as it was`() = runTest {
        val first = records.answeredSent(month)!!
        val second = records.answeredSent(month.copy(tripCount = 32, tenths = 4201))!!
        log.entries.clear()

        assertTrue(records.removed(second.id))

        assertEquals(listOf(first), dao.rows)
        assertTrue(
            logged(EventCategory.REPORT).single().contains(
                "2026-10, revision 1, 32 trips, 420.1 km. The month stays marked as submitted: " +
                    "1 other report(s) for it are still in the list (revision 0)",
            ),
        )
    }

    @Test
    fun `removing a date range changes no month`() = runTest {
        records.answeredSent(month)
        val part = records.answeredSent(range)!!
        log.entries.clear()

        assertTrue(records.removed(part.id))

        assertEquals(1, dao.rows.size)
        assertEquals(0, monthSubmission(october, dao.rows)?.revisions)
        assertEquals(
            listOf(
                "Sent report removed from the list by hand, on the Report screen: 2026-10-05 " +
                    "to 2026-10-18, the first report for this period, 12 trips, 150.0 km. A " +
                    "date range marked no month as submitted. No trip and no email was touched",
            ),
            logged(EventCategory.REPORT),
        )
    }

    @Test
    fun `a second press for a report that is already gone removes nothing, and says so`() =
        runTest {
            val kept = records.answeredSent(month)!!
            val gone = records.answeredSent(range)!!
            assertTrue(records.removed(gone.id))
            log.entries.clear()

            assertFalse(records.removed(gone.id))

            assertEquals(listOf(kept), dao.rows)
            assertEquals(
                listOf(
                    "Remove refused on the Report screen: the list of sent reports holds no " +
                        "report with the id ${gone.id}. Nothing changed",
                ),
                logged(EventCategory.REPORT),
            )
        }

    @Test
    fun `a removal that storage refuses removes nothing and is an error line`() = runTest {
        val stored = records.answeredSent(month)!!
        log.entries.clear()
        dao.failNextDelete = IOException("disk full")

        assertFalse(records.removed(stored.id))

        assertEquals(listOf(stored), dao.rows)
        assertEquals(stored, monthSubmission(october, dao.rows)?.first)
        assertEquals(
            listOf("Removing a report from the list of sent reports failed"),
            logged(EventCategory.ERROR),
        )
        assertEquals(emptyList<String>(), logged(EventCategory.REPORT))
    }
}

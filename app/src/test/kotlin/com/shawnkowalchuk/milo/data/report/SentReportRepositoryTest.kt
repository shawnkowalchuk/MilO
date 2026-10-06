package com.shawnkowalchuk.milo.data.report

import com.shawnkowalchuk.milo.core.report.ReportPeriod
import java.io.IOException
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** What is stored when Shawn says he sent a report, against the stand-in table. */
class SentReportRepositoryTest {
    private val dao = FakeSentReportDao()
    private val sent = SentReportRepository(dao)
    private val october = ReportPeriod.Month(YearMonth.of(2026, 10))
    private val range = ReportPeriod.Range(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 18))

    @Test
    fun `a month's report is stored with its two days, its figures and when it was sent`() =
        runTest {
            val stored = sent.recordSent(
                october,
                sentAtMs = 9_000,
                tripCount = 31,
                distanceMetres = 412_300.0,
            )

            assertEquals(
                SentReport(
                    id = 1,
                    kind = SentReportKind.MONTH,
                    firstDay = LocalDate.of(2026, 10, 1).toEpochDay(),
                    lastDay = LocalDate.of(2026, 10, 31).toEpochDay(),
                    sentAtMs = 9_000,
                    tripCount = 31,
                    distanceMetres = 412_300.0,
                    revision = 0,
                ),
                stored,
            )
            assertEquals(listOf(stored), dao.rows)
            assertEquals(october, stored.period)
        }

    @Test
    fun `a date range is stored as a range, with the days it was given`() = runTest {
        val stored = sent.recordSent(
            range,
            sentAtMs = 9_000,
            tripCount = 4,
            distanceMetres = 26_800.0,
        )

        assertEquals(SentReportKind.RANGE, stored.kind)
        assertEquals(range, stored.period)
        assertEquals(0, stored.revision)
    }

    @Test
    fun `a period sent again is numbered as the next revision of it, and of no other`() = runTest {
        val first = sent.recordSent(october, 1_000, 31, 412_300.0)
        val part = sent.recordSent(range, 2_000, 4, 26_800.0)
        val second = sent.recordSent(october, 3_000, 32, 420_100.0)
        val third = sent.recordSent(october, 4_000, 32, 420_100.0)
        val partAgain = sent.recordSent(range, 5_000, 4, 26_800.0)
        val september = sent.recordSent(
            ReportPeriod.Month(YearMonth.of(2026, 9)),
            6_000,
            20,
            300_000.0,
        )

        assertEquals(listOf(0, 1, 2), listOf(first, second, third).map { it.revision })
        assertEquals(listOf(0, 1), listOf(part, partAgain).map { it.revision })
        assertEquals(0, september.revision)
        // Nothing is changed afterwards: the first report still says what was sent then.
        assertEquals(first, dao.rows.first())
        assertEquals(6, dao.rows.size)
    }

    @Test
    fun `the list is read newest first`() = runTest {
        sent.recordSent(october, 1_000, 31, 412_300.0)
        sent.recordSent(range, 3_000, 4, 26_800.0)
        sent.recordSent(october, 2_000, 32, 420_100.0)

        assertEquals(listOf(3_000L, 2_000L, 1_000L), sent.observeSent().first().map { it.sentAtMs })
    }

    @Test
    fun `figures that cannot be a report's are refused, and nothing is stored`() = runTest {
        val wrong =
            listOf<suspend () -> Unit>(
                { sent.recordSent(october, sentAtMs = -1, tripCount = 1, distanceMetres = 1.0) },
                { sent.recordSent(october, sentAtMs = 1, tripCount = -1, distanceMetres = 1.0) },
                { sent.recordSent(october, sentAtMs = 1, tripCount = 1, distanceMetres = -1.0) },
                {
                    sent.recordSent(
                        october,
                        sentAtMs = 1,
                        tripCount = 1,
                        distanceMetres = Double.NaN,
                    )
                },
            )
        for (attempt in wrong) {
            try {
                attempt()
                fail("A wrong figure was stored")
            } catch (refused: IllegalArgumentException) {
                assertTrue(refused.message.orEmpty().isNotEmpty())
            }
        }

        assertEquals(emptyList<SentReport>(), dao.rows)
    }

    @Test
    fun `a report with no trips at all can be recorded as sent`() = runTest {
        val stored = sent.recordSent(october, 1_000, tripCount = 0, distanceMetres = 0.0)

        assertEquals(0, stored.tripCount)
        assertEquals(0.0, stored.distanceMetres, 0.0)
    }

    @Test
    fun `a failure of storage reaches the caller, and leaves no row`() {
        dao.failNextInsert = IOException("disk full")

        assertThrows(IOException::class.java) {
            runBlocking { sent.recordSent(october, 1_000, 31, 412_300.0) }
        }
        assertEquals(emptyList<SentReport>(), dao.rows)
    }
}

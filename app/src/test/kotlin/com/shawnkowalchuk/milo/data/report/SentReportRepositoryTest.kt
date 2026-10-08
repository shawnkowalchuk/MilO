package com.shawnkowalchuk.milo.data.report

import com.shawnkowalchuk.milo.core.report.ReportPeriod
import com.shawnkowalchuk.milo.core.util.DistanceUnit
import java.io.IOException
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
                unit = DistanceUnit.KILOMETRES,
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
            unit = DistanceUnit.KILOMETRES,
        )

        assertEquals(SentReportKind.RANGE, stored.kind)
        assertEquals(range, stored.period)
        assertEquals(0, stored.revision)
    }

    @Test
    fun `a period sent again is numbered as the next revision of it, and of no other`() = runTest {
        val first = sent.recordSent(october, 1_000, 31, 412_300.0, DistanceUnit.KILOMETRES)
        val part = sent.recordSent(range, 2_000, 4, 26_800.0, DistanceUnit.KILOMETRES)
        val second = sent.recordSent(october, 3_000, 32, 420_100.0, DistanceUnit.KILOMETRES)
        val third = sent.recordSent(october, 4_000, 32, 420_100.0, DistanceUnit.KILOMETRES)
        val partAgain = sent.recordSent(range, 5_000, 4, 26_800.0, DistanceUnit.KILOMETRES)
        val september = sent.recordSent(
            ReportPeriod.Month(YearMonth.of(2026, 9)),
            6_000,
            20,
            300_000.0,
            DistanceUnit.KILOMETRES,
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
        sent.recordSent(october, 1_000, 31, 412_300.0, DistanceUnit.KILOMETRES)
        sent.recordSent(range, 3_000, 4, 26_800.0, DistanceUnit.KILOMETRES)
        sent.recordSent(october, 2_000, 32, 420_100.0, DistanceUnit.KILOMETRES)

        assertEquals(listOf(3_000L, 2_000L, 1_000L), sent.observeSent().first().map { it.sentAtMs })
    }

    @Test
    fun `figures that cannot be a report's are refused, and nothing is stored`() = runTest {
        val wrong =
            listOf<suspend () -> Unit>(
                {
                    sent.recordSent(
                        october,
                        sentAtMs = -1,
                        tripCount = 1,
                        distanceMetres = 1.0,
                        unit = DistanceUnit.KILOMETRES,
                    )
                },
                {
                    sent.recordSent(
                        october,
                        sentAtMs = 1,
                        tripCount = -1,
                        distanceMetres = 1.0,
                        unit = DistanceUnit.KILOMETRES,
                    )
                },
                {
                    sent.recordSent(
                        october,
                        sentAtMs = 1,
                        tripCount = 1,
                        distanceMetres = -1.0,
                        unit = DistanceUnit.KILOMETRES,
                    )
                },
                {
                    sent.recordSent(
                        october,
                        sentAtMs = 1,
                        tripCount = 1,
                        distanceMetres = Double.NaN,
                        unit = DistanceUnit.KILOMETRES,
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
        val stored = sent.recordSent(
            october,
            1_000,
            tripCount = 0,
            distanceMetres = 0.0,
            unit = DistanceUnit.KILOMETRES,
        )

        assertEquals(0, stored.tripCount)
        assertEquals(0.0, stored.distanceMetres, 0.0)
    }

    @Test
    fun `a removed report leaves the list, and the others keep their numbers`() = runTest {
        val first = sent.recordSent(october, 1_000, 31, 412_300.0, DistanceUnit.KILOMETRES)
        val second = sent.recordSent(october, 2_000, 32, 420_100.0, DistanceUnit.KILOMETRES)
        val part = sent.recordSent(range, 3_000, 4, 26_800.0, DistanceUnit.KILOMETRES)

        val removed = sent.remove(first.id)

        assertEquals(first, removed?.report)
        assertEquals(listOf(second), removed?.leftForPeriod)
        // Nothing else was written: the revision is still revision 1, the range untouched.
        assertEquals(listOf(second, part), dao.rows)
        assertEquals(listOf(part, second), sent.currentSent())
    }

    @Test
    fun `the next report after a removal takes the number after the highest one still listed`() =
        runTest {
            val first = sent.recordSent(october, 1_000, 31, 412_300.0, DistanceUnit.KILOMETRES)
            val second = sent.recordSent(october, 2_000, 32, 420_100.0, DistanceUnit.KILOMETRES)

            // The original removed: the revision keeps its 1, and the next is 2.
            sent.remove(first.id)
            assertEquals(
                2,
                sent.recordSent(october, 3_000, 32, 420_100.0, DistanceUnit.KILOMETRES).revision,
            )

            // The newest removed: its number is free again.
            sent.remove(dao.rows.last().id)
            assertEquals(
                2,
                sent.recordSent(october, 4_000, 32, 420_100.0, DistanceUnit.KILOMETRES).revision,
            )
            assertEquals(listOf(second.id, 4L), dao.rows.map { it.id })
        }

    @Test
    fun `removing a report that is not in the list changes nothing, also the second time`() =
        runTest {
            val only = sent.recordSent(october, 1_000, 31, 412_300.0, DistanceUnit.KILOMETRES)

            assertNull(sent.remove(only.id + 7))
            assertEquals(listOf(only), dao.rows)

            assertEquals(only, sent.remove(only.id)?.report)
            assertNull(sent.remove(only.id))
            assertEquals(emptyList<SentReport>(), dao.rows)
        }

    @Test
    fun `a removal that storage refuses reaches the caller, and leaves the row`() = runTest {
        val only = sent.recordSent(october, 1_000, 31, 412_300.0, DistanceUnit.KILOMETRES)
        dao.failNextDelete = IOException("disk full")

        assertThrows(IOException::class.java) { runBlocking { sent.remove(only.id) } }

        assertEquals(listOf(only), dao.rows)
    }

    @Test
    fun `a failure of storage reaches the caller, and leaves no row`() {
        dao.failNextInsert = IOException("disk full")

        assertThrows(IOException::class.java) {
            runBlocking { sent.recordSent(october, 1_000, 31, 412_300.0, DistanceUnit.KILOMETRES) }
        }
        assertEquals(emptyList<SentReport>(), dao.rows)
    }
}

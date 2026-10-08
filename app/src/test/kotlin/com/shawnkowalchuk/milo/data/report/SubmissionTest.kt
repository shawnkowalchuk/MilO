package com.shawnkowalchuk.milo.data.report

import com.shawnkowalchuk.milo.core.report.ReportPeriod
import com.shawnkowalchuk.milo.core.util.DistanceUnit
import com.shawnkowalchuk.milo.core.util.metresOfTenths
import com.shawnkowalchuk.milo.core.util.sumOfTenths
import com.shawnkowalchuk.milo.core.util.tenthsOf
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * What the list of sent reports says: whether a month is submitted, which revision the next
 * report for a period is, what removing a report does, and whether a month's trips are still
 * what was sent.
 */
class SubmissionTest {
    private val october = YearMonth.of(2026, 10)
    private val september = YearMonth.of(2026, 9)
    private var nextId = 1L

    private fun sent(period: ReportPeriod, revision: Int = 0, sentAtMs: Long = 1_000L * nextId) =
        SentReport(
            id = nextId++,
            kind = period.kind,
            firstDay = period.firstDay.toEpochDay(),
            lastDay = period.lastDay.toEpochDay(),
            sentAtMs = sentAtMs,
            tripCount = 31,
            distanceMetres = 412_300.0,
            revision = revision,
        )

    private fun month(month: YearMonth) = ReportPeriod.Month(month)

    private fun range(first: Int, last: Int) =
        ReportPeriod.Range(LocalDate.of(2026, 10, first), LocalDate.of(2026, 10, last))

    @Test
    fun `a month nothing was sent for is not submitted`() {
        assertNull(monthSubmission(october, emptyList()))
        assertNull(monthSubmission(october, listOf(sent(month(september)))))
    }

    @Test
    fun `a month is submitted once its report has been sent, with the day it was`() {
        val report = sent(month(october), sentAtMs = 77_000)

        val submission = monthSubmission(october, listOf(report, sent(month(september))))

        assertNotNull(submission)
        assertEquals(report, submission?.first)
        assertEquals(report, submission?.latest)
        assertEquals(77_000L, submission?.first?.sentAtMs)
        assertEquals(0, submission?.revisions)
    }

    @Test
    fun `a date range never marks a month as submitted, not even one that covers it all`() {
        val wholeMonthAsRange = sent(range(1, 31))
        val part = sent(range(5, 18))

        assertNull(monthSubmission(october, listOf(wholeMonthAsRange, part)))
    }

    @Test
    fun `a range that spans two months marks neither`() {
        val spanning =
            sent(ReportPeriod.Range(LocalDate.of(2026, 9, 15), LocalDate.of(2026, 10, 15)))

        assertNull(monthSubmission(september, listOf(spanning)))
        assertNull(monthSubmission(october, listOf(spanning)))
    }

    @Test
    fun `a month sent again stays submitted since its first report, and names its newest`() {
        val first = sent(month(october), revision = 0, sentAtMs = 1_000)
        val second = sent(month(october), revision = 1, sentAtMs = 5_000)
        val third = sent(month(october), revision = 2, sentAtMs = 9_000)

        // Newest first, the order storage hands them in.
        val submission = monthSubmission(october, listOf(third, second, first))

        assertEquals(first, submission?.first)
        assertEquals(third, submission?.latest)
        assertEquals(2, submission?.revisions)
    }

    @Test
    fun `the first report of a period is revision 0, and each one after it one more`() {
        val first = sent(month(october), revision = 0)
        val second = sent(month(october), revision = 1)

        assertEquals(0, nextRevision(emptyList()))
        assertEquals(1, nextRevision(listOf(first)))
        assertEquals(2, nextRevision(listOf(second, first)))
        // One more than the highest, not the number of rows: a number is never given twice.
        assertEquals(6, nextRevision(listOf(sent(month(october), revision = 5))))
    }

    @Test
    fun `reports are sent for a period only if kind and both days are the same`() {
        val octoberReport = sent(month(october))
        val septemberReport = sent(month(september))
        val sameDaysAsRange = sent(range(1, 31))
        val part = sent(range(5, 18))
        val partAgain = sent(range(5, 18), revision = 1)
        val otherPart = sent(range(5, 19))
        val all =
            listOf(partAgain, otherPart, part, sameDaysAsRange, septemberReport, octoberReport)

        assertEquals(listOf(octoberReport), sentFor(month(october), all))
        assertEquals(listOf(sameDaysAsRange), sentFor(range(1, 31), all))
        // Oldest first.
        assertEquals(listOf(part, partAgain), sentFor(range(5, 18), all))
        assertEquals(emptyList<SentReport>(), sentFor(range(6, 18), all))
    }

    @Test
    fun `a date range sent twice is a revision of itself`() {
        val part = sent(range(5, 18))

        assertEquals(1, nextRevision(sentFor(range(5, 18), listOf(part, sent(month(october))))))
        // And the month's own count is not moved by it.
        assertEquals(0, nextRevision(sentFor(month(september), listOf(part))))
    }

    @Test
    fun `the first report of a month is what marks it as submitted`() {
        assertEquals(SentEffect.MarksMonth, sentEffect(month(october), emptyList()))
        // Neither another month's report nor a range over the same days makes it a revision.
        val others = listOf(sent(month(september)), sent(range(1, 31)))
        assertEquals(SentEffect.MarksMonth, sentEffect(month(october), others))
    }

    @Test
    fun `a month sent again gets the next revision and keeps the day of its first report`() {
        val first = sent(month(october), revision = 0, sentAtMs = 1_000)
        val second = sent(month(october), revision = 1, sentAtMs = 5_000)

        assertEquals(
            SentEffect.RevisesMonth(revision = 1, firstSentAtMs = 1_000),
            sentEffect(month(october), listOf(first)),
        )
        // Newest first, the order storage hands them in: the day is still the first report's.
        assertEquals(
            SentEffect.RevisesMonth(revision = 2, firstSentAtMs = 1_000),
            sentEffect(month(october), listOf(second, first)),
        )
    }

    @Test
    fun `a date range is listed and marks no month, however often it is sent`() {
        val part = sent(range(5, 18))

        assertEquals(SentEffect.ListsRange, sentEffect(range(5, 18), emptyList()))
        assertEquals(SentEffect.ListsRange, sentEffect(range(5, 18), listOf(part)))
        assertEquals(SentEffect.ListsRange, sentEffect(range(1, 31), listOf(sent(month(october)))))
    }

    // ---- Removing a report from the list ----------------------------------------------------------

    @Test
    fun `removing a month's only report takes the month back to not submitted`() {
        val only = sent(month(october))
        val others = listOf(sent(month(september)), sent(range(1, 31)))

        assertEquals(RemovalEffect.UnmarksMonth, removalEffect(only, listOf(only) + others))
        // The same answer when the list handed in no longer holds the report itself.
        assertEquals(RemovalEffect.UnmarksMonth, removalEffect(only, others))
        assertNull(monthSubmission(october, others))
    }

    @Test
    fun `removing one of a month's reports leaves it submitted by the earliest that is left`() {
        val first = sent(month(october), revision = 0, sentAtMs = 1_000)
        val second = sent(month(october), revision = 1, sentAtMs = 5_000)
        val third = sent(month(october), revision = 2, sentAtMs = 9_000)
        val all = listOf(third, second, first)

        assertEquals(RemovalEffect.MonthStaysSubmitted(5_000), removalEffect(first, all))
        assertEquals(RemovalEffect.MonthStaysSubmitted(1_000), removalEffect(second, all))
        assertEquals(RemovalEffect.MonthStaysSubmitted(1_000), removalEffect(third, all))
    }

    @Test
    fun `a month whose original was removed is submitted by its revision, number kept`() {
        val revision = sent(month(october), revision = 1, sentAtMs = 5_000)

        val submission = monthSubmission(october, listOf(revision))

        assertEquals(revision, submission?.first)
        assertEquals(1, submission?.revisions)
        // Nothing was sent again after it: it is the one report that is left.
        assertEquals(false, submission?.sentAgain)
        // And the next one counts on from it.
        assertEquals(2, nextRevision(sentFor(month(october), listOf(revision))))
    }

    @Test
    fun `a month with a later report says that it was sent again`() {
        val first = sent(month(october), revision = 0)
        val second = sent(month(october), revision = 1)

        assertEquals(false, monthSubmission(october, listOf(first))?.sentAgain)
        assertEquals(true, monthSubmission(october, listOf(second, first))?.sentAgain)
    }

    @Test
    fun `removing a date range changes no month`() {
        val part = sent(range(5, 18))
        val monthReport = sent(month(october))

        assertEquals(RemovalEffect.UnlistsRange, removalEffect(part, listOf(part, monthReport)))
        assertEquals(RemovalEffect.UnlistsRange, removalEffect(sent(range(1, 31)), emptyList()))
    }

    // ---- Changed since it was sent ----------------------------------------------------------------

    @Test
    fun `a month that still has the trips and the total of its report has not changed`() {
        // The rows of this test hold 31 trips and 412.3 km.
        val submission = monthSubmission(october, listOf(sent(month(october))))

        assertNull(changedSinceSent(submission, tripCount = 31) { 4_123 })
    }

    @Test
    fun `a trip more or less, or another total, is a change, with both sets of figures`() {
        val submission = monthSubmission(october, listOf(sent(month(october))))

        assertEquals(
            ChangedSinceSent(
                sentTripCount = 31,
                sentTenths = 4_123,
                tripCount = 32,
                tenths = 4_201,
                unit = DistanceUnit.KILOMETRES,
            ),
            changedSinceSent(submission, tripCount = 32) { 4_201 },
        )
        // One trip marked Personal and another, as long, marked Business: the count is the
        // same and the total too, and nothing is noticed. A tenth of a kilometre is.
        assertNull(changedSinceSent(submission, tripCount = 31) { 4_123 })
        assertEquals(4_124L, changedSinceSent(submission, 31) { 4_124 }?.tenths)
        assertEquals(30, changedSinceSent(submission, 30) { 4_123 }?.tripCount)
    }

    @Test
    fun `it is the newest report the month is held against`() {
        val first = sent(month(october), revision = 0)
        val revised =
            sent(month(october), revision = 1).copy(tripCount = 32, distanceMetres = 420_100.0)
        val submission = monthSubmission(october, listOf(revised, first))

        // What the revision listed is what the month has: nothing to say.
        assertNull(changedSinceSent(submission, tripCount = 32) { 4_201 })
        // The figures of the first report are no longer the measure.
        assertEquals(32, changedSinceSent(submission, 31) { 4_123 }?.sentTripCount)
    }

    @Test
    fun `a month that is not submitted has nothing to have changed from`() {
        assertNull(changedSinceSent(submission = null, tripCount = 12) { 3_456 })
    }

    // ---- Kilometres or miles (2026-10-07) ---------------------------------------------------------

    /** A month of trips that round awkwardly, and differently in the two units. */
    private val awkward =
        listOf(300.0, 12_350.0, 100_050.0, 23_449.0, 563.2704) + List(26) { 11_504.0 }

    /** The row "I sent it" stores for a report of [trips] printed in [unit]. */
    private fun sentIn(unit: DistanceUnit, trips: List<Double> = awkward) =
        sent(month(october)).copy(
            tripCount = trips.size,
            distanceMetres = metresOfTenths(sumOfTenths(trips, unit), unit),
            distanceUnit = unit,
        )

    @Test
    fun `a sent report reads back as the total it printed, in the unit it was printed in`() {
        for (unit in DistanceUnit.entries) {
            for (printed in (0L..120_000L) + listOf(999_999L, 9_999_999L)) {
                val row =
                    sent(month(october)).copy(
                        distanceMetres = metresOfTenths(printed, unit),
                        distanceUnit = unit,
                    )

                assertEquals("$printed tenths, $unit", printed, row.printedTenths)
            }
        }
        // A row from before there was a choice of unit says nothing, and is in kilometres.
        assertEquals(DistanceUnit.KILOMETRES, sent(month(october)).distanceUnit)
        assertEquals(4_123L, sent(month(october)).printedTenths)
    }

    @Test
    fun `a change of the unit alone can never say that the month has changed`() {
        for (printedIn in DistanceUnit.entries) {
            val submission = monthSubmission(october, listOf(sentIn(printedIn)))

            // Nothing about the unit MilO is set to now is asked for or passed in: the month is
            // added up in the unit the report was printed in, whichever one is on screen.
            assertNull(
                "printed in $printedIn",
                changedSinceSent(submission, awkward.size) { unit -> sumOfTenths(awkward, unit) },
            )
        }
    }

    @Test
    fun `the month is held against its report in the report's unit, not in the other one`() {
        val asked = mutableListOf<DistanceUnit>()
        val inMiles = monthSubmission(october, listOf(sentIn(DistanceUnit.MILES)))

        changedSinceSent(inMiles, awkward.size) { unit ->
            asked += unit
            sumOfTenths(awkward, unit)
        }

        assertEquals(listOf(DistanceUnit.MILES), asked)
        // Which is what keeps it quiet: these trips are 435.8 km and 269.7 mi as printed, and
        // 435.8 km turned into miles is 270.8. Held against the wrong one, it would speak.
        assertEquals(4_358L, sumOfTenths(awkward, DistanceUnit.KILOMETRES))
        assertEquals(2_697L, sumOfTenths(awkward, DistanceUnit.MILES))
        assertEquals(2_708L, tenthsOf(435_800.0, DistanceUnit.MILES))
    }

    @Test
    fun `a trip made longer after a report in miles is noticed, with both figures in miles`() {
        val submission = monthSubmission(october, listOf(sentIn(DistanceUnit.MILES)))
        val longer = awkward.drop(1) + 5_300.0

        val changed =
            changedSinceSent(submission, longer.size) { unit -> sumOfTenths(longer, unit) }

        // 0.3 km became 5.3 km: 0.2 mi became 3.3 mi.
        assertEquals(
            ChangedSinceSent(
                sentTripCount = 31,
                sentTenths = 2_697,
                tripCount = 31,
                tenths = 2_728,
                unit = DistanceUnit.MILES,
            ),
            changed,
        )
    }

    @Test
    fun `a report sent in kilometres is still held in kilometres after miles are chosen`() {
        val submission = monthSubmission(october, listOf(sentIn(DistanceUnit.KILOMETRES)))
        val longer = awkward.drop(1) + 5_300.0

        val changed =
            changedSinceSent(submission, longer.size) { unit -> sumOfTenths(longer, unit) }

        assertEquals(DistanceUnit.KILOMETRES, changed?.unit)
        assertEquals(4_358L, changed?.sentTenths)
        assertEquals(4_408L, changed?.tenths)
    }

    @Test
    fun `whether a month is submitted does not depend on the unit its report was printed in`() {
        for (unit in DistanceUnit.entries) {
            val report = sentIn(unit)

            assertEquals(report, monthSubmission(october, listOf(report))?.first)
            assertEquals(listOf(report), sentFor(month(october), listOf(report)))
            assertEquals(1, nextRevision(listOf(report)))
        }
    }

    @Test
    fun `a row reads back as the period it was sent for`() {
        assertEquals(month(october), sent(month(october)).period)
        assertEquals(range(5, 18), sent(range(5, 18)).period)
        assertEquals(range(5, 5), sent(range(5, 5)).period)
        // A leap day, and a month whose length the row does not have to know.
        val february = ReportPeriod.Month(YearMonth.of(2028, 2))
        assertEquals(february, sent(february).period)
        assertEquals(SentReportKind.MONTH, february.kind)
        assertEquals(SentReportKind.RANGE, range(5, 18).kind)
    }

    @Test
    fun `a row whose days make no sense, which nothing can write, is still read`() {
        // This runs while a screen is drawn. It must not be what ends the process.
        val backwards = sent(range(5, 18)).copy(firstDay = 20_750, lastDay = 20_740)
        val absurd = sent(range(5, 18)).copy(firstDay = Long.MAX_VALUE, lastDay = Long.MIN_VALUE)

        assertEquals(LocalDate.ofEpochDay(20_750), backwards.period.firstDay)
        assertEquals(LocalDate.ofEpochDay(20_750), backwards.period.lastDay)
        assertEquals(LocalDate.MAX, absurd.period.firstDay)
        assertEquals(LocalDate.MAX, absurd.period.lastDay)
    }
}

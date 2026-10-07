package com.shawnkowalchuk.milo.feature.report

import com.shawnkowalchuk.milo.core.report.ReportPeriod
import com.shawnkowalchuk.milo.data.report.RemovalEffect
import com.shawnkowalchuk.milo.data.report.SentReport
import com.shawnkowalchuk.milo.data.report.kind
import com.shawnkowalchuk.milo.data.settings.MiloSettings
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What "Email the report" asks before it sends, and what the screen says removing a sent
 * report would do. Kept apart from `ReportUiStateTest`, which is at its size limit.
 */
class ReportSendQuestionsTest {
    private val zone = ZoneId.of("America/Edmonton")
    private val october = ReportPeriod.Month(YearMonth.of(2026, 10))
    private val september = ReportPeriod.Month(YearMonth.of(2026, 9))

    private fun day(dayOfOctober: Int) = LocalDate.of(2026, 10, dayOfOctober)

    private fun sent(period: ReportPeriod, revision: Int, id: Long, sentAtMs: Long = id * 1_000) =
        SentReport(
            id = id,
            kind = period.kind,
            firstDay = period.firstDay.toEpochDay(),
            lastDay = period.lastDay.toEpochDay(),
            sentAtMs = sentAtMs,
            tripCount = 31,
            distanceMetres = 412_300.0,
            revision = revision,
        )

    private fun state(today: LocalDate, month: YearMonth, sent: List<SentReport> = emptyList()) =
        reportUiState(
            choice = openingChoice(month, today),
            today = today,
            zone = zone,
            selection = null,
            settings = MiloSettings(),
            sent = sent,
            passing = ReportPassing(),
        )

    // ---- Before sending ---------------------------------------------------------------------------

    @Test
    fun `last month's first report is sent without a question`() {
        assertEquals(emptyList<SendQuestion>(), sendQuestions(september, day(6), false))
        // The first day of the next month is the first day on which the month has ended.
        assertEquals(
            emptyList<SendQuestion>(),
            sendQuestions(october, LocalDate.of(2026, 11, 1), false),
        )
    }

    @Test
    fun `a month that has not ended asks first, on its last day too`() {
        val notEnded = listOf(SendQuestion.MONTH_NOT_ENDED)

        assertEquals(notEnded, sendQuestions(october, day(1), sentBefore = false))
        assertEquals(notEnded, sendQuestions(october, day(28), sentBefore = false))
        // On the 31st the last trips of the month may still be to come.
        assertEquals(notEnded, sendQuestions(october, day(31), sentBefore = false))
    }

    @Test
    fun `February ends on the 28th, and on the 29th in a leap year`() {
        val plain = ReportPeriod.Month(YearMonth.of(2027, 2))
        val leap = ReportPeriod.Month(YearMonth.of(2028, 2))
        val notEnded = listOf(SendQuestion.MONTH_NOT_ENDED)

        assertEquals(notEnded, sendQuestions(plain, LocalDate.of(2027, 2, 28), false))
        assertEquals(
            emptyList<SendQuestion>(),
            sendQuestions(plain, LocalDate.of(2027, 3, 1), false),
        )
        assertEquals(notEnded, sendQuestions(leap, LocalDate.of(2028, 2, 29), false))
        assertEquals(
            emptyList<SendQuestion>(),
            sendQuestions(leap, LocalDate.of(2028, 3, 1), false),
        )
    }

    @Test
    fun `a period that was sent before asks whether to send it again`() {
        val again = listOf(SendQuestion.SENT_BEFORE)

        assertEquals(again, sendQuestions(september, day(6), sentBefore = true))
    }

    @Test
    fun `an unfinished month that was sent before asks both, the unfinished month first`() {
        assertEquals(
            listOf(SendQuestion.MONTH_NOT_ENDED, SendQuestion.SENT_BEFORE),
            sendQuestions(october, day(28), sentBefore = true),
        )
    }

    @Test
    fun `a date range is never asked about its end, also when it ends today`() {
        val toToday = ReportPeriod.Range(day(1), day(6))
        val wholeMonth = ReportPeriod.Range(day(1), day(31))

        assertEquals(emptyList<SendQuestion>(), sendQuestions(toToday, day(6), false))
        assertEquals(emptyList<SendQuestion>(), sendQuestions(wholeMonth, day(31), false))
        assertEquals(listOf(SendQuestion.SENT_BEFORE), sendQuestions(toToday, day(6), true))
    }

    @Test
    fun `the screen is handed the questions for the period that is chosen`() {
        val octoberSent = listOf(sent(october, revision = 0, id = 1))

        // Opened from Trips on the current month, which is the trap the question is for.
        val current = state(day(6), YearMonth.of(2026, 10))
        val currentSentBefore = state(day(6), YearMonth.of(2026, 10), octoberSent)
        val lastMonth = state(day(6), YearMonth.of(2026, 9), octoberSent)

        assertEquals(listOf(SendQuestion.MONTH_NOT_ENDED), current.sendQuestions)
        assertEquals(
            listOf(SendQuestion.MONTH_NOT_ENDED, SendQuestion.SENT_BEFORE),
            currentSentBefore.sendQuestions,
        )
        // September itself was never sent: October's report is not a report for it.
        assertEquals(emptyList<SendQuestion>(), lastMonth.sendQuestions)
    }

    // ---- Removing a sent report -------------------------------------------------------------------

    @Test
    fun `each line of the list says what removing it would do`() {
        val range = ReportPeriod.Range(day(5), day(18))
        val all =
            listOf(
                sent(range, revision = 0, id = 4),
                sent(october, revision = 1, id = 3, sentAtMs = 9_000),
                sent(october, revision = 0, id = 2, sentAtMs = 5_000),
                sent(september, revision = 0, id = 1),
            )

        val lines = state(day(6), YearMonth.of(2026, 10), all).sent

        assertEquals(listOf(4L, 3L, 2L, 1L), lines.map { it.id })
        assertEquals(
            listOf(
                RemovalEffect.UnlistsRange,
                // The original stays: the month is then submitted on its day again.
                RemovalEffect.MonthStaysSubmitted(5_000),
                // The revision stays, and marks the month by its own day.
                RemovalEffect.MonthStaysSubmitted(9_000),
                RemovalEffect.UnmarksMonth,
            ),
            lines.map { it.removal },
        )
    }
}

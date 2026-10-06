package com.shawnkowalchuk.milo.core.util

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

private const val HOUR_MS = 60L * 60 * 1000
private const val DAY_MS = 24 * HOUR_MS

/**
 * Where a month begins and ends in a fixed time zone. Edmonton is used because it is the kind of
 * zone the phone is in: seven hours behind UTC in winter, six in summer, with the clocks
 * changing on 8 March and 1 November 2026.
 */
class TimeSpanTest {
    private val edmonton = ZoneId.of("America/Edmonton")

    private fun utc(text: String): Long = Instant.parse(text).toEpochMilli()

    @Test
    fun `a month runs from local midnight on the 1st to local midnight on the next 1st`() {
        val october = monthSpan(YearMonth.of(2026, 10), edmonton)

        // Summer time: local midnight is 06:00 UTC.
        assertEquals(utc("2026-10-01T06:00:00Z"), october.fromMs)
        assertEquals(utc("2026-11-01T06:00:00Z"), october.untilMs)
        assertEquals(31 * DAY_MS, october.untilMs - october.fromMs)
    }

    @Test
    fun `the month in which the clocks go back is an hour longer than its days`() {
        val november = monthSpan(YearMonth.of(2026, 11), edmonton)

        // It starts in summer time (the change is at 02:00 on the 1st) and ends in winter time.
        assertEquals(utc("2026-11-01T06:00:00Z"), november.fromMs)
        assertEquals(utc("2026-12-01T07:00:00Z"), november.untilMs)
        assertEquals(30 * DAY_MS + HOUR_MS, november.untilMs - november.fromMs)
    }

    @Test
    fun `the month in which the clocks go forward is an hour shorter than its days`() {
        val march = monthSpan(YearMonth.of(2026, 3), edmonton)

        assertEquals(utc("2026-03-01T07:00:00Z"), march.fromMs)
        assertEquals(utc("2026-04-01T06:00:00Z"), march.untilMs)
        assertEquals(31 * DAY_MS - HOUR_MS, march.untilMs - march.fromMs)
    }

    @Test
    fun `December ends where January of the next year begins`() {
        val december = monthSpan(YearMonth.of(2026, 12), edmonton)
        val january = monthSpan(YearMonth.of(2027, 1), edmonton)

        assertEquals(utc("2027-01-01T07:00:00Z"), december.untilMs)
        // Nothing between the two months, and nothing in both.
        assertEquals(december.untilMs, january.fromMs)
    }

    @Test
    fun `a day runs from local midnight to the next local midnight`() {
        val today = daySpan(LocalDate.of(2026, 10, 5), edmonton)
        val tomorrow = daySpan(LocalDate.of(2026, 10, 6), edmonton)

        assertEquals(utc("2026-10-05T06:00:00Z"), today.fromMs)
        assertEquals(utc("2026-10-06T06:00:00Z"), today.untilMs)
        // Nothing between two days, and nothing in both.
        assertEquals(today.untilMs, tomorrow.fromMs)
    }

    @Test
    fun `the day the clocks change is an hour longer or shorter`() {
        val clocksGoBack = daySpan(LocalDate.of(2026, 11, 1), edmonton)
        val clocksGoForward = daySpan(LocalDate.of(2026, 3, 8), edmonton)

        assertEquals(DAY_MS + HOUR_MS, clocksGoBack.untilMs - clocksGoBack.fromMs)
        assertEquals(DAY_MS - HOUR_MS, clocksGoForward.untilMs - clocksGoForward.fromMs)
    }

    @Test
    fun `a time belongs to the local day and month, not the UTC one`() {
        // 23:30 on 31 October in Edmonton is already 1 November in UTC.
        val lateOnTheLast = utc("2026-11-01T05:30:00Z")

        assertEquals(LocalDate.of(2026, 10, 31), localDateOf(lateOnTheLast, edmonton))
        assertEquals(YearMonth.of(2026, 10), monthOf(lateOnTheLast, edmonton))
        assertEquals(YearMonth.of(2026, 11), monthOf(lateOnTheLast, ZoneId.of("UTC")))
    }

    @Test
    fun `the last second of the year and the first of the next fall in different months`() {
        val lastSecond = utc("2027-01-01T06:59:59Z")
        val firstSecond = utc("2027-01-01T07:00:00Z")
        val december = monthSpan(YearMonth.of(2026, 12), edmonton)

        assertEquals(YearMonth.of(2026, 12), monthOf(lastSecond, edmonton))
        assertEquals(YearMonth.of(2027, 1), monthOf(firstSecond, edmonton))
        // The span agrees with monthOf at its very edge: the end is not part of the month.
        assertEquals(true, lastSecond in december.fromMs until december.untilMs)
        assertEquals(false, firstSecond in december.fromMs until december.untilMs)
    }

    @Test
    fun `a run of days goes from the first midnight to the midnight that ends the last day`() {
        val span = daysSpan(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 18), edmonton)

        assertEquals(utc("2026-10-05T06:00:00Z"), span.fromMs)
        assertEquals(utc("2026-10-19T06:00:00Z"), span.untilMs)
        assertEquals(14 * DAY_MS, span.untilMs - span.fromMs)
    }

    @Test
    fun `a run of one day is that day, and a whole month's days are the month`() {
        val monday = LocalDate.of(2026, 10, 5)
        val october = YearMonth.of(2026, 10)

        assertEquals(daySpan(monday, edmonton), daysSpan(monday, monday, edmonton))
        assertEquals(
            monthSpan(october, edmonton),
            daysSpan(october.atDay(1), october.atEndOfMonth(), edmonton),
        )
    }

    @Test
    fun `a run of days over a clock change is as long as it really was`() {
        // The clocks go back on Sunday 1 November 2026: that day has 25 hours.
        val span = daysSpan(LocalDate.of(2026, 10, 31), LocalDate.of(2026, 11, 2), edmonton)

        assertEquals(3 * DAY_MS + HOUR_MS, span.untilMs - span.fromMs)
    }

    @Test
    fun `days that end before they start are refused`() {
        assertThrows(IllegalArgumentException::class.java) {
            daysSpan(LocalDate.of(2026, 10, 18), LocalDate.of(2026, 10, 5), edmonton)
        }
    }
}

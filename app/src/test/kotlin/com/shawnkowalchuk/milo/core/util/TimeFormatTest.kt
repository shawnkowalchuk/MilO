package com.shawnkowalchuk.milo.core.util

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

/** 2026-10-03 12:00 UTC. */
private const val NOON_UTC_MS = 1_791_028_800_000L

class TimeFormatTest {
    @Test
    fun `a stored time is shown as the time of day in the given zone`() {
        // Edmonton is six hours behind UTC in October.
        val edmonton = ZoneId.of("America/Edmonton")

        assertEquals("06:00", formatTimeOfDay(NOON_UTC_MS, edmonton, Locale.UK))
        assertEquals("12:00", formatTimeOfDay(NOON_UTC_MS, ZoneId.of("UTC"), Locale.UK))
    }

    @Test
    fun `the locale decides the form`() {
        val utc = ZoneId.of("UTC")
        val afternoon = NOON_UTC_MS + 2 * 60 * 60 * 1000 + 14 * 60 * 1000

        assertEquals("14:14", formatTimeOfDay(afternoon, utc, Locale.GERMANY))
        // The exact spelling of "p.m." differs between Java versions; the 12-hour clock is the
        // point.
        assertEquals("2:14", formatTimeOfDay(afternoon, utc, Locale.CANADA).take(4))
    }

    @Test
    fun `the event log's time is exact to the second, in local time, in one fixed form`() {
        val edmonton = ZoneId.of("America/Edmonton")
        val threeSecondsPast = NOON_UTC_MS + 3_000

        assertEquals("2026-10-03 06:00:03", formatLogTime(threeSecondsPast, edmonton))
        assertEquals("2026-10-03 12:00:03", formatLogTime(threeSecondsPast, ZoneId.of("UTC")))
    }

    @Test
    fun `the event log shows the hour that is repeated when the clocks go back`() {
        val edmonton = ZoneId.of("America/Edmonton")
        // 1 November 2026: 01:30 happens twice in Edmonton, an hour apart.
        val firstTime = Instant.parse("2026-11-01T07:30:00Z").toEpochMilli()
        val secondTime = Instant.parse("2026-11-01T08:30:00Z").toEpochMilli()

        assertEquals("2026-11-01 01:30:00", formatLogTime(firstTime, edmonton))
        assertEquals("2026-11-01 01:30:00", formatLogTime(secondTime, edmonton))
    }

    @Test
    fun `a month is named in full with its year`() {
        assertEquals("October 2026", formatMonthAndYear(YearMonth.of(2026, 10), Locale.CANADA))
        assertEquals("Oktober 2026", formatMonthAndYear(YearMonth.of(2026, 10), Locale.GERMANY))
    }

    @Test
    fun `a day heading names the weekday, the day, the month and the year`() {
        val heading = formatDay(LocalDate.of(2026, 10, 5), Locale.CANADA)

        // The order and the punctuation belong to the language; the four parts are the point.
        for (part in listOf("Monday", "October", "5", "2026")) {
            assertEquals("\"$part\" in \"$heading\"", true, heading.contains(part))
        }
    }

    @Test
    fun `a date is the local day the time fell on`() {
        // 23:30 on 2 October in Edmonton is already 3 October in UTC.
        val lateEvening = NOON_UTC_MS - 6 * 60 * 60 * 1000 - 30 * 60 * 1000

        val inEdmonton = formatDate(lateEvening, ZoneId.of("America/Edmonton"), Locale.UK)
        val inUtc = formatDate(lateEvening, ZoneId.of("UTC"), Locale.UK)

        assertEquals("2 Oct 2026", inEdmonton)
        assertEquals("3 Oct 2026", inUtc)
    }
}

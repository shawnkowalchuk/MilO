package com.shawnkowalchuk.milo.core.util

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
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

        assertEquals("06:00", formatTimeOfDay(NOON_UTC_MS, edmonton, Locale.UK, true))
        assertEquals("12:00", formatTimeOfDay(NOON_UTC_MS, ZoneId.of("UTC"), Locale.UK, true))
    }

    @Test
    fun `where the phone's clock is the language's own, the language decides the form`() {
        val utc = ZoneId.of("UTC")
        val afternoon = NOON_UTC_MS + 2 * 60 * 60 * 1000 + 14 * 60 * 1000

        assertEquals("14:14", formatTimeOfDay(afternoon, utc, Locale.GERMANY, true))
        // The exact spelling of "p.m." differs between Java versions; the 12-hour clock is the
        // point.
        assertEquals("2:14", formatTimeOfDay(afternoon, utc, Locale.CANADA, false).take(4))
    }

    @Test
    fun `a phone set to 24 hours gets 24 hours, in a language that writes AM and PM too`() {
        val utc = ZoneId.of("UTC")
        val afternoon = NOON_UTC_MS + 2 * 60 * 60 * 1000 + 14 * 60 * 1000
        val morning = NOON_UTC_MS - 4 * 60 * 60 * 1000 + 5 * 60 * 1000

        assertEquals("14:14", formatTimeOfDay(afternoon, utc, Locale.CANADA, true))
        assertEquals("14:14", formatTimeOfDay(afternoon, utc, Locale.US, true))
        // Two digits for the hour, as Android's own clock writes it on such a phone.
        assertEquals("08:05", formatTimeOfDay(morning, utc, Locale.CANADA, true))
    }

    @Test
    fun `a phone set to AM and PM gets them, in a language that writes 24 hours too`() {
        val utc = ZoneId.of("UTC")
        val afternoon = NOON_UTC_MS + 2 * 60 * 60 * 1000 + 14 * 60 * 1000

        val german = formatTimeOfDay(afternoon, utc, Locale.GERMANY, false)
        val british = formatTimeOfDay(afternoon, utc, Locale.UK, false)

        // How a language spells PM differs between Java versions; the hour is the point.
        assertEquals("2:14", german.take(4))
        assertEquals("2:14", british.take(4))
        assertEquals(german, false, german.contains("14:"))
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
    fun `a time of day without a date is written like a trip's times`() {
        val start = LocalTime.of(8, 0)
        val end = LocalTime.of(16, 30)

        assertEquals("08:00", formatClockTime(start, Locale.UK, true))
        assertEquals("16:30", formatClockTime(end, Locale.GERMANY, true))
        // The exact spelling of "a.m." differs between Java versions, as above.
        assertEquals("8:00", formatClockTime(start, Locale.CANADA, false).take(4))
        assertEquals("4:30", formatClockTime(end, Locale.US, false).take(4))
    }

    @Test
    fun `a day's hours are written with 24 hours on a phone set to them, whatever the language`() {
        // The phone the schedule was written for: English, set to 24 hours. The dial then has
        // no AM and PM, and neither have the two times beside it.
        assertEquals("08:00", formatClockTime(LocalTime.of(8, 0), Locale.CANADA, true))
        assertEquals("16:30", formatClockTime(LocalTime.of(16, 30), Locale.CANADA, true))
        assertEquals("16:30", formatClockTime(LocalTime.of(16, 30), Locale.US, true))
        assertEquals("00:00", formatClockTime(LocalTime.MIDNIGHT, Locale.US, true))
    }

    @Test
    fun `the phone's switch decides between 24 hours and AM and PM in every language`() {
        val languages =
            listOf(
                Locale.CANADA,
                Locale.US,
                Locale.UK,
                Locale.GERMANY,
                Locale.CANADA_FRENCH,
                Locale.JAPAN,
                Locale.KOREA,
                Locale.TAIWAN,
            )
        val evening = LocalTime.of(21, 5)
        for (locale in languages) {
            val on = formatClockTime(evening, locale, true)
            val off = formatClockTime(evening, locale, false)

            // The dial is drawn by the same switch, so what is printed must agree with it:
            // "21" exactly when the dial has no AM and PM.
            assertEquals("$locale, 24 hours: $on", true, on.contains("21"))
            assertEquals("$locale, AM and PM: $off", false, off.contains("21"))
            assertEquals("$locale, AM and PM: $off", true, off.contains("9"))
        }
    }

    @Test
    fun `which clock a language writes by itself is read from its hour, not its AM or PM`() {
        assertEquals(true, usesTwelveHourClock(Locale.CANADA))
        assertEquals(true, usesTwelveHourClock(Locale.US))
        assertEquals(false, usesTwelveHourClock(Locale.UK))
        assertEquals(false, usesTwelveHourClock(Locale.GERMANY))
        // French writes "21 h 05": the "h" between the figures is text, not the 12-hour letter.
        assertEquals(false, usesTwelveHourClock(Locale.CANADA_FRENCH))
    }

    @Test
    fun `a day of the week is named in full`() {
        assertEquals("Monday", formatDayOfWeek(DayOfWeek.MONDAY, Locale.CANADA))
        assertEquals("Sunday", formatDayOfWeek(DayOfWeek.SUNDAY, Locale.UK))
        assertEquals("Montag", formatDayOfWeek(DayOfWeek.MONDAY, Locale.GERMANY))
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

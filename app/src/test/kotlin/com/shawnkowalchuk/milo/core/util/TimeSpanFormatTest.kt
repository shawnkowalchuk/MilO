package com.shawnkowalchuk.milo.core.util

import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A trip's two times, written the short way the design has them: the half of the day once,
 * after the end, where both times share it.
 */
class TimeSpanFormatTest {
    private val edmonton = ZoneId.of("America/Edmonton")

    private fun at(dateTime: String): Long =
        LocalDateTime.parse(dateTime).atZone(edmonton).toInstant().toEpochMilli()

    private fun span(from: String, to: String, locale: Locale, twentyFourHour: Boolean = false) =
        formatTimeSpan(at(from), at(to), edmonton, locale, twentyFourHour)

    @Test
    fun `two times in the same half of the day name it once, after the end`() {
        val (start, end) = span("2026-10-06T17:30", "2026-10-06T17:41", Locale.US)

        assertEquals("5:30", start)
        // The exact spelling of "PM", and the space before it, differ between Java versions.
        assertTrue(end, end.startsWith("5:41") && end.endsWith("PM"))
    }

    @Test
    fun `the end is always written as a time of day is written anywhere else`() {
        val startMs = at("2026-10-06T07:42")
        val endMs = at("2026-10-06T08:06")

        val (_, end) = formatTimeSpan(startMs, endMs, edmonton, Locale.US, false)

        assertEquals(formatTimeOfDay(endMs, edmonton, Locale.US, false), end)
    }

    @Test
    fun `a trip from the morning into the afternoon names both halves`() {
        val startMs = at("2026-10-06T11:50")

        val (start, end) = span("2026-10-06T11:50", "2026-10-06T12:10", Locale.US)

        assertEquals(formatTimeOfDay(startMs, edmonton, Locale.US, false), start)
        assertTrue(start, start.endsWith("AM"))
        assertTrue(end, end.endsWith("PM"))
    }

    @Test
    fun `a trip that runs past midnight names both halves`() {
        val (start, end) = span("2026-10-06T23:50", "2026-10-07T00:10", Locale.US)

        assertTrue(start, start.startsWith("11:50") && start.endsWith("PM"))
        assertTrue(end, end.startsWith("12:10") && end.endsWith("AM"))
    }

    @Test
    fun `noon and the hour after it are both afternoon`() {
        val (start, end) = span("2026-10-06T12:40", "2026-10-06T13:07", Locale.US)

        assertEquals("12:40", start)
        assertTrue(end, end.startsWith("1:07") && end.endsWith("PM"))
    }

    @Test
    fun `on a 24-hour clock both times stand in full`() {
        assertEquals(
            "17:30" to "17:41",
            span("2026-10-06T17:30", "2026-10-06T17:41", Locale.US, true),
        )
        assertEquals(
            "07:42" to "08:06",
            span("2026-10-06T07:42", "2026-10-06T08:06", Locale.UK, true),
        )
    }

    @Test
    fun `it goes by what is written, so Canada's own spelling is left out as well`() {
        val startMs = at("2026-10-06T17:30")
        val endMs = at("2026-10-06T17:41")

        val (start, end) = formatTimeSpan(startMs, endMs, edmonton, Locale.CANADA, false)

        // "5:30 p.m." in full; the short start is that without what follows its last digit.
        assertEquals("5:30", start)
        assertEquals(formatTimeOfDay(endMs, edmonton, Locale.CANADA, false), end)
        assertTrue(end, end.length > "5:41".length)
    }
}

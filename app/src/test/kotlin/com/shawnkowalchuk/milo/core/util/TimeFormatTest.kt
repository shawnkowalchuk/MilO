package com.shawnkowalchuk.milo.core.util

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
}

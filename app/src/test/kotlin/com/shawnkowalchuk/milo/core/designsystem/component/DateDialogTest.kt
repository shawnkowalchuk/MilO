package com.shawnkowalchuk.milo.core.designsystem.component

import java.time.LocalDate
import java.time.ZoneId
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A calendar day as Material's date picker counts it: milliseconds since 1970 at midnight UTC,
 * whatever the phone's time zone.
 */
class DateDialogTest {
    @Test
    fun `a day is midnight UTC of that day`() {
        assertEquals(0L, utcMillisOf(LocalDate.of(1970, 1, 1)))
        assertEquals(1_791_158_400_000L, utcMillisOf(LocalDate.of(2026, 10, 5)))
    }

    @Test
    fun `a day survives the trip to the picker and back, on the days the clocks change too`() {
        val days =
            listOf(
                LocalDate.of(2026, 3, 8),
                LocalDate.of(2026, 10, 5),
                LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 12, 31),
                LocalDate.of(2028, 2, 29),
            )

        for (day in days) assertEquals(day, dateOfUtcMillis(utcMillisOf(day)))
    }

    @Test
    fun `the phone's time zone does not move the day to its neighbour`() {
        val day = LocalDate.of(2026, 10, 5)
        val phones = listOf("America/Edmonton", "Pacific/Auckland", "Pacific/Honolulu")
        val before = TimeZone.getDefault()
        try {
            for (zone in phones) {
                TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of(zone)))

                assertEquals(zone, day, dateOfUtcMillis(utcMillisOf(day)))
                // The last millisecond of the day as the picker counts it is still that day.
                assertEquals(zone, day, dateOfUtcMillis(utcMillisOf(day.plusDays(1)) - 1))
            }
        } finally {
            TimeZone.setDefault(before)
        }
    }
}

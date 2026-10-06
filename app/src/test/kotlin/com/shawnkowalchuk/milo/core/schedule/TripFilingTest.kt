package com.shawnkowalchuk.milo.core.schedule

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

private const val MINUTE_MS = 60_000L

/**
 * How a trip that has just ended is put away: sorted by the schedule, and, with "Ignore them"
 * chosen, marked as ignored if it turned out Personal. The sorting itself is in
 * `TripClassificationTest`. Times are Edmonton wall-clock times; 5 October 2026 is a Monday and
 * 10 October a Saturday.
 */
class TripFilingTest {
    private val edmonton = ZoneId.of("America/Edmonton")
    private val business = TripClassification(TripCategory.BUSINESS, ranPastSchedule = false)
    private val ranPast = TripClassification(TripCategory.BUSINESS, ranPastSchedule = true)
    private val personal = TripClassification(TripCategory.PERSONAL, ranPastSchedule = false)

    private fun local(dateTime: String): Long =
        LocalDateTime.parse(dateTime).atZone(edmonton).toInstant().toEpochMilli()

    // ---- Putting a finished trip away -----------------------------------------------------------

    private val saveAsPersonal = FilingRules(DEFAULT_WORK_SCHEDULE, ignoreOutsideSchedule = false)
    private val ignore = FilingRules(DEFAULT_WORK_SCHEDULE, ignoreOutsideSchedule = true)

    private fun file(start: Long, kept: Boolean, rules: FilingRules?): TripFiling =
        fileTrip(start, start + 20 * MINUTE_MS, kept, rules, edmonton)

    @Test
    fun `by default a trip outside the schedule is kept, as Personal`() {
        val filing = file(local("2026-10-10T10:00"), kept = true, rules = saveAsPersonal)

        assertEquals(TripFiling(personal, ignored = false), filing)
    }

    @Test
    fun `with ignore chosen a kept trip that turns out Personal is marked as ignored`() {
        val filing = file(local("2026-10-10T10:00"), kept = true, rules = ignore)

        // Still sorted: it is Personal if it is counted after all.
        assertEquals(TripFiling(personal, ignored = true), filing)
    }

    @Test
    fun `ignore never touches a Business trip`() {
        assertEquals(
            TripFiling(business, ignored = false),
            file(local("2026-10-05T10:00"), kept = true, rules = ignore),
        )
        // Not even one that ran far past the schedule.
        val start = local("2026-10-05T16:00")
        val long = fileTrip(start, local("2026-10-05T22:00"), kept = true, ignore, edmonton)
        assertEquals(TripFiling(ranPast, ignored = false), long)
    }

    @Test
    fun `a trip the trip rules already discarded is sorted, and not marked as ignored`() {
        // Too short, or a false start: that is why it is discarded, whatever the schedule says.
        val filing = file(local("2026-10-10T10:00"), kept = false, rules = ignore)

        assertEquals(TripFiling(personal, ignored = false), filing)
    }

    @Test
    fun `without a schedule to go by the trip is left unsorted and is never ignored`() {
        val filing = file(local("2026-10-10T10:00"), kept = true, rules = null)

        assertNull(filing.classification)
        assertFalse(filing.ignored)
    }
}

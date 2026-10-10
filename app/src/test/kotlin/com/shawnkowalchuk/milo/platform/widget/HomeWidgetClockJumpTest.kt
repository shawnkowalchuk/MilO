package com.shawnkowalchuk.milo.platform.widget

import com.shawnkowalchuk.milo.core.clock.JumpingPhone
import com.shawnkowalchuk.milo.core.schedule.TripCategory
import com.shawnkowalchuk.milo.core.trip.TripStartCause
import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.core.util.DistanceUnit
import com.shawnkowalchuk.milo.data.trip.Trip
import com.shawnkowalchuk.milo.platform.car.MINUTE_MS
import com.shawnkowalchuk.milo.platform.car.STARTED_AT_MS
import com.shawnkowalchuk.milo.platform.car.carContent
import com.shawnkowalchuk.milo.platform.car.carTrip
import com.shawnkowalchuk.milo.platform.trip.TripActivity
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

private const val SECOND_MS = 1_000L

/**
 * What the home-screen widget and the Android Auto screen print if they are drawn in the
 * seconds in which the phone's date is one day ahead (ADR-006). Both are handed MilO's clock,
 * and both are drawings only: nothing here is stored.
 *
 * These were the investigation's proofs (2026-10-07) of what a drawing made with tomorrow's
 * time shows: next month at $0 on the last evening of a month, next year's name over this
 * year's total, a trip a day longer. Each now shows the drawing as it should be.
 */
class HomeWidgetClockJumpTest {
    private val zone: ZoneId = ZoneId.of("America/Edmonton")
    private var nextId = 1L

    private fun at(text: String): Long =
        LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()

    /** What MilO's clock reads in a jump of the phone's date that begins at the true [atMs]. */
    private fun timeInsideAJumpAt(atMs: Long): Long {
        val phone = JumpingPhone(atMs - 4 * SECOND_MS)
        phone.clock.now()
        phone.setAhead()
        phone.advance(4 * SECOND_MS)
        return phone.clock.now()
    }

    private fun business(start: String, km: Double) = Trip(
        id = nextId++,
        startedAtMs = at(start),
        endedAtMs = at(start) + 3_600_000L,
        status = TripStatus.FINISHED,
        startedBy = TripStartCause.TRUCK,
        truckSeen = true,
        distanceMetres = km * 1000,
        category = TripCategory.BUSINESS,
    )

    private fun dollarsAt(nowMs: Long, trips: List<Trip>) = checkNotNull(
        homeWidgetContent(
            TripActivity(),
            trips,
            centsPerKm = 70,
            nowMs,
            zone,
            Locale.US,
            DistanceUnit.KILOMETRES,
        ).dollars,
    )

    @Test
    fun `on an ordinary evening the widget's dollars are the same inside the jump`() {
        val trips =
            listOf(business("2026-02-10T09:00", 4_000.0), business("2026-10-01T09:00", 1_500.0))
        val evening = at("2026-10-07T18:32")

        assertEquals(dollarsAt(evening, trips), dollarsAt(timeInsideAJumpAt(evening), trips))
    }

    @Test
    fun `on the last evening of a month a drawing made inside the jump still shows that month`() {
        val trips =
            listOf(business("2026-02-10T09:00", 4_000.0), business("2026-11-12T09:00", 1_500.0))
        val evening = at("2026-11-30T18:32")

        val drawn = dollarsAt(timeInsideAJumpAt(evening), trips)

        // Before the fix: "Dec" over "$0".
        assertEquals("Nov" to "$1,050", drawn.monthLabel to drawn.month)
        assertEquals(dollarsAt(evening, trips), drawn)
    }

    @Test
    fun `on New Year's Eve it names this year over this year's total`() {
        val trips =
            listOf(business("2026-02-10T09:00", 4_000.0), business("2026-12-12T09:00", 1_500.0))
        val evening = at("2026-12-31T18:32")

        val drawn = dollarsAt(timeInsideAJumpAt(evening), trips)

        // Before the fix: "Jan" at "$0", and "2027" over 2026's total.
        assertEquals("Dec" to "$1,050", drawn.monthLabel to drawn.month)
        assertEquals("2026" to "$3,850", drawn.yearLabel to drawn.year)
    }

    @Test
    fun `a trip in progress reads its true length on the Android Auto screen and the widget`() {
        val activity = TripActivity(trip = carTrip())
        val twelveMinutesIn = STARTED_AT_MS + 12 * MINUTE_MS

        val honest = carContent(activity, nowMs = twelveMinutesIn)
        val inTheJump = carContent(activity, nowMs = timeInsideAJumpAt(twelveMinutesIn))

        // Before the fix: 24 h 12 min.
        assertEquals(0L to 12L, inTheJump.trip?.hours to inTheJump.trip?.minutes)
        assertEquals(honest, inTheJump)
    }
}

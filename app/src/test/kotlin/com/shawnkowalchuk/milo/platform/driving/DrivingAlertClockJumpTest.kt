package com.shawnkowalchuk.milo.platform.driving

import com.shawnkowalchuk.milo.core.clock.JumpingPhone
import com.shawnkowalchuk.milo.core.trip.GPS_AFTER_VEHICLE_REPORT_MS
import com.shawnkowalchuk.milo.core.trip.PARKED_GPS_MS
import com.shawnkowalchuk.milo.core.trip.parkedGps
import org.junit.Assert.assertEquals
import org.junit.Test

private const val SECOND_MS = 1_000L

/**
 * The driving alert's rules, handed the time MilO's clock gives while the phone's date is set
 * one day ahead for a few seconds (ADR-006). The rules are not changed. These were the
 * investigation's proofs (2026-10-07) of what they make of a time that is a day ahead: the
 * work-hours test going by tomorrow's weekday, the quiet time after an alert over at once, an
 * alert stamped tomorrow. Each now shows that the rules are no longer handed such a time.
 *
 * A report's AGE was always measured on the clock that counts from boot (`DrivingReceiver`,
 * `vehicleReport`), which no date change touches.
 */
class DrivingAlertClockJumpTest {
    /** What MilO's clock reads in a jump of the phone's date that begins at the true [atMs]. */
    private fun timeInsideAJumpAt(atMs: Long): Long {
        val phone = JumpingPhone(atMs - 4 * SECOND_MS)
        phone.clock.now()
        phone.setAhead()
        phone.advance(4 * SECOND_MS)
        return phone.clock.now()
    }

    @Test
    fun `the work-hours test goes by today's weekday while the phone reads tomorrow`() {
        // Friday 9 October, 10:15, a drive without the truck, while the phone says Saturday.
        // Before the fix: no alert, and one that was showing withdrawn.
        val friday = at(day = 9, hour = 10, minute = 15)
        assertEquals(friday, timeInsideAJumpAt(friday))
        assertEquals(
            DrivingVerdict.DRIVING_WITHOUT_THE_TRUCK,
            judgeDriving(drivingMoment(nowMs = timeInsideAJumpAt(friday))),
        )

        // The other way round: Sunday 11 October at 10:15, while the phone says Monday.
        val sunday = at(day = 11, hour = 10, minute = 15)
        assertEquals(
            DrivingVerdict.OUTSIDE_WORK_HOURS,
            judgeDriving(drivingMoment(nowMs = timeInsideAJumpAt(sunday))),
        )
    }

    @Test
    fun `the quiet time after an alert holds while the date is ahead`() {
        val alertedAt = TUESDAY_MORNING
        val fiveMinutesOn = timeInsideAJumpAt(alertedAt + 5 * MINUTE_MS)

        // Before the fix the 30 minutes had "passed", and a second alert was posted.
        assertEquals(
            DrivingVerdict.ALERTED_RECENTLY,
            judgeDriving(drivingMoment(lastAlertAtMs = alertedAt, nowMs = fiveMinutesOn)),
        )
    }

    @Test
    fun `an alert posted while the date is ahead is stamped now, and is quiet now, not tomorrow`() {
        // Posted during the seconds ahead: the time stored is MilO's.
        val stamped = timeInsideAJumpAt(TUESDAY_MORNING)
        assertEquals(TUESDAY_MORNING, stamped)

        assertEquals(
            DrivingVerdict.ALERTED_RECENTLY,
            judgeDriving(
                drivingMoment(lastAlertAtMs = stamped, nowMs = TUESDAY_MORNING + MINUTE_MS),
            ),
        )
        // The next day at the same time a real drive is alerted. Before the fix the stamp of
        // tomorrow silenced exactly that half hour.
        assertEquals(
            DrivingVerdict.DRIVING_WITHOUT_THE_TRUCK,
            judgeDriving(
                drivingMoment(
                    lastAlertAtMs = stamped,
                    nowMs = TUESDAY_MORNING + DAY_MS + 10 * MINUTE_MS,
                ),
            ),
        )
    }

    @Test
    fun `a trip that ends while the date is ahead is quiet after it like any other`() {
        // The driving alert is handed the newest end of a trip. One stamped inside the jump
        // carries MilO's time, and the five quiet minutes after it hold.
        val endedAt = timeInsideAJumpAt(TUESDAY_MORNING - 2 * MINUTE_MS)

        assertEquals(
            DrivingVerdict.TRIP_JUST_ENDED,
            judgeDriving(drivingMoment(lastTripEndedAtMs = endedAt)),
        )
    }

    @Test
    fun `a report of getting into a vehicle that arrives while the date is ahead is dated now`() {
        val realNow = at(day = 7, hour = 18, minute = 32)
        // DrivingReceiver: enteredVehicleAtMs(reports, container.clock()).
        val enteredAt = enteredVehicleAtMs(listOf(ENTERED), timeInsideAJumpAt(realNow))
        assertEquals(realNow - ENTERED.ageMs, enteredAt)

        // The trip worker reads GPS beside the parked truck from it: every 5 seconds for ten
        // minutes, and for the first hour of the wait every 30. Before the fix the report was
        // dated tomorrow, and GPS ran every 5 seconds for a day and ten minutes.
        val waitingSince = realNow - 40 * MINUTE_MS
        val gps = parkedGps(waitingSince, enteredAt, sensorWatching = true)
        assertEquals(realNow - ENTERED.ageMs + GPS_AFTER_VEHICLE_REPORT_MS, gps.fastUntilMs)
        assertEquals(waitingSince + PARKED_GPS_MS, gps.untilMs)
    }
}

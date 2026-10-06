package com.shawnkowalchuk.milo.platform.driving

import com.shawnkowalchuk.milo.core.schedule.DEFAULT_WORK_SCHEDULE
import com.shawnkowalchuk.milo.platform.bluetooth.TruckReading
import java.time.DayOfWeek
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When the phone's report of driving becomes a notification. The moment every test starts from
 * is the one the alert exists for ([drivingMoment]): a Tuesday morning inside the work hours, a
 * fresh report of entering a vehicle, the alert switched on, no trip being recorded and the
 * paired truck not connected. The three conditions that came with the review (a truck is
 * paired, no trip just ended, no alert just posted) are in `DrivingAlertLimitsTest`.
 */
class DrivingAlertRulesTest {
    // ---- When it is shown -------------------------------------------------------------------------

    @Test
    fun `driving in the work hours with no trip and the truck not connected shows the alert`() {
        val verdict = judgeDriving(drivingMoment())

        assertEquals(DrivingVerdict.DRIVING_WITHOUT_THE_TRUCK, verdict)
        assertEquals(DrivingAlertStep.SHOW, verdict.step)
    }

    @Test
    fun `a truck that cannot be read does not stop the alert`() {
        // The opposite of the trip rules, where unknown must never start anything. Here the
        // worst an unknown can cause is one notification.
        val verdict = judgeDriving(drivingMoment(truck = TruckReading.Answer.UNKNOWN))

        assertEquals(DrivingVerdict.DRIVING_TRUCK_UNKNOWN, verdict)
        assertEquals(DrivingAlertStep.SHOW, verdict.step)
    }

    @Test
    fun `only two verdicts show the alert, and both need a report of driving`() {
        val showing = DrivingVerdict.entries.filter { it.step == DrivingAlertStep.SHOW }

        assertEquals(
            listOf(DrivingVerdict.DRIVING_WITHOUT_THE_TRUCK, DrivingVerdict.DRIVING_TRUCK_UNKNOWN),
            showing,
        )
    }

    // ---- Each reason for keeping quiet ------------------------------------------------------------

    @Test
    fun `a trip that is being recorded needs no alert`() {
        val verdict = judgeDriving(drivingMoment(tripInProgress = true))

        assertEquals(DrivingVerdict.TRIP_IN_PROGRESS, verdict)
        assertEquals(DrivingAlertStep.WITHDRAW, verdict.step)
    }

    @Test
    fun `a connected truck needs no alert`() {
        val verdict = judgeDriving(drivingMoment(truck = TruckReading.Answer.CONNECTED))

        assertEquals(DrivingVerdict.TRUCK_CONNECTED, verdict)
        assertEquals(DrivingAlertStep.WITHDRAW, verdict.step)
    }

    @Test
    fun `the alert switched off shows nothing, and takes a showing alert away`() {
        val verdict = judgeDriving(drivingMoment(alertEnabled = false))

        assertEquals(DrivingVerdict.SWITCHED_OFF, verdict)
        assertEquals(DrivingAlertStep.WITHDRAW, verdict.step)
    }

    @Test
    fun `leaving the vehicle withdraws the alert, whatever else is true`() {
        val verdict = judgeDriving(drivingMoment(reports = listOf(LEFT)))

        assertEquals(DrivingVerdict.LEFT_THE_VEHICLE, verdict)
        assertEquals(DrivingAlertStep.WITHDRAW, verdict.step)
    }

    @Test
    fun `a delivery without a report about a vehicle changes nothing`() {
        val verdict = judgeDriving(drivingMoment(reports = emptyList()))

        assertEquals(DrivingVerdict.NOTHING_REPORTED, verdict)
        assertEquals(DrivingAlertStep.LEAVE, verdict.step)
    }

    // ---- Which report counts ----------------------------------------------------------------------

    @Test
    fun `only the newest report counts`() {
        // The phone hands reports over oldest first, and can hand over several at once.
        val enteredThenLeft = judgeDriving(drivingMoment(reports = listOf(ENTERED, LEFT)))
        val leftThenEntered = judgeDriving(drivingMoment(reports = listOf(LEFT, ENTERED)))

        assertEquals(DrivingVerdict.LEFT_THE_VEHICLE, enteredThenLeft)
        assertEquals(DrivingVerdict.DRIVING_WITHOUT_THE_TRUCK, leftThenEntered)
    }

    @Test
    fun `a report of entering a vehicle is acted on up to five minutes old and not beyond`() {
        val atTheLimit = ENTERED.copy(ageMs = MAX_DRIVING_REPORT_AGE_MS)
        val pastIt = ENTERED.copy(ageMs = MAX_DRIVING_REPORT_AGE_MS + 1)

        assertEquals(5 * 60_000L, MAX_DRIVING_REPORT_AGE_MS)
        assertEquals(
            DrivingVerdict.DRIVING_WITHOUT_THE_TRUCK,
            judgeDriving(drivingMoment(reports = listOf(atTheLimit))),
        )
        // This morning's drive, handed over again when MilO asked to be told once more.
        val stale = judgeDriving(drivingMoment(reports = listOf(pastIt)))
        assertEquals(DrivingVerdict.REPORT_TOO_OLD, stale)
        // It says nothing about now, so an alert that is showing is left alone too.
        assertEquals(DrivingAlertStep.LEAVE, stale.step)
    }

    @Test
    fun `an old report of leaving still withdraws the alert`() {
        val longAgo = LEFT.copy(ageMs = 3 * 60 * 60_000L)

        assertEquals(
            DrivingVerdict.LEFT_THE_VEHICLE,
            judgeDriving(drivingMoment(reports = listOf(longAgo))),
        )
    }

    // ---- The work hours ---------------------------------------------------------------------------

    @Test
    fun `the work hours are the schedule's own, to the stroke`() {
        fun verdictAt(hour: Int, minute: Int, second: Int = 0) =
            judgeDriving(drivingMoment(nowMs = at(day = 6, hour, minute, second)))

        // The same edges as a trip that started then: on the stroke of the start is inside,
        // on the stroke of the end is outside.
        assertEquals(DrivingVerdict.OUTSIDE_WORK_HOURS, verdictAt(7, 59, 59))
        assertEquals(DrivingVerdict.DRIVING_WITHOUT_THE_TRUCK, verdictAt(8, 0))
        assertEquals(DrivingVerdict.DRIVING_WITHOUT_THE_TRUCK, verdictAt(16, 29, 59))
        assertEquals(DrivingVerdict.OUTSIDE_WORK_HOURS, verdictAt(16, 30))
    }

    @Test
    fun `a day that is not tracked is outside the work hours all day`() {
        // The 10th is a Saturday.
        val saturday = judgeDriving(drivingMoment(nowMs = at(day = 10, hour = 10, minute = 15)))
        val tuesdayOff =
            judgeDriving(
                drivingMoment(
                    schedule = DEFAULT_WORK_SCHEDULE.withTracked(DayOfWeek.TUESDAY, false),
                ),
            )

        assertEquals(DrivingVerdict.OUTSIDE_WORK_HOURS, saturday)
        assertEquals(DrivingAlertStep.WITHDRAW, saturday.step)
        assertEquals(DrivingVerdict.OUTSIDE_WORK_HOURS, tuesdayOff)
    }

    @Test
    fun `the hours are read in the phone's time zone`() {
        // 10:15 in Edmonton is 01:15 the next day in Tokyo.
        val verdict = judgeDriving(drivingMoment(zone = ZoneId.of("Asia/Tokyo")))

        assertEquals(DrivingVerdict.OUTSIDE_WORK_HOURS, verdict)
    }

    // ---- Which reason is given when several hold --------------------------------------------------

    @Test
    fun `a trip in progress is named before the truck and the hours`() {
        val verdict =
            judgeDriving(
                drivingMoment(
                    tripInProgress = true,
                    truck = TruckReading.Answer.CONNECTED,
                    nowMs = at(day = 10, hour = 22, minute = 0),
                ),
            )

        assertEquals(DrivingVerdict.TRIP_IN_PROGRESS, verdict)
    }

    @Test
    fun `outside the work hours an unreadable truck shows nothing`() {
        val verdict =
            judgeDriving(
                drivingMoment(
                    truck = TruckReading.Answer.UNKNOWN,
                    nowMs = at(day = 6, hour = 19, minute = 0),
                ),
            )

        assertEquals(DrivingVerdict.OUTSIDE_WORK_HOURS, verdict)
    }

    // ---- Whether the phone is asked at all --------------------------------------------------------

    @Test
    fun `driving is watched for only with the switch on and the permission granted`() {
        assertTrue(DrivingWatch(alertEnabled = true, permissionGranted = true).wanted)
        assertFalse(DrivingWatch(alertEnabled = true, permissionGranted = false).wanted)
        assertFalse(DrivingWatch(alertEnabled = false, permissionGranted = true).wanted)
        assertFalse(DrivingWatch(alertEnabled = false, permissionGranted = false).wanted)
    }
}

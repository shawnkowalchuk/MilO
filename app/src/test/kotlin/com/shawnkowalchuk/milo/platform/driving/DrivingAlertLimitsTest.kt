package com.shawnkowalchuk.milo.platform.driving

import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.DetectedActivity
import com.shawnkowalchuk.milo.platform.bluetooth.TruckReading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private const val SECOND_MS = 1000L
private const val NANOS_PER_SECOND = 1_000_000_000L

/**
 * The three conditions that keep the alert from being a nuisance: a truck must be paired, no
 * trip may have ended in the last five minutes, and no alert may have been posted in the last
 * thirty. And how an event of the phone's driving detection is read as a report. Every test
 * starts from the moment the alert is shown at ([drivingMoment]).
 */
class DrivingAlertLimitsTest {
    private val now = TUESDAY_MORNING

    // ---- A truck is paired ------------------------------------------------------------------------

    @Test
    fun `with no truck paired nothing is shown, however the truck reads`() {
        // The phone as it is before pairing: every ride in any vehicle would alert otherwise,
        // and a tap would start a trip for a truck MilO does not know.
        val notConnected = judgeDriving(drivingMoment(truckPaired = false))
        val unknown =
            judgeDriving(drivingMoment(truckPaired = false, truck = TruckReading.Answer.UNKNOWN))

        assertEquals(DrivingVerdict.NO_TRUCK_PAIRED, notConnected)
        assertEquals(DrivingAlertStep.WITHDRAW, notConnected.step)
        assertEquals(DrivingVerdict.NO_TRUCK_PAIRED, unknown)
    }

    // ---- No trip ended in the last five minutes ---------------------------------------------------

    @Test
    fun `for five minutes after a trip's end a report shows nothing, and then it does`() {
        fun verdictAfter(sinceEndMs: Long) =
            judgeDriving(drivingMoment(lastTripEndedAtMs = now - sinceEndMs))

        assertEquals(5 * MINUTE_MS, AFTER_TRIP_QUIET_MS)
        // End trip pressed, and the phone reports the drive that has just been recorded.
        assertEquals(DrivingVerdict.TRIP_JUST_ENDED, verdictAfter(0))
        assertEquals(DrivingVerdict.TRIP_JUST_ENDED, verdictAfter(5 * MINUTE_MS - SECOND_MS))
        assertEquals(DrivingAlertStep.WITHDRAW, DrivingVerdict.TRIP_JUST_ENDED.step)
        assertEquals(DrivingVerdict.DRIVING_WITHOUT_THE_TRUCK, verdictAfter(5 * MINUTE_MS))
        assertEquals(DrivingVerdict.DRIVING_WITHOUT_THE_TRUCK, verdictAfter(3 * DAY_MS))
    }

    // ---- No alert in the last thirty minutes ------------------------------------------------------

    @Test
    fun `no second alert within thirty minutes of the last, and at thirty minutes there is one`() {
        fun verdictAfter(sinceAlertMs: Long) =
            judgeDriving(drivingMoment(lastAlertAtMs = now - sinceAlertMs))

        assertEquals(30 * MINUTE_MS, ALERT_QUIET_MS)
        assertEquals(DrivingVerdict.ALERTED_RECENTLY, verdictAfter(22 * SECOND_MS))
        assertEquals(DrivingVerdict.ALERTED_RECENTLY, verdictAfter(30 * MINUTE_MS - SECOND_MS))
        assertEquals(DrivingVerdict.DRIVING_WITHOUT_THE_TRUCK, verdictAfter(30 * MINUTE_MS))
    }

    @Test
    fun `an unreadable truck does not get round the limit on a second alert`() {
        val verdict =
            judgeDriving(
                drivingMoment(truck = TruckReading.Answer.UNKNOWN, lastAlertAtMs = now - MINUTE_MS),
            )

        assertEquals(DrivingVerdict.ALERTED_RECENTLY, verdict)
    }

    @Test
    fun `the limit leaves a showing alert alone, and every other reason still takes it away`() {
        val lately = now - MINUTE_MS

        // An alert that is still showing says something true, so it stays.
        assertEquals(DrivingAlertStep.LEAVE, DrivingVerdict.ALERTED_RECENTLY.step)
        // The limit is asked last: whatever would withdraw the alert without it still does.
        assertEquals(
            DrivingVerdict.TRIP_IN_PROGRESS,
            judgeDriving(drivingMoment(tripInProgress = true, lastAlertAtMs = lately)),
        )
        assertEquals(
            DrivingVerdict.TRUCK_CONNECTED,
            judgeDriving(
                drivingMoment(truck = TruckReading.Answer.CONNECTED, lastAlertAtMs = lately),
            ),
        )
        assertEquals(
            DrivingVerdict.OUTSIDE_WORK_HOURS,
            judgeDriving(drivingMoment(nowMs = at(6, 19, 0), lastAlertAtMs = at(6, 18, 59))),
        )
        assertEquals(
            DrivingVerdict.TRIP_JUST_ENDED,
            judgeDriving(drivingMoment(lastTripEndedAtMs = lately, lastAlertAtMs = lately)),
        )
        assertEquals(
            DrivingVerdict.LEFT_THE_VEHICLE,
            judgeDriving(drivingMoment(reports = listOf(LEFT), lastAlertAtMs = lately)),
        )
    }

    @Test
    fun `a time the phone's clock has not reached yet silences nothing`() {
        // The clock was set back after the time was stored. Counted as recent, it would keep
        // the alert quiet until the clock had caught up, however long that is.
        val ahead = now + 2 * DAY_MS

        assertEquals(
            DrivingVerdict.DRIVING_WITHOUT_THE_TRUCK,
            judgeDriving(drivingMoment(lastAlertAtMs = ahead, lastTripEndedAtMs = ahead)),
        )
    }

    // ---- Reading the phone's event ----------------------------------------------------------------

    @Test
    fun `entering a vehicle and leaving it are told apart`() {
        fun report(transition: Int) =
            vehicleReport(DetectedActivity.IN_VEHICLE, transition, eventNanos = 0, nowNanos = 0)

        assertEquals(true, report(ActivityTransition.ACTIVITY_TRANSITION_ENTER)?.entered)
        assertEquals(false, report(ActivityTransition.ACTIVITY_TRANSITION_EXIT)?.entered)
    }

    @Test
    fun `a report's age is the time since the phone noticed, in milliseconds`() {
        val report =
            vehicleReport(
                DetectedActivity.IN_VEHICLE,
                ActivityTransition.ACTIVITY_TRANSITION_ENTER,
                eventNanos = 1_000 * NANOS_PER_SECOND,
                nowNanos = 1_061 * NANOS_PER_SECOND + NANOS_PER_SECOND / 2,
            )

        assertEquals(VehicleReport(entered = true, ageMs = 61_500), report)
    }

    @Test
    fun `anything that is not a vehicle being entered or left is dropped`() {
        fun report(activity: Int, transition: Int = ActivityTransition.ACTIVITY_TRANSITION_ENTER) =
            vehicleReport(activity, transition, eventNanos = 0, nowNanos = NANOS_PER_SECOND)

        // Only vehicles are asked for. A phone that reports something else is not believed.
        assertNull(report(DetectedActivity.ON_FOOT))
        assertNull(report(DetectedActivity.ON_BICYCLE))
        assertNull(report(DetectedActivity.STILL))
        // A kind of transition that does not exist today.
        assertNull(report(DetectedActivity.IN_VEHICLE, transition = 7))
    }
}

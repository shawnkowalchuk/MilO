package com.shawnkowalchuk.milo.platform.car

import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.platform.system.PreflightProblem
import com.shawnkowalchuk.milo.platform.trip.StartFailure
import com.shawnkowalchuk.milo.platform.trip.TripActivity
import com.shawnkowalchuk.milo.platform.trip.TripTrigger
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the Android Auto screen shows in every state the trip controller, today's trips and the
 * setup checklist can be in. The screen itself cannot be tested off a car (there is no
 * Robolectric, STANDARDS section 11); these tests cover everything it decides.
 */
class CarScreenContentTest {
    // ---- The Status row, the This trip row and the button, state by state ----------------------

    @Test
    fun `no trip, and nothing known about the truck yet`() {
        val shown = carContent(TripActivity())

        assertEquals(CarStatus.NOT_RECORDING, shown.status)
        assertNull(shown.trip)
        assertEquals(CarAction.START_TRIP, shown.action)
    }

    @Test
    fun `no trip and the truck connected is plain Not recording`() {
        // Ended by hand with the truck still connected: automatic start is held off.
        val shown = carContent(TripActivity(truckConnected = true))

        assertEquals(CarStatus.NOT_RECORDING, shown.status)
        assertEquals(CarAction.START_TRIP, shown.action)
    }

    @Test
    fun `no trip and the truck not connected says so`() {
        val shown = carContent(TripActivity(truckConnected = false))

        assertEquals(CarStatus.TRUCK_NOT_CONNECTED, shown.status)
        assertNull(shown.trip)
        assertEquals(CarAction.START_TRIP, shown.action)
    }

    @Test
    fun `recording shows the kilometres to one decimal and the time in whole minutes`() {
        val shown = carContent(TripActivity(trip = carTrip(12_449.0), truckConnected = true))

        assertEquals(CarStatus.RECORDING, shown.status)
        assertEquals(TripFigures(kilometres = "12.4", hours = 0, minutes = 23), shown.trip)
        assertEquals(CarAction.END_TRIP, shown.action)
    }

    @Test
    fun `recording with nothing known about the truck is plain Recording`() {
        assertEquals(CarStatus.RECORDING, carContent(TripActivity(trip = carTrip())).status)
    }

    @Test
    fun `recording without the truck says the truck is not connected`() {
        // A trip started by hand that the truck never joined, or one Android Auto holds open.
        val shown = carContent(TripActivity(trip = carTrip(), truckConnected = false))

        assertEquals(CarStatus.RECORDING_WITHOUT_TRUCK, shown.status)
        assertEquals(CarAction.END_TRIP, shown.action)
    }

    @Test
    fun `in the grace period the screen waits for the truck and still offers End`() {
        val waiting = TripActivity(trip = carTrip(waitingForTruck = true), truckConnected = false)

        val shown = carContent(waiting)

        assertEquals(CarStatus.WAITING_FOR_TRUCK, shown.status)
        assertEquals(TripFigures(kilometres = "12.4", hours = 0, minutes = 23), shown.trip)
        assertEquals(CarAction.END_TRIP, shown.action)
    }

    @Test
    fun `setup incomplete is said only while no trip is open`() {
        val idle = carContent(TripActivity(truckConnected = false), setupNeedsAttention = true)
        val recording = carContent(TripActivity(trip = carTrip()), setupNeedsAttention = true)

        // It explains more than "truck not connected" does: a trip may not start by itself.
        assertEquals(CarStatus.SETUP_INCOMPLETE, idle.status)
        assertEquals(CarAction.START_TRIP, idle.action)
        assertEquals(CarStatus.RECORDING, recording.status)
    }

    // ---- A start that was refused ---------------------------------------------------------------

    @Test
    fun `a refused start names what the preflight found`() {
        val expected =
            mapOf(
                PreflightProblem.LOCATION_PERMISSION_MISSING to
                    CarStatus.REFUSED_LOCATION_PERMISSION,
                PreflightProblem.BACKGROUND_LOCATION_MISSING to
                    CarStatus.REFUSED_BACKGROUND_LOCATION,
                PreflightProblem.LOCATION_SWITCHED_OFF to CarStatus.REFUSED_LOCATION_OFF,
                PreflightProblem.BACKGROUND_RESTRICTED to CarStatus.REFUSED_BATTERY_RESTRICTED,
                PreflightProblem.BLUETOOTH_PERMISSION_MISSING to
                    CarStatus.REFUSED_BLUETOOTH_PERMISSION,
            )
        // A new kind of problem must get a line of its own here.
        assertEquals(PreflightProblem.entries.toSet(), expected.keys)

        for ((problem, status) in expected) {
            val shown = carContent(TripActivity(startFailure = StartFailure(listOf(problem))))

            assertEquals("$problem", status, shown.status)
            // The way out is to press Start again once it is put right.
            assertEquals("$problem", CarAction.START_TRIP, shown.action)
        }
    }

    @Test
    fun `a refused start with several problems names the first`() {
        val failure =
            StartFailure(
                listOf(
                    PreflightProblem.BACKGROUND_LOCATION_MISSING,
                    PreflightProblem.BLUETOOTH_PERMISSION_MISSING,
                ),
            )

        val shown = carContent(TripActivity(startFailure = failure))

        assertEquals(CarStatus.REFUSED_BACKGROUND_LOCATION, shown.status)
    }

    @Test
    fun `a start that Android itself refused says so`() {
        val failure = StartFailure(emptyList(), "ForegroundServiceStartNotAllowedException")

        val shown = carContent(TripActivity(startFailure = failure), setupNeedsAttention = true)

        // The refusal is the more exact explanation, so it comes before "setup incomplete".
        assertEquals(CarStatus.REFUSED_BY_ANDROID, shown.status)
    }

    @Test
    fun `a trip that is recording outranks an older refusal`() {
        val failure = StartFailure(listOf(PreflightProblem.LOCATION_SWITCHED_OFF))

        val shown = carContent(TripActivity(trip = carTrip(), startFailure = failure))

        assertEquals(CarStatus.RECORDING, shown.status)
    }

    @Test
    fun `the refusal lines, and only they, count as a refused start`() {
        // The screen shows its "could not start" message for exactly these.
        val flagged = CarStatus.entries.filter { it.startRefused }
        val named = CarStatus.entries.filter { it.name.startsWith("REFUSED_") }

        assertEquals(named, flagged)
        assertEquals(PreflightProblem.entries.size + 1, flagged.size)
    }

    // ---- The figures ----------------------------------------------------------------------------

    @Test
    fun `the time passes the hour and stays in whole minutes`() {
        val activity = TripActivity(trip = carTrip())

        val justUnderAMinute = carContent(activity, nowMs = STARTED_AT_MS + 59_999)
        val overAnHour = carContent(activity, nowMs = STARTED_AT_MS + 65 * MINUTE_MS + 30_000)

        assertEquals(0L, justUnderAMinute.trip?.minutes)
        assertEquals(1L, overAnHour.trip?.hours)
        assertEquals(5L, overAnHour.trip?.minutes)
    }

    @Test
    fun `a clock set back during a trip shows no time, not a negative one`() {
        val before = STARTED_AT_MS - 5 * MINUTE_MS

        val shown = carContent(TripActivity(trip = carTrip()), nowMs = before)

        assertEquals(0L, shown.trip?.hours)
        assertEquals(0L, shown.trip?.minutes)
    }

    @Test
    fun `the decimal separator follows the phone's language`() {
        val shown = carContent(TripActivity(trip = carTrip(12_449.0)), locale = Locale.GERMANY)

        assertEquals("12,4", shown.trip?.kilometres)
    }

    @Test
    fun `today is not available until it has been read`() {
        assertNull(carContent(TripActivity(), today = null).today)
    }

    @Test
    fun `today shows the count and the kilometres rounded once`() {
        val today = todayOf(20_000.0, 20_000.0, 1_249.0)

        val shown = carContent(TripActivity(), today = today)

        assertEquals(TodayFigures(tripCount = 3, kilometres = "41.2"), shown.today)
    }

    // ---- The button -----------------------------------------------------------------------------

    @Test
    fun `the button sends the triggers the phone's button sends, under the phone's own words`() {
        assertEquals(TripTrigger.MANUAL_START, CarAction.START_TRIP.trigger)
        assertEquals(TripTrigger.MANUAL_END, CarAction.END_TRIP.trigger)
        assertEquals(R.string.home_start_trip, CarAction.START_TRIP.labelRes)
        assertEquals(R.string.home_end_trip, CarAction.END_TRIP.labelRes)
        // The source is what tells a press on the car from one on the phone in the event log.
        assertTrue(CarAction.entries.all { it.source.startsWith("Android Auto") })
        assertFalse(CarAction.START_TRIP.source == CarAction.END_TRIP.source)
    }
}

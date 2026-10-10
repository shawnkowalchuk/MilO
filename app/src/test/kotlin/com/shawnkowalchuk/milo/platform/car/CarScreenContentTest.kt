package com.shawnkowalchuk.milo.platform.car

import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.odometer.OdometerFigure
import com.shawnkowalchuk.milo.core.odometer.OdometerReading
import com.shawnkowalchuk.milo.core.schedule.TripCategory
import com.shawnkowalchuk.milo.core.trip.TripStartCause
import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.core.util.DistanceUnit
import com.shawnkowalchuk.milo.data.settings.StoredVehicle
import com.shawnkowalchuk.milo.data.trip.Trip
import com.shawnkowalchuk.milo.data.trip.VehicleOdometer
import com.shawnkowalchuk.milo.platform.system.PreflightProblem
import com.shawnkowalchuk.milo.platform.trip.ParkedTruckWatch
import com.shawnkowalchuk.milo.platform.trip.StartFailure
import com.shawnkowalchuk.milo.platform.trip.TripActivity
import com.shawnkowalchuk.milo.platform.trip.TripTrigger
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the Android Auto screen shows in every state the trip controller, the month's trips and
 * the setup checklist can be in. The screen itself cannot be tested off a car (there is no
 * Robolectric, STANDARDS section 11); these tests cover everything it decides.
 */
class CarScreenContentTest {
    // ---- The status line, the trip's figures and the button, state by state --------------------

    @Test
    fun `no trip, and nothing known about the truck yet`() {
        val shown = carContent(TripActivity())

        assertEquals(CarStatus.NOT_RECORDING, shown.status)
        assertNull(shown.trip)
        assertEquals(CarAction.START_TRIP, shown.action)
    }

    @Test
    fun `no trip beside a parked, connected truck says that a trip starts when it moves`() {
        val waiting = TripActivity(truckConnected = true, parked = ParkedTruckWatch.WAITING_TO_MOVE)

        val shown = carContent(waiting)

        assertEquals(CarStatus.PARKED_WAITING, shown.status)
        assertNull(shown.trip)
        // Start is still the button: a press starts a trip at once.
        assertEquals(CarAction.START_TRIP, shown.action)
        // That is no failure, so it is said even while the setup checklist has something open.
        assertEquals(
            CarStatus.PARKED_WAITING,
            carContent(waiting, setupNeedsAttention = true).status,
        )
    }

    @Test
    fun `a truck MilO has stopped watching says so, and what to press`() {
        val left = TripActivity(truckConnected = true, parked = ParkedTruckWatch.NO_LONGER_WATCHED)

        assertEquals(CarStatus.PARKED_NOT_WATCHED, carContent(left).status)
        assertEquals(CarAction.START_TRIP, carContent(left).action)
        // A setup that is not in order explains more, and comes first.
        val incomplete = carContent(left, setupNeedsAttention = true)
        assertEquals(CarStatus.SETUP_INCOMPLETE, incomplete.status)
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
    fun `with miles chosen the trip is in miles, and the Business row is the one made in miles`() {
        val recording = TripActivity(trip = carTrip(12_449.0), truckConnected = true)
        val inMiles = businessOf(today = "8.3", unit = DistanceUnit.MILES)

        val shown = carContent(recording, DistanceUnit.MILES, inMiles)

        assertEquals(TripFigures(kilometres = "7.7", hours = 0, minutes = 23), shown.trip)
        assertEquals(inMiles, shown.business)
        assertEquals(DistanceUnit.MILES, shown.unit)
        // Figures still in kilometres a moment after the unit was changed are not printed
        // under "mi": the row says that it is not available until the new ones arrive.
        assertNull(carContent(recording, DistanceUnit.MILES, businessOf()).business)
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

    // ---- The Business row -----------------------------------------------------------------------

    private val edmonton = ZoneId.of("America/Edmonton")
    private val october6 = LocalDate.of(2026, 10, 6)
    private var nextId = 1L

    /** A finished trip that started at [start], local time in Edmonton. */
    private fun stored(
        start: String,
        metres: Double,
        category: TripCategory = TripCategory.BUSINESS,
    ): Trip {
        val startedAtMs = LocalDateTime.parse(start).atZone(edmonton).toInstant().toEpochMilli()
        return Trip(
            id = nextId++,
            startedAtMs = startedAtMs,
            endedAtMs = startedAtMs + 20 * MINUTE_MS,
            status = TripStatus.FINISHED,
            startedBy = TripStartCause.TRUCK,
            truckSeen = true,
            distanceMetres = metres,
            category = category,
        )
    }

    private fun october() = listOf(
        stored("2026-10-01T08:00", 1_149.0),
        stored("2026-10-02T08:00", 1_149.0),
        stored("2026-10-06T08:00", 1_149.0),
        stored("2026-10-06T09:00", 12_300.0),
        stored("2026-10-06T10:00", 5_000.0, TripCategory.PERSONAL),
        stored("2026-10-03T08:00", 9_000.0, TripCategory.PERSONAL),
    )

    private fun odometer(value: Long, unit: DistanceUnit = DistanceUnit.KILOMETRES) =
        OdometerFigure(
            value = value,
            unit = unit,
            reading = OdometerReading(atMs = STARTED_AT_MS, value = value, unit = unit),
            drivenTenths = 0,
            estimated = false,
        )

    private fun figures(
        monthTrips: List<Trip>,
        unit: DistanceUnit = DistanceUnit.KILOMETRES,
        centsPerKm: Int? = 70,
        odometer: OdometerFigure? = odometer(84_212),
    ) = businessFigures(
        BusinessInput(october6, edmonton, monthTrips, centsPerKm, odometer),
        Locale.CANADA,
        unit,
    )

    @Test
    fun `the Business row is not available until it has been read`() {
        assertNull(carContent(TripActivity(), business = null).business)
    }

    @Test
    fun `today and the month are the Business trips, added up as Home and the report add up`() {
        val shown = figures(october())

        // Today: 1.1 + 12.3 km. The Personal 5.0 km of the same day is not in it.
        assertEquals("13.4", shown.today)
        // The month: 1.1 + 1.1 + 1.1 + 12.3 km, each trip rounded to a tenth first. The metres
        // added up and rounded once would be 15.7.
        assertEquals("15.6", shown.month)
        assertEquals("October", shown.monthName)
        assertEquals(DistanceUnit.KILOMETRES, shown.unit)
    }

    @Test
    fun `the dollars are the month's Business kilometres at the rate, in whole dollars`() {
        // 15.6 km at $0.70 a kilometre is $10.92.
        assertEquals("$11", figures(october()).dollars)
        assertEquals("$0", figures(emptyList()).dollars)
        // Until the rate has been read there are none, and the distances stand without them.
        val withoutRate = figures(october(), centsPerKm = null)
        assertNull(withoutRate.dollars)
        assertEquals("15.6", withoutRate.month)
    }

    @Test
    fun `with miles chosen the distances are in miles and the dollars do not move`() {
        val shown =
            figures(october(), DistanceUnit.MILES, odometer = odometer(52_326, DistanceUnit.MILES))

        // 0.7 + 7.6 mi today, and 0.7 + 0.7 + 0.7 + 7.6 mi in the month.
        assertEquals("8.3", shown.today)
        assertEquals("9.7", shown.month)
        // Still priced from the 15.6 km: the rate is a rate per kilometre.
        assertEquals("$11", shown.dollars)
        assertEquals("52,326", shown.odometer)
        assertEquals(DistanceUnit.MILES, shown.unit)
    }

    @Test
    fun `the odometer is in whole units, and left out before its first reading`() {
        assertEquals("84,212", figures(october()).odometer)
        assertNull(figures(october(), odometer = null).odometer)
        // One worked out in the other unit, a moment after the unit was changed, is left out.
        assertNull(figures(october(), DistanceUnit.MILES).odometer)
    }

    @Test
    fun `with several vehicles the odometer is the one of the vehicle the trip is about`() {
        fun vehicle(address: String, value: Long) = VehicleOdometer(
            vehicle = StoredVehicle(address, name = null, associationId = null, pairedAtMs = 0),
            named = true,
            figure = odometer(value),
        )
        val both = listOf(vehicle("AA:AA:AA:AA:AA:01", 84_212), vehicle("AA:AA:AA:AA:AA:02", 9_100))

        assertEquals(9_100L, odometerShown(both, "AA:AA:AA:AA:AA:02")?.value)
        // With no vehicle named, or one that is not paired any more, it is the first one's.
        assertEquals(84_212L, odometerShown(both, vehicle = null)?.value)
        assertEquals(84_212L, odometerShown(both, "AA:AA:AA:AA:AA:09")?.value)
        assertNull(odometerShown(emptyList(), vehicle = null))
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

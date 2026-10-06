package com.shawnkowalchuk.milo.platform.address

import com.shawnkowalchuk.milo.core.trip.TripStartCause
import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.data.crash.CrashFileStore
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.trip.Trip
import com.shawnkowalchuk.milo.data.trip.TripRepository
import com.shawnkowalchuk.milo.platform.trip.CurrentTrip
import com.shawnkowalchuk.milo.platform.trip.FakeEventLogDao
import com.shawnkowalchuk.milo.platform.trip.FakeTripDao
import com.shawnkowalchuk.milo.platform.trip.TripActivity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private const val MINUTE_MS = 60_000L
private const val HOUR_MS = 60 * MINUTE_MS

private val SHOP = PlaceParts(houseNumber = "12", street = "Shop Rd", town = "Edmonton")
private val MAIN = PlaceParts(houseNumber = "48", street = "Main St", town = "Leduc")

/**
 * The address lookup as a whole, on stand-ins held in memory: which trips are asked about, what
 * is stored, what counts as an attempt and what the event log says.
 */
// runCurrent() is how a test lets the lookup's coroutines run. The API is marked experimental
// by the coroutines library; there is no stable equivalent.
@OptIn(ExperimentalCoroutinesApi::class)
class TripAddressesTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val trips = FakeTripDao()
    private val log = FakeEventLogDao()
    private val activity = MutableStateFlow(TripActivity())
    private var nowMs = 1_791_028_800_000L
    private var online = true

    /** What the geocoder answers for each latitude. Anything else has no address. */
    private val answers = mutableMapOf<Double, LookupAnswer>()
    private val asked = mutableListOf<Double>()

    private fun TestScope.addresses(): TripAddresses = TripAddresses(
        trips = TripRepository(trips),
        eventLog = EventLogRepository(log),
        crashFileStore = CrashFileStore(folder.root),
        lookup = { latitude, _ ->
            asked += latitude
            answers[latitude] ?: LookupAnswer.Places(emptyList())
        },
        isOnline = { online },
        tripActivity = activity,
        clock = { nowMs },
        scope = backgroundScope,
    ).also { runCurrent() }

    /** Stores a trip that runs from latitude [from] to latitude [to]. */
    private fun stored(from: Double, to: Double, status: TripStatus = TripStatus.FINISHED): Trip {
        val trip =
            Trip(
                id = trips.rows.size + 1L,
                startedAtMs = nowMs - HOUR_MS,
                endedAtMs = (nowMs - 30 * MINUTE_MS).takeIf { status != TripStatus.OPEN },
                status = status,
                startedBy = TripStartCause.TRUCK,
                truckSeen = true,
                distanceMetres = 12_300.0,
                startLatitude = from.takeIf { status != TripStatus.OPEN },
                startLongitude = (-113.5).takeIf { status != TripStatus.OPEN },
                endLatitude = to.takeIf { status != TripStatus.OPEN },
                endLongitude = (-113.5).takeIf { status != TripStatus.OPEN },
            )
        trips.rows += trip
        return trip
    }

    private fun row(id: Long): Trip = trips.rows.single { it.id == id }

    private fun logged(category: EventCategory = EventCategory.ADDRESS): List<String> =
        log.entries.filter { it.category == category }.map { it.message }

    private fun TestScope.pass(service: TripAddresses, reason: String = "the test") {
        service.catchUp(reason)
        runCurrent()
    }

    // ---- Finished trips ---------------------------------------------------------------------------

    @Test
    fun `a finished trip gets both addresses, and one line in the log`() = runTest {
        answers[53.1] = LookupAnswer.Places(listOf(SHOP))
        answers[53.2] = LookupAnswer.Places(listOf(MAIN))
        val trip = stored(from = 53.1, to = 53.2)

        val service = addresses()
        pass(service)
        // A second pass finds nothing left to ask.
        pass(service)

        assertEquals("12 Shop Rd, Edmonton", row(trip.id).startAddress)
        assertEquals("48 Main St, Leduc", row(trip.id).endAddress)
        assertEquals(0, row(trip.id).addressAttempts)
        assertEquals(listOf(53.1, 53.2), asked)
        assertEquals(listOf("Trip 1: start address found; end address found."), logged())
        // Nothing but the address columns changed.
        val untouched =
            row(trip.id).copy(startAddress = null, endAddress = null, addressLastAttemptAtMs = null)
        assertEquals(trip, untouched)
    }

    @Test
    fun `a discarded trip and a trip in progress are not looked up`() = runTest {
        stored(from = 53.1, to = 53.2, status = TripStatus.DISCARDED)
        stored(from = 53.3, to = 53.4, status = TripStatus.OPEN)

        pass(addresses())

        assertTrue(asked.isEmpty())
        assertTrue(logged().isEmpty())
    }

    // ---- When it fails ----------------------------------------------------------------------------

    @Test
    fun `without a network nothing is asked and no attempt is counted`() = runTest {
        online = false
        val trip = stored(from = 53.1, to = 53.2)
        val service = addresses()

        pass(service, reason = "process start")
        pass(service, reason = "the Trips screen")

        assertTrue(asked.isEmpty())
        assertEquals(0, row(trip.id).addressAttempts)
        assertNull(row(trip.id).addressLastAttemptAtMs)
        // One line for the stretch without a network, not one per pass.
        assertEquals(
            listOf(
                "Address lookup put off (process start): no network connection. " +
                    "Waiting: 1 finished trip.",
            ),
            logged(),
        )
    }

    @Test
    fun `a place with no address costs an attempt and keeps what was found`() = runTest {
        answers[53.1] = LookupAnswer.Places(listOf(SHOP))
        val trip = stored(from = 53.1, to = 53.2)

        pass(addresses())

        assertEquals("12 Shop Rd, Edmonton", row(trip.id).startAddress)
        assertNull(row(trip.id).endAddress)
        assertEquals(1, row(trip.id).addressAttempts)
        assertEquals(nowMs, row(trip.id).addressLastAttemptAtMs)
        assertEquals(
            listOf(
                "Trip 1: start address found; end address none (the geocoder knows no address " +
                    "there). Failed attempt 1 of 4; the next one is at least 2 min away.",
            ),
            logged(),
        )
    }

    @Test
    fun `a trip is asked about again only after its wait, and never after its last attempt`() =
        runTest {
            val trip = stored(from = 53.1, to = 53.2)
            val service = addresses()

            pass(service)
            pass(service)
            assertEquals("Asked twice within the wait", 1, row(trip.id).addressAttempts)

            for (wait in listOf(2 * MINUTE_MS, HOUR_MS, 24 * HOUR_MS)) {
                nowMs += wait
                pass(service)
            }
            assertEquals(MAX_ADDRESS_ATTEMPTS, row(trip.id).addressAttempts)
            assertTrue(logged().last().endsWith("this trip is not looked up again."))

            val askedSoFar = asked.size
            nowMs += 1_000 * HOUR_MS
            pass(service)
            assertEquals(askedSoFar, asked.size)
        }

    @Test
    fun `a failed lookup stops the pass, so the trips behind it lose no attempt`() = runTest {
        answers[53.3] = LookupAnswer.Failed("grpc failed")
        val older = stored(from = 53.1, to = 53.2)
        val newer = stored(from = 53.3, to = 53.4)

        pass(addresses())

        // The newest trip is asked about first. Its end is not asked for after the failure.
        assertEquals(listOf(53.3), asked)
        assertEquals(1, row(newer.id).addressAttempts)
        assertEquals(0, row(older.id).addressAttempts)
        assertEquals(
            listOf(
                "Trip 2: start address lookup failed (grpc failed); end address not asked. " +
                    "Failed attempt 1 of 4; the next one is at least 2 min away. " +
                    "1 other trip was not asked about.",
            ),
            logged(),
        )
    }

    @Test
    fun `a failure inside a pass is logged, and the next pass still runs`() = runTest {
        val trip = stored(from = 53.1, to = 53.2)
        answers[53.1] = LookupAnswer.Places(listOf(SHOP))
        answers[53.2] = LookupAnswer.Places(listOf(MAIN))
        trips.failNextAddressWrite = IllegalStateException("the disk is full")
        val service = addresses()

        pass(service)
        assertEquals(listOf("The address lookup failed"), logged(EventCategory.ERROR))
        assertNull(row(trip.id).startAddress)

        pass(service)
        assertEquals("12 Shop Rd, Edmonton", row(trip.id).startAddress)
    }

    // ---- The trip in progress ---------------------------------------------------------------------

    private fun inProgress(id: Long, latitude: Double?) = TripActivity(
        trip =
            CurrentTrip(
                tripId = id,
                startedAtMs = nowMs,
                startedBy = TripStartCause.TRUCK,
                distanceMetres = 0.0,
                waitingForTruck = false,
                startLatitude = latitude,
                startLongitude = latitude?.let { -113.5 },
            ),
    )

    @Test
    fun `the start of a trip in progress is looked up once it has a position`() = runTest {
        answers[53.1] = LookupAnswer.Places(listOf(SHOP))
        val open = stored(from = 0.0, to = 0.0, status = TripStatus.OPEN)
        val service = addresses()

        activity.value = inProgress(open.id, latitude = null)
        runCurrent()
        assertNull(service.openTripStart.value)

        activity.value = inProgress(open.id, latitude = 53.1)
        runCurrent()

        val expected = OpenTripStart(open.id, 53.1, -113.5, TripPlace.Known("12 Shop Rd, Edmonton"))
        assertEquals(expected, service.openTripStart.value)
        assertEquals(listOf("Trip 1 (in progress): start address found."), logged())
        // The row of a trip in progress is not touched.
        assertEquals(open, row(open.id))
    }

    @Test
    fun `a trip is looked up when it ends, reusing the start found while it was in progress`() =
        runTest {
            answers[53.1] = LookupAnswer.Places(listOf(SHOP))
            answers[53.2] = LookupAnswer.Places(listOf(MAIN))
            val open = stored(from = 0.0, to = 0.0, status = TripStatus.OPEN)
            val service = addresses()
            activity.value = inProgress(open.id, latitude = 53.1)
            runCurrent()

            // The controller closes the row, then publishes that no trip is in progress.
            trips.rows[0] =
                open.copy(
                    status = TripStatus.FINISHED,
                    endedAtMs = nowMs,
                    startLatitude = 53.1,
                    startLongitude = -113.5,
                    endLatitude = 53.2,
                    endLongitude = -113.5,
                )
            activity.value = TripActivity()
            runCurrent()

            assertEquals("12 Shop Rd, Edmonton", row(open.id).startAddress)
            assertEquals("48 Main St, Leduc", row(open.id).endAddress)
            assertEquals("The start was asked for once only", listOf(53.1, 53.2), asked)
            assertEquals(open.id, service.openTripStart.value?.tripId)
        }
}

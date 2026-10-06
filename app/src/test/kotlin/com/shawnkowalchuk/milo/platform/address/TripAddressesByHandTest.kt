package com.shawnkowalchuk.milo.platform.address

import com.shawnkowalchuk.milo.core.schedule.DEFAULT_WORK_SCHEDULE
import com.shawnkowalchuk.milo.core.trip.TripStartCause
import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.data.crash.CrashFileStore
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.trip.Trip
import com.shawnkowalchuk.milo.data.trip.TripEdit
import com.shawnkowalchuk.milo.data.trip.TripRepository
import com.shawnkowalchuk.milo.data.trip.TypedAddress
import com.shawnkowalchuk.milo.data.trip.TypedTrip
import com.shawnkowalchuk.milo.platform.trip.FakeEventLogDao
import com.shawnkowalchuk.milo.platform.trip.FakeTripDao
import com.shawnkowalchuk.milo.platform.trip.TripActivity
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
 * The address lookup and an address that is Shawn's own: one he typed, one he emptied, and the
 * two of a trip he added by hand. The lookup must never ask about such an address and never
 * write to it. On the same stand-ins as `TripAddressesTest`.
 */
// runCurrent() is how a test lets the lookup's coroutines run. The API is marked experimental
// by the coroutines library; there is no stable equivalent.
@OptIn(ExperimentalCoroutinesApi::class)
class TripAddressesByHandTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val edmonton = ZoneId.of("America/Edmonton")
    private val trips = FakeTripDao()
    private val repository = TripRepository(trips)
    private val log = FakeEventLogDao()
    private val nowMs = 1_791_028_800_000L

    /** What the geocoder answers for each latitude, and what it was asked about. */
    private val answers =
        mapOf(53.1 to LookupAnswer.Places(listOf(SHOP)), 53.2 to LookupAnswer.Places(listOf(MAIN)))
    private val asked = mutableListOf<Double>()

    private fun TestScope.lookUp() {
        val service =
            TripAddresses(
                trips = repository,
                eventLog = EventLogRepository(log),
                crashFileStore = CrashFileStore(folder.root),
                lookup = { latitude, _ ->
                    asked += latitude
                    answers[latitude] ?: LookupAnswer.Places(emptyList())
                },
                isOnline = { true },
                tripActivity = MutableStateFlow(TripActivity()),
                clock = { nowMs },
                scope = backgroundScope,
            )
        runCurrent()
        service.catchUp("the test")
        runCurrent()
    }

    /** Stores a finished trip from latitude 53.1 to 53.2 that has no address yet. */
    private fun recorded(): Trip {
        val trip =
            Trip(
                id = trips.rows.size + 1L,
                startedAtMs = nowMs - HOUR_MS,
                endedAtMs = nowMs - 30 * MINUTE_MS,
                status = TripStatus.FINISHED,
                startedBy = TripStartCause.TRUCK,
                truckSeen = true,
                distanceMetres = 12_300.0,
                startLatitude = 53.1,
                startLongitude = -113.5,
                endLatitude = 53.2,
                endLongitude = -113.5,
            )
        trips.rows += trip
        return trip
    }

    private fun row(id: Long): Trip = trips.rows.single { it.id == id }

    private suspend fun edit(tripId: Long, edit: TripEdit) {
        repository.editByHand(tripId, edit, DEFAULT_WORK_SCHEDULE, edmonton)
    }

    private fun logged(): List<String> =
        log.entries.filter { it.category == EventCategory.ADDRESS }.map { it.message }

    @Test
    fun `a typed address is kept, and only the other end is looked up`() = runTest {
        val trip = recorded()
        edit(trip.id, TripEdit(startAddress = TypedAddress("Home")))

        lookUp()

        assertEquals("Home", row(trip.id).startAddress)
        assertEquals("48 Main St, Leduc", row(trip.id).endAddress)
        // The start was not even asked about.
        assertEquals(listOf(53.2), asked)
        assertEquals(listOf("Trip 1: start address already stored; end address found."), logged())
    }

    @Test
    fun `an address that was emptied by hand is not looked up again`() = runTest {
        val trip = recorded()
        trips.rows[0] = trip.copy(startAddress = "12 Shop Rd, Edmonton")
        edit(trip.id, TripEdit(startAddress = TypedAddress(null)))

        lookUp()

        assertNull(row(trip.id).startAddress)
        assertEquals("48 Main St, Leduc", row(trip.id).endAddress)
        assertEquals(listOf(53.2), asked)
        assertEquals(
            listOf(
                "Trip 1: start address none (left empty by hand, so not looked up); " +
                    "end address found.",
            ),
            logged(),
        )
        // Nothing is missing that a later lookup could find, so no attempt is counted.
        assertEquals(0, row(trip.id).addressAttempts)
    }

    @Test
    fun `a trip whose two addresses are by hand is never asked about`() = runTest {
        val trip = recorded()
        edit(
            trip.id,
            TripEdit(startAddress = TypedAddress("Home"), endAddress = TypedAddress("Site")),
        )
        val edited = row(trip.id)

        lookUp()

        assertTrue(asked.isEmpty())
        assertTrue(logged().isEmpty())
        assertEquals(edited, row(trip.id))
    }

    @Test
    fun `a trip that was added by hand is never looked up, with or without addresses`() = runTest {
        val typed =
            TypedTrip(
                startedAtMs = nowMs - 2 * HOUR_MS,
                endedAtMs = nowMs - HOUR_MS,
                distanceMetres = 23_400.0,
                startAddress = "Shop",
                endAddress = null,
                category = null,
            )
        val added = repository.addByHand(typed, DEFAULT_WORK_SCHEDULE, edmonton)

        lookUp()

        assertTrue(asked.isEmpty())
        assertEquals(added, row(added.id))
        assertFalse(isDueForLookup(added, nowMs))
        assertEquals(TripPlace.Known("Shop"), added.startPlace())
        assertEquals(TripPlace.LeftBlank, added.endPlace())
    }

    @Test
    fun `a lookup that was under way while an address was typed does not overwrite it`() = runTest {
        val trip = recorded()
        // The lookup has read the trip and asked the geocoder. Before its answer is
        // stored, Shawn saves the edit form with a start address of his own.
        edit(trip.id, TripEdit(startAddress = TypedAddress("Home")))

        val stored =
            repository.recordAddressLookup(
                tripId = trip.id,
                startAddress = "12 Shop Rd, Edmonton",
                endAddress = "48 Main St, Leduc",
                stillLacking = false,
                atMs = nowMs,
            )

        assertTrue(stored)
        assertEquals("Home", row(trip.id).startAddress)
        assertEquals("48 Main St, Leduc", row(trip.id).endAddress)
    }

    @Test
    fun `the same holds for an address that was emptied while the lookup was under way`() =
        runTest {
            val trip = recorded()
            trips.rows[0] = trip.copy(startAddress = "12 Shop Rd, Edmonton")
            edit(trip.id, TripEdit(startAddress = TypedAddress(null)))

            repository.recordAddressLookup(trip.id, "12 Shop Rd, Edmonton", null, false, nowMs)

            assertNull(row(trip.id).startAddress)
        }

    @Test
    fun `after a restore the typed address is looked up again, from a fresh count`() = runTest {
        val trip = recorded()
        trips.rows[0] = trip.copy(addressAttempts = MAX_ADDRESS_ATTEMPTS)
        edit(trip.id, TripEdit(startAddress = TypedAddress("Home"), distanceMetres = 9_000.0))

        repository.restoreRecorded(trip.id, DEFAULT_WORK_SCHEDULE, edmonton)
        lookUp()

        assertEquals("12 Shop Rd, Edmonton", row(trip.id).startAddress)
        assertEquals("48 Main St, Leduc", row(trip.id).endAddress)
        assertEquals(listOf(53.1, 53.2), asked)
    }

    @Test
    fun `the query for trips that lack an address agrees with the rule in Kotlin`() = runTest {
        // Every mix of a present or missing address and a by-hand mark, for each end.
        val ends = listOf(null to false, null to true, "typed" to true, "found" to false)
        val template = recorded()
        trips.rows.clear()
        for ((start, startByHand) in ends) {
            for ((end, endByHand) in ends) {
                trips.rows +=
                    template.copy(
                        id = trips.rows.size + 1L,
                        startAddress = start,
                        startAddressByHand = startByHand,
                        endAddress = end,
                        endAddressByHand = endByHand,
                    )
            }
        }

        val found = repository.findTripsLackingAddress(MAX_ADDRESS_ATTEMPTS).map { it.id }.toSet()
        val due = trips.rows.filter { isDueForLookup(it, nowMs) }.map { it.id }.toSet()

        assertEquals(due, found)
        // Seven of the sixteen: those with an end that is missing and not by hand.
        assertEquals(7, found.size)
    }
}

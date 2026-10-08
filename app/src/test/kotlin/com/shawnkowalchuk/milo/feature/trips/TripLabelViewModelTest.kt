package com.shawnkowalchuk.milo.feature.trips

import com.shawnkowalchuk.milo.core.trip.TripStartCause
import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.trip.Trip
import com.shawnkowalchuk.milo.data.trip.TripRepository
import com.shawnkowalchuk.milo.platform.trip.FakeEventLogDao
import com.shawnkowalchuk.milo.platform.trip.FakeTripDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val HOUR_MS = 3_600_000L

// Where the trips of these tests end: the shop, and a site 5 km south of it.
private const val SHOP_LATITUDE = 53.5461
private const val SHOP_LONGITUDE = -113.4938
private const val SITE_LATITUDE = 53.5011

/**
 * A trip's label on the Trips screen (2026-10-08): the dialog opens on the label used where the
 * trip ended, Save writes what was chosen, and every save leaves one line in the event log. On
 * the stand-in trips table.
 */
// A ViewModel's coroutines run on the main dispatcher, which a plain JVM test has to supply
// (setMain), and runCurrent() lets them run. Both are marked experimental by the coroutines
// library; there is no stable equivalent.
@OptIn(ExperimentalCoroutinesApi::class)
class TripLabelViewModelTest {
    private val trips = FakeTripDao()
    private val log = FakeEventLogDao()
    private val nowMs = 1_791_028_800_000L

    private fun TestScope.viewModel(): TripLabelViewModel {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        return TripLabelViewModel(TripRepository(trips), EventLogRepository(log)) { nowMs }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** Stores a finished trip that ended at [latitude], [SHOP_LONGITUDE]. */
    private fun stored(
        hoursAgo: Long,
        latitude: Double = SHOP_LATITUDE,
        label: String? = null,
        status: TripStatus = TripStatus.FINISHED,
    ): Trip {
        val trip =
            Trip(
                id = trips.rows.size + 1L,
                startedAtMs = nowMs - hoursAgo * HOUR_MS,
                endedAtMs = (nowMs - hoursAgo * HOUR_MS + HOUR_MS / 2).takeIf {
                    status != TripStatus.OPEN
                },
                status = status,
                startedBy = TripStartCause.TRUCK,
                truckSeen = true,
                distanceMetres = 12_340.0,
                endLatitude = latitude,
                endLongitude = SHOP_LONGITUDE,
                label = label,
            )
        trips.rows += trip
        return trip
    }

    private fun labelOf(id: Long): String? = trips.rows.single { it.id == id }.label

    private fun logged(category: EventCategory): List<String> =
        log.entries.filter { it.category == category }.map { it.message }

    @Test
    fun `the dialog opens on the label used where the trip ended, listed first`() = runTest {
        stored(hoursAgo = 30, label = "Shop")
        stored(hoursAgo = 20, latitude = SITE_LATITUDE, label = "Site")
        val trip = stored(hoursAgo = 2)
        val labels = viewModel()

        labels.onLabel(trip.id)
        runCurrent()

        val pick = labels.state.value.pick!!
        assertEquals("Shop", pick.choices.suggested)
        assertEquals(listOf("Shop", "Site"), pick.choices.used)
        assertEquals("Shop", pick.initial)
        // Only suggested: nothing is stored until Save.
        assertNull(labelOf(trip.id))
    }

    @Test
    fun `save gives the trip its label and logs it`() = runTest {
        stored(hoursAgo = 30, label = "Shop")
        val trip = stored(hoursAgo = 2)
        val labels = viewModel()

        labels.onLabel(trip.id)
        runCurrent()
        labels.onSave("Shop")
        runCurrent()

        assertEquals("Shop", labelOf(trip.id))
        assertNull(labels.state.value.pick)
        assertFalse(labels.state.value.saveFailed)
        assertEquals(
            listOf(
                "Trip ${trip.id}: labelled \"Shop\" on the Trips screen (was none, the label " +
                    "used where it ended before)",
            ),
            logged(EventCategory.TRIP),
        )
    }

    @Test
    fun `a typed label is cleaned, and written as the list writes it`() = runTest {
        stored(hoursAgo = 30, latitude = SITE_LATITUDE, label = "Site")
        val trip = stored(hoursAgo = 2)
        val labels = viewModel()

        labels.onLabel(trip.id)
        runCurrent()
        labels.onSave("  site ")
        runCurrent()

        assertEquals("Site", labelOf(trip.id))
    }

    @Test
    fun `No label takes the label away`() = runTest {
        val trip = stored(hoursAgo = 2, label = "Shop")
        val labels = viewModel()

        labels.onLabel(trip.id)
        runCurrent()
        assertEquals("Shop", labels.state.value.pick!!.current)
        labels.onSave(null)
        runCurrent()

        assertNull(labelOf(trip.id))
        assertEquals(
            listOf("Trip ${trip.id}: label taken away on the Trips screen (was \"Shop\")"),
            logged(EventCategory.TRIP),
        )
    }

    @Test
    fun `saving the label the trip has writes nothing`() = runTest {
        val trip = stored(hoursAgo = 2, label = "Shop")
        val labels = viewModel()

        labels.onLabel(trip.id)
        runCurrent()
        labels.onSave("shop")
        runCurrent()

        assertEquals("Shop", labelOf(trip.id))
        assertTrue(log.entries.isEmpty())
    }

    @Test
    fun `a trip that is still being recorded is refused, and the screen says so`() = runTest {
        val trip = stored(hoursAgo = 1, status = TripStatus.OPEN)
        val labels = viewModel()

        labels.onLabel(trip.id)
        runCurrent()
        labels.onSave("Shop")
        runCurrent()

        assertNull(labelOf(trip.id))
        assertTrue(labels.state.value.saveFailed)
        assertTrue(logged(EventCategory.TRIP).single().contains("label refused"))
    }

    @Test
    fun `cancel changes nothing`() = runTest {
        val trip = stored(hoursAgo = 2)
        val labels = viewModel()

        labels.onLabel(trip.id)
        runCurrent()
        labels.onDismiss()
        labels.onSave("Shop")
        runCurrent()

        assertNull(labels.state.value.pick)
        assertNull(labelOf(trip.id))
        assertTrue(log.entries.isEmpty())
    }
}

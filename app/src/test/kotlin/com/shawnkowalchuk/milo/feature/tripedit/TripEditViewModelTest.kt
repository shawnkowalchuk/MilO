package com.shawnkowalchuk.milo.feature.tripedit

import com.shawnkowalchuk.milo.core.util.DistanceUnit
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.settings.SettingsStore
import com.shawnkowalchuk.milo.data.settings.setDistanceUnit
import com.shawnkowalchuk.milo.data.trip.TripRepository
import com.shawnkowalchuk.milo.platform.trip.UnreadableSettingsFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Which unit the edit form opens in. A form keeps its unit for as long as it is open, so it
 * must open in the unit that is stored, also in the first moment of a process, when the unit
 * the app holds in memory is still kilometres.
 */
// A ViewModel's coroutines run on the main dispatcher, which a plain JVM test has to supply
// (setMain), and runCurrent() lets them run. Both are marked experimental by the coroutines
// library; there is no stable equivalent.
@OptIn(ExperimentalCoroutinesApi::class)
class TripEditViewModelTest : TripEditingFixture() {
    @After
    fun giveTheMainDispatcherBack() {
        Dispatchers.resetMain()
    }

    /** The form for [tripId] once it has opened, with [inMemory] as the unit the app holds. */
    private fun TestScope.opened(
        tripId: Long?,
        inMemory: DistanceUnit,
        screen: TripEditing = editing,
    ): TripEditUiState.Ready {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel =
            TripEditViewModel(
                tripId = tripId,
                editing = screen,
                unit = MutableStateFlow(inMemory),
                lookUpAddresses = {},
                clock = { nowMs },
                zone = { edmonton },
            )
        // The state is worked out only while something watches it, as the screen does.
        backgroundScope.launch { viewModel.state.collect {} }
        runCurrent()
        return viewModel.state.value as TripEditUiState.Ready
    }

    @Test
    fun `the form opens in the stored unit, also before the app has read it`() = runTest {
        SettingsStore(settingsFile).setDistanceUnit(DistanceUnit.MILES)
        val trip = recorded()

        // The first moment of a process: the unit in memory is still kilometres.
        val edited = opened(trip.id, inMemory = DistanceUnit.KILOMETRES)
        val added = opened(tripId = null, inMemory = DistanceUnit.KILOMETRES)

        assertEquals(DistanceUnit.MILES, edited.unit)
        assertEquals(DistanceUnit.MILES, added.unit)
    }

    @Test
    fun `with nothing chosen the form opens in kilometres`() = runTest {
        val trip = recorded()

        assertEquals(DistanceUnit.KILOMETRES, opened(trip.id, DistanceUnit.KILOMETRES).unit)
    }

    @Test
    fun `if the settings cannot be read the form is in the unit the other screens show`() =
        runTest {
            val unreadable =
                TripEditing(
                    trips = TripRepository(trips),
                    settings = SettingsStore(UnreadableSettingsFile),
                    eventLog = EventLogRepository(log),
                    clock = { nowMs },
                )

            val form = opened(tripId = null, inMemory = DistanceUnit.MILES, screen = unreadable)

            assertEquals(DistanceUnit.MILES, form.unit)
            assertEquals(
                listOf("The edit screen could not read the work schedule and the unit"),
                logged(EventCategory.ERROR),
            )
        }
}

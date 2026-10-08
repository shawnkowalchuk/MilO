package com.shawnkowalchuk.milo.feature.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.settings.SettingsStore
import com.shawnkowalchuk.milo.platform.trip.FakeEventLogDao
import com.shawnkowalchuk.milo.platform.trip.FakeSettingsFile
import com.shawnkowalchuk.milo.platform.trip.UnreadableSettingsFile
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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

/** A settings file that can be read, holds nothing, and refuses every write. */
private object WidgetReadOnlySettingsFile : DataStore<Preferences> {
    override val data: Flow<Preferences> = flowOf(emptyPreferences())

    override suspend fun updateData(
        transform: suspend (t: Preferences) -> Preferences,
    ): Preferences = throw IOException("the disk is full")
}

/**
 * The home-screen widget's tile in Settings: what it shows for what is stored, and that a press
 * is stored first and applied to the widget afterwards. On a settings file held in memory.
 */
// A ViewModel's coroutines run on the main dispatcher, which a plain JVM test has to supply
// (setMain), and runCurrent() lets them run. Both are marked experimental by the coroutines
// library; there is no stable equivalent.
@OptIn(ExperimentalCoroutinesApi::class)
class HomeWidgetViewModelTest {
    private val log = FakeEventLogDao()

    /** Each switch applied to the widget, with what was stored for it at that moment. */
    private val applied = mutableListOf<Pair<Boolean, Boolean>>()
    private var askedToAdd = 0

    private val october2026 =
        LocalDate.of(2026, 10, 7).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun TestScope.viewModel(
        file: DataStore<Preferences>,
        homeScreenTakesRequests: Boolean = true,
    ): HomeWidgetViewModel {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val settings = SettingsStore(file)
        return HomeWidgetViewModel(
            settings = settings,
            applySwitch = { on ->
                applied += on to runBlocking { settings.current().homeWidgetEnabled }
            },
            homeScreenTakesRequests = { homeScreenTakesRequests },
            askToAdd = { askedToAdd++ },
            eventLog = EventLogRepository(log),
            clock = { october2026 + 12 * 3_600_000L },
        )
    }

    /** The tile's state is worked out only while something watches it, as the screen does. */
    private fun TestScope.watch(viewModel: HomeWidgetViewModel) {
        backgroundScope.launch { viewModel.state.collect {} }
        runCurrent()
    }

    @After
    fun giveTheMainDispatcherBack() {
        Dispatchers.resetMain()
    }

    @Test
    fun `out of the box the widget is on, can be added, and its rate is 70 cents`() = runTest {
        val viewModel = viewModel(FakeSettingsFile())
        assertNull(viewModel.state.value)

        watch(viewModel)

        assertEquals(
            HomeWidgetCardState(
                enabled = true,
                canAskToAdd = true,
                centsPerKm = 70,
                couldNotSave = false,
            ),
            viewModel.state.value,
        )
    }

    @Test
    fun `a home screen that takes no requests gets no button`() = runTest {
        val viewModel = viewModel(FakeSettingsFile(), homeScreenTakesRequests = false)

        watch(viewModel)

        assertEquals(false, viewModel.state.value?.canAskToAdd)
    }

    @Test
    fun `the switch is stored first, then applied, and switched off nothing can be added`() =
        runTest {
            val file = FakeSettingsFile()
            val viewModel = viewModel(file)
            watch(viewModel)

            viewModel.onEnabled(false)
            runCurrent()

            assertEquals(false, SettingsStore(file).current().homeWidgetEnabled)
            // Applied once, with the new value in the file already.
            assertEquals(listOf(false to false), applied)
            assertEquals(false, viewModel.state.value?.enabled)
            assertEquals(false, viewModel.state.value?.canAskToAdd)
        }

    @Test
    fun `a typed rate is stored and shown`() = runTest {
        val file = FakeSettingsFile()
        val viewModel = viewModel(file)
        watch(viewModel)

        assertTrue(viewModel.onSaveRate("0.73"))
        runCurrent()

        assertEquals(73, SettingsStore(file).current().homeWidgetCentsPerKm)
        assertEquals(73, viewModel.state.value?.centsPerKm)
        assertEquals(false, viewModel.state.value?.couldNotSave)
    }

    @Test
    fun `what is not a rate is refused, and nothing is stored`() = runTest {
        val file = FakeSettingsFile()
        val viewModel = viewModel(file)
        watch(viewModel)

        assertFalse(viewModel.onSaveRate("0.705"))
        assertFalse(viewModel.onSaveRate("seventy"))
        runCurrent()

        assertEquals(70, SettingsStore(file).current().homeWidgetCentsPerKm)
        assertEquals(emptyList<String>(), log.entries.map { it.message })
    }

    @Test
    fun `a rate that cannot be stored is said on the tile and in the log`() = runTest {
        val viewModel = viewModel(WidgetReadOnlySettingsFile)
        watch(viewModel)

        assertTrue(viewModel.onSaveRate("0.73"))
        runCurrent()

        assertEquals(70, viewModel.state.value?.centsPerKm)
        assertEquals(true, viewModel.state.value?.couldNotSave)
        assertEquals(
            listOf("The Settings screen could not store the widget's rate"),
            log.entries.filter { it.category == EventCategory.ERROR }.map { it.message },
        )
    }

    @Test
    fun `the button asks the home screen`() = runTest {
        val viewModel = viewModel(FakeSettingsFile())

        viewModel.onAddToHomeScreen()

        assertEquals(1, askedToAdd)
    }

    @Test
    fun `a switch that cannot be stored is said on the tile and in the log, and not applied`() =
        runTest {
            val viewModel = viewModel(WidgetReadOnlySettingsFile)
            watch(viewModel)

            viewModel.onEnabled(false)
            runCurrent()

            assertEquals(true, viewModel.state.value?.enabled)
            assertEquals(true, viewModel.state.value?.couldNotSave)
            assertEquals(emptyList<Pair<Boolean, Boolean>>(), applied)
            assertEquals(
                listOf("The Settings screen could not store the widget's switch"),
                log.entries.filter { it.category == EventCategory.ERROR }.map { it.message },
            )
        }

    @Test
    fun `with settings that cannot be read the tile draws nothing`() = runTest {
        val viewModel = viewModel(UnreadableSettingsFile)

        watch(viewModel)

        assertNull(viewModel.state.value)
    }
}

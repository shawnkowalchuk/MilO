package com.shawnkowalchuk.milo.feature.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.settings.NothingRecordedStored
import com.shawnkowalchuk.milo.data.settings.SettingsStore
import com.shawnkowalchuk.milo.platform.trip.FakeEventLogDao
import com.shawnkowalchuk.milo.platform.trip.FakeSettingsFile
import com.shawnkowalchuk.milo.platform.trip.UnreadableSettingsFile
import java.io.IOException
import java.time.LocalDate
import java.time.LocalTime
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
import org.junit.Assert.assertNull
import org.junit.Test

/** A settings file that can be read, holds nothing, and refuses every write. */
private object ReadOnlySettingsFile : DataStore<Preferences> {
    override val data: Flow<Preferences> = flowOf(emptyPreferences())

    override suspend fun updateData(
        transform: suspend (t: Preferences) -> Preferences,
    ): Preferences = throw IOException("the disk is full")
}

/**
 * The card of the daily "nothing recorded" check: what it shows for what is stored, and that a
 * press is stored first and the check told afterwards. On a settings file held in memory.
 */
// A ViewModel's coroutines run on the main dispatcher, which a plain JVM test has to supply
// (setMain), and runCurrent() lets them run. Both are marked experimental by the coroutines
// library; there is no stable equivalent.
@OptIn(ExperimentalCoroutinesApi::class)
class NothingRecordedViewModelTest {
    private val log = FakeEventLogDao()

    /** Each time the check was told: by what name, and what was stored for it at that moment. */
    private val armed = mutableListOf<Pair<String, NothingRecordedStored>>()

    private fun TestScope.viewModel(file: DataStore<Preferences>): NothingRecordedViewModel {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val settings = SettingsStore(file)
        return NothingRecordedViewModel(
            settings = settings,
            // The check reads the stored settings when it is told, so what is stored at that
            // moment is what matters.
            armCheck = { source ->
                armed += source to runBlocking { settings.current().nothingRecorded }
            },
            eventLog = EventLogRepository(log),
            clock = { 0L },
        )
    }

    /** The card's state is worked out only while something watches it, as the screen does. */
    private fun TestScope.watch(viewModel: NothingRecordedViewModel) {
        backgroundScope.launch { viewModel.state.collect {} }
        runCurrent()
    }

    @After
    fun giveTheMainDispatcherBack() {
        Dispatchers.resetMain()
    }

    @Test
    fun `the card shows what is stored, and never the day a notification was shown`() {
        val stored =
            NothingRecordedStored(
                enabled = false,
                checkAt = LocalTime.of(10, 45),
                shownOn = LocalDate.of(2026, 10, 6),
            )

        assertEquals(
            NothingRecordedCardState(
                enabled = false,
                checkAt = LocalTime.of(10, 45),
                couldNotSave = true,
            ),
            nothingRecordedCardState(stored, couldNotSave = true),
        )
    }

    @Test
    fun `out of the box the card shows the check switched on at noon`() = runTest {
        val viewModel = viewModel(FakeSettingsFile())
        assertNull(viewModel.state.value)

        watch(viewModel)

        assertEquals(
            NothingRecordedCardState(
                enabled = true,
                checkAt = LocalTime.NOON,
                couldNotSave = false,
            ),
            viewModel.state.value,
        )
    }

    @Test
    fun `the switch is stored first, and then the check is told`() = runTest {
        val file = FakeSettingsFile()
        val viewModel = viewModel(file)
        watch(viewModel)

        viewModel.onEnabled(false)
        runCurrent()

        assertEquals(false, SettingsStore(file).current().nothingRecorded.enabled)
        // Told once, under the name the event log gives the card, with the new value in the
        // file already.
        assertEquals(
            listOf("the check was changed in Settings" to NothingRecordedStored(enabled = false)),
            armed,
        )
        assertEquals(false, viewModel.state.value?.enabled)
    }

    @Test
    fun `a picked time is stored, the card shows it, and the check is told`() = runTest {
        val file = FakeSettingsFile()
        val viewModel = viewModel(file)
        watch(viewModel)

        viewModel.onTimePicked(hour = 10, minute = 45)
        runCurrent()

        assertEquals(LocalTime.of(10, 45), SettingsStore(file).current().nothingRecorded.checkAt)
        assertEquals(LocalTime.of(10, 45), viewModel.state.value?.checkAt)
        assertEquals(LocalTime.of(10, 45), armed.single().second.checkAt)
    }

    @Test
    fun `a change that cannot be stored is said on the card and in the log, and tells nothing`() =
        runTest {
            val viewModel = viewModel(ReadOnlySettingsFile)
            watch(viewModel)

            viewModel.onEnabled(false)
            runCurrent()

            assertEquals(
                NothingRecordedCardState(
                    enabled = true,
                    checkAt = LocalTime.NOON,
                    couldNotSave = true,
                ),
                viewModel.state.value,
            )
            assertEquals(emptyList<Pair<String, NothingRecordedStored>>(), armed)
            assertEquals(
                listOf("The Settings screen could not store a change"),
                log.entries.filter { it.category == EventCategory.ERROR }.map { it.message },
            )
        }

    @Test
    fun `with settings that cannot be read the card draws nothing`() = runTest {
        val viewModel = viewModel(UnreadableSettingsFile)

        watch(viewModel)

        assertNull(viewModel.state.value)
    }
}

package com.shawnkowalchuk.milo.feature.whatsnew

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.settings.FirstRunStage
import com.shawnkowalchuk.milo.data.settings.SettingsStore
import com.shawnkowalchuk.milo.data.settings.setFirstRunStage
import com.shawnkowalchuk.milo.platform.trip.FakeEventLogDao
import com.shawnkowalchuk.milo.platform.trip.FakeSettingsFile
import com.shawnkowalchuk.milo.platform.trip.UnreadableSettingsFile
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A settings file past the first start that can be read and refuses every write. */
private object PastFirstStartReadOnlyFile : DataStore<Preferences> {
    override val data: Flow<Preferences> =
        flowOf(mutablePreferencesOf(stringPreferencesKey("first_run_stage") to "done"))

    override suspend fun updateData(
        transform: suspend (t: Preferences) -> Preferences,
    ): Preferences = throw IOException("the disk is full")
}

/**
 * What's new after an update (2026-10-08): it opens once, and a write that fails does not
 * open it again. On a settings file held in memory.
 */
// A ViewModel's coroutines run on the main dispatcher, which a plain JVM test has to supply
// (setMain), and runCurrent() lets them run. Both are marked experimental by the coroutines
// library; there is no stable equivalent.
@OptIn(ExperimentalCoroutinesApi::class)
class WhatsNewNoticeViewModelTest {
    private val log = FakeEventLogDao()

    private fun TestScope.viewModel(file: DataStore<Preferences>): WhatsNewNoticeViewModel {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel =
            WhatsNewNoticeViewModel(
                settings = SettingsStore(file),
                installedVersion = "0.2.0",
                eventLog = EventLogRepository(log),
                clock = { 0L },
            )
        // Worked out only while something watches it, as the app does.
        backgroundScope.launch { viewModel.due.collect {} }
        runCurrent()
        return viewModel
    }

    private val errors: List<String>
        get() = log.entries.filter { it.category == EventCategory.ERROR }.map { it.message }

    @After
    fun giveTheMainDispatcherBack() {
        Dispatchers.resetMain()
    }

    @Test
    fun `once shown it is stored as seen and is no longer due`() = runTest {
        val file = FakeSettingsFile()
        SettingsStore(file).setFirstRunStage(FirstRunStage.DONE)
        val viewModel = viewModel(file)
        assertTrue(viewModel.due.value)

        viewModel.onShown()
        runCurrent()

        assertFalse(viewModel.due.value)
        assertEquals("0.2.0", SettingsStore(file).current().whatsNewSeenVersion)
    }

    @Test
    fun `the first start's OK stores the version, so a fresh install never sees it`() = runTest {
        val file = FakeSettingsFile()
        val viewModel = viewModel(file)

        viewModel.onFirstStart()
        SettingsStore(file).setFirstRunStage(FirstRunStage.DONE)
        runCurrent()

        assertFalse(viewModel.due.value)
        assertEquals("0.2.0", SettingsStore(file).current().whatsNewSeenVersion)
    }

    @Test
    fun `a write that fails does not open it again, and the log says why`() = runTest {
        val viewModel = viewModel(PastFirstStartReadOnlyFile)
        assertTrue(viewModel.due.value)

        viewModel.onShown()
        runCurrent()

        assertFalse(viewModel.due.value)
        assertEquals(listOf("MilO could not store that What's new was shown for 0.2.0"), errors)
    }

    @Test
    fun `settings that cannot be read count as seen`() = runTest {
        val viewModel = viewModel(UnreadableSettingsFile)

        assertFalse(viewModel.due.value)
    }
}

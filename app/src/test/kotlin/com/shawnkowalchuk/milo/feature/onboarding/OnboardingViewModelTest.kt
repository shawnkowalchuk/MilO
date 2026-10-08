package com.shawnkowalchuk.milo.feature.onboarding

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.settings.FirstRunStage
import com.shawnkowalchuk.milo.data.settings.SettingsStore
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
import org.junit.Assert.assertNull
import org.junit.Test

/** A settings file that can be read, holds nothing, and refuses every write. */
private object OnboardingReadOnlySettingsFile : DataStore<Preferences> {
    override val data: Flow<Preferences> = flowOf(emptyPreferences())

    override suspend fun updateData(
        transform: suspend (t: Preferences) -> Preferences,
    ): Preferences = throw IOException("the disk is full")
}

/**
 * The first start (2026-10-08): which stage the app is shown, what OK and Done store, and that
 * neither a write nor a read that fails keeps anyone on the first page. On a settings file held
 * in memory.
 */
// A ViewModel's coroutines run on the main dispatcher, which a plain JVM test has to supply
// (setMain), and runCurrent() lets them run. Both are marked experimental by the coroutines
// library; there is no stable equivalent.
@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {
    private val log = FakeEventLogDao()

    private fun TestScope.viewModel(file: DataStore<Preferences>): OnboardingViewModel {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        return OnboardingViewModel(SettingsStore(file), EventLogRepository(log), clock = { 0L })
    }

    /** The stage is worked out only while something watches it, as the app does. */
    private fun TestScope.watch(viewModel: OnboardingViewModel) {
        backgroundScope.launch { viewModel.stage.collect {} }
        runCurrent()
    }

    private val errors: List<String>
        get() = log.entries.filter { it.category == EventCategory.ERROR }.map { it.message }

    @After
    fun giveTheMainDispatcherBack() {
        Dispatchers.resetMain()
    }

    @Test
    fun `out of the box the first page is shown`() = runTest {
        val viewModel = viewModel(FakeSettingsFile())
        assertNull(viewModel.stage.value)

        watch(viewModel)

        assertEquals(FirstRunStage.INTRO, viewModel.stage.value)
    }

    @Test
    fun `OK leads to Setup with its Done button, and Done ends the first start`() = runTest {
        val file = FakeSettingsFile()
        val viewModel = viewModel(file)
        watch(viewModel)

        viewModel.onOk()
        runCurrent()
        assertEquals(FirstRunStage.SETUP, viewModel.stage.value)
        assertEquals(FirstRunStage.SETUP, SettingsStore(file).current().firstRunStage)

        viewModel.onSetupDone()
        runCurrent()
        assertEquals(FirstRunStage.DONE, viewModel.stage.value)
        assertEquals(FirstRunStage.DONE, SettingsStore(file).current().firstRunStage)
        assertEquals(emptyList<String>(), errors)
    }

    @Test
    fun `a stage that cannot be stored still counts, and the log says why`() = runTest {
        val viewModel = viewModel(OnboardingReadOnlySettingsFile)
        watch(viewModel)

        viewModel.onOk()
        runCurrent()

        assertEquals(FirstRunStage.SETUP, viewModel.stage.value)
        assertEquals(
            listOf("MilO could not store how far the first start has got (SETUP)"),
            errors,
        )
    }

    @Test
    fun `settings that cannot be read open the app as it always opened`() = runTest {
        val viewModel = viewModel(UnreadableSettingsFile)

        watch(viewModel)

        assertEquals(FirstRunStage.DONE, viewModel.stage.value)
        assertEquals(
            listOf("MilO could not read how far the first start has got; it is not shown"),
            errors,
        )
    }

    @Test
    fun `a stage once reached is not given back`() = runTest {
        val viewModel = viewModel(OnboardingReadOnlySettingsFile)
        watch(viewModel)

        viewModel.onSetupDone()
        viewModel.onOk()
        runCurrent()

        assertEquals(FirstRunStage.DONE, viewModel.stage.value)
    }
}

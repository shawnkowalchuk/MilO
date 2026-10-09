package com.shawnkowalchuk.milo.feature.greeting

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
private object GreetingReadOnlySettingsFile : DataStore<Preferences> {
    override val data: Flow<Preferences> = flowOf(emptyPreferences())

    override suspend fun updateData(
        transform: suspend (t: Preferences) -> Preferences,
    ): Preferences = throw IOException("the disk is full")
}

/**
 * The greeting's safety catch: a greeting is counted before the mascot is drawn and the count
 * is cleared when the mascot was seen, so that a phone the drawing kills the app on stops
 * greeting. Each new ViewModel here is one more start of the app. On a settings file held in
 * memory.
 */
// A ViewModel's coroutines run on the main dispatcher, which a plain JVM test has to supply
// (setMain), and runCurrent() lets them run. Both are marked experimental by the coroutines
// library; there is no stable equivalent.
@OptIn(ExperimentalCoroutinesApi::class)
class GreetingViewModelTest {
    private val log = FakeEventLogDao()

    private fun TestScope.start(file: DataStore<Preferences>): GreetingViewModel {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        return GreetingViewModel(SettingsStore(file), EventLogRepository(log), clock = { 0L })
    }

    /** What the app is told once it asks: the answer is read only when something watches. */
    private fun TestScope.trusted(viewModel: GreetingViewModel): Boolean? {
        backgroundScope.launch { viewModel.trusted.collect {} }
        runCurrent()
        return viewModel.trusted.value
    }

    private val errors: List<String>
        get() = log.entries.filter { it.category == EventCategory.ERROR }.map { it.message }

    @After
    fun giveTheMainDispatcherBack() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a phone that has never greeted may`() = runTest {
        val viewModel = start(FakeSettingsFile())
        assertNull(viewModel.trusted.value)

        assertEquals(true, trusted(viewModel))
        assertEquals(emptyList<String>(), errors)
    }

    @Test
    fun `one greeting that never showed the mascot is forgiven, the second is not`() = runTest {
        val file = FakeSettingsFile()
        start(file).starting()

        val second = start(file)
        assertEquals(true, trusted(second))
        second.starting()

        assertEquals(false, trusted(start(file)))
        assertEquals(
            listOf("The greeting is off: 2 in a row began and never showed the mascot"),
            errors,
        )
        // And it stays off: nothing counts down.
        assertEquals(false, trusted(start(file)))
    }

    @Test
    fun `a mascot that was seen starts the count again`() = runTest {
        val file = FakeSettingsFile()
        start(file).starting()

        val second = start(file)
        second.starting()
        second.onShown()
        runCurrent()

        assertEquals(0, SettingsStore(file).current().greetingsUnfinished)
        assertEquals(true, trusted(start(file)))
        assertEquals(emptyList<String>(), errors)
    }

    @Test
    fun `a start's own count does not end its own greeting`() = runTest {
        val file = FakeSettingsFile()
        start(file).starting()
        val second = start(file)
        assertEquals(true, trusted(second))

        second.starting()
        runCurrent()

        assertEquals(2, SettingsStore(file).current().greetingsUnfinished)
        assertEquals(true, second.trusted.value)
    }

    @Test
    fun `a count that cannot be stored holds no greeting back, and the log says why`() = runTest {
        val viewModel = start(GreetingReadOnlySettingsFile)
        assertEquals(true, trusted(viewModel))

        viewModel.starting()
        viewModel.onShown()
        runCurrent()

        assertEquals(
            listOf(
                "MilO could not count the greeting it is about to show",
                "MilO could not store that the greeting was shown",
            ),
            errors,
        )
    }

    @Test
    fun `settings that cannot be read give no greeting`() = runTest {
        assertEquals(false, trusted(start(UnreadableSettingsFile)))
        assertEquals(
            listOf("MilO could not read whether the greeting may be shown; it is not shown"),
            errors,
        )
    }
}

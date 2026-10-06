package com.shawnkowalchuk.milo.feature.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.settings.SettingsStore
import com.shawnkowalchuk.milo.data.sound.OwnSoundStore
import com.shawnkowalchuk.milo.platform.trip.FakeEventLogDao
import com.shawnkowalchuk.milo.platform.trip.FakeSettingsFile
import com.shawnkowalchuk.milo.platform.trip.OwnTripSound
import com.shawnkowalchuk.milo.platform.trip.PickedAudio
import com.shawnkowalchuk.milo.platform.trip.UnreadableSettingsFile
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The one press on the Settings screen that has to tell something besides the settings file:
 * the driving alert's switch. What the screen shows is tested in `SettingsUiStateTest`; this is
 * the ViewModel itself, on a settings file held in memory.
 */
// A ViewModel's coroutines run on the main dispatcher, which a plain JVM test has to supply
// (setMain), and runCurrent() lets them run. Both are marked experimental by the coroutines
// library; there is no stable equivalent.
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val log = FakeEventLogDao()

    /** Each time the driving alert was told: by what name, and what the switch was stored as. */
    private val armed = mutableListOf<Pair<String, Boolean>>()

    /** Each time the monthly reminder was told: what was stored for it at that moment. */
    private val reminderLooks = mutableListOf<Pair<Boolean, Int>>()

    private val noFiles =
        object : PickedAudio {
            override fun open(uri: String): InputStream = throw FileNotFoundException(uri)

            override fun nameOf(uri: String): String? = null
        }

    private fun TestScope.viewModel(file: DataStore<Preferences>): SettingsViewModel {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val settings = SettingsStore(file)
        val eventLog = EventLogRepository(log)
        return SettingsViewModel(
            settings = settings,
            ownSound =
                OwnTripSound(
                    picked = noFiles,
                    playbackProblem = { null },
                    store = OwnSoundStore(File(temporaryFolder.root, "trip_sound")),
                    settings = settings,
                    eventLog = eventLog,
                    clock = { 0L },
                ),
            playSound = {},
            // The alert reads the stored switch when it is told, so what is stored at that
            // moment is what matters.
            armDrivingAlert = { source ->
                armed += source to runBlocking { settings.current().drivingAlertEnabled }
            },
            // The reminder reads the stored settings when it looks, like the alert.
            lookAtReminder = {
                val stored = runBlocking { settings.current() }
                reminderLooks += stored.reminderEnabled to stored.reminderDay
            },
            eventLog = eventLog,
            clock = { 0L },
        )
    }

    @After
    fun giveTheMainDispatcherBack() {
        Dispatchers.resetMain()
    }

    @Test
    fun `the driving alert's switch is stored first, and then the alert is told`() = runTest {
        val file = FakeSettingsFile()
        val viewModel = viewModel(file)

        viewModel.onDrivingAlertEnabled(false)
        runCurrent()

        assertEquals(false, SettingsStore(file).current().drivingAlertEnabled)
        // Told once, under the name the event log gives the switch, with the new value in
        // the file already.
        assertEquals(listOf("the Settings switch" to false), armed)

        viewModel.onDrivingAlertEnabled(true)
        runCurrent()

        assertEquals(true, SettingsStore(file).current().drivingAlertEnabled)
        assertEquals(listOf("the Settings switch" to false, "the Settings switch" to true), armed)
    }

    @Test
    fun `the reminder's switch and day are stored first, and then the reminder looks`() = runTest {
        val file = FakeSettingsFile()
        val viewModel = viewModel(file)

        viewModel.onReminderDayStep(later = true)
        runCurrent()
        viewModel.onReminderDayStep(later = true)
        runCurrent()
        viewModel.onReminderEnabled(false)
        runCurrent()

        assertEquals(3, SettingsStore(file).current().reminderDay)
        assertEquals(false, SettingsStore(file).current().reminderEnabled)
        // Each look found the value of its own press in the file already.
        assertEquals(listOf(true to 2, true to 3, false to 3), reminderLooks)
    }

    @Test
    fun `the reminder's day stops at the 1st and at the 31st`() = runTest {
        val file = FakeSettingsFile()
        val viewModel = viewModel(file)

        viewModel.onReminderDayStep(later = false)
        runCurrent()
        assertEquals(1, SettingsStore(file).current().reminderDay)

        SettingsStore(file).setReminderDay(31)
        viewModel.onReminderDayStep(later = true)
        runCurrent()
        assertEquals(31, SettingsStore(file).current().reminderDay)
    }

    @Test
    fun `a switch that could not be stored tells the alert nothing`() = runTest {
        val viewModel = viewModel(UnreadableSettingsFile)

        viewModel.onDrivingAlertEnabled(false)
        runCurrent()

        assertEquals(emptyList<Pair<String, Boolean>>(), armed)
        assertEquals(
            listOf("The Settings screen could not store a change"),
            log.entries.filter { it.category == EventCategory.ERROR }.map { it.message },
        )
    }
}

package com.shawnkowalchuk.milo.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.shawnkowalchuk.milo.core.schedule.DEFAULT_WORK_SCHEDULE
import com.shawnkowalchuk.milo.core.schedule.DayHours
import java.io.File
import java.time.DayOfWeek
import java.time.LocalTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Runs against a real DataStore file in a temporary folder. DataStore needs nothing from Android
 * once it is given a file, so this is a plain JVM test.
 */
class SettingsStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    /**
     * Opens the settings file, runs [block], and closes the file again. DataStore allows one open
     * instance per file, so "reopen" in a test means: let the first one finish, then call again.
     */
    private fun withStore(block: suspend (SettingsStore) -> Unit) {
        val file = File(temporaryFolder.root, "settings.preferences_pb")
        val job = Job()
        val dataStore =
            PreferenceDataStoreFactory.create(
                scope = CoroutineScope(Dispatchers.IO + job),
                produceFile = { file },
            )
        runBlocking {
            block(SettingsStore(dataStore))
            job.cancelAndJoin()
        }
    }

    @Test
    fun `a fresh install has the defaults from the brief`() = withStore { store ->
        val settings = store.current()

        assertEquals(MiloSettings(), settings)
        // Spelled out, because these two numbers are Shawn's decisions.
        assertEquals(120, settings.gracePeriodSeconds)
        assertEquals(300, settings.minimumTripDistanceMetres)
        assertEquals(true, settings.soundEnabled)
        assertEquals(null, settings.autoStartHeldOffSinceMs)
        // Monday to Friday, 08:00 to 16:30, and a trip outside it is saved as Personal.
        assertEquals(DEFAULT_WORK_SCHEDULE, settings.schedule)
        assertEquals(false, settings.ignoreTripsOutsideSchedule)
        // The driving alert is part of the brief's safety net, so it starts switched on.
        assertEquals(true, settings.drivingAlertEnabled)
    }

    @Test
    fun `the driving alert's switch survives the process`() {
        withStore { store -> store.setDrivingAlertEnabled(false) }

        withStore { store -> assertEquals(false, store.current().drivingAlertEnabled) }

        withStore { store -> store.setDrivingAlertEnabled(true) }

        withStore { store -> assertEquals(true, store.current().drivingAlertEnabled) }
    }

    @Test
    fun `the work schedule and the choice for trips outside it survive the process`() {
        val schedule =
            DEFAULT_WORK_SCHEDULE
                .withTracked(DayOfWeek.SATURDAY, true)
                .with(DayOfWeek.MONDAY, DayHours(true, LocalTime.of(7, 0), LocalTime.of(17, 15)))
        withStore { store ->
            store.setSchedule(schedule)
            store.setIgnoreTripsOutsideSchedule(true)
        }

        withStore { reopened ->
            val settings = reopened.current()
            assertEquals(schedule, settings.schedule)
            assertEquals(true, settings.ignoreTripsOutsideSchedule)
        }
    }

    @Test
    fun `storing the schedule changes no other setting, and the other way round`() =
        withStore { store ->
            store.setGracePeriodSeconds(300)
            store.setSchedule(DEFAULT_WORK_SCHEDULE.withTracked(DayOfWeek.FRIDAY, false))
            store.setMinimumTripDistanceMetres(500)

            assertEquals(
                MiloSettings(
                    gracePeriodSeconds = 300,
                    minimumTripDistanceMetres = 500,
                    schedule = DEFAULT_WORK_SCHEDULE.withTracked(DayOfWeek.FRIDAY, false),
                ),
                store.current(),
            )
        }

    @Test
    fun `every setting is read back as it was written`() = withStore { store ->
        store.setTruck("AA:BB:CC:DD:EE:FF", name = "Work truck", associationId = 12)
        store.setGracePeriodSeconds(300)
        store.setMinimumTripDistanceMetres(500)
        store.setSoundEnabled(false)
        store.setCustomSound("file:/data/sounds/own_trip_start_sound_1", name = "r2d2.mp3")
        store.setAutoStartHeldOffSinceMs(1_791_028_700_000)
        store.setLastProcessExitImportedAtMs(1_791_028_800_000)

        assertEquals(
            MiloSettings(
                truckAddress = "AA:BB:CC:DD:EE:FF",
                truckName = "Work truck",
                truckAssociationId = 12,
                gracePeriodSeconds = 300,
                minimumTripDistanceMetres = 500,
                soundEnabled = false,
                customSoundUri = "file:/data/sounds/own_trip_start_sound_1",
                customSoundName = "r2d2.mp3",
                autoStartHeldOffSinceMs = 1_791_028_700_000,
                lastProcessExitImportedAtMs = 1_791_028_800_000,
            ),
            store.current(),
        )
    }

    @Test
    fun `settings survive the process`() {
        withStore { store ->
            store.setTruck("AA:BB:CC:DD:EE:FF", name = "Work truck", associationId = 12)
            store.setAutoStartHeldOffSinceMs(1_791_028_700_000)
            store.setLastDrivingAlertAtMs(1_791_028_750_000)
        }

        withStore { reopened ->
            val settings = reopened.current()
            assertEquals("AA:BB:CC:DD:EE:FF", settings.truckAddress)
            assertEquals(1_791_028_700_000, settings.autoStartHeldOffSinceMs)
            assertEquals(1_791_028_750_000, settings.lastDrivingAlertAtMs)
        }
    }

    @Test
    fun `the flow delivers the new value after a change`() = withStore { store ->
        store.setGracePeriodSeconds(45)

        assertEquals(45, store.settings.first().gracePeriodSeconds)
    }

    @Test
    fun `a truck with no name and no association is stored without them`() = withStore { store ->
        store.setTruck("AA:BB:CC:DD:EE:FF", name = "Old name", associationId = 1)

        store.setTruck("11:22:33:44:55:66", name = null, associationId = null)

        val settings = store.current()
        assertEquals("11:22:33:44:55:66", settings.truckAddress)
        assertEquals(null, settings.truckName)
        assertEquals(null, settings.truckAssociationId)
    }

    @Test
    fun `clearing the truck forgets all three values and nothing else`() = withStore { store ->
        store.setTruck("AA:BB:CC:DD:EE:FF", name = "Work truck", associationId = 12)
        store.setGracePeriodSeconds(300)

        store.clearTruck()

        assertEquals(MiloSettings(gracePeriodSeconds = 300), store.current())
    }

    @Test
    fun `going back to the bundled sound forgets the custom sound and its name`() =
        withStore { store ->
            store.setSoundEnabled(false)
            store.setCustomSound("file:/data/sounds/own_trip_start_sound_1", name = "r2d2.mp3")

            store.clearCustomSound()

            // Whether the sound plays at all is another setting, and stays as it was.
            assertEquals(MiloSettings(soundEnabled = false), store.current())
        }

    @Test
    fun `a custom sound whose file had no name is stored without one`() = withStore { store ->
        store.setCustomSound("file:/data/sounds/own_trip_start_sound_1", name = "r2d2.mp3")

        store.setCustomSound("file:/data/sounds/own_trip_start_sound_2", name = null)

        val settings = store.current()
        assertEquals("file:/data/sounds/own_trip_start_sound_2", settings.customSoundUri)
        // Not the name of the sound before.
        assertEquals(null, settings.customSoundName)
    }

    @Test
    fun `the hold-off is released by storing no time`() = withStore { store ->
        store.setAutoStartHeldOffSinceMs(1_791_028_700_000)

        store.setAutoStartHeldOffSinceMs(null)

        assertEquals(null, store.current().autoStartHeldOffSinceMs)
    }

    @Test
    fun `a grace period of zero is allowed`() = withStore { store ->
        store.setGracePeriodSeconds(0)

        assertEquals(0, store.current().gracePeriodSeconds)
    }

    @Test
    fun `nonsense values are refused and nothing is stored`() = withStore { store ->
        assertRefused { store.setGracePeriodSeconds(-1) }
        assertRefused { store.setMinimumTripDistanceMetres(-1) }
        assertRefused { store.setTruck(" ", name = null, associationId = null) }
        assertRefused { store.setTruck("AA:BB:CC:DD:EE:FF", name = " ", associationId = null) }
        assertRefused { store.setCustomSound("", name = "r2d2.mp3") }
        assertRefused { store.setCustomSound("file:/data/sounds/own", name = " ") }
        assertRefused { store.setAutoStartHeldOffSinceMs(-1) }
        assertRefused { store.setLastProcessExitImportedAtMs(-1) }
        assertRefused { store.setLastDrivingAlertAtMs(-1) }

        assertEquals(MiloSettings(), store.current())
    }

    /** assertThrows cannot call a suspend function, so the same check is spelled out. */
    private suspend fun assertRefused(write: suspend () -> Unit) {
        val refused =
            try {
                write()
                false
            } catch (expected: IllegalArgumentException) {
                expected.message.orEmpty().isNotBlank()
            }
        assertTrue("the value was accepted, or refused without saying why", refused)
    }

    @Test
    fun `a confirmed setup step is stored with its time and can be taken back`() =
        withStore { store ->
            store.setConfirmedAtMs(ConfirmedStep.XIAOMI_BATTERY_SAVER, 1_791_028_800_000)
            store.setConfirmedAtMs(ConfirmedStep.XIAOMI_RECENTS_LOCK, 1_791_028_900_000)

            assertEquals(
                mapOf(
                    ConfirmedStep.XIAOMI_BATTERY_SAVER to 1_791_028_800_000,
                    ConfirmedStep.XIAOMI_RECENTS_LOCK to 1_791_028_900_000,
                ),
                store.current().confirmedAtMs,
            )

            store.setConfirmedAtMs(ConfirmedStep.XIAOMI_BATTERY_SAVER, null)

            assertEquals(
                mapOf(ConfirmedStep.XIAOMI_RECENTS_LOCK to 1_791_028_900_000),
                store.current().confirmedAtMs,
            )
        }

    @Test
    fun `confirmations survive the process`() {
        withStore { store ->
            store.setConfirmedAtMs(ConfirmedStep.XIAOMI_AUTOSTART, 1_791_028_800_000)
        }

        withStore { reopened ->
            assertEquals(
                mapOf(ConfirmedStep.XIAOMI_AUTOSTART to 1_791_028_800_000),
                reopened.current().confirmedAtMs,
            )
        }
    }

    @Test
    fun `the names the confirmations are stored under never change`() {
        // These are written to the settings file on the phone. A changed name would silently
        // take back a confirmation Shawn gave.
        assertEquals(
            mapOf(
                ConfirmedStep.XIAOMI_AUTOSTART to "confirmed_xiaomi_autostart_at_ms",
                ConfirmedStep.XIAOMI_BATTERY_SAVER to "confirmed_xiaomi_battery_saver_at_ms",
                ConfirmedStep.XIAOMI_OTHER_PERMISSIONS to
                    "confirmed_xiaomi_other_permissions_at_ms",
                ConfirmedStep.XIAOMI_RECENTS_LOCK to "confirmed_xiaomi_recents_lock_at_ms",
            ),
            ConfirmedStep.entries.associateWith { it.key },
        )
    }
}

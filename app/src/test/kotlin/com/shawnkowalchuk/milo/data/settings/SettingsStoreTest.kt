package com.shawnkowalchuk.milo.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
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
        assertEquals(false, settings.autoStartHeldOff)
    }

    @Test
    fun `every setting is read back as it was written`() = withStore { store ->
        store.setTruck("AA:BB:CC:DD:EE:FF", name = "Work truck", associationId = 12)
        store.setGracePeriodSeconds(300)
        store.setMinimumTripDistanceMetres(500)
        store.setSoundEnabled(false)
        store.setCustomSoundUri("content://media/external/audio/media/42")
        store.setAutoStartHeldOff(true)
        store.setLastProcessExitImportedAtMs(1_791_028_800_000)

        assertEquals(
            MiloSettings(
                truckAddress = "AA:BB:CC:DD:EE:FF",
                truckName = "Work truck",
                truckAssociationId = 12,
                gracePeriodSeconds = 300,
                minimumTripDistanceMetres = 500,
                soundEnabled = false,
                customSoundUri = "content://media/external/audio/media/42",
                autoStartHeldOff = true,
                lastProcessExitImportedAtMs = 1_791_028_800_000,
            ),
            store.current(),
        )
    }

    @Test
    fun `settings survive the process`() {
        withStore { store ->
            store.setTruck("AA:BB:CC:DD:EE:FF", name = "Work truck", associationId = 12)
            store.setAutoStartHeldOff(true)
        }

        withStore { reopened ->
            val settings = reopened.current()
            assertEquals("AA:BB:CC:DD:EE:FF", settings.truckAddress)
            assertEquals(true, settings.autoStartHeldOff)
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
    fun `the custom sound can be removed again`() = withStore { store ->
        store.setCustomSoundUri("content://media/external/audio/media/42")

        store.setCustomSoundUri(null)

        assertEquals(null, store.current().customSoundUri)
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
        assertRefused { store.setCustomSoundUri("") }
        assertRefused { store.setLastProcessExitImportedAtMs(-1) }

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
}

package com.shawnkowalchuk.milo.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The home-screen widget's switch and rate, against a real DataStore file in a temporary folder,
 * like `NothingRecordedStorageTest`.
 */
class WidgetStorageTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val file: File get() = File(temporaryFolder.root, "settings.preferences_pb")

    /** Opens the settings file, runs [block], and closes the file again. */
    private fun withStore(block: suspend (SettingsStore) -> Unit) {
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

    /** Writes [cents] under the rate's key as it is, past the checks of the setter. */
    private fun writeRawRate(cents: Int) = withStore { store ->
        store.dataStore.edit { it[intPreferencesKey("home_widget_cents_per_km")] = cents }
    }

    @Test
    fun `out of the box the widget is offered`() = withStore { store ->
        assertTrue(store.current().homeWidgetEnabled)
    }

    @Test
    fun `the switch is stored, and survives the file being opened again`() {
        withStore { store -> store.setHomeWidgetEnabled(false) }

        withStore { store ->
            assertFalse(store.current().homeWidgetEnabled)
            store.setHomeWidgetEnabled(true)
            assertTrue(store.current().homeWidgetEnabled)
        }
    }

    @Test
    fun `out of the box the rate is 70 cents`() = withStore { store ->
        assertEquals(70, store.current().homeWidgetCentsPerKm)
    }

    @Test
    fun `a rate is stored, and survives the file being opened again`() {
        withStore { store -> store.setHomeWidgetCentsPerKm(73) }

        withStore { store -> assertEquals(73, store.current().homeWidgetCentsPerKm) }
    }

    @Test
    fun `a stored rate MilO could not have written is read as 70 cents`() {
        writeRawRate(0)
        withStore { store -> assertEquals(70, store.current().homeWidgetCentsPerKm) }

        writeRawRate(501)
        withStore { store -> assertEquals(70, store.current().homeWidgetCentsPerKm) }
    }

    @Test
    fun `a rate outside a cent to five dollars is never stored`() = withStore { store ->
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { store.setHomeWidgetCentsPerKm(0) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { store.setHomeWidgetCentsPerKm(501) }
        }
        assertEquals(70, store.current().homeWidgetCentsPerKm)
    }
}

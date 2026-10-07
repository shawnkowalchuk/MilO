package com.shawnkowalchuk.milo.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The home-screen widget's switch, against a real DataStore file in a temporary folder, like
 * `NothingRecordedStorageTest`.
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
}

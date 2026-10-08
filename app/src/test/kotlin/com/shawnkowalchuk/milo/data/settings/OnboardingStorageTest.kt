package com.shawnkowalchuk.milo.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * How far the first start has got, against a real DataStore file in a temporary folder, like
 * `WidgetStorageTest`.
 */
class OnboardingStorageTest {
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
    fun `out of the box, and on a phone that had MilO before, the first page is next`() =
        withStore { store ->
            assertEquals(FirstRunStage.INTRO, store.current().firstRunStage)
        }

    @Test
    fun `each stage is stored, and survives the file being opened again`() {
        withStore { store -> store.setFirstRunStage(FirstRunStage.SETUP) }
        withStore { store -> assertEquals(FirstRunStage.SETUP, store.current().firstRunStage) }

        withStore { store -> store.setFirstRunStage(FirstRunStage.DONE) }
        withStore { store -> assertEquals(FirstRunStage.DONE, store.current().firstRunStage) }
    }

    @Test
    fun `the first stage removes what was stored`() = withStore { store ->
        store.setFirstRunStage(FirstRunStage.DONE)
        store.setFirstRunStage(FirstRunStage.INTRO)

        assertEquals(FirstRunStage.INTRO, store.current().firstRunStage)
    }

    @Test
    fun `a stage this build does not know is read as done`() {
        withStore { store ->
            store.dataStore.edit { it[stringPreferencesKey("first_run_stage")] = "tour" }
        }

        withStore { store -> assertEquals(FirstRunStage.DONE, store.current().firstRunStage) }
    }
}

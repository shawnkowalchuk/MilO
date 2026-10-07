package com.shawnkowalchuk.milo.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The settings of the "nothing recorded" check, against a real DataStore file in a temporary
 * folder, like `ReminderSettingsStoreTest`.
 */
class NothingRecordedStorageTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val file: File get() = File(temporaryFolder.root, "settings.preferences_pb")

    /** Opens the settings file, runs [block], and closes the file again. */
    private fun withStore(block: suspend (SettingsStore) -> Unit) {
        withFile { block(SettingsStore(it)) }
    }

    private fun withFile(block: suspend (DataStore<Preferences>) -> Unit) {
        val job = Job()
        val dataStore =
            PreferenceDataStoreFactory.create(
                scope = CoroutineScope(Dispatchers.IO + job),
                produceFile = { file },
            )
        runBlocking {
            block(dataStore)
            job.cancelAndJoin()
        }
    }

    @Test
    fun `out of the box the check is on, set to noon, and nothing was ever shown`() =
        withStore { store ->
            assertEquals(
                NothingRecordedStored(enabled = true, checkAt = LocalTime.NOON, shownOn = null),
                store.current().nothingRecorded,
            )
        }

    @Test
    fun `the three are read back as written, and survive the process`() {
        withStore { store ->
            store.setNothingRecordedEnabled(false)
            store.setNothingRecordedTime(LocalTime.of(10, 45))
            store.setNothingRecordedShownOn(LocalDate.of(2026, 10, 6))
        }

        withStore { store ->
            assertEquals(
                NothingRecordedStored(
                    enabled = false,
                    checkAt = LocalTime.of(10, 45),
                    shownOn = LocalDate.of(2026, 10, 6),
                ),
                store.current().nothingRecorded,
            )
        }
    }

    @Test
    fun `the first and the last minute of a day can both be stored`() = withStore { store ->
        for (time in listOf(LocalTime.MIDNIGHT, LocalTime.of(23, 59))) {
            store.setNothingRecordedTime(time)
            assertEquals(time, store.current().nothingRecorded.checkAt)
        }
    }

    @Test
    fun `a time with seconds in it is refused, and nothing is stored`() = withStore { store ->
        store.setNothingRecordedTime(LocalTime.of(11, 0))

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { store.setNothingRecordedTime(LocalTime.of(12, 0, 30)) }
        }

        assertEquals(LocalTime.of(11, 0), store.current().nothingRecorded.checkAt)
    }

    @Test
    fun `changing the check leaves every other setting as it was`() = withStore { store ->
        store.setTruck("AA:BB:CC:DD:EE:FF", "Work truck", 7)
        store.setReminderDay(12)
        val before = store.current()

        store.setNothingRecordedEnabled(false)
        store.setNothingRecordedTime(LocalTime.of(9, 30))
        store.setNothingRecordedShownOn(LocalDate.of(2026, 10, 6))

        assertEquals(before, store.current().copy(nothingRecorded = NothingRecordedStored()))
    }

    @Test
    fun `the key names in the file are the ones this version writes`() {
        // A renamed key silently resets the setting on the phone, so the names are pinned here.
        withStore { store ->
            store.setNothingRecordedEnabled(false)
            store.setNothingRecordedTime(LocalTime.of(10, 45))
            store.setNothingRecordedShownOn(LocalDate.of(2026, 10, 6))
        }

        withFile { dataStore ->
            val stored = dataStore.data.first()
            assertEquals(false, stored[booleanPreferencesKey("nothing_recorded_enabled")])
            assertEquals(645, stored[intPreferencesKey("nothing_recorded_minute_of_day")])
            assertEquals(
                LocalDate.of(2026, 10, 6).toEpochDay(),
                stored[longPreferencesKey("nothing_recorded_shown_on_day")],
            )
        }
    }

    @Test
    fun `nonsense in the file reads as the defaults, and never stops the settings being read`() {
        for (noMinute in listOf(-1, 1440, 9999)) {
            withFile { dataStore ->
                dataStore.edit {
                    it[intPreferencesKey("nothing_recorded_minute_of_day")] = noMinute
                    it[longPreferencesKey("nothing_recorded_shown_on_day")] = Long.MAX_VALUE
                }
            }

            withStore { store ->
                assertEquals(NothingRecordedStored(), store.current().nothingRecorded)
            }
        }
    }
}

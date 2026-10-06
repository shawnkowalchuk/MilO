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
import java.time.YearMonth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The monthly reminder's settings, against a real DataStore file in a temporary folder, like
 * `SettingsStoreTest`, which is at its size limit.
 */
class ReminderSettingsStoreTest {
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
    fun `out of the box the reminder is on, starts on the 1st, and was never shown`() =
        withStore { store ->
            val settings = store.current()

            assertTrue(settings.reminderEnabled)
            assertEquals(1, settings.reminderDay)
            assertNull(settings.reminderShown)
        }

    @Test
    fun `the three are read back as written, and survive the process`() {
        val shown = ReminderShown(YearMonth.of(2026, 9), LocalDate.of(2026, 10, 6))
        withStore { store ->
            store.setReminderEnabled(false)
            store.setReminderDay(31)
            store.setReminderShown(shown)
        }

        withStore { store ->
            val settings = store.current()
            assertEquals(false, settings.reminderEnabled)
            assertEquals(31, settings.reminderDay)
            assertEquals(shown, settings.reminderShown)
        }
    }

    @Test
    fun `a day that is no day of a month is refused, and nothing is stored`() = withStore { store ->
        store.setReminderDay(15)

        for (noDay in listOf(0, -1, 32, 99)) {
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { store.setReminderDay(noDay) }
            }
        }

        assertEquals(15, store.current().reminderDay)
    }

    @Test
    fun `changing the reminder leaves every other setting as it was`() = withStore { store ->
        store.setTruck("AA:BB:CC:DD:EE:FF", "Work truck", 7)
        store.setReportName("Sam Driver")
        val before = store.current()

        store.setReminderEnabled(false)
        store.setReminderDay(20)
        store.setReminderShown(ReminderShown(YearMonth.of(2026, 9), LocalDate.of(2026, 10, 6)))

        val after = store.current()
        assertEquals(
            before,
            after.copy(reminderEnabled = true, reminderDay = 1, reminderShown = null),
        )
    }

    @Test
    fun `the key names in the file are the ones this version writes`() {
        // A renamed key silently resets the setting on the phone, so the names are pinned here.
        val shown = ReminderShown(YearMonth.of(2026, 9), LocalDate.of(2026, 10, 6))
        withStore { store ->
            store.setReminderEnabled(false)
            store.setReminderDay(12)
            store.setReminderShown(shown)
        }

        withFile { dataStore ->
            val stored = dataStore.data.first()
            assertEquals(false, stored[booleanPreferencesKey("reminder_enabled")])
            assertEquals(12, stored[intPreferencesKey("reminder_day_of_month")])
            assertEquals(
                LocalDate.of(2026, 9, 1).toEpochDay(),
                stored[longPreferencesKey("reminder_shown_for_month_first_day")],
            )
            assertEquals(
                LocalDate.of(2026, 10, 6).toEpochDay(),
                stored[longPreferencesKey("reminder_shown_on_day")],
            )
        }
    }

    @Test
    fun `nonsense in the file reads as the defaults, and never stops the settings being read`() {
        withFile { dataStore ->
            dataStore.edit {
                it[intPreferencesKey("reminder_day_of_month")] = 40
                // Only one of the two values of "shown", and a number that is no day.
                it[longPreferencesKey("reminder_shown_on_day")] = Long.MAX_VALUE
            }
        }

        withStore { store ->
            val settings = store.current()
            assertEquals(1, settings.reminderDay)
            assertNull(settings.reminderShown)
            assertTrue(settings.reminderEnabled)
        }
    }

    @Test
    fun `half a record of the last reminder reads as none`() {
        withFile { dataStore ->
            dataStore.edit {
                it[longPreferencesKey("reminder_shown_for_month_first_day")] =
                    LocalDate.of(2026, 9, 1).toEpochDay()
            }
        }

        withStore { store -> assertNull(store.current().reminderShown) }
    }
}

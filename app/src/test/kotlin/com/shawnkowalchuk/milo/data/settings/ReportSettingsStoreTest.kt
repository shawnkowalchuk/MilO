package com.shawnkowalchuk.milo.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.shawnkowalchuk.milo.core.report.ReportPeriod
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
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The four settings of the report for the accountant, against a real DataStore file in a
 * temporary folder, like `SettingsStoreTest`, which is at its size limit.
 */
class ReportSettingsStoreTest {
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
    fun `nothing is set on a fresh install`() = withStore { store ->
        val settings = store.current()

        assertNull(settings.reportName)
        assertNull(settings.reportCompany)
        assertNull(settings.reportVehicle)
        assertNull(settings.accountantEmail)
    }

    @Test
    fun `the four are read back as written, and survive the process`() {
        withStore { store ->
            store.setReportName("Sam Driver")
            store.setReportCompany("Northside Electric Ltd.")
            store.setReportVehicle("2019 Ford F-150, plate ABC-123")
            store.setAccountantEmail("accounts@example.ca")
        }

        withStore { reopened ->
            val settings = reopened.current()
            assertEquals("Sam Driver", settings.reportName)
            assertEquals("Northside Electric Ltd.", settings.reportCompany)
            assertEquals("2019 Ford F-150, plate ABC-123", settings.reportVehicle)
            assertEquals("accounts@example.ca", settings.accountantEmail)
        }
    }

    @Test
    fun `each is forgotten by storing nothing, and the others stay`() = withStore { store ->
        store.setReportName("Sam Driver")
        store.setReportCompany("Northside Electric Ltd.")
        store.setReportVehicle("Ford F-150")
        store.setAccountantEmail("accounts@example.ca")

        store.setReportCompany(null)
        store.setAccountantEmail(null)

        val settings = store.current()
        assertEquals("Sam Driver", settings.reportName)
        assertNull(settings.reportCompany)
        assertEquals("Ford F-150", settings.reportVehicle)
        assertNull(settings.accountantEmail)
    }

    @Test
    fun `storing them changes no other setting`() = withStore { store ->
        store.setTruck("AA:BB:CC:DD:EE:FF", "Work truck", associationId = 7)
        store.setGracePeriodSeconds(150)
        val before = store.current()

        store.setReportName("Sam Driver")
        store.setAccountantEmail("accounts@example.ca")

        val expected = before.copy(
            reportName = "Sam Driver",
            accountantEmail = "accounts@example.ca",
        )
        assertEquals(expected, store.current())
    }

    @Test
    fun `an address that is not an email address is refused, and the one before stays`() =
        withStore { store ->
            store.setAccountantEmail("accounts@example.ca")

            for (wrong in listOf(
                "",
                "accounts",
                "accounts@example",
                "a@example.ca, b@example.ca",
            )) {
                try {
                    store.setAccountantEmail(wrong)
                    fail("\"$wrong\" was stored")
                } catch (refused: IllegalArgumentException) {
                    assertTrue(refused.message.orEmpty().contains("email address"))
                }
            }

            assertEquals("accounts@example.ca", store.current().accountantEmail)
        }

    @Test
    fun `a blank, untrimmed or overlong text is refused, and nothing is stored`() =
        withStore { store ->
            val wrong =
                listOf(
                    "",
                    "   ",
                    " Sam Driver",
                    "Sam Driver ",
                    "a".repeat(MAX_REPORT_TEXT_LENGTH + 1),
                )
            for (text in wrong) {
                for (set in listOf(
                    store::setReportName,
                    store::setReportCompany,
                    store::setReportVehicle,
                )) {
                    try {
                        set(text)
                        fail("\"$text\" was stored")
                    } catch (refused: IllegalArgumentException) {
                        assertTrue(refused.message.orEmpty().isNotEmpty())
                    }
                }
            }

            assertEquals(MiloSettings(), store.current())
        }

    @Test
    fun `the names the four are stored under never change`() {
        // These are written to the settings file on the phone. A changed name would silently
        // forget who the report is from and where it goes.
        withStore { store ->
            store.setReportName("Sam Driver")
            store.setReportCompany("Northside Electric Ltd.")
            store.setReportVehicle("Ford F-150")
            store.setAccountantEmail("accounts@example.ca")
        }

        withFile { raw ->
            val stored = raw.data.first()
            assertEquals("Sam Driver", stored[stringPreferencesKey("report_name")])
            assertEquals("Northside Electric Ltd.", stored[stringPreferencesKey("report_company")])
            assertEquals("Ford F-150", stored[stringPreferencesKey("report_vehicle")])
            assertEquals("accounts@example.ca", stored[stringPreferencesKey("accountant_email")])
            assertEquals(4, stored.asMap().size)
        }
    }
    private val september = ReportPeriod.Month(YearMonth.of(2026, 9))
    private val twoWeeks =
        ReportPeriod.Range(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 18))

    @Test
    fun `a report that waits for its answer survives the process, month or range`() {
        val month = ReportHandOver(september, 193, 22_191, 1_791_300_000_000)
        val range = ReportHandOver(twoWeeks, tripCount = 0, tenths = 0, atMs = 1_791_300_000_000)
        for (handOver in listOf(month, range)) {
            withStore { it.setReportHandOver(handOver) }

            withStore { reopened -> assertEquals(handOver, reopened.current().reportHandOver) }
        }
    }

    @Test
    fun `answering forgets the report that waited, and nothing else`() = withStore { store ->
        store.setReportName("Sam Driver")
        store.setReportHandOver(ReportHandOver(september, 193, 22_191, 5))

        store.setReportHandOver(null)

        assertEquals(MiloSettings(reportName = "Sam Driver"), store.current())
    }

    @Test
    fun `a hand-over with figures that cannot be a report's is refused`() = withStore { store ->
        val wrong =
            listOf(
                ReportHandOver(september, tripCount = -1, tenths = 1, atMs = 1),
                ReportHandOver(september, tripCount = 1, tenths = -1, atMs = 1),
                ReportHandOver(september, tripCount = 1, tenths = 1, atMs = -1),
            )
        for (handOver in wrong) {
            try {
                store.setReportHandOver(handOver)
                fail("$handOver was stored")
            } catch (refused: IllegalArgumentException) {
                assertTrue(refused.message.orEmpty().isNotEmpty())
            }
        }

        assertNull(store.current().reportHandOver)
    }

    @Test
    fun `a file that holds only part of a hand-over, or nonsense, reads as nothing waiting`() {
        val kind = stringPreferencesKey("report_handed_over_kind")
        val firstDay = longPreferencesKey("report_handed_over_first_day")
        val lastDay = longPreferencesKey("report_handed_over_last_day")
        val damage =
            listOf<(MutablePreferences) -> Unit>(
                { it.remove(longPreferencesKey("report_handed_over_at_ms")) },
                { it.remove(intPreferencesKey("report_handed_over_trip_count")) },
                { it[kind] = "WEEK" },
                { it[lastDay] = twoWeeks.firstDay.toEpochDay() - 1 },
                { it[firstDay] = Long.MAX_VALUE },
                { it[longPreferencesKey("report_handed_over_tenths")] = -5 },
            )
        for (damaged in damage) {
            withStore { it.setReportHandOver(ReportHandOver(twoWeeks, 4, 268, 5)) }
            withFile { raw -> raw.edit { damaged(it) } }

            // Nothing is asked about, so nothing can be recorded as sent that was not.
            withStore { reopened -> assertNull(reopened.current().reportHandOver) }
        }
    }

    @Test
    fun `the names a waiting report is stored under never change`() {
        val handOver = ReportHandOver(twoWeeks, tripCount = 4, tenths = 268, atMs = 1_791_300_000)
        withStore { it.setReportHandOver(handOver) }

        withFile { raw ->
            val stored = raw.data.first()
            fun long(name: String) = stored[longPreferencesKey(name)]
            assertEquals("RANGE", stored[stringPreferencesKey("report_handed_over_kind")])
            assertEquals(twoWeeks.firstDay.toEpochDay(), long("report_handed_over_first_day"))
            assertEquals(twoWeeks.lastDay.toEpochDay(), long("report_handed_over_last_day"))
            assertEquals(4, stored[intPreferencesKey("report_handed_over_trip_count")])
            assertEquals(268L, long("report_handed_over_tenths"))
            assertEquals(1_791_300_000L, long("report_handed_over_at_ms"))
            assertEquals(6, stored.asMap().size)
        }
        withStore { it.setReportHandOver(handOver.copy(period = september)) }
        withFile { raw ->
            assertEquals("MONTH", raw.data.first()[stringPreferencesKey("report_handed_over_kind")])
        }
    }
}

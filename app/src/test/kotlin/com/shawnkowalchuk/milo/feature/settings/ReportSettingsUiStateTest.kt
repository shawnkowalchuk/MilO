package com.shawnkowalchuk.milo.feature.settings

import com.shawnkowalchuk.milo.data.settings.MiloSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the Settings screen shows for the four settings of the report for the accountant. They
 * are tested here and not in `SettingsUiStateTest`, which is at its size limit.
 */
class ReportSettingsUiStateTest {
    private fun shown(settings: MiloSettings = MiloSettings()) =
        settingsUiState(settings, copyingSound = false, problem = null, problemDay = null)

    @Test
    fun `the report's four settings are shown as stored, and empty while they are not set`() {
        val stored =
            MiloSettings(
                reportName = "Sam Driver",
                reportVehicle = "Ford F-150",
                accountantEmail = "accounts@example.ca",
            )

        assertEquals(ReportFields("", "", "", "", emailRefused = false), shown().report)
        assertEquals(
            ReportFields("Sam Driver", "", "Ford F-150", "accounts@example.ca", false),
            shown(stored).report,
        )
    }

    @Test
    fun `an address that was not stored stands in its field with what is said of it`() {
        val stored = MiloSettings(accountantEmail = "accounts@example.ca")

        val refused = settingsUiState(stored, false, null, null, refusedEmail = "accounts@example")

        // The field is built from the text that was refused: with the phone turned, or back
        // from the pairing screen, "not saved" stands under that text and not under the stored
        // address. What is stored stays the address in force, and nothing else changes.
        assertEquals("accounts@example", refused.report.accountantEmail)
        assertTrue(refused.report.emailRefused)
        assertNull(refused.problem)
        assertEquals(shown(stored).copy(report = refused.report), refused)
    }

    @Test
    fun `the stored address is never shown as not saved`() {
        val stored = MiloSettings(accountantEmail = "accounts@example.ca")

        val unrefused = settingsUiState(stored, false, null, null, refusedEmail = null).report
        val emptied = settingsUiState(MiloSettings(), false, null, null, refusedEmail = null).report

        assertEquals("accounts@example.ca", unrefused.accountantEmail)
        assertFalse(unrefused.emailRefused)
        assertEquals("", emptied.accountantEmail)
        assertFalse(emptied.emailRefused)
    }
}

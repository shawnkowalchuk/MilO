package com.shawnkowalchuk.milo.platform.system

import com.shawnkowalchuk.milo.data.settings.ConfirmedStep
import com.shawnkowalchuk.milo.platform.bluetooth.PairingState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which rows of the setup checklist are required, and the home screen's rule: warn while a
 * required row is not OK, which includes "no truck is paired".
 */
class SetupAttentionTest {
    @Test
    fun `the required rows are the ones an automatic trip depends on`() {
        assertEquals(
            setOf(SetupItem.UNUSED_APP_PAUSE, SetupItem.XIAOMI_OTHER_PERMISSIONS),
            SetupItem.entries.filterNot { it.required }.toSet(),
        )
    }

    @Test
    fun `the home screen warns for every required row that is not OK`() {
        val broken =
            mapOf(
                SetupItem.PRECISE_LOCATION to
                    allGood.copy(preflight = preflightGood.copy(fineLocationGranted = false)),
                SetupItem.BACKGROUND_LOCATION to
                    allGood.copy(
                        preflight = preflightGood.copy(backgroundLocationGranted = false),
                    ),
                SetupItem.NOTIFICATIONS to allGood.copy(notificationsEnabled = false),
                SetupItem.NEARBY_DEVICES to
                    allGood.copy(preflight = preflightGood.copy(bluetoothGranted = false)),
                SetupItem.LOCATION_SERVICES to
                    allGood.copy(preflight = preflightGood.copy(locationSwitchedOn = false)),
                SetupItem.BATTERY_EXEMPTION to allGood.copy(ignoringBatteryOptimizations = false),
                SetupItem.BATTERY_SAVER_OFF to allGood.copy(batterySaverOn = true),
            )

        for ((item, facts) in broken) {
            val rows = rows(facts)

            assertEquals("$item", SetupState.PROBLEM, rows.row(item).state)
            assertTrue("$item", needsAttention(rows))
        }
    }

    @Test
    fun `the home screen warns while no truck is paired, or its association is gone`() {
        assertTrue(needsAttention(rows(pairing = PairingState.NO_TRUCK)))
        assertTrue(needsAttention(rows(pairing = PairingState.ASSOCIATION_MISSING)))
        assertTrue(needsAttention(rows(pairing = PairingState.FAILED)))
    }

    @Test
    fun `a pairing that has not been checked yet raises no warning`() {
        // Otherwise the warning would flash at every start of the app.
        assertFalse(needsAttention(rows(pairing = null)))
    }

    @Test
    fun `a recommended row that is not in order raises no warning`() {
        val facts = xiaomiGood.copy(exemptFromUnusedAppPause = false)
        val confirmed = everythingConfirmed - ConfirmedStep.XIAOMI_OTHER_PERMISSIONS

        val rows = rows(facts, confirmedAtMs = confirmed)

        assertEquals(SetupState.PROBLEM, rows.row(SetupItem.UNUSED_APP_PAUSE).state)
        assertEquals(
            SetupState.NEEDS_CONFIRMATION,
            rows.row(SetupItem.XIAOMI_OTHER_PERMISSIONS).state,
        )
        assertFalse(needsAttention(rows))
    }

    @Test
    fun `on a Xiaomi phone the warning stays until the required HyperOS rows are confirmed`() {
        assertTrue(needsAttention(rows(xiaomiGood)))

        for (step in listOf(
            ConfirmedStep.XIAOMI_BATTERY_SAVER,
            ConfirmedStep.XIAOMI_RECENTS_LOCK,
        )) {
            assertTrue("$step", needsAttention(rows(xiaomiGood, confirmedAtMs = mapOf(step to 1L))))
        }
        assertFalse(needsAttention(rows(xiaomiGood, confirmedAtMs = everythingConfirmed)))
    }

    @Test
    fun `an Autostart that looks off, or cannot be read, keeps the warning up`() {
        val off = xiaomiGood.copy(autostart = AutostartReading.LOOKS_OFF)
        val unreadable = xiaomiGood.copy(autostart = AutostartReading.UNKNOWN)
        val othersConfirmed = everythingConfirmed - ConfirmedStep.XIAOMI_AUTOSTART

        assertTrue(needsAttention(rows(off, confirmedAtMs = everythingConfirmed)))
        assertTrue(needsAttention(rows(unreadable, confirmedAtMs = othersConfirmed)))
        assertFalse(needsAttention(rows(unreadable, confirmedAtMs = everythingConfirmed)))
    }
}

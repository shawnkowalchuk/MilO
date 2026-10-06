package com.shawnkowalchuk.milo.platform.system

import android.Manifest
import com.shawnkowalchuk.milo.data.settings.ConfirmedStep
import com.shawnkowalchuk.milo.platform.bluetooth.PairingState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How each row of the setup checklist is decided from what the phone reports. Which rows are
 * required, and when the home screen warns, is in `SetupAttentionTest`. Reading the phone
 * itself, and what the buttons open, can only be tested on the phone.
 */
class SetupRulesTest {
    // ---- The Android rows -------------------------------------------------------------------------

    @Test
    fun `with everything in place every row is OK and nothing needs attention`() {
        val rows = rows()

        assertTrue(rows.all { it.state == SetupState.OK })
        assertFalse(needsAttention(rows))
    }

    @Test
    fun `a stock Android phone has the nine Android rows and no Xiaomi row`() {
        val items = rows().map { it.item }

        assertEquals(SetupItem.entries.filterNot { it.xiaomiOnly }, items)
        assertEquals(9, items.size)
    }

    @Test
    fun `a Xiaomi phone has the four HyperOS rows as well, after the Android ones`() {
        assertEquals(SetupItem.entries, rows(xiaomiGood).map { it.item })
    }

    @Test
    fun `precise location missing is a problem whose button asks for both location permissions`() {
        val facts = allGood.copy(preflight = preflightGood.copy(fineLocationGranted = false))

        val row = rows(facts).row(SetupItem.PRECISE_LOCATION)

        assertEquals(SetupState.PROBLEM, row.state)
        assertEquals(
            SetupFix.AskPermission(
                listOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
                ifNotAsked = SystemScreen.APP_DETAILS,
            ),
            row.fix,
        )
    }

    @Test
    fun `Allow all the time is asked for only after precise location is granted`() {
        val neither =
            allGood.copy(
                preflight =
                    preflightGood.copy(
                        fineLocationGranted = false,
                        backgroundLocationGranted = false,
                    ),
            )
        val whileInUse =
            allGood.copy(preflight = preflightGood.copy(backgroundLocationGranted = false))

        val blocked = rows(neither).row(SetupItem.BACKGROUND_LOCATION)
        val askable = rows(whileInUse).row(SetupItem.BACKGROUND_LOCATION)

        // No button: Android ignores the request while location is not allowed at all.
        assertEquals(SetupState.PROBLEM, blocked.state)
        assertEquals(SetupDetail.PRECISE_LOCATION_FIRST, blocked.detail)
        assertNull(blocked.fix)

        assertEquals(SetupDetail.NOT_SET, askable.detail)
        assertEquals(
            SetupFix.AskPermission(
                listOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                ifNotAsked = SystemScreen.APP_DETAILS,
            ),
            askable.fix,
        )
    }

    @Test
    fun `notifications are asked for with the dialog from Android 13 and in settings before`() {
        val android13 = allGood.copy(notificationsEnabled = false)
        val android12 = android13.copy(notificationPermissionAskable = false)

        assertEquals(
            SetupFix.AskPermission(
                listOf("android.permission.POST_NOTIFICATIONS"),
                ifNotAsked = SystemScreen.APP_NOTIFICATIONS,
            ),
            rows(android13).row(SetupItem.NOTIFICATIONS).fix,
        )
        assertEquals(
            SetupFix.Open(SystemScreen.APP_NOTIFICATIONS),
            rows(android12).row(SetupItem.NOTIFICATIONS).fix,
        )
    }

    @Test
    fun `location switched off and Nearby devices missing each have their own fix`() {
        val facts =
            allGood.copy(
                preflight =
                    preflightGood.copy(locationSwitchedOn = false, bluetoothGranted = false),
            )
        val rows = rows(facts)

        assertEquals(
            SetupFix.Open(SystemScreen.LOCATION),
            rows.row(SetupItem.LOCATION_SERVICES).fix,
        )
        assertEquals(
            SetupFix.AskPermission(
                listOf(Manifest.permission.BLUETOOTH_CONNECT),
                ifNotAsked = SystemScreen.APP_DETAILS,
            ),
            rows.row(SetupItem.NEARBY_DEVICES).fix,
        )
    }

    @Test
    fun `the battery row tells Restricted from merely not exempt`() {
        val optimised = allGood.copy(ignoringBatteryOptimizations = false)
        val restricted =
            optimised.copy(preflight = preflightGood.copy(backgroundRestricted = true))

        val notExempt = rows(optimised).row(SetupItem.BATTERY_EXEMPTION)
        val blocked = rows(restricted).row(SetupItem.BATTERY_EXEMPTION)

        // Not exempt: Android's own request dialog lifts it.
        assertEquals(SetupDetail.NOT_SET, notExempt.detail)
        assertEquals(SetupFix.Open(SystemScreen.BATTERY_EXEMPTION), notExempt.fix)
        // Restricted: that dialog does not change it; the app's own page does.
        assertEquals(SetupDetail.BATTERY_RESTRICTED, blocked.detail)
        assertEquals(SetupFix.Open(SystemScreen.APP_DETAILS), blocked.fix)
    }

    @Test
    fun `on a Xiaomi phone a Restricted battery setting is worded for HyperOS`() {
        val restricted =
            xiaomiGood.copy(preflight = preflightGood.copy(backgroundRestricted = true))

        val row = rows(restricted).row(SetupItem.BATTERY_EXEMPTION)

        // HyperOS has no "Unrestricted" to send Shawn to; the row names "No restrictions".
        assertEquals(SetupState.PROBLEM, row.state)
        assertEquals(SetupDetail.BATTERY_RESTRICTED_HYPEROS, row.detail)
        // Android's page for MilO is HyperOS's "App info" there, which holds that setting.
        assertEquals(SetupFix.Open(SystemScreen.APP_DETAILS), row.fix)
    }

    @Test
    fun `Battery Saver switched on is a problem`() {
        val row = rows(allGood.copy(batterySaverOn = true)).row(SetupItem.BATTERY_SAVER_OFF)

        assertEquals(SetupState.PROBLEM, row.state)
        assertEquals(SetupFix.Open(SystemScreen.BATTERY_SAVER), row.fix)
    }

    @Test
    fun `a row that is in order has no button`() {
        val simpleRows = rows().filterNot { it.item == SetupItem.TRUCK }

        assertTrue(simpleRows.all { it.fix == null })
    }

    // ---- The truck row ----------------------------------------------------------------------------

    @Test
    fun `the truck row follows the pairing check, and always leads to the pairing screen`() {
        val expected =
            mapOf(
                PairingState.ARMED to (SetupState.OK to SetupDetail.FINE),
                PairingState.NO_TRUCK to (SetupState.PROBLEM to SetupDetail.TRUCK_NOT_PAIRED),
                PairingState.ASSOCIATION_MISSING to
                    (SetupState.PROBLEM to SetupDetail.TRUCK_ASSOCIATION_MISSING),
                PairingState.NOT_SUPPORTED to
                    (SetupState.PROBLEM to SetupDetail.TRUCK_NOT_WATCHED),
                PairingState.FAILED to (SetupState.UNKNOWN to SetupDetail.TRUCK_CHECK_FAILED),
                null to (SetupState.UNKNOWN to SetupDetail.TRUCK_NOT_CHECKED_YET),
            )
        // Every state the pairing check can report is covered above.
        assertEquals(PairingState.entries.toSet(), expected.keys.filterNotNull().toSet())

        for ((pairing, stateAndDetail) in expected) {
            val row = rows(pairing = pairing).row(SetupItem.TRUCK)

            assertEquals("$pairing", stateAndDetail, row.state to row.detail)
            // The pairing screen is also where the truck is changed, so the button stays.
            assertEquals("$pairing", SetupFix.OpenPairing, row.fix)
            assertEquals("Work truck", row.truckName)
        }
    }

    // ---- The Xiaomi rows --------------------------------------------------------------------------

    @Test
    fun `Autostart is shown as it reads, never as more than that`() {
        val on = rows(xiaomiGood).row(SetupItem.XIAOMI_AUTOSTART)
        val off =
            rows(xiaomiGood.copy(autostart = AutostartReading.LOOKS_OFF))
                .row(SetupItem.XIAOMI_AUTOSTART)

        assertEquals(SetupState.OK to SetupDetail.AUTOSTART_LOOKS_ON, on.state to on.detail)
        assertEquals(
            SetupState.PROBLEM to SetupDetail.AUTOSTART_LOOKS_OFF,
            off.state to off.detail,
        )
        // Neither reading is certain, so the way to the HyperOS screen stays in both, and
        // neither can be confirmed by hand: the reading is what counts.
        for (row in listOf(on, off)) {
            assertEquals(SetupFix.Open(SystemScreen.XIAOMI_AUTOSTART), row.fix)
            assertNull(row.confirmStep)
        }
    }

    @Test
    fun `Shawn's confirmation does not hide an Autostart that reads as off`() {
        val facts = xiaomiGood.copy(autostart = AutostartReading.LOOKS_OFF)

        val row = rows(facts, confirmedAtMs = everythingConfirmed).row(SetupItem.XIAOMI_AUTOSTART)

        assertEquals(SetupState.PROBLEM, row.state)
    }

    @Test
    fun `an Autostart that cannot be read falls back on Shawn's confirmation`() {
        val facts = xiaomiGood.copy(autostart = AutostartReading.UNKNOWN)

        val open = rows(facts).row(SetupItem.XIAOMI_AUTOSTART)
        val confirmed =
            rows(facts, confirmedAtMs = mapOf(ConfirmedStep.XIAOMI_AUTOSTART to 42L))
                .row(SetupItem.XIAOMI_AUTOSTART)

        assertEquals(SetupState.UNKNOWN, open.state)
        assertEquals(SetupDetail.AUTOSTART_UNREADABLE, open.detail)
        assertEquals(ConfirmedStep.XIAOMI_AUTOSTART, open.confirmStep)
        assertNull(open.confirmedAtMs)

        assertEquals(SetupState.OK, confirmed.state)
        assertEquals(SetupDetail.CONFIRMED, confirmed.detail)
        assertEquals(42L, confirmed.confirmedAtMs)
    }

    @Test
    fun `the settings MilO cannot read wait for a confirmation and show its date`() {
        val unreadable =
            mapOf(
                SetupItem.XIAOMI_BATTERY_SAVER to ConfirmedStep.XIAOMI_BATTERY_SAVER,
                SetupItem.XIAOMI_OTHER_PERMISSIONS to ConfirmedStep.XIAOMI_OTHER_PERMISSIONS,
                SetupItem.XIAOMI_RECENTS_LOCK to ConfirmedStep.XIAOMI_RECENTS_LOCK,
            )
        val before = rows(xiaomiGood)
        val after = rows(xiaomiGood, confirmedAtMs = unreadable.values.associateWith { 7_000L })

        for ((item, step) in unreadable) {
            val waiting = before.row(item)
            assertEquals("$item", SetupState.NEEDS_CONFIRMATION, waiting.state)
            assertEquals("$item", step, waiting.confirmStep)
            assertNull("$item", waiting.confirmedAtMs)

            val confirmed = after.row(item)
            assertEquals("$item", SetupState.OK, confirmed.state)
            assertEquals("$item", SetupDetail.CONFIRMED, confirmed.detail)
            assertEquals("$item", 7_000L, confirmed.confirmedAtMs)
            // Still set, so that the confirmation can be taken back.
            assertEquals("$item", step, confirmed.confirmStep)
        }
    }

    @Test
    fun `each HyperOS row opens its own screen, and the recents lock has none to open`() {
        val rows = rows(xiaomiGood)

        assertEquals(
            SetupFix.Open(SystemScreen.XIAOMI_BATTERY_SAVER),
            rows.row(SetupItem.XIAOMI_BATTERY_SAVER).fix,
        )
        assertEquals(
            SetupFix.Open(SystemScreen.XIAOMI_OTHER_PERMISSIONS),
            rows.row(SetupItem.XIAOMI_OTHER_PERMISSIONS).fix,
        )
        // The lock is a gesture on MilO's card in the recent apps.
        assertNull(rows.row(SetupItem.XIAOMI_RECENTS_LOCK).fix)
    }
}

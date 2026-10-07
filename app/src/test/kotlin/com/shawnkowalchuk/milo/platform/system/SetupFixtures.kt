package com.shawnkowalchuk.milo.platform.system

import com.shawnkowalchuk.milo.data.settings.ConfirmedStep
import com.shawnkowalchuk.milo.platform.bluetooth.PairingState

// What the tests of the setup checklist's rules share: phones with everything in order, and
// short ways to ask for the rows.

internal val preflightGood =
    PreflightFacts(
        fineLocationGranted = true,
        backgroundLocationGranted = true,
        locationSwitchedOn = true,
        backgroundRestricted = false,
        bluetoothGranted = true,
    )

/** A stock Android phone with everything in order. */
internal val allGood =
    SetupFacts(
        preflight = preflightGood,
        notificationsEnabled = true,
        ignoringBatteryOptimizations = true,
        exemptFromUnusedAppPause = true,
        batterySaverOn = false,
        activityRecognitionGranted = true,
        isXiaomi = false,
        autostart = AutostartReading.UNKNOWN,
    )

/** The POCO, with the one HyperOS setting MilO can read looking right. */
internal val xiaomiGood = allGood.copy(isXiaomi = true, autostart = AutostartReading.LOOKS_ON)

internal val everythingConfirmed = ConfirmedStep.entries.associateWith { 1_000L }

/** The rows for a phone, with the truck paired and watched unless a test says otherwise. */
internal fun rows(
    facts: SetupFacts = allGood,
    pairing: PairingState? = PairingState.ARMED,
    confirmedAtMs: Map<ConfirmedStep, Long> = emptyMap(),
    drivingAlertEnabled: Boolean = true,
): List<SetupRow> =
    setupRows(facts, pairing, truckName = "Work truck", confirmedAtMs, drivingAlertEnabled)

internal fun List<SetupRow>.row(item: SetupItem): SetupRow = single { it.item == item }

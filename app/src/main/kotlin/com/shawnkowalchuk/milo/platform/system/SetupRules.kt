package com.shawnkowalchuk.milo.platform.system

import android.Manifest
import com.shawnkowalchuk.milo.data.settings.ConfirmedStep
import com.shawnkowalchuk.milo.platform.bluetooth.PairingState

// The rules of the setup checklist: how each row's state is decided from what the phone reports,
// and when the home screen warns. Pure functions, so they are tested without a phone.

/**
 * Whether this row is one of the things MilO needs that is not in order: a required row that is
 * not OK. A row that is still being read for the first time does not count, or the home
 * screen's warning would flash at every start of the app.
 */
fun SetupRow.needsAttention(): Boolean = item.required &&
    state != SetupState.OK &&
    detail != SetupDetail.TRUCK_NOT_CHECKED_YET

/**
 * The home screen's rule: warn while any required row is not OK. A paired and watched truck is
 * one of the required rows, so "no truck is paired" is covered.
 */
fun needsAttention(rows: List<SetupRow>): Boolean = rows.any { it.needsAttention() }

/**
 * Decides every row of the checklist from what the phone reports.
 *
 * @param pairing the state of the truck's pairing, or null before its first check has finished.
 * @param confirmedAtMs when Shawn confirmed each step MilO cannot read.
 * @param drivingAlertEnabled the Settings switch of the driving alert. While it is off, the
 * Physical activity permission is not asked for.
 */
fun setupRows(
    facts: SetupFacts,
    pairing: PairingState?,
    truckName: String?,
    confirmedAtMs: Map<ConfirmedStep, Long>,
    drivingAlertEnabled: Boolean,
): List<SetupRow> = buildList {
    add(preciseLocationRow(facts.preflight))
    add(backgroundLocationRow(facts.preflight))
    add(notificationsRow(facts))
    add(nearbyDevicesRow(facts.preflight))
    add(locationServicesRow(facts.preflight))
    add(truckRow(pairing, truckName))
    add(batteryExemptionRow(facts))
    add(unusedAppPauseRow(facts))
    add(batterySaverRow(facts))
    add(physicalActivityRow(facts, drivingAlertEnabled))
    if (facts.isXiaomi) {
        add(autostartRow(facts.autostart, confirmedAtMs[ConfirmedStep.XIAOMI_AUTOSTART]))
        add(
            confirmRow(
                SetupItem.XIAOMI_BATTERY_SAVER,
                ConfirmedStep.XIAOMI_BATTERY_SAVER,
                confirmedAtMs,
                SetupFix.Open(SystemScreen.XIAOMI_BATTERY_SAVER),
            ),
        )
        add(
            confirmRow(
                SetupItem.XIAOMI_OTHER_PERMISSIONS,
                ConfirmedStep.XIAOMI_OTHER_PERMISSIONS,
                confirmedAtMs,
                SetupFix.Open(SystemScreen.XIAOMI_OTHER_PERMISSIONS),
            ),
        )
        // No button: the lock is a gesture on MilO's card in the recent apps, not a screen.
        add(
            confirmRow(
                SetupItem.XIAOMI_RECENTS_LOCK,
                ConfirmedStep.XIAOMI_RECENTS_LOCK,
                confirmedAtMs,
                fix = null,
            ),
        )
    }
}

/** A row that is either in order or not, with a button only while it is not. */
private fun simpleRow(item: SetupItem, isOk: Boolean, fix: SetupFix): SetupRow = if (isOk) {
    SetupRow(item, SetupState.OK, SetupDetail.FINE)
} else {
    SetupRow(item, SetupState.PROBLEM, SetupDetail.NOT_SET, fix)
}

private fun preciseLocationRow(preflight: PreflightFacts): SetupRow = simpleRow(
    SetupItem.PRECISE_LOCATION,
    preflight.fineLocationGranted,
    SetupFix.AskPermission(
        // Android requires the approximate permission to be asked for beside the precise one.
        listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ),
        ifNotAsked = SystemScreen.APP_DETAILS,
    ),
)

private fun backgroundLocationRow(preflight: PreflightFacts): SetupRow = when {
    preflight.backgroundLocationGranted ->
        SetupRow(SetupItem.BACKGROUND_LOCATION, SetupState.OK, SetupDetail.FINE)

    // Android ignores a request for "Allow all the time" while location is not allowed at
    // all, so the row sends Shawn to the row above first and offers no button of its own.
    !preflight.fineLocationGranted ->
        SetupRow(
            SetupItem.BACKGROUND_LOCATION,
            SetupState.PROBLEM,
            SetupDetail.PRECISE_LOCATION_FIRST,
        )

    else ->
        SetupRow(
            SetupItem.BACKGROUND_LOCATION,
            SetupState.PROBLEM,
            SetupDetail.NOT_SET,
            SetupFix.AskPermission(
                listOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                ifNotAsked = SystemScreen.APP_DETAILS,
            ),
        )
}

private fun notificationsRow(facts: SetupFacts): SetupRow = simpleRow(
    SetupItem.NOTIFICATIONS,
    facts.notificationsEnabled,
    SetupFix.AskPermission(
        listOf(Manifest.permission.POST_NOTIFICATIONS),
        ifNotAsked = SystemScreen.APP_NOTIFICATIONS,
    ),
)

private fun nearbyDevicesRow(preflight: PreflightFacts): SetupRow = simpleRow(
    SetupItem.NEARBY_DEVICES,
    preflight.bluetoothGranted,
    SetupFix.AskPermission(
        listOf(Manifest.permission.BLUETOOTH_CONNECT),
        ifNotAsked = SystemScreen.APP_DETAILS,
    ),
)

private fun locationServicesRow(preflight: PreflightFacts): SetupRow = simpleRow(
    SetupItem.LOCATION_SERVICES,
    preflight.locationSwitchedOn,
    SetupFix.Open(SystemScreen.LOCATION),
)

/** The truck row always has a button: the pairing screen is also where the truck is changed. */
private fun truckRow(pairing: PairingState?, truckName: String?): SetupRow {
    val (state, detail) =
        when (pairing) {
            PairingState.ARMED -> SetupState.OK to SetupDetail.FINE

            PairingState.NO_TRUCK -> SetupState.PROBLEM to SetupDetail.TRUCK_NOT_PAIRED

            PairingState.ASSOCIATION_MISSING ->
                SetupState.PROBLEM to SetupDetail.TRUCK_ASSOCIATION_MISSING

            PairingState.NOT_SUPPORTED -> SetupState.PROBLEM to SetupDetail.TRUCK_NOT_WATCHED

            PairingState.FAILED -> SetupState.UNKNOWN to SetupDetail.TRUCK_CHECK_FAILED

            null -> SetupState.UNKNOWN to SetupDetail.TRUCK_NOT_CHECKED_YET
        }
    return SetupRow(SetupItem.TRUCK, state, detail, SetupFix.OpenPairing, truckName = truckName)
}

/**
 * Android's three battery settings in one row. "Restricted" blocks the trip service outright
 * and is changed on the app's own settings page; "Optimised" is lifted with Android's own
 * request dialog.
 *
 * On a Xiaomi phone that page is HyperOS's "App info", where the setting has another name, so
 * the row says which words to look for there.
 */
private fun batteryExemptionRow(facts: SetupFacts): SetupRow = when {
    facts.preflight.backgroundRestricted ->
        SetupRow(
            SetupItem.BATTERY_EXEMPTION,
            SetupState.PROBLEM,
            if (facts.isXiaomi) {
                SetupDetail.BATTERY_RESTRICTED_HYPEROS
            } else {
                SetupDetail.BATTERY_RESTRICTED
            },
            SetupFix.Open(SystemScreen.APP_DETAILS),
        )

    else ->
        simpleRow(
            SetupItem.BATTERY_EXEMPTION,
            facts.ignoringBatteryOptimizations,
            SetupFix.Open(SystemScreen.BATTERY_EXEMPTION),
        )
}

private fun unusedAppPauseRow(facts: SetupFacts): SetupRow = simpleRow(
    SetupItem.UNUSED_APP_PAUSE,
    facts.exemptFromUnusedAppPause,
    SetupFix.Open(SystemScreen.UNUSED_APP_PAUSE),
)

private fun batterySaverRow(facts: SetupFacts): SetupRow = simpleRow(
    SetupItem.BATTERY_SAVER_OFF,
    !facts.batterySaverOn,
    SetupFix.Open(SystemScreen.BATTERY_SAVER),
)

/**
 * The one permission only the driving alert needs. With the alert switched off in Settings
 * nothing needs it, so the row is in order and asks for nothing: a row that asked for a
 * permission no feature would use is how a checklist stops being believed.
 */
private fun physicalActivityRow(facts: SetupFacts, drivingAlertEnabled: Boolean): SetupRow = when {
    facts.activityRecognitionGranted ->
        SetupRow(SetupItem.PHYSICAL_ACTIVITY, SetupState.OK, SetupDetail.FINE)

    !drivingAlertEnabled ->
        SetupRow(SetupItem.PHYSICAL_ACTIVITY, SetupState.OK, SetupDetail.DRIVING_ALERT_OFF)

    else ->
        SetupRow(
            SetupItem.PHYSICAL_ACTIVITY,
            SetupState.PROBLEM,
            SetupDetail.NOT_SET,
            SetupFix.AskPermission(
                listOf(Manifest.permission.ACTIVITY_RECOGNITION),
                ifNotAsked = SystemScreen.APP_DETAILS,
            ),
        )
}

/**
 * Autostart keeps its button in every state, because none of the states is certain. A reading
 * of "on" or "off" is shown as it is. Only when the phone gives no reading at all does the row
 * fall back on Shawn's word, like the rows that can never be read.
 */
private fun autostartRow(reading: AutostartReading, confirmedAtMs: Long?): SetupRow {
    val fix = SetupFix.Open(SystemScreen.XIAOMI_AUTOSTART)
    val item = SetupItem.XIAOMI_AUTOSTART
    return when (reading) {
        AutostartReading.LOOKS_ON ->
            SetupRow(item, SetupState.OK, SetupDetail.AUTOSTART_LOOKS_ON, fix)

        AutostartReading.LOOKS_OFF ->
            SetupRow(item, SetupState.PROBLEM, SetupDetail.AUTOSTART_LOOKS_OFF, fix)

        AutostartReading.UNKNOWN ->
            if (confirmedAtMs != null) {
                SetupRow(
                    item,
                    SetupState.OK,
                    SetupDetail.CONFIRMED,
                    fix,
                    ConfirmedStep.XIAOMI_AUTOSTART,
                    confirmedAtMs,
                )
            } else {
                SetupRow(
                    item,
                    SetupState.UNKNOWN,
                    SetupDetail.AUTOSTART_UNREADABLE,
                    fix,
                    ConfirmedStep.XIAOMI_AUTOSTART,
                )
            }
    }
}

/** A setting MilO cannot read at all: it is in order when Shawn says so, and not before. */
private fun confirmRow(
    item: SetupItem,
    step: ConfirmedStep,
    confirmedAtMs: Map<ConfirmedStep, Long>,
    fix: SetupFix?,
): SetupRow {
    val at = confirmedAtMs[step]
    return if (at != null) {
        SetupRow(item, SetupState.OK, SetupDetail.CONFIRMED, fix, step, at)
    } else {
        SetupRow(item, SetupState.NEEDS_CONFIRMATION, SetupDetail.NOT_CONFIRMED, fix, step)
    }
}

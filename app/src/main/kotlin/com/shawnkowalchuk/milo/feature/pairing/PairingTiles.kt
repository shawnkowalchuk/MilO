package com.shawnkowalchuk.milo.feature.pairing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.component.GroupLabel
import com.shawnkowalchuk.milo.core.designsystem.component.RowButton
import com.shawnkowalchuk.milo.core.designsystem.component.RowStatus
import com.shawnkowalchuk.milo.core.designsystem.component.StatusRow
import com.shawnkowalchuk.milo.core.designsystem.component.StatusRowAction
import com.shawnkowalchuk.milo.core.designsystem.component.Tile
import com.shawnkowalchuk.milo.core.designsystem.component.TileRow
import com.shawnkowalchuk.milo.core.designsystem.component.tileRowPlace
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme
import com.shawnkowalchuk.milo.platform.bluetooth.PairedDevice
import com.shawnkowalchuk.milo.platform.system.SystemScreen

// The three parts of the pairing screen, each a small label on the page over a tile, as the
// owner's design groups the rows of Setup: the truck and how the last attempt went, what is in
// the way, and the phone's devices to pick from.

/**
 * The paired vehicles, each with its state in plain words and, since 2026-10-08, a button that
 * removes it; then the result of the attempt just made.
 *
 * @param onRemove asks first: the screen shows the question.
 */
@Composable
internal fun TruckGroup(
    vehicles: List<TruckLine>,
    attempt: PairingAttempt,
    onRemove: (TruckLine) -> Unit,
) {
    GroupLabel(stringResource(R.string.pairing_truck_title))
    Tile(modifier = Modifier.fillMaxWidth(), gap = MiloTheme.spacing.rowGap) {
        if (vehicles.isEmpty()) {
            StatusRow(
                label = stringResource(R.string.pairing_no_truck),
                status = RowStatus.PROBLEM,
                supportingText = stringResource(R.string.pairing_no_truck_detail),
            )
        }
        for (line in vehicles) {
            key(line.vehicle.address) {
                StatusRow(
                    label = line.name ?: stringResource(R.string.truck_without_a_name),
                    status = line.watch.rowStatus(),
                    supportingText = stringResource(line.watch.textRes()),
                    action =
                        StatusRowAction(stringResource(R.string.pairing_action_remove)) {
                            onRemove(line)
                        },
                )
            }
        }
        AttemptRow(attempt)
    }
}

@Composable
private fun AttemptRow(attempt: PairingAttempt) {
    when (attempt) {
        PairingAttempt.None -> Unit

        PairingAttempt.Asking ->
            Text(
                text = stringResource(R.string.pairing_attempt_asking),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

        is PairingAttempt.Paired ->
            StatusRow(
                label =
                    stringResource(
                        R.string.pairing_attempt_paired,
                        attempt.name ?: stringResource(R.string.truck_without_a_name),
                    ),
                status = RowStatus.OK,
            )

        PairingAttempt.Declined ->
            StatusRow(
                label = stringResource(R.string.pairing_attempt_declined_title),
                status = RowStatus.PROBLEM,
                supportingText = stringResource(R.string.pairing_attempt_declined),
            )

        is PairingAttempt.Failed ->
            StatusRow(
                label = stringResource(R.string.pairing_attempt_failed_title),
                status = RowStatus.PROBLEM,
                // The reason is `TruckPairing`'s own English line, the same one the event log
                // holds: exact, if not pretty. The advice after it is what can be tried.
                supportingText = stringResource(R.string.pairing_attempt_failed, attempt.why),
            )
    }
}

private fun TruckWatch.rowStatus(): RowStatus = when (this) {
    TruckWatch.WATCHED -> RowStatus.OK
    TruckWatch.ASSOCIATION_MISSING, TruckWatch.NOT_SUPPORTED -> RowStatus.PROBLEM
    TruckWatch.CHECK_FAILED, TruckWatch.CHECKING -> RowStatus.UNKNOWN
}

private fun TruckWatch.textRes(): Int = when (this) {
    TruckWatch.WATCHED -> R.string.pairing_watch_ok
    TruckWatch.ASSOCIATION_MISSING -> R.string.pairing_watch_association_missing
    TruckWatch.NOT_SUPPORTED -> R.string.pairing_watch_not_supported
    TruckWatch.CHECK_FAILED -> R.string.pairing_watch_check_failed
    TruckWatch.CHECKING -> R.string.setup_detail_checking
}

/** What stops Shawn from picking a truck, and the button that leads to the fix. */
@Composable
internal fun BlockerGroup(blocker: PairingBlocker, actions: PairingActions) {
    val bluetoothSettings =
        StatusRowAction(stringResource(R.string.pairing_action_bluetooth_settings)) {
            actions.onOpenScreen(SystemScreen.BLUETOOTH)
        }
    val (textRes, action) =
        when (blocker) {
            PairingBlocker.PERMISSION_MISSING ->
                R.string.pairing_blocker_permission to
                    StatusRowAction(
                        stringResource(R.string.pairing_action_allow_nearby_devices),
                        actions.onAllowNearbyDevices,
                    )

            PairingBlocker.NO_BLUETOOTH -> R.string.pairing_blocker_no_bluetooth to null

            PairingBlocker.BLUETOOTH_OFF ->
                R.string.pairing_blocker_bluetooth_off to bluetoothSettings

            PairingBlocker.LOCATION_OFF ->
                R.string.pairing_blocker_location_off to
                    StatusRowAction(stringResource(R.string.pairing_action_location_settings)) {
                        actions.onOpenScreen(SystemScreen.LOCATION)
                    }

            PairingBlocker.NOTHING_PAIRED ->
                R.string.pairing_blocker_nothing_paired to bluetoothSettings
        }
    GroupLabel(stringResource(R.string.pairing_blocker_title))
    Tile(modifier = Modifier.fillMaxWidth()) {
        StatusRow(
            label = stringResource(textRes),
            status = RowStatus.PROBLEM,
            action = action,
        )
    }
}

/**
 * The phone's paired devices as the rows of one tile, with a hairline between two of them.
 * Each has its own button, so it is plain what a press will do: "Pair" in the accent colour
 * while no truck is stored, because that is the one thing to do here; once one is stored, a
 * quiet "Pair again" on each paired vehicle and a quiet "Add" on every other device.
 *
 * @param canPick false greys every button: something is in the way, or Android is being asked.
 */
@Composable
internal fun DevicesGroup(
    devices: List<DeviceLine>,
    hasTruck: Boolean,
    canPick: Boolean,
    onPick: (PairedDevice) -> Unit,
) {
    GroupLabel(stringResource(R.string.pairing_devices_title))
    Column {
        devices.forEachIndexed { index, line ->
            key(line.device.address) {
                TileRow(place = tileRowPlace(index, devices.size)) {
                    DeviceRow(line = line, hasTruck = hasTruck, canPick = canPick, onPick = onPick)
                }
            }
        }
    }
    if (hasTruck) {
        // Under the tile it explains, and in from the edge like the label above it.
        Text(
            text = stringResource(R.string.pairing_change_note),
            modifier = Modifier.padding(horizontal = MiloTheme.spacing.extraSmall),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DeviceRow(
    line: DeviceLine,
    hasTruck: Boolean,
    canPick: Boolean,
    onPick: (PairedDevice) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MiloTheme.spacing.rowGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(MiloTheme.spacing.textGap),
        ) {
            Text(
                text = line.device.name ?: stringResource(R.string.pairing_device_no_name),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text =
                    if (line.isTruck) {
                        stringResource(R.string.pairing_device_current, line.device.address)
                    } else {
                        line.device.address
                    },
                style = MiloTheme.textStyles.tileLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Another device is added beside the vehicles paired (since 2026-10-08): "Add".
        RowButton(
            text =
                stringResource(
                    when {
                        line.isTruck -> R.string.pairing_action_pair_again
                        hasTruck -> R.string.pairing_action_switch
                        else -> R.string.pairing_action_pair
                    },
                ),
            onClick = { onPick(line.device) },
            accent = !hasTruck,
            enabled = canPick,
        )
    }
}

package com.shawnkowalchuk.milo.feature.pairing

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme
import com.shawnkowalchuk.milo.platform.bluetooth.PairedDevice

// The pairing screen as Android Studio draws it, in the states an emulator cannot reach: it
// has no Bluetooth device to list. In a file of its own beside the screen's. Sample values are
// written inline because a preview is never shown to a user or shipped.

private val devices =
    listOf(
        PairedDevice("AA:BB:CC:DD:EE:FF", "Work truck"),
        PairedDevice("11:22:33:44:55:66", "Earbuds"),
        PairedDevice("77:88:99:AA:BB:CC", null),
    )

@Composable
private fun PairingPreview(state: PairingUiState?) {
    MiloTheme {
        Surface {
            PairingContent(state = state, actions = PairingActions({}, {}, {}), onBack = {})
        }
    }
}

/** A truck is stored and watched, just after it was paired on this visit. */
@Preview
@Composable
private fun PairingWithTruckPreview() {
    PairingPreview(
        PairingUiState(
            truck = TruckLine("Work truck", TruckWatch.WATCHED),
            devices = devices.mapIndexed { index, device -> DeviceLine(device, index == 0) },
            attempt = PairingAttempt.Paired("Work truck"),
        ),
    )
}

/** No truck yet, and the phone's devices to pick from. */
@Preview
@Composable
private fun PairingNoTruckPreview() {
    PairingPreview(PairingUiState(devices = devices.map { DeviceLine(it, isTruck = false) }))
}

/** Android has been asked: every button waits. */
@Preview
@Composable
private fun PairingAskingPreview() {
    PairingPreview(
        PairingUiState(
            devices = devices.map { DeviceLine(it, isTruck = false) },
            attempt = PairingAttempt.Asking,
        ),
    )
}

/** Location is off: the devices are listed, and none can be picked. */
@Preview
@Composable
private fun PairingLocationOffPreview() {
    PairingPreview(
        PairingUiState(
            truck = TruckLine("Work truck", TruckWatch.ASSOCIATION_MISSING),
            blocker = PairingBlocker.LOCATION_OFF,
            devices = devices.mapIndexed { index, device -> DeviceLine(device, index == 0) },
            attempt = PairingAttempt.Failed("Android reported error 3"),
        ),
    )
}

/** Bluetooth is off, after a dialog that was closed without allowing. */
@Preview
@Composable
private fun PairingBlockedPreview() {
    PairingPreview(
        PairingUiState(blocker = PairingBlocker.BLUETOOTH_OFF, attempt = PairingAttempt.Declined),
    )
}

/** The phone's devices are being read for the first time. */
@Preview
@Composable
private fun PairingReadingPreview() {
    PairingPreview(null)
}

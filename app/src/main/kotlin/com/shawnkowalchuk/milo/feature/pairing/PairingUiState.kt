package com.shawnkowalchuk.milo.feature.pairing

import android.content.IntentSender
import com.shawnkowalchuk.milo.platform.bluetooth.PairedDevice
import com.shawnkowalchuk.milo.platform.bluetooth.PairedDeviceList
import com.shawnkowalchuk.milo.platform.bluetooth.PairedDevicesProblem
import com.shawnkowalchuk.milo.platform.bluetooth.PairingProgress
import com.shawnkowalchuk.milo.platform.bluetooth.PairingState
import com.shawnkowalchuk.milo.platform.bluetooth.PairingStatus
import com.shawnkowalchuk.milo.platform.bluetooth.Truck
import com.shawnkowalchuk.milo.platform.bluetooth.sameAddress

/** The one thing that stops Shawn from picking a truck right now. The screen says how to fix it. */
enum class PairingBlocker {
    /** "Nearby devices" is not allowed, so the phone's paired devices cannot even be listed. */
    PERMISSION_MISSING,
    NO_BLUETOOTH,
    BLUETOOTH_OFF,

    /** Location is switched off for the phone. Android needs it on to make the association. */
    LOCATION_OFF,

    /** The list was read and is empty: the truck is not paired with the phone itself yet. */
    NOTHING_PAIRED,
}

/** Whether Android will wake MilO for the stored truck. */
enum class TruckWatch { WATCHED, ASSOCIATION_MISSING, NOT_SUPPORTED, CHECK_FAILED, CHECKING }

/** The truck MilO has stored, and whether it is being watched for. */
data class TruckLine(val name: String?, val watch: TruckWatch)

/** How far the attempt to pair that was started on this screen has got. */
sealed interface PairingAttempt {
    /** Nothing has been tried since the screen was opened. */
    data object None : PairingAttempt

    /** Android has been asked. Its consent dialog may be showing. */
    data object Asking : PairingAttempt

    data class Paired(val name: String?) : PairingAttempt

    /** Shawn closed Android's dialog without allowing. Nothing was changed. */
    data object Declined : PairingAttempt

    data class Failed(val why: String) : PairingAttempt
}

/** A device from the phone's list. [isTruck] marks the one MilO has stored as the truck. */
data class DeviceLine(val device: PairedDevice, val isTruck: Boolean)

/**
 * Everything the pairing screen shows.
 *
 * @param consent Android's consent dialog, waiting to be shown by the screen, or null.
 */
data class PairingUiState(
    val truck: TruckLine? = null,
    val blocker: PairingBlocker? = null,
    val devices: List<DeviceLine> = emptyList(),
    val attempt: PairingAttempt = PairingAttempt.None,
    val consent: IntentSender? = null,
) {
    /** A truck can be picked when nothing is in the way and no attempt is under way. */
    val canPick: Boolean get() = blocker == null && attempt != PairingAttempt.Asking
}

/**
 * What the screen's state is worked out from.
 *
 * @param paired the phone's paired devices as last read, or why they could not be read.
 * @param truck the truck in the settings, or null.
 * @param status the last check of the pairing, or null before the first one.
 * @param progress `TruckPairing`'s progress. It belongs to the whole app and outlives the
 * screen, so it can still show the result of an attempt made on an earlier visit.
 * @param attemptedHere true once a device has been picked on this visit to the screen. Until
 * then [progress] is old news and is not shown.
 * @param dialogClosedWithoutAllowing true if Shawn closed Android's consent dialog, or refused
 * in it (see [consentWasDeclined]). A dialog that failed by itself does not set it.
 */
data class PairingInputs(
    val paired: PairedDeviceList,
    val locationOn: Boolean,
    val truck: Truck?,
    val status: PairingStatus?,
    val progress: PairingProgress,
    val attemptedHere: Boolean,
    val dialogClosedWithoutAllowing: Boolean,
)

/** The consent dialog's result when it was closed with Back or a tap outside it. */
private const val CONSENT_CANCELLED = 0

/**
 * The consent dialog's result for "Don't allow": `CompanionDeviceManager.RESULT_USER_REJECTED`,
 * written out because the constant arrived with Android 13 and MilO also runs on Android 12.
 */
private const val CONSENT_REFUSED = 1

/**
 * Whether [resultCode], the result of Android's consent dialog, means that Shawn himself closed
 * or refused it. That is not an error: he changed his mind, and nothing was changed.
 *
 * From Android 13 the dialog has other results that are not "allowed" either: Android gave up
 * looking for the device (2), or failed inside (3). Those are failures, and the screen has to
 * say so; told to "pick again" he would never learn why it keeps not working.
 */
fun consentWasDeclined(resultCode: Int): Boolean =
    resultCode == CONSENT_CANCELLED || resultCode == CONSENT_REFUSED

/** Decides what the pairing screen shows. A pure function, so it is tested without a phone. */
fun pairingUiState(inputs: PairingInputs): PairingUiState = PairingUiState(
    truck = inputs.truck?.let { TruckLine(it.name, truckWatch(inputs.status)) },
    blocker = blockerOf(inputs.paired, inputs.locationOn),
    devices =
        inputs.paired.devices.map {
            DeviceLine(it, isTruck = sameAddress(it.address, inputs.truck?.address))
        },
    attempt = if (inputs.attemptedHere) attemptOf(inputs) else PairingAttempt.None,
    consent =
        (inputs.progress as? PairingProgress.ConsentNeeded)
            ?.intentSender
            ?.takeIf { inputs.attemptedHere },
)

/**
 * The first thing in the way, in the order Shawn has to deal with them: nothing can be listed
 * without the permission and Bluetooth, and nothing can be paired without location.
 */
private fun blockerOf(paired: PairedDeviceList, locationOn: Boolean): PairingBlocker? = when {
    paired.problem == PairedDevicesProblem.PERMISSION_MISSING -> PairingBlocker.PERMISSION_MISSING
    paired.problem == PairedDevicesProblem.NO_BLUETOOTH -> PairingBlocker.NO_BLUETOOTH
    paired.problem == PairedDevicesProblem.BLUETOOTH_OFF -> PairingBlocker.BLUETOOTH_OFF
    !locationOn -> PairingBlocker.LOCATION_OFF
    paired.devices.isEmpty() -> PairingBlocker.NOTHING_PAIRED
    else -> null
}

private fun truckWatch(status: PairingStatus?): TruckWatch = when (status?.state) {
    PairingState.ARMED -> TruckWatch.WATCHED

    PairingState.ASSOCIATION_MISSING -> TruckWatch.ASSOCIATION_MISSING

    PairingState.NOT_SUPPORTED -> TruckWatch.NOT_SUPPORTED

    PairingState.FAILED -> TruckWatch.CHECK_FAILED

    // "No truck" beside a stored truck is a check that ran before the truck was stored: the
    // next one is on its way.
    PairingState.NO_TRUCK, null -> TruckWatch.CHECKING
}

private fun attemptOf(inputs: PairingInputs): PairingAttempt =
    when (val progress = inputs.progress) {
        PairingProgress.Idle -> PairingAttempt.None

        PairingProgress.Asking, is PairingProgress.ConsentNeeded -> PairingAttempt.Asking

        is PairingProgress.Paired -> PairingAttempt.Paired(progress.truck.name)

        is PairingProgress.Failed ->
            // `TruckPairing` reports a dialog Shawn closed as a failure like any other. It is
            // told apart here because it is not an error.
            if (inputs.dialogClosedWithoutAllowing) {
                PairingAttempt.Declined
            } else {
                PairingAttempt.Failed(progress.why)
            }
    }

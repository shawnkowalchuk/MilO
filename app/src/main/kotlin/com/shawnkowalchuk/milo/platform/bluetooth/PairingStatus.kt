package com.shawnkowalchuk.milo.platform.bluetooth

import android.content.IntentSender

// What [TruckPairing] reports: the standing state of the pairing, and how far an attempt to
// pair has got. The pairing screen and the permission checklist will show both.

/** Whether the system will wake MilO for the truck. */
enum class PairingState {
    /** The association exists and Android has been asked to watch for the truck. */
    ARMED,

    /**
     * No truck has been picked yet. Also on a phone without companion device support, as long
     * as no truck is stored: the detail then says so.
     */
    NO_TRUCK,

    /**
     * A truck is stored, but Android no longer lists its association. That happens when it is
     * removed in the phone's Bluetooth settings. The truck has to be paired again.
     */
    ASSOCIATION_MISSING,

    /**
     * A truck is stored, but this phone has no companion device support. The Bluetooth receiver
     * still works by itself, but nothing gives MilO a standing permission to start recording
     * from the background.
     */
    NOT_SUPPORTED,

    /** The check itself failed. The detail says how. */
    FAILED,
}

/** @param detail the state in words, for the event log and for a screen that wants to say more. */
data class PairingStatus(val state: PairingState, val detail: String)

/** Where an attempt to pair the truck has got to. The pairing screen shows it. */
sealed interface PairingProgress {
    /** Nothing is being paired. */
    data object Idle : PairingProgress

    /** Android has been asked and has not answered yet. */
    data object Asking : PairingProgress

    /**
     * Android wants Shawn's consent. The screen launches [intentSender] from its Activity, which
     * shows Android's own dialog, and passes the result to [TruckPairing.onConsentResult].
     */
    data class ConsentNeeded(val intentSender: IntentSender) : PairingProgress

    /** The truck is stored. The next [TruckPairing.status] says whether it is armed. */
    data class Paired(val truck: Truck) : PairingProgress

    data class Failed(val why: String) : PairingProgress
}

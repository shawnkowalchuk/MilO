package com.shawnkowalchuk.milo.feature.pairing

import android.app.Activity
import android.content.IntentSender
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shawnkowalchuk.milo.data.settings.SettingsStore
import com.shawnkowalchuk.milo.platform.bluetooth.PairedDevice
import com.shawnkowalchuk.milo.platform.bluetooth.PairedDeviceList
import com.shawnkowalchuk.milo.platform.bluetooth.Truck
import com.shawnkowalchuk.milo.platform.bluetooth.TruckPairing
import com.shawnkowalchuk.milo.platform.bluetooth.trucks
import com.shawnkowalchuk.milo.platform.system.PermissionAsk
import com.shawnkowalchuk.milo.platform.system.SetupFix
import com.shawnkowalchuk.milo.platform.system.SystemScreen
import com.shawnkowalchuk.milo.platform.system.SystemScreens
import com.shawnkowalchuk.milo.platform.system.androidDidNotAsk
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** How long the state stays current after the screen stopped watching it (a rotation). */
private const val KEEP_WATCHING_MS = 5_000L

/**
 * The pairing screen's link to `TruckPairing`, which does the pairing and already existed
 * before there was a screen. This class adds only what a screen needs on top: the phone's list
 * of devices read afresh, and what happened on this visit.
 *
 * @param isLocationOn asks the phone whether location is switched on.
 * @param bluetoothSwitched says each time the phone's Bluetooth has finished switching on or off.
 */
class PairingViewModel(
    private val pairing: TruckPairing,
    settings: SettingsStore,
    private val isLocationOn: () -> Boolean,
    bluetoothSwitched: Flow<Unit>,
    private val screens: SystemScreens,
) : ViewModel() {
    /** What only this visit to the screen knows. */
    private data class Visit(
        val paired: PairedDeviceList = PairedDeviceList(emptyList(), problem = null),
        val locationOn: Boolean = true,
        val hasRead: Boolean = false,
        val attemptedHere: Boolean = false,
        val dialogClosedWithoutAllowing: Boolean = false,
    )

    private val visit = MutableStateFlow(Visit())

    /**
     * The paired vehicles, the truck first. If the settings cannot be read there is none to
     * show; the pairing check reports the unreadable file itself, in its state and in the log.
     */
    private val trucks: Flow<List<Truck>> =
        settings.settings.map { it.trucks() }.catch { failure ->
            if (failure !is IOException) throw failure
            emit(emptyList())
        }

    /** Null until the phone's devices have been read for the first time. */
    val state: StateFlow<PairingUiState?> =
        combine(visit, trucks, pairing.status, pairing.progress) { here, stored, status, progress ->
            if (!here.hasRead) return@combine null
            pairingUiState(
                PairingInputs(
                    paired = here.paired,
                    locationOn = here.locationOn,
                    trucks = stored,
                    status = status,
                    progress = progress,
                    attemptedHere = here.attemptedHere,
                    dialogClosedWithoutAllowing = here.dialogClosedWithoutAllowing,
                ),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(KEEP_WATCHING_MS), null)

    /** The consent dialog already handed to the screen, so a rotation does not show it twice. */
    private var shownConsent: IntentSender? = null

    private var pendingAsk: PermissionAsk? = null

    /**
     * One reading of the phone at a time. Several things ask for one within a moment of each
     * other (the screen resumes, its window gets the focus, Bluetooth finishes switching on),
     * and a reading that started before Bluetooth was on must not be the last one shown.
     */
    private val reading = Mutex()

    init {
        // Bluetooth takes a second or two to switch on, and Shawn is back on this screen sooner
        // than that. The list read at his return would say "switched off" for good, so it is
        // read once more when Android says the switch has settled. Watched for as long as the
        // pairing screen is on the back stack.
        viewModelScope.launch { bluetoothSwitched.collect { onCameToFront() } }
    }

    /**
     * Reads the phone again: Shawn leaves this screen to switch Bluetooth or location on, or to
     * pair the truck with the phone, and Android reports none of that to MilO. Safe to call
     * twice in a row.
     */
    fun onCameToFront() {
        viewModelScope.launch(Dispatchers.Default) {
            reading.withLock {
                val paired = pairing.pairedDevices()
                val locationOn = isLocationOn()
                visit.update { it.copy(paired = paired, locationOn = locationOn, hasRead = true) }
            }
        }
    }

    /**
     * Shawn picked [device]. Since 2026-10-08 `TruckPairing` adds it beside the vehicles
     * already paired, or pairs it again if it is one of them.
     *
     * @param activity the screen's Activity: Android shows its consent dialog on top of it. It
     * is passed through and not kept.
     */
    fun onDevicePicked(device: PairedDevice, activity: Activity) {
        visit.update { it.copy(attemptedHere = true, dialogClosedWithoutAllowing = false) }
        pairing.associate(device, activity)
    }

    /** Shawn confirmed that [vehicle] is to be removed. */
    fun onRemove(vehicle: Truck) {
        pairing.remove(vehicle)
    }

    /** True the first time it is asked about [consent]: the screen then shows the dialog. */
    fun claimConsent(consent: IntentSender): Boolean {
        if (shownConsent === consent) return false
        shownConsent = consent
        return true
    }

    /**
     * Android's consent dialog has closed. Only Shawn closing or refusing it is "not paired,
     * nothing changed"; a dialog that failed by itself is left to be shown as the failure it
     * is, with `TruckPairing`'s reason.
     */
    fun onConsentResult(resultCode: Int) {
        if (consentWasDeclined(resultCode)) {
            visit.update { it.copy(dialogClosedWithoutAllowing = true) }
        }
        pairing.onConsentResult(resultCode)
    }

    fun onOpenScreen(screen: SystemScreen) {
        screens.open(screen)
    }

    /** The screen is about to show Android's permission dialog for [fix]. */
    fun onAsking(fix: SetupFix.AskPermission, couldExplainBefore: Boolean) {
        pendingAsk = PermissionAsk(fix, couldExplainBefore)
    }

    /** Android's permission dialog has answered, or never appeared (see [androidDidNotAsk]). */
    fun onPermissionAnswer(granted: Boolean, canExplainAfter: Boolean) {
        val ask = pendingAsk ?: return
        pendingAsk = null
        if (androidDidNotAsk(granted, ask.couldExplainBefore, canExplainAfter)) {
            screens.open(ask.fix.ifNotAsked)
        }
        onCameToFront()
    }
}

package com.shawnkowalchuk.milo.platform.bluetooth

import android.app.Activity
import android.content.Context
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.settings.SettingsStore
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Pairs MilO with the truck (ADR-002, "Pairing") and keeps the pairing armed.
 *
 * Pairing is three steps. The screen lists [pairedDevices] and Shawn picks the truck. MilO asks
 * Android to associate it ([associate]), and Android asks Shawn for his consent in a dialog of
 * its own. Then MilO stores the truck and asks Android to watch for it. From then on Android
 * binds MilO's companion service whenever the truck connects.
 *
 * The association is Android's, not MilO's: it can be removed in the phone's Bluetooth settings
 * without MilO being told (before Android 16). So [check] runs at every start of the process
 * and every time the app is opened. What it looks at is in [TruckPairingCheck].
 *
 * There is no screen yet. Everything here is what the pairing screen will call.
 *
 * @param onTruckChanged called after a truck has been stored. Android does not always report a
 * truck that is already connected when it starts to watch for it, so the caller reads the
 * truck's connection itself.
 * @param scope the application scope. Everything that touches storage or Android runs in it,
 * so nothing here holds up the thread it is called on.
 */
class TruckPairing internal constructor(
    private val settings: SettingsStore,
    private val link: CompanionLink,
    private val listPaired: () -> PairedDeviceList,
    private val eventLog: EventLogRepository,
    private val onTruckChanged: () -> Unit,
    private val clock: () -> Long,
    private val scope: CoroutineScope,
) {
    private val pairingCheck =
        TruckPairingCheck(settings, link, listPaired, eventLog, onTruckChanged, clock)

    private val mutableProgress = MutableStateFlow<PairingProgress>(PairingProgress.Idle)
    val progress: StateFlow<PairingProgress> = mutableProgress.asStateFlow()

    private val mutableStatus = MutableStateFlow<PairingStatus?>(null)

    /** What the last [check] found, or null before the first one has finished. */
    val status: StateFlow<PairingStatus?> = mutableStatus.asStateFlow()

    /**
     * The device an association is being made for. Taken when the pairing finishes or fails,
     * so that it happens once: both the callback and the dialog's result report success.
     */
    private val pending = AtomicReference<PairedDevice?>(null)

    /** The phone's paired Bluetooth devices, for Shawn to pick the truck from. */
    fun pairedDevices(): PairedDeviceList = listPaired()

    /**
     * Starts pairing with [device]. What happens next arrives in [progress].
     *
     * On a phone without companion device support the truck is stored without an association,
     * so that the Bluetooth receiver at least knows which device to listen for.
     *
     * @param activity the pairing screen's Activity. Android's consent dialog belongs to it.
     */
    fun associate(device: PairedDevice, activity: Activity) {
        val truck = device.copy(address = device.address.asBluetoothAddress())
        pending.set(truck)
        mutableProgress.value = PairingProgress.Asking
        if (!link.supported) {
            finish()
            return
        }
        try {
            link.associate(activity, truck.address, ::onStep)
        } catch (refused: RuntimeException) {
            // Android's companion manager refuses with unchecked exceptions of several kinds
            // (a malformed address, a missing permission). Whichever it is, pairing failed.
            fail("Android refused the request to associate: $refused")
        }
    }

    /**
     * The screen passes on the result of Android's consent dialog. Android reports a new
     * association through a callback as well; whichever of the two comes first stores the truck.
     */
    fun onConsentResult(resultCode: Int) {
        if (resultCode == Activity.RESULT_OK) {
            finish()
        } else {
            fail("the request was not approved (result code $resultCode)")
        }
    }

    /**
     * Checks that the truck's association still exists and asks Android (again) to watch for
     * the truck. Safe to call at any time and as often as wanted: asking twice changes nothing.
     * The answer arrives in [status] and in the event log.
     *
     * @param occasion what prompted the check, for the event log.
     */
    fun check(occasion: String) {
        scope.launch { runCheck(occasion) }
    }

    private fun onStep(step: AssociationStep) {
        when (step) {
            is AssociationStep.ConsentNeeded ->
                mutableProgress.value = PairingProgress.ConsentNeeded(step.intentSender)

            AssociationStep.Created -> finish()

            is AssociationStep.Failed -> fail(step.why)
        }
    }

    private fun finish() {
        val device = pending.getAndSet(null) ?: return
        scope.launch {
            try {
                store(device)
            } catch (unwritable: IOException) {
                report(PairingProgress.Failed("the truck could not be stored: $unwritable"))
            } catch (refused: RuntimeException) {
                // See associate(): Android's refusals are unchecked exceptions.
                report(PairingProgress.Failed("Android refused while pairing: $refused"))
            }
        }
    }

    private fun fail(why: String) {
        // Nothing is pending: the pairing already finished, and this is a late echo.
        pending.getAndSet(null) ?: return
        scope.launch { report(PairingProgress.Failed(why)) }
    }

    private suspend fun store(device: PairedDevice) {
        val associations = if (link.supported) link.associations() else emptyList()
        val association = associations.newestFor(device.address)
        if (link.supported && association == null) {
            val why = "Android reported success but lists no association for ${device.address}"
            report(PairingProgress.Failed(why))
            return
        }
        // A blank name is no name: the settings store refuses to hold one.
        val truck = Truck(device.address, device.name?.takeIf { it.isNotBlank() }, association?.id)
        settings.setTruck(truck.address, truck.name, truck.associationId)
        // Every other association is left over from an earlier pairing. Android would go on
        // waking MilO for them.
        associations.filterNot { it == association }.forEach(link::remove)
        report(PairingProgress.Paired(truck))
        runCheck("paired with the truck")
        onTruckChanged()
    }

    private suspend fun report(result: PairingProgress) {
        mutableProgress.value = result
        val what =
            when (result) {
                is PairingProgress.Paired -> "Paired with the truck: ${result.truck}"
                is PairingProgress.Failed -> "Pairing failed: ${result.why}"
                else -> return
            }
        eventLog.add(clock(), EventCategory.PAIRING, what)
    }

    private suspend fun runCheck(occasion: String) {
        val found =
            try {
                pairingCheck.look()
            } catch (unreadable: IOException) {
                val detail = "the settings cannot be read or written: $unreadable"
                PairingStatus(PairingState.FAILED, detail)
            } catch (refused: RuntimeException) {
                // See associate(): Android's refusals are unchecked exceptions.
                PairingStatus(PairingState.FAILED, "Android refused: $refused")
            }
        mutableStatus.value = found
        val what = "Truck pairing checked ($occasion): ${found.state}: ${found.detail}"
        eventLog.add(clock(), EventCategory.PAIRING, what)
    }
}

/** Builds [TruckPairing] on the phone's own companion device manager and Bluetooth adapter. */
fun buildTruckPairing(
    context: Context,
    settings: SettingsStore,
    eventLog: EventLogRepository,
    onTruckChanged: () -> Unit,
    clock: () -> Long,
    scope: CoroutineScope,
): TruckPairing {
    val appContext = context.applicationContext
    return TruckPairing(
        settings = settings,
        link = SystemCompanionLink(appContext),
        listPaired = { listPairedDevices(appContext) },
        eventLog = eventLog,
        onTruckChanged = onTruckChanged,
        clock = clock,
        scope = scope,
    )
}

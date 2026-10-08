package com.shawnkowalchuk.milo.platform.bluetooth

import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.settings.SettingsStore
import com.shawnkowalchuk.milo.data.settings.setVehicleAssociation

/**
 * The check behind [TruckPairing.check]: does the truck's association still exist, and is
 * Android watching for the truck? It also adopts an association that was made outside MilO.
 * Since 2026-10-08 it does the same for every other vehicle paired beside the truck, whose
 * associations are theirs and are never adopted as the truck.
 *
 * The association is Android's, not MilO's. It can be removed in the phone's Bluetooth settings
 * without MilO being told (before Android 16), and it can be made over adb.
 *
 * @param onTruckChanged called after a truck has been stored.
 */
internal class TruckPairingCheck(
    private val settings: SettingsStore,
    private val link: CompanionLink,
    private val listPaired: () -> PairedDeviceList,
    private val eventLog: EventLogRepository,
    private val onTruckChanged: () -> Unit,
    private val clock: () -> Long,
) {
    /**
     * Looks at the pairing and asks Android (again) to watch for the truck. Asking twice
     * changes nothing.
     *
     * @throws java.io.IOException if the settings cannot be read or written.
     * @throws RuntimeException if Android refuses: its companion manager does so with unchecked
     * exceptions of several kinds.
     */
    suspend fun look(): PairingStatus {
        val now = settings.current()
        val stored = now.truck()
        val more = now.trucks().drop(1)
        if (!link.supported) return withoutCompanionSupport(stored)
        val all = link.associations()
        // The other vehicles' associations are theirs: never adopted as the truck.
        val associations = all.filterNot { a -> more.any { sameAddress(it.address, a.address) } }
        val truck =
            stored?.takeIf { associations.newestFor(it.address) != null }
                ?: adopt(associations, inPlaceOf = stored)
        val observed = mutableListOf<String>()
        val missing = mutableListOf<String>()
        for (vehicle in listOfNotNull(truck) + more) {
            val association = all.newestFor(vehicle.address)
            if (association == null) {
                missing += vehicle.address
                continue
            }
            // Android assigns the id. It is stored only so that an event which names the id
            // alone (Android 16) can be matched, so it follows whatever Android says it is.
            if (association.id != vehicle.associationId) {
                settings.setVehicleAssociation(vehicle.address, association.id)
            }
            link.startObserving(association)
            observed += "association ${association.id} for ${vehicle.address}"
        }
        if (truck == null) {
            val notArmed = notArmed(stored, associations)
            val lost = listOfNotNull(stored?.address) + missing
            if (more.isEmpty()) return notArmed.copy(missing = lost)
            val detail = "${notArmed.detail} ${vehiclesText(observed, missing)}"
            return PairingStatus(notArmed.state, detail, lost)
        }
        if (missing.isEmpty()) {
            return PairingStatus(PairingState.ARMED, vehiclesText(observed, missing))
        }
        val detail = "${vehiclesText(observed, missing)} ${pairedWithThePhone()}"
        return PairingStatus(PairingState.ASSOCIATION_MISSING, detail, missing.toList())
    }

    /**
     * Which vehicles are watched for and which are not, in words: "association 7 for AA:…
     * is observed", as the line has always read with one vehicle.
     */
    private fun vehiclesText(observed: List<String>, missing: List<String>): String {
        val parts = mutableListOf<String>()
        if (observed.isNotEmpty()) {
            val verb = if (observed.size == 1) "is" else "are"
            parts += "${observed.joinToString()} $verb observed"
        }
        if (missing.isNotEmpty()) {
            parts += "no association any more for ${missing.joinToString()}: to be paired again"
        }
        return parts.joinToString("; ")
    }

    /**
     * A phone that does not report companion device support. Nothing can be associated or
     * adopted on it; a truck gets stored only by the pairing screen, for the Bluetooth receiver.
     */
    private fun withoutCompanionSupport(stored: Truck?): PairingStatus {
        val unsupported = "this phone has no companion device support"
        if (stored != null) {
            val detail = "$unsupported. Only the Bluetooth receiver can start a trip for " +
                stored.address
            return PairingStatus(PairingState.NOT_SUPPORTED, detail)
        }
        val detail = "no truck is paired, and $unsupported, so no association can be adopted. " +
            pairedWithThePhone()
        return PairingStatus(PairingState.NO_TRUCK, detail)
    }

    /**
     * An association that has no truck stored for it: MilO takes its device for the truck,
     * provided it is the only association and its device is paired with the phone. MilO makes
     * the association and stores the truck together, so in ordinary use this never happens. It
     * is here so that automatic start can be tried on the phone before the pairing screen
     * exists: `adb shell cmd companiondevice associate` makes the association, and the next
     * check adopts it (docs/DEVICE_TEST_CHECKLIST.md).
     *
     * A device that is not paired with the phone cannot be the truck. Adopted all the same, one
     * mistyped digit in the adb command would be stored as the truck, and every broadcast for
     * the real truck would be ignored as "another device".
     *
     * @param inPlaceOf the stored truck whose own association is gone, or null if none is
     * stored. Replacing it is the way back from a wrong address, and the way to pair another
     * truck, while there is no pairing screen.
     */
    private suspend fun adopt(associations: List<Association>, inPlaceOf: Truck?): Truck? {
        val only = associations.singleOrNull() ?: return null
        val device = pairedDevice(only) ?: return null
        val name = device.name?.takeIf { it.isNotBlank() }
        val truck = Truck(only.address.asBluetoothAddress(), name, only.id)
        settings.setTruck(truck.address, truck.name, truck.associationId)
        val what =
            if (inPlaceOf == null) {
                "No truck was stored and Android lists one association. Adopted it: $truck"
            } else {
                "Android lists no association for the stored truck (${inPlaceOf.address}) and " +
                    "one for another paired device. Adopted it in its place: $truck"
            }
        eventLog.add(clock(), EventCategory.PAIRING, what)
        onTruckChanged()
        return truck
    }

    private fun pairedDevice(association: Association): PairedDevice? =
        listPaired().devices.firstOrNull { sameAddress(it.address, association.address) }

    /** No truck is armed, and nothing could be adopted. Says which of the two, and why. */
    private fun notArmed(stored: Truck?, associations: List<Association>): PairingStatus {
        val others =
            when (associations.size) {
                0 -> "Android lists no association at all."

                // The one adopt() turned down.
                1 ->
                    "Android lists one association, for ${associations.single().address}, " +
                        "which was not adopted: MilO does not find that address among the " +
                        "devices paired with the phone."

                else ->
                    "Android lists ${associations.size} associations, and MilO does not guess " +
                        "between them."
            }
        if (stored == null) {
            val detail = "no truck is paired. $others ${pairedWithThePhone()}"
            return PairingStatus(PairingState.NO_TRUCK, detail)
        }
        val detail = "the truck (${stored.address}) has no association any more and has to be " +
            "paired again. $others ${pairedWithThePhone()}"
        return PairingStatus(PairingState.ASSOCIATION_MISSING, detail)
    }

    /**
     * The phone's paired devices with their addresses, for the event log. Until the pairing
     * screen exists this line is where the truck's address is read from.
     */
    private fun pairedWithThePhone(): String {
        val paired = listPaired()
        val devices = paired.devices.joinToString { "${it.name ?: "no name"} (${it.address})" }
        return "Paired with the phone: ${paired.problem ?: devices.ifEmpty { "nothing" }}"
    }
}

/**
 * The association for [address]. Android numbers associations upwards, so if it made a second
 * one for a truck that was paired before, the one with the highest id is the newest.
 */
internal fun List<Association>.newestFor(address: String): Association? =
    filter { sameAddress(it.address, address) }.maxByOrNull { it.id }

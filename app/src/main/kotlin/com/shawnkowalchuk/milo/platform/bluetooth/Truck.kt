package com.shawnkowalchuk.milo.platform.bluetooth

import com.shawnkowalchuk.milo.data.settings.MiloSettings
import com.shawnkowalchuk.milo.data.settings.SettingsStore
import java.io.IOException
import java.util.Locale
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * A vehicle MilO starts trips for, as it was stored at pairing: the truck it always had, and
 * since 2026-10-08 any other vehicle paired beside it (`data/settings/VehicleStorage.kt`). Each
 * is matched, read and watched for the same way.
 *
 * @param address its Bluetooth address, in capitals, the way Android's Bluetooth classes write it.
 * @param name its name as the phone shows it, or null if it has none.
 * @param associationId the id of its companion device association. Null on a phone without
 * companion device support, and for a truck that arrived with a restore or an import and has no
 * association on this phone yet.
 */
data class Truck(val address: String, val name: String?, val associationId: Int?) {
    /**
     * Whether an event is about this truck. An event names its device by address, by
     * association id, or by both, depending on the Android version that sent it.
     */
    fun isDevice(address: String?, associationId: Int?): Boolean =
        sameAddress(this.address, address) ||
            (associationId != null && associationId == this.associationId)
}

/** A device from the phone's own list of paired Bluetooth devices. The pairing screen lists them. */
data class PairedDevice(val address: String, val name: String?)

/** The first paired vehicle, the truck MilO always had, or null before pairing. */
fun MiloSettings.truck(): Truck? = truckAddress?.let {
    Truck(it, truckName, truckAssociationId)
}

/** Every paired vehicle, the first one first and the others in the order they were paired. */
fun MiloSettings.trucks(): List<Truck> = listOfNotNull(truck()) +
    moreVehicles.map { Truck(it.address, it.name, it.associationId) }

/** The paired vehicle an event names, by address or by association id, or null if none. */
fun List<Truck>.named(address: String?, associationId: Int?): Truck? =
    firstOrNull { it.isDevice(address, associationId) }

/**
 * Bluetooth addresses are compared without regard to case. Android's Bluetooth classes write
 * them in capitals and its companion device classes in small letters, so a plain comparison of
 * the two would never match.
 */
fun sameAddress(one: String?, other: String?): Boolean =
    one != null && other != null && one.equals(other, ignoreCase = true)

/** The form addresses are stored in, and the only form Android's Bluetooth classes accept. */
fun String.asBluetoothAddress(): String = uppercase(Locale.ROOT)

/**
 * What a trigger learns when it asks which devices are the paired vehicles.
 *
 * @param trucks every paired vehicle, the first one first; empty if none is paired.
 * @param problem why the question could not be answered, or null when it was. No [trucks] with
 * no [problem] means that no vehicle is paired.
 */
data class TruckLookup(val trucks: List<Truck>, val problem: String? = null) {
    /** For a lookup that found one truck, or none. */
    constructor(truck: Truck?, problem: String? = null) : this(listOfNotNull(truck), problem)

    /** The first paired vehicle, or null. */
    val truck: Truck? get() = trucks.firstOrNull()
}

/** The longest a trigger waits for the settings before it stops trying to tell whose event it is. */
private const val LOOKUP_LIMIT_MS = 1_000L

/**
 * Tells a trigger which device is the truck, at once, on whatever thread the trigger fired.
 *
 * **It blocks, on purpose.** A Bluetooth broadcast arrives for every device the phone connects
 * to, and the receiver has to decide inside `onReceive` whether this one is about the truck:
 * ADR-002 has the trip service asked for before `onReceive` returns, because Android's
 * allowance for that start is counted in seconds from the broadcast. The truck's address is in
 * the settings file, which can only be read asynchronously, so the caller waits for it here.
 * The file is a few hundred bytes; after the first read the answer comes from memory. A read
 * that takes longer than a second is given up, and the caller falls back on reading the truck's
 * connection instead of trusting the event.
 */
class PairedTruck(private val settings: SettingsStore) {
    fun now(): TruckLookup = try {
        val current = runBlocking { withTimeoutOrNull(LOOKUP_LIMIT_MS) { settings.current() } }
        if (current == null) {
            TruckLookup(null, "the settings took more than $LOOKUP_LIMIT_MS ms to read")
        } else {
            TruckLookup(current.trucks())
        }
    } catch (unreadable: IOException) {
        // The settings file is never reset (see buildSettingsStore). The trip controller logs
        // the unreadable file with its stack trace; here only the fact is needed.
        TruckLookup(null, "the settings cannot be read ($unreadable)")
    }
}

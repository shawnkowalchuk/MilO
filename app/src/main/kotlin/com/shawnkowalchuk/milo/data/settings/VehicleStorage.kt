package com.shawnkowalchuk.milo.data.settings

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey

// The vehicles MilO starts trips for (Shawn's request of 2026-10-08: "can we add the option to
// link multiple vehicles to blutooth"; his answers: several work vehicles, each trip records
// which one it was in, an odometer for each, the phone only).
//
// **The first vehicle is the truck MilO always had,** kept under the three keys it always had
// (truck_address, truck_name, truck_association_id; `SettingsStore.setTruck`). Every path that
// knew one truck goes on reading it there unchanged, and a phone with one vehicle stores
// nothing new. **The others** are one entry each of a string set,
// "address<TAB>pairedAtMs<TAB>associationId<TAB>name", the association id and the name empty
// when there is none, read back in the order they were paired. An entry that is not of that
// form is skipped, never guessed at. Removing the first vehicle moves the next one into its
// keys, so the first is always stored there.

private val MORE_VEHICLES = stringSetPreferencesKey("more_vehicles")

/**
 * True once the trips recorded before MilO knew several vehicles have been given the first
 * vehicle (`data/trip/TripVehicleCatchUp`). Not a setting.
 */
private val TRIP_VEHICLES_FILLED = booleanPreferencesKey("trip_vehicles_filled")

private const val SEPARATOR = '\t'
private const val PARTS = 4

/**
 * One vehicle MilO starts trips for, beside the first.
 *
 * @param address its Bluetooth address, in capitals.
 * @param name its name as the phone shows it, for display only; null if it has none.
 * @param associationId the id of its companion device association, or null without one.
 * @param pairedAtMs when it was paired: the list is in that order.
 */
data class StoredVehicle(
    val address: String,
    val name: String?,
    val associationId: Int?,
    val pairedAtMs: Long,
)

/**
 * Every paired vehicle, the first one (the truck, which has no time of pairing: 0) first and
 * the others in the order they were paired.
 */
fun MiloSettings.pairedVehicles(): List<StoredVehicle> =
    listOfNotNull(truckAddress?.let { StoredVehicle(it, truckName, truckAssociationId, 0) }) +
        moreVehicles

/**
 * How a vehicle is named on the report and the screens: its name as the phone shows it, or its
 * address where it has none or is no longer paired.
 */
fun MiloSettings.vehicleNamed(address: String): String =
    pairedVehicles().firstOrNull { it.address.equals(address, ignoreCase = true) }?.name ?: address

/** The vehicles beside the first, in the order they were paired. */
internal fun Preferences.readMoreVehicles(): List<StoredVehicle> = this[MORE_VEHICLES]
    .orEmpty()
    .mapNotNull(::vehicleOf)
    .sortedWith(compareBy({ it.pairedAtMs }, { it.address }))

/** Whether the trips recorded before several vehicles have been given the first one. */
internal fun Preferences.readTripVehiclesFilled(): Boolean = this[TRIP_VEHICLES_FILLED] ?: false

/**
 * Stores a vehicle that was just paired, or paired again: the first vehicle if there is none,
 * that vehicle again if it is the first, and otherwise one of the others, in place of an entry
 * with the same address. A vehicle paired again keeps its place in the list.
 *
 * @param pairedAtMs now, for a vehicle that is new to the list.
 */
suspend fun SettingsStore.storeVehicle(
    address: String,
    name: String?,
    associationId: Int?,
    pairedAtMs: Long,
) {
    require(address.isNotBlank()) { "A vehicle needs a Bluetooth address" }
    require(name == null || name.isNotBlank()) { "A vehicle's name is text, or null for none" }
    dataStore.edit { stored ->
        val first = stored[SettingsStore.TRUCK_ADDRESS]
        if (first == null || first.equals(address, ignoreCase = true)) {
            stored.setFirstVehicle(address, name, associationId)
            return@edit
        }
        val others = stored.readMoreVehicles()
        val kept = others.firstOrNull { it.address.equals(address, ignoreCase = true) }
        val vehicle = StoredVehicle(address, name, associationId, kept?.pairedAtMs ?: pairedAtMs)
        stored.setMoreVehicles(others.filterNot { it === kept } + vehicle)
    }
}

/**
 * Follows the id Android gave a vehicle's association (it assigns the id, and may give a new
 * one). Nothing happens for an address that is not a vehicle.
 */
suspend fun SettingsStore.setVehicleAssociation(address: String, associationId: Int?) {
    dataStore.edit { stored ->
        val first = stored[SettingsStore.TRUCK_ADDRESS]
        if (first != null && first.equals(address, ignoreCase = true)) {
            stored.setOrRemove(SettingsStore.TRUCK_ASSOCIATION_ID, associationId)
            return@edit
        }
        val others = stored.readMoreVehicles()
        if (others.none { it.address.equals(address, ignoreCase = true) }) return@edit
        stored.setMoreVehicles(
            others.map {
                if (it.address.equals(address, ignoreCase = true)) {
                    it.copy(associationId = associationId)
                } else {
                    it
                }
            },
        )
    }
}

/**
 * Forgets a vehicle. Removing the first moves the next in the list into its place, so that
 * whatever knew only one truck finds one as long as any is paired.
 */
suspend fun SettingsStore.removeVehicle(address: String) {
    dataStore.edit { stored ->
        val others = stored.readMoreVehicles()
        val first = stored[SettingsStore.TRUCK_ADDRESS]
        if (first == null || !first.equals(address, ignoreCase = true)) {
            stored.setMoreVehicles(
                others.filterNot {
                    it.address.equals(address, ignoreCase = true)
                },
            )
            return@edit
        }
        val next = others.firstOrNull()
        if (next == null) {
            stored.remove(SettingsStore.TRUCK_ADDRESS)
            stored.remove(SettingsStore.TRUCK_NAME)
            stored.remove(SettingsStore.TRUCK_ASSOCIATION_ID)
        } else {
            stored.setFirstVehicle(next.address, next.name, next.associationId)
            stored.setMoreVehicles(others.drop(1))
        }
    }
}

/** Notes that the trips recorded before several vehicles have their vehicle. */
suspend fun SettingsStore.setTripVehiclesFilled() {
    dataStore.edit { it[TRIP_VEHICLES_FILLED] = true }
}

/**
 * Takes the association ids off the vehicles beside the first: a restored settings file has
 * the ids of the phone it was backed up from, which mean nothing here. The pairing check at
 * the next start gives each vehicle Android on this phone watches for its id again.
 */
internal fun MutablePreferences.forgetMoreVehicleAssociations() {
    val others = readMoreVehicles()
    if (others.isNotEmpty()) setMoreVehicles(others.map { it.copy(associationId = null) })
}

/**
 * Has the pass over trips without a vehicle run again: an import brings trips from a file
 * that may not say which vehicle each was in.
 */
internal fun MutablePreferences.forgetTripVehiclesFilled() {
    remove(TRIP_VEHICLES_FILLED)
}

private fun MutablePreferences.setFirstVehicle(
    address: String,
    name: String?,
    associationId: Int?,
) {
    this[SettingsStore.TRUCK_ADDRESS] = address
    setOrRemove(SettingsStore.TRUCK_NAME, name)
    setOrRemove(SettingsStore.TRUCK_ASSOCIATION_ID, associationId)
}

private fun MutablePreferences.setMoreVehicles(vehicles: List<StoredVehicle>) {
    if (vehicles.isEmpty()) {
        remove(MORE_VEHICLES)
    } else {
        this[MORE_VEHICLES] =
            vehicles.map(::entryOf).toSet()
    }
}

internal fun entryOf(vehicle: StoredVehicle): String = listOf(
    vehicle.address,
    vehicle.pairedAtMs.toString(),
    vehicle.associationId?.toString().orEmpty(),
    vehicle.name.orEmpty(),
).joinToString(SEPARATOR.toString())

internal fun vehicleOf(entry: String): StoredVehicle? {
    val parts = entry.split(SEPARATOR, limit = PARTS)
    if (parts.size != PARTS || parts[0].isBlank()) return null
    val pairedAtMs = parts[1].toLongOrNull()?.takeIf { it >= 0 } ?: return null
    val associationId = if (parts[2].isEmpty()) null else parts[2].toIntOrNull() ?: return null
    return StoredVehicle(parts[0], parts[3].ifBlank { null }, associationId, pairedAtMs)
}

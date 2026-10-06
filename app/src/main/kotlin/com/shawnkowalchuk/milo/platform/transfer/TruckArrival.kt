package com.shawnkowalchuk.milo.platform.transfer

import com.shawnkowalchuk.milo.data.settings.TransferredTruck
import com.shawnkowalchuk.milo.data.settings.TruckChange
import com.shawnkowalchuk.milo.platform.bluetooth.sameAddress

// What becomes of the truck's pairing when settings arrive from somewhere else: restored by
// Android from a backup or from another phone, or read from an export file. One pure function
// decides it, so the decision is tested without a phone.
//
// The pairing has two halves. Which device the truck is (its Bluetooth address and name) is
// MilO's to know, and travels well. The companion device association is Android's, made on one
// phone with Shawn's consent in a dialog; its number means nothing anywhere else, and no file
// can make one. So the rule is: **the association on this phone decides, and nothing that
// arrives is ever taken to be paired because it says so.**

/**
 * What Android on this phone says about companion devices.
 *
 * @param companionSupported false on a phone that has no companion device support at all.
 * There a truck is stored without an association, and only the Bluetooth signal starts a trip.
 * @param associatedAddresses the Bluetooth addresses MilO holds an association for, in
 * Android's own spelling.
 */
data class PairingHere(val companionSupported: Boolean, val associatedAddresses: List<String>) {
    fun isAssociated(truck: TransferredTruck): Boolean =
        associatedAddresses.any { sameAddress(it, truck.address) }

    /**
     * Whether this phone can use [truck] as it stands: Android watches for it, or this phone
     * cannot watch for any device and so has nothing to pair.
     */
    fun isInOrder(truck: TransferredTruck): Boolean = !companionSupported || isAssociated(truck)
}

/** What becomes of the truck. */
sealed interface TruckArrival {
    /** No truck arrived and none was stored here: there is still none. */
    data object NoTruck : TruckArrival

    /**
     * This phone's own truck stays, because it is in order here. An import does not change
     * which truck starts trips on a phone where that works.
     *
     * @param other the truck that arrived, if it was a different one. It is not stored.
     */
    data class OwnKept(val truck: TransferredTruck, val other: TransferredTruck?) : TruckArrival

    /** The truck that arrived is one Android on this phone already watches for. */
    data class Paired(val truck: TransferredTruck) : TruckArrival

    /**
     * The truck is known by address and name, and Android on this phone holds no association
     * for it. It is stored without one, so that MilO shows it as "paired before, pair it
     * again" and not as paired.
     */
    data class PairAgain(val truck: TransferredTruck) : TruckArrival

    /** This phone cannot watch for any device. The truck is stored for the Bluetooth signal. */
    data class StoredUnwatched(val truck: TransferredTruck) : TruckArrival
}

/**
 * Decides what becomes of the truck.
 *
 * @param arrived the truck in the settings that arrived, or null if they name none.
 * @param here the truck that was stored on this phone before, or null. After a restore there
 * is no "before": the settings file itself was replaced, so [arrived] is what is stored and
 * this is null.
 */
fun truckOnArrival(
    arrived: TransferredTruck?,
    here: TransferredTruck?,
    pairing: PairingHere,
): TruckArrival {
    if (here != null && pairing.isInOrder(here)) {
        val other = arrived?.takeUnless { sameAddress(it.address, here.address) }
        return TruckArrival.OwnKept(here, other)
    }
    // From here on this phone has no truck of its own that works. What arrived is taken; if
    // nothing arrived, the truck that was stored here stays, still to be paired.
    val truck = arrived ?: here ?: return TruckArrival.NoTruck
    return when {
        !pairing.companionSupported -> TruckArrival.StoredUnwatched(truck)
        pairing.isAssociated(truck) -> TruckArrival.Paired(truck)
        else -> TruckArrival.PairAgain(truck)
    }
}

/** Whether Shawn has to pair the truck on this phone before a trip can start by itself. */
val TruckArrival.needsPairing: Boolean get() = this is TruckArrival.PairAgain

/**
 * What an import writes to the stored truck. A truck that is stored anew is stored without an
 * association: where Android holds one for it, the pairing check that follows finds it.
 */
fun TruckArrival.asChange(): TruckChange = when (this) {
    TruckArrival.NoTruck, is TruckArrival.OwnKept -> TruckChange.Keep
    is TruckArrival.Paired -> TruckChange.Store(truck)
    is TruckArrival.PairAgain -> TruckChange.Store(truck)
    is TruckArrival.StoredUnwatched -> TruckChange.Store(truck)
}

/** The decision in a sentence, for the event log's `PAIRING` line. */
fun TruckArrival.inWords(): String = when (this) {
    TruckArrival.NoTruck -> "no truck arrived and none is stored"

    is TruckArrival.OwnKept ->
        "the truck this phone is paired with stays (${truck.address})" +
            (other?.let { ". The other truck that arrived (${it.address}) was not taken" } ?: "")

    is TruckArrival.Paired ->
        "the truck (${truck.address}) is one Android on this phone already watches for"

    is TruckArrival.PairAgain ->
        "the truck (${truck.address}) is known by address and name only. Android on this " +
            "phone holds no association for it, so it has to be paired again. The association " +
            "from the other installation was not kept"

    is TruckArrival.StoredUnwatched ->
        "the truck (${truck.address}) is stored. This phone has no companion device support, " +
            "so there is nothing to pair"
}

package com.shawnkowalchuk.milo.platform.transfer

import com.shawnkowalchuk.milo.data.settings.TransferredTruck
import com.shawnkowalchuk.milo.data.settings.TruckChange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What becomes of the truck's pairing when settings arrive from elsewhere: after a restore by
 * Android, and after an import of an export file. The rule: the association Android holds on
 * this phone decides, and nothing is taken to be paired because a file says so.
 */
class TruckArrivalTest {
    private val truck = TransferredTruck("AA:BB:CC:DD:EE:FF", "Work truck")
    private val other = TransferredTruck("11:22:33:44:55:66", "Old truck")

    private val nothingAssociated = PairingHere(companionSupported = true, emptyList())
    private val noSupport = PairingHere(companionSupported = false, emptyList())

    /** Android writes the addresses of its associations in small letters. */
    private fun associatedWith(vararg trucks: TransferredTruck) =
        PairingHere(companionSupported = true, trucks.map { it.address.lowercase() })

    // After a restore: the settings file itself was replaced, so nothing was "here" before.

    @Test
    fun `restored on another phone, the truck is known and has to be paired again`() {
        val arrival = truckOnArrival(arrived = truck, here = null, nothingAssociated)

        assertEquals(TruckArrival.PairAgain(truck), arrival)
        assertTrue(arrival.needsPairing)
    }

    @Test
    fun `restored onto an installation that still holds the truck's association, it is paired`() {
        val arrival = truckOnArrival(arrived = truck, here = null, associatedWith(truck))

        assertEquals(TruckArrival.Paired(truck), arrival)
        assertFalse(arrival.needsPairing)
    }

    @Test
    fun `an association for some other device does not make the restored truck paired`() {
        val arrival = truckOnArrival(arrived = truck, here = null, associatedWith(other))

        assertEquals(TruckArrival.PairAgain(truck), arrival)
    }

    @Test
    fun `restored without a truck, there is none`() {
        assertEquals(TruckArrival.NoTruck, truckOnArrival(null, null, nothingAssociated))
        assertEquals(TruckArrival.NoTruck, truckOnArrival(null, null, associatedWith(other)))
        assertFalse(TruckArrival.NoTruck.needsPairing)
    }

    @Test
    fun `restored on a phone that cannot watch for a device, the truck is only stored`() {
        val arrival = truckOnArrival(arrived = truck, here = null, noSupport)

        assertEquals(TruckArrival.StoredUnwatched(truck), arrival)
        assertFalse(arrival.needsPairing)
    }

    // After an import: this phone may have a truck of its own.

    @Test
    fun `an import onto the phone the file came from leaves its pairing alone`() {
        val arrival = truckOnArrival(arrived = truck, here = truck, associatedWith(truck))

        assertEquals(TruckArrival.OwnKept(truck, other = null), arrival)
        assertEquals(TruckChange.Keep, arrival.asChange())
        assertFalse(arrival.needsPairing)
    }

    @Test
    fun `an import never changes which truck starts trips on a phone where that works`() {
        val arrival = truckOnArrival(arrived = other, here = truck, associatedWith(truck))

        assertEquals(TruckArrival.OwnKept(truck, other = other), arrival)
        assertEquals(TruckChange.Keep, arrival.asChange())
    }

    @Test
    fun `an import of a file without a truck does not unpair the phone`() {
        val arrival = truckOnArrival(arrived = null, here = truck, associatedWith(truck))

        assertEquals(TruckArrival.OwnKept(truck, other = null), arrival)
    }

    @Test
    fun `an import onto a new phone stores the file's truck, to be paired`() {
        val arrival = truckOnArrival(arrived = truck, here = null, nothingAssociated)

        assertEquals(TruckArrival.PairAgain(truck), arrival)
        assertEquals(TruckChange.Store(truck), arrival.asChange())
    }

    @Test
    fun `a truck stored here whose association is gone gives way to the file's truck`() {
        val arrival = truckOnArrival(arrived = other, here = truck, nothingAssociated)

        assertEquals(TruckArrival.PairAgain(other), arrival)
    }

    @Test
    fun `a truck stored here whose association is gone stays if the file names none`() {
        val arrival = truckOnArrival(arrived = null, here = truck, nothingAssociated)

        assertEquals(TruckArrival.PairAgain(truck), arrival)
        assertTrue(arrival.needsPairing)
    }

    @Test
    fun `the file's truck is paired if Android here already watches for it`() {
        val arrival = truckOnArrival(arrived = other, here = truck, associatedWith(other))

        assertEquals(TruckArrival.Paired(other), arrival)
        // Stored without the association's number: the pairing check that follows finds it.
        assertEquals(TruckChange.Store(other), arrival.asChange())
    }

    @Test
    fun `on a phone that cannot watch for a device, its own truck stays, or a first is taken`() {
        assertEquals(TruckArrival.OwnKept(truck, other), truckOnArrival(other, truck, noSupport))
        assertEquals(TruckArrival.StoredUnwatched(other), truckOnArrival(other, null, noSupport))
    }

    @Test
    fun `no outcome ever stores an association, and none says paired without one on this phone`() {
        val arrivals = listOf(truck, other, null)
        val heres = listOf(truck, other, null)
        val pairings =
            listOf(nothingAssociated, noSupport, associatedWith(truck), associatedWith(other))

        for (arrived in arrivals) {
            for (here in heres) {
                for (pairing in pairings) {
                    val arrival = truckOnArrival(arrived, here, pairing)
                    val paired = (arrival as? TruckArrival.Paired)?.truck
                    val kept = (arrival as? TruckArrival.OwnKept)?.truck
                    if (paired != null) assertTrue(pairing.isAssociated(paired))
                    if (kept != null) assertTrue(pairing.isInOrder(kept))
                    val stays = arrival is TruckArrival.OwnKept || arrival == TruckArrival.NoTruck
                    assertEquals(stays, arrival.asChange() == TruckChange.Keep)
                }
            }
        }
    }

    @Test
    fun `the decision is said in the log without a name, only the device's address`() {
        val line = TruckArrival.PairAgain(truck).inWords()

        assertTrue(line, line.contains("AA:BB:CC:DD:EE:FF"))
        assertTrue(line, line.contains("has to be paired again"))
        assertFalse(line, line.contains("Work truck"))
        val kept = TruckArrival.OwnKept(truck, other).inWords()
        assertTrue(kept, kept.contains("11:22:33:44:55:66") && kept.contains("was not taken"))
    }
}

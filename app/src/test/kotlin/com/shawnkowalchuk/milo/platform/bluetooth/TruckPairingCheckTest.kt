package com.shawnkowalchuk.milo.platform.bluetooth

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** One digit away from the truck's address: what a slip in the adb command produces. */
private const val MISTYPED_ADDRESS = "AA:BB:CC:DD:EE:F0"

/**
 * The check that runs at every process start and every time MilO is opened: is the pairing
 * still armed? And the adoption of an association made over adb, which is how the truck is
 * paired until the pairing screen exists (docs/DEVICE_TEST_CHECKLIST.md).
 */
// See TruckPairingTest for why runCurrent() needs the opt-in.
@OptIn(ExperimentalCoroutinesApi::class)
class TruckPairingCheckTest {
    private val world = PairingWorld()
    private val settings = world.settings
    private val link = world.link

    private fun TestScope.pairing() = world.pairing(backgroundScope)

    // ---- Still armed? -----------------------------------------------------------------------------

    @Test
    fun `the check arms observation again and stays armed however often it runs`() = runTest {
        settings.setTruck(TRUCK_ADDRESS, "Work truck", 7)
        link.held += Association(TRUCK_ADDRESS, 7)
        val pairing = pairing()

        pairing.check("process start")
        pairing.check("app opened")
        runCurrent()

        assertEquals(PairingState.ARMED, pairing.status.value?.state)
        assertEquals(2, link.observed.size)
        assertEquals(
            "Truck pairing checked (app opened): ARMED: " +
                "association 7 for AA:BB:CC:DD:EE:FF is observed",
            world.logged().last(),
        )
        // Nothing about the truck changed, so nobody is asked to look at it.
        assertEquals(0, world.truckChanges)
    }

    @Test
    fun `an association removed in the phone's settings is noticed`() = runTest {
        settings.setTruck(TRUCK_ADDRESS, "Work truck", 7)
        val pairing = pairing()

        pairing.check("app opened")
        runCurrent()

        assertEquals(PairingState.ASSOCIATION_MISSING, pairing.status.value?.state)
        // The truck stays stored: the Bluetooth receiver still knows which device to listen for.
        assertEquals(TRUCK_ADDRESS, settings.current().truckAddress)
        assertEquals(emptyList<Association>(), link.observed)
    }

    @Test
    fun `with nothing paired and nothing associated there is no truck`() = runTest {
        val pairing = pairing()

        pairing.check("process start")
        runCurrent()

        assertEquals(PairingState.NO_TRUCK, pairing.status.value?.state)
        // Until the pairing screen exists, this line is where the truck's address is read.
        assertEquals(
            "Truck pairing checked (process start): NO_TRUCK: no truck is paired. Android lists " +
                "no association at all. Paired with the phone: Work truck (AA:BB:CC:DD:EE:FF)",
            world.logged().single(),
        )
    }

    @Test
    fun `the stored association id follows what Android lists`() = runTest {
        // Android 16 names the truck by this id alone, so a stale one would match nothing.
        settings.setTruck(TRUCK_ADDRESS, "Work truck", 7)
        link.held += Association(TRUCK_ADDRESS, 12)
        val pairing = pairing()

        pairing.check("process start")
        runCurrent()

        assertEquals(12, settings.current().truckAssociationId)
        assertEquals(PairingState.ARMED, pairing.status.value?.state)
    }

    @Test
    fun `Android refusing to observe is reported, not swallowed`() = runTest {
        settings.setTruck(TRUCK_ADDRESS, "Work truck", 7)
        link.held += Association(TRUCK_ADDRESS, 7)
        link.refuseWith = SecurityException("no permission to observe")
        val pairing = pairing()

        pairing.check("process start")
        runCurrent()

        assertEquals(PairingState.FAILED, pairing.status.value?.state)
        assertTrue(world.logged().single().contains("no permission to observe"))
    }

    @Test
    fun `without companion support and with no truck, the line says so and lists the devices`() =
        runTest {
            // Whether HyperOS reports the feature is not known. If it does not, this line is
            // all Shawn has to go on: it must say why, and still show the truck's address.
            link.supported = false
            val pairing = pairing()

            pairing.check("app opened")
            runCurrent()

            assertEquals(PairingState.NO_TRUCK, pairing.status.value?.state)
            assertEquals(
                "Truck pairing checked (app opened): NO_TRUCK: no truck is paired, and this " +
                    "phone has no companion device support, so no association can be adopted. " +
                    "Paired with the phone: Work truck (AA:BB:CC:DD:EE:FF)",
                world.logged().single(),
            )
        }

    // ---- Adopting an association made over adb ----------------------------------------------------

    @Test
    fun `a single association with no truck stored is adopted as the truck`() = runTest {
        // Made with adb before the pairing screen exists (docs/DEVICE_TEST_CHECKLIST.md).
        link.held += Association(TRUCK_ADDRESS, 4)
        val pairing = pairing()

        pairing.check("app opened")
        runCurrent()

        assertEquals(Truck(TRUCK_ADDRESS, "Work truck", 4), settings.current().truck())
        assertEquals(PairingState.ARMED, pairing.status.value?.state)
        assertEquals(1, world.truckChanges)
    }

    @Test
    fun `an adopted address is stored in capitals, whatever Android's spelling`() = runTest {
        // Android's Bluetooth classes accept an address in capitals only.
        link.held += Association("aa:bb:cc:dd:ee:ff", 4)
        val pairing = pairing()

        pairing.check("app opened")
        runCurrent()

        assertEquals(TRUCK_ADDRESS, settings.current().truckAddress)
        // Android is asked to watch for it in the spelling it listed.
        assertEquals(listOf(Association("aa:bb:cc:dd:ee:ff", 4)), link.observed)
    }

    @Test
    fun `two associations with no truck stored are not guessed between`() = runTest {
        link.held += Association(TRUCK_ADDRESS, 4)
        link.held += Association(OLD_TRUCK_ADDRESS, 5)
        val pairing = pairing()

        pairing.check("app opened")
        runCurrent()

        assertNull(settings.current().truck())
        assertEquals(PairingState.NO_TRUCK, pairing.status.value?.state)
    }

    @Test
    fun `an association for a device that is not paired with the phone is not adopted`() = runTest {
        // One digit mistyped in the adb command. Adopted, it would be stored as the truck,
        // and every broadcast for the real truck would be ignored as "another device".
        link.held += Association(MISTYPED_ADDRESS, 4)
        val pairing = pairing()

        pairing.check("app opened")
        runCurrent()

        assertNull(settings.current().truck())
        assertEquals(PairingState.NO_TRUCK, pairing.status.value?.state)
        assertEquals(0, world.truckChanges)
        assertEquals(emptyList<Association>(), link.observed)
        // The line says what was turned down, and still shows the address to use.
        val line = world.logged().single()
        assertTrue(line, line.contains("for $MISTYPED_ADDRESS, which was not adopted"))
        assertTrue(line, line.endsWith("Paired with the phone: Work truck ($TRUCK_ADDRESS)"))
    }

    @Test
    fun `nothing is adopted while the phone's paired devices cannot be read`() = runTest {
        // Without the Bluetooth permission MilO cannot tell whether the address is the truck's.
        world.paired = PairedDeviceList(emptyList(), PairedDevicesProblem.PERMISSION_MISSING)
        link.held += Association(TRUCK_ADDRESS, 4)
        val pairing = pairing()

        pairing.check("app opened")
        runCurrent()

        assertNull(settings.current().truck())
        assertTrue(world.logged().single().endsWith("Paired with the phone: PERMISSION_MISSING"))
    }

    @Test
    fun `a stored truck whose association is gone gives way to the one association left`() =
        runTest {
            // The way back from a wrong truck while there is no pairing screen: remove its
            // association over adb, make the right one, open MilO.
            settings.setTruck(OLD_TRUCK_ADDRESS, "Old truck", 3)
            link.held += Association(TRUCK_ADDRESS, 9)
            val pairing = pairing()

            pairing.check("app opened")
            runCurrent()

            assertEquals(Truck(TRUCK_ADDRESS, "Work truck", 9), settings.current().truck())
            assertEquals(PairingState.ARMED, pairing.status.value?.state)
            assertEquals(1, world.truckChanges)
            assertTrue(world.logged().first().contains("Adopted it in its place"))
        }

    @Test
    fun `a stored truck is not replaced by a device that is not paired with the phone`() = runTest {
        settings.setTruck(TRUCK_ADDRESS, "Work truck", 7)
        link.held += Association(MISTYPED_ADDRESS, 8)
        val pairing = pairing()

        pairing.check("app opened")
        runCurrent()

        assertEquals(Truck(TRUCK_ADDRESS, "Work truck", 7), settings.current().truck())
        assertEquals(PairingState.ASSOCIATION_MISSING, pairing.status.value?.state)
        assertEquals(0, world.truckChanges)
    }

    @Test
    fun `a stored truck that still has its association is never replaced`() = runTest {
        // A second association appears beside the truck's own. The truck stays the truck.
        settings.setTruck(OLD_TRUCK_ADDRESS, "Old truck", 3)
        link.held += Association(OLD_TRUCK_ADDRESS, 3)
        link.held += Association(TRUCK_ADDRESS, 9)
        val pairing = pairing()

        pairing.check("app opened")
        runCurrent()

        assertEquals(OLD_TRUCK_ADDRESS, settings.current().truckAddress)
        assertEquals(PairingState.ARMED, pairing.status.value?.state)
        assertEquals(0, world.truckChanges)
    }
}

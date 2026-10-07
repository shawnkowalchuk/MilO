package com.shawnkowalchuk.milo.platform.bluetooth

import android.app.Activity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pairing with the truck, against a stand-in for Android's companion device manager
 * ([PairingWorld]). What Android itself does (the consent dialog, binding the service when the
 * truck connects) can only be tested on the phone. The check that the pairing is still armed is
 * in [TruckPairingCheckTest].
 */
// runCurrent() is how a test lets the pairing's coroutines run. The API is marked experimental
// by the coroutines library; there is no stable equivalent.
@OptIn(ExperimentalCoroutinesApi::class)
class TruckPairingTest {
    private val world = PairingWorld()
    private val settings = world.settings
    private val link = world.link

    /** The pairing screen. Only handed through to Android, which the test replaces. */
    private val screen = Activity()

    private fun TestScope.pairing() = world.pairing(backgroundScope)

    @Test
    fun `the callback reports the association and the truck is stored`() = runTest {
        val pairing = pairing()

        pairing.associate(TRUCK_DEVICE, screen)
        assertEquals(PairingProgress.Asking, pairing.progress.value)
        assertEquals(TRUCK_ADDRESS, link.askedFor)

        link.create(TRUCK_ADDRESS, id = 7)
        runCurrent()

        val truck = Truck(TRUCK_ADDRESS, "Work truck", associationId = 7)
        assertEquals(PairingProgress.Paired(truck), pairing.progress.value)
        assertEquals(truck, settings.current().truck())
        // Armed in the same step, and the caller is told to look at the truck: Android does
        // not always report a truck that is already connected.
        assertEquals(PairingState.ARMED, pairing.status.value?.state)
        assertEquals(listOf(Association(TRUCK_ADDRESS, 7)), link.observed)
        assertEquals(1, world.truckChanges)
    }

    @Test
    fun `the dialog's result and the callback both report success, and the truck is stored once`() =
        runTest {
            val pairing = pairing()
            pairing.associate(TRUCK_DEVICE, screen)

            link.create(TRUCK_ADDRESS, id = 7)
            pairing.onConsentResult(Activity.RESULT_OK)
            runCurrent()

            assertEquals(1, world.logged().count { it.startsWith("Paired with the truck") })
            assertEquals(1, world.truckChanges)
        }

    @Test
    fun `the dialog's result alone stores the truck when the callback has not come`() = runTest {
        val pairing = pairing()
        pairing.associate(TRUCK_DEVICE, screen)

        // Android lists the association, and the dialog's result is the first to say so.
        link.held += Association(TRUCK_ADDRESS, id = 7)
        pairing.onConsentResult(Activity.RESULT_OK)
        runCurrent()

        assertEquals(Truck(TRUCK_ADDRESS, "Work truck", 7), settings.current().truck())
        assertEquals(PairingState.ARMED, pairing.status.value?.state)
    }

    @Test
    fun `consent refused - nothing is stored and the reason is logged`() = runTest {
        val pairing = pairing()
        pairing.associate(TRUCK_DEVICE, screen)

        pairing.onConsentResult(Activity.RESULT_CANCELED)
        runCurrent()

        assertTrue(pairing.progress.value is PairingProgress.Failed)
        assertNull(settings.current().truck())
        assertTrue(
            world.logged().single().startsWith("Pairing failed: the request was not approved"),
        )
    }

    @Test
    fun `Android refusing the request is a failed pairing, not a crash`() = runTest {
        val pairing = pairing()
        link.refuseWith = IllegalArgumentException("not a Bluetooth address")

        pairing.associate(TRUCK_DEVICE, screen)
        runCurrent()

        assertTrue(pairing.progress.value is PairingProgress.Failed)
        assertNull(settings.current().truck())
    }

    @Test
    fun `success reported with no association listed is a failure`() = runTest {
        // Storing the truck here would leave MilO believing in a pairing Android does not have.
        val pairing = pairing()
        pairing.associate(TRUCK_DEVICE, screen)

        pairing.onConsentResult(Activity.RESULT_OK)
        runCurrent()

        assertTrue(pairing.progress.value is PairingProgress.Failed)
        assertNull(settings.current().truck())
    }

    @Test
    fun `pairing a new truck removes the associations of the old one`() = runTest {
        link.held += Association(OLD_TRUCK_ADDRESS, id = 3)
        link.held += Association(TRUCK_ADDRESS, id = 5)
        val pairing = pairing()
        pairing.associate(TRUCK_DEVICE, screen)

        // Android makes a second association for a truck that was paired before.
        link.create(TRUCK_ADDRESS, id = 9)
        runCurrent()

        assertEquals(listOf(Association(TRUCK_ADDRESS, 9)), link.held)
        assertEquals(9, settings.current().truckAssociationId)
    }

    @Test
    fun `without companion device support the truck is stored for the Bluetooth receiver`() =
        runTest {
            link.supported = false
            val pairing = pairing()

            pairing.associate(TRUCK_DEVICE, screen)
            runCurrent()

            assertEquals(Truck(TRUCK_ADDRESS, "Work truck", null), settings.current().truck())
            assertEquals(PairingState.NOT_SUPPORTED, pairing.status.value?.state)
            assertNull(link.askedFor)
        }

    @Test
    fun `an address in small letters is stored in capitals and a blank name as no name`() =
        runTest {
            val pairing = pairing()
            pairing.associate(PairedDevice("aa:bb:cc:dd:ee:ff", name = " "), screen)

            link.create(TRUCK_ADDRESS, id = 7)
            runCurrent()

            assertEquals(Truck(TRUCK_ADDRESS, name = null, 7), settings.current().truck())
        }
}

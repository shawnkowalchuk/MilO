package com.shawnkowalchuk.milo.feature.pairing

import com.shawnkowalchuk.milo.platform.bluetooth.PairedDevice
import com.shawnkowalchuk.milo.platform.bluetooth.PairedDeviceList
import com.shawnkowalchuk.milo.platform.bluetooth.PairedDevicesProblem
import com.shawnkowalchuk.milo.platform.bluetooth.PairingProgress
import com.shawnkowalchuk.milo.platform.bluetooth.PairingState
import com.shawnkowalchuk.milo.platform.bluetooth.PairingStatus
import com.shawnkowalchuk.milo.platform.bluetooth.Truck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private val TRUCK_DEVICE = PairedDevice("AA:BB:CC:DD:EE:FF", "Work truck")
private val EARBUDS = PairedDevice("11:22:33:44:55:66", "Earbuds")
private val STORED_TRUCK = Truck("AA:BB:CC:DD:EE:FF", "Work truck", associationId = 7)

/** A second vehicle paired beside the truck: in these tests, the device the earbuds are. */
private val STORED_EARBUDS = Truck("11:22:33:44:55:66", "Earbuds", associationId = 8)

/**
 * What the pairing screen shows for each thing the phone and `TruckPairing` can report. Android's
 * consent dialog and the pairing itself can only be tested on the phone.
 */
class PairingUiStateTest {
    /** Bluetooth and location on, two devices paired with the phone, nothing tried yet. */
    private val ready =
        PairingInputs(
            paired = PairedDeviceList(listOf(EARBUDS, TRUCK_DEVICE), problem = null),
            locationOn = true,
            trucks = emptyList(),
            status = PairingStatus(PairingState.NO_TRUCK, "no truck is paired"),
            progress = PairingProgress.Idle,
            attemptedHere = false,
            dialogClosedWithoutAllowing = false,
        )

    private fun listProblem(problem: PairedDevicesProblem) =
        ready.copy(paired = PairedDeviceList(emptyList(), problem))

    // ---- What is in the way -----------------------------------------------------------------------

    @Test
    fun `with nothing in the way the phone's devices can be picked from`() {
        val state = pairingUiState(ready)

        assertNull(state.blocker)
        assertNull(state.truck)
        assertEquals(listOf(EARBUDS, TRUCK_DEVICE), state.devices.map { it.device })
        assertTrue(state.devices.none { it.isTruck })
        assertTrue(state.canPick)
    }

    @Test
    fun `a missing Nearby devices permission comes first`() {
        // Location is off as well, but without the permission there is nothing to pick from.
        val inputs =
            listProblem(PairedDevicesProblem.PERMISSION_MISSING).copy(locationOn = false)

        val state = pairingUiState(inputs)

        assertEquals(PairingBlocker.PERMISSION_MISSING, state.blocker)
        assertFalse(state.canPick)
    }

    @Test
    fun `Bluetooth switched off, and a phone without Bluetooth, are told apart`() {
        assertEquals(
            PairingBlocker.BLUETOOTH_OFF,
            pairingUiState(listProblem(PairedDevicesProblem.BLUETOOTH_OFF)).blocker,
        )
        assertEquals(
            PairingBlocker.NO_BLUETOOTH,
            pairingUiState(listProblem(PairedDevicesProblem.NO_BLUETOOTH)).blocker,
        )
    }

    @Test
    fun `with location switched off the devices are listed but cannot be picked`() {
        val state = pairingUiState(ready.copy(locationOn = false))

        assertEquals(PairingBlocker.LOCATION_OFF, state.blocker)
        assertEquals(2, state.devices.size)
        assertFalse(state.canPick)
    }

    @Test
    fun `an empty list that was read means nothing is paired with the phone`() {
        val inputs = ready.copy(paired = PairedDeviceList(emptyList(), problem = null))

        val state = pairingUiState(inputs)

        assertEquals(PairingBlocker.NOTHING_PAIRED, state.blocker)
        assertFalse(state.canPick)
    }

    // ---- The stored truck -------------------------------------------------------------------------

    @Test
    fun `the stored truck is marked in the list, whatever the spelling of its address`() {
        val smallLetters = STORED_TRUCK.copy(address = "aa:bb:cc:dd:ee:ff")

        val state = pairingUiState(ready.copy(trucks = listOf(smallLetters)))

        assertEquals(listOf(false, true), state.devices.map { it.isTruck })
    }

    @Test
    fun `the truck's line says whether Android is watching for it`() {
        val expected =
            mapOf(
                PairingState.ARMED to TruckWatch.WATCHED,
                PairingState.ASSOCIATION_MISSING to TruckWatch.ASSOCIATION_MISSING,
                PairingState.NOT_SUPPORTED to TruckWatch.NOT_SUPPORTED,
                PairingState.FAILED to TruckWatch.CHECK_FAILED,
                // A check that ran before the truck was stored: the next one is on its way.
                PairingState.NO_TRUCK to TruckWatch.CHECKING,
            )
        assertEquals(PairingState.entries.toSet(), expected.keys)

        for ((pairingState, watch) in expected) {
            val inputs =
                ready.copy(
                    trucks = listOf(STORED_TRUCK),
                    status = PairingStatus(pairingState, "detail"),
                )

            assertEquals(
                "$pairingState",
                TruckLine("Work truck", watch, STORED_TRUCK),
                pairingUiState(inputs).truck,
            )
        }
    }

    @Test
    fun `before the first check of the pairing the truck's line says it is checking`() {
        val inputs = ready.copy(trucks = listOf(STORED_TRUCK), status = null)

        assertEquals(TruckWatch.CHECKING, pairingUiState(inputs).truck?.watch)
    }

    // ---- The attempt ------------------------------------------------------------------------------

    @Test
    fun `the result of an attempt made on an earlier visit is not shown`() {
        // TruckPairing's progress belongs to the whole app and still holds the old failure.
        val inputs = ready.copy(progress = PairingProgress.Failed("an old failure"))

        val state = pairingUiState(inputs)

        assertEquals(PairingAttempt.None, state.attempt)
        assertTrue(state.canPick)
    }

    @Test
    fun `while Android is being asked no second device can be picked`() {
        val inputs = ready.copy(progress = PairingProgress.Asking, attemptedHere = true)

        val state = pairingUiState(inputs)

        assertEquals(PairingAttempt.Asking, state.attempt)
        assertFalse(state.canPick)
        // Android has not handed over its dialog yet.
        assertNull(state.consent)
    }

    @Test
    fun `a finished pairing names the truck, and another device can be picked to change it`() {
        val inputs =
            ready.copy(
                trucks = listOf(STORED_TRUCK),
                status = PairingStatus(PairingState.ARMED, "association 7 is observed"),
                progress = PairingProgress.Paired(STORED_TRUCK),
                attemptedHere = true,
            )

        val state = pairingUiState(inputs)

        assertEquals(PairingAttempt.Paired("Work truck"), state.attempt)
        assertEquals(TruckLine("Work truck", TruckWatch.WATCHED, STORED_TRUCK), state.truck)
        assertTrue(state.canPick)
    }

    // ---- Several vehicles (2026-10-08) ------------------------------------------------------------

    @Test
    fun `every paired vehicle has its line, and each is marked in the phone's list`() {
        val inputs =
            ready.copy(
                trucks = listOf(STORED_TRUCK, STORED_EARBUDS),
                status = PairingStatus(PairingState.ARMED, "both observed"),
            )

        val state = pairingUiState(inputs)

        assertEquals(listOf("Work truck", "Earbuds"), state.vehicles.map { it.name })
        assertEquals(listOf(true, true), state.devices.map { it.isTruck })
    }

    @Test
    fun `among several vehicles only those the check names have lost their association`() {
        val inputs =
            ready.copy(
                trucks = listOf(STORED_TRUCK, STORED_EARBUDS),
                status =
                    PairingStatus(
                        PairingState.ASSOCIATION_MISSING,
                        "detail",
                        missing = listOf(STORED_EARBUDS.address),
                    ),
            )

        val state = pairingUiState(inputs)

        assertEquals(
            listOf(TruckWatch.WATCHED, TruckWatch.ASSOCIATION_MISSING),
            state.vehicles.map { it.watch },
        )
    }

    @Test
    fun `closing Android's dialog without allowing is not shown as an error`() {
        val inputs =
            ready.copy(
                progress = PairingProgress.Failed("the request was not approved (result code 0)"),
                attemptedHere = true,
                dialogClosedWithoutAllowing = true,
            )

        val state = pairingUiState(inputs)

        assertEquals(PairingAttempt.Declined, state.attempt)
        // Nothing was changed, and Shawn can simply pick again.
        assertTrue(state.canPick)
    }

    @Test
    fun `only Shawn closing or refusing Android's dialog counts as declined`() {
        // Back or a tap outside the dialog, and "Don't allow".
        assertTrue(consentWasDeclined(0))
        assertTrue(consentWasDeclined(1))
        // Allowed.
        assertFalse(consentWasDeclined(-1))
        // Android gave up looking for the device, it failed inside, and whatever a later
        // version may add.
        assertFalse(consentWasDeclined(2))
        assertFalse(consentWasDeclined(3))
        assertFalse(consentWasDeclined(4))
    }

    @Test
    fun `a dialog that failed by itself is shown as a failure, with the reason`() {
        // Result code 2: Android gave up looking for the device. Shown as "closed without
        // allowing", Shawn would pick again for ever and never learn why.
        val inputs =
            ready.copy(
                progress = PairingProgress.Failed("discovery_timeout"),
                attemptedHere = true,
                dialogClosedWithoutAllowing = consentWasDeclined(2),
            )

        val state = pairingUiState(inputs)

        assertEquals(PairingAttempt.Failed("discovery_timeout"), state.attempt)
        assertTrue(state.canPick)
    }

    @Test
    fun `any other failure is shown with exactly what went wrong`() {
        val why = "Android reported success but lists no association for AA:BB:CC:DD:EE:FF"
        val inputs = ready.copy(progress = PairingProgress.Failed(why), attemptedHere = true)

        val state = pairingUiState(inputs)

        assertEquals(PairingAttempt.Failed(why), state.attempt)
        assertTrue(state.canPick)
    }
}

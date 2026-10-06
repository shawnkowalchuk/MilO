package com.shawnkowalchuk.milo.platform.system

import org.junit.Assert.assertEquals
import org.junit.Test

/** The preflight's verdict. Reading the facts from the phone can only be tested on the phone. */
class TripPreflightTest {
    private val allGood =
        PreflightFacts(
            fineLocationGranted = true,
            backgroundLocationGranted = true,
            locationSwitchedOn = true,
            backgroundRestricted = false,
            bluetoothGranted = true,
        )

    @Test
    fun `with everything in place there is nothing in the way`() {
        assertEquals(emptyList<PreflightProblem>(), preflightProblems(allGood))
    }

    @Test
    fun `location allowed only while the app is in use is a problem`() {
        // The case Android itself reports as "granted": the fine-location check passes, and a
        // start from the background then throws.
        val whileInUse = allGood.copy(backgroundLocationGranted = false)

        assertEquals(
            listOf(PreflightProblem.BACKGROUND_LOCATION_MISSING),
            preflightProblems(whileInUse),
        )
    }

    @Test
    fun `without the Bluetooth permission no trip is started, not even by hand`() {
        // MilO would hear neither the truck connect nor the truck disconnect.
        val noBluetooth = allGood.copy(bluetoothGranted = false)

        assertEquals(
            listOf(PreflightProblem.BLUETOOTH_PERMISSION_MISSING),
            preflightProblems(noBluetooth),
        )
    }

    @Test
    fun `every problem is reported, not only the first`() {
        val nothingSet =
            PreflightFacts(
                fineLocationGranted = false,
                backgroundLocationGranted = false,
                locationSwitchedOn = false,
                backgroundRestricted = true,
                bluetoothGranted = false,
            )

        assertEquals(PreflightProblem.entries, preflightProblems(nothingSet))
    }
}

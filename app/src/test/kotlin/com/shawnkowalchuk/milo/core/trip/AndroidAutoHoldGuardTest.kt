package com.shawnkowalchuk.milo.core.trip

import org.junit.Assert.assertEquals
import org.junit.Test

/** The guard against a stuck Android Auto value holding a trip open for ever. */
class AndroidAutoHoldGuardTest {
    private val guard = AndroidAutoHoldGuard()

    @Test
    fun `what Android Auto reports is believed while the truck is connected, however long`() {
        val verdicts =
            listOf(T0, T0 + 20 * HOUR, T0 + 40 * HOUR).map { atMs ->
                guard.believed(reported = true, truckConnected = true, atMs)
            }

        assertEquals(listOf(true, true, true), verdicts)
    }

    @Test
    fun `not connected is always passed on`() {
        assertEquals(false, guard.believed(reported = false, truckConnected = false, T0))
        assertEquals(false, guard.believed(reported = false, truckConnected = true, T0))
    }

    @Test
    fun `Android Auto alone holds a trip for up to 12 hours`() {
        // Bluetooth dropped at T0 and the cable stayed in: Shawn's long day on Android Auto.
        guard.believed(reported = true, truckConnected = false, T0)

        val justBefore = T0 + ANDROID_AUTO_ALONE_LIMIT_MS - 1
        assertEquals(true, guard.believed(reported = true, truckConnected = false, justBefore))
    }

    @Test
    fun `after 12 hours alone the value is taken for stuck`() {
        guard.believed(reported = true, truckConnected = false, T0)

        val atTheLimit = T0 + ANDROID_AUTO_ALONE_LIMIT_MS
        assertEquals(false, guard.believed(reported = true, truckConnected = false, atTheLimit))
    }

    @Test
    fun `the truck coming back starts the 12 hours again`() {
        guard.believed(reported = true, truckConnected = false, T0)
        guard.believed(reported = true, truckConnected = true, T0 + 11 * HOUR)
        guard.believed(reported = true, truckConnected = false, T0 + 11 * HOUR + MINUTE)

        assertEquals(true, guard.believed(reported = true, truckConnected = false, T0 + 20 * HOUR))
    }

    @Test
    fun `a stuck value stays unbelieved even after the truck's next visit`() {
        guard.believed(reported = true, truckConnected = false, T0)
        guard.believed(reported = true, truckConnected = false, T0 + 13 * HOUR)

        // Next morning the truck connects and drops again. Android Auto still says connected.
        val withTruck = guard.believed(reported = true, truckConnected = true, T0 + 20 * HOUR)
        val alone = guard.believed(reported = true, truckConnected = false, T0 + 21 * HOUR)

        assertEquals(listOf(false, false), listOf(withTruck, alone))
    }

    @Test
    fun `once Android Auto has been seen to disconnect it is believed again`() {
        guard.believed(reported = true, truckConnected = false, T0)
        guard.believed(reported = true, truckConnected = false, T0 + 13 * HOUR)

        guard.believed(reported = false, truckConnected = false, T0 + 14 * HOUR)

        assertEquals(true, guard.believed(reported = true, truckConnected = false, T0 + 15 * HOUR))
    }
}

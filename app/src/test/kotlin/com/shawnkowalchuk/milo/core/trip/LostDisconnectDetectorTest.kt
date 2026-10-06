package com.shawnkowalchuk.milo.core.trip

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * ADR-002, amendment 6: the once-a-minute reading of the truck's connection. Two "not connected"
 * readings in a row are a disconnect; one alone is not.
 */
class LostDisconnectDetectorTest {
    private val detector = LostDisconnectDetector()

    @Test
    fun `one reading of not connected is not enough`() {
        assertEquals(false, detector.onReading(connected = false))
    }

    @Test
    fun `two readings of not connected in a row are a disconnect`() {
        detector.onReading(connected = false)

        assertEquals(true, detector.onReading(connected = false))
    }

    @Test
    fun `a connected reading in between starts the count again`() {
        // The profile state lagged for one reading and then caught up.
        val verdicts =
            listOf(false, true, false).map { connected -> detector.onReading(connected) }

        assertEquals(listOf(false, false, false), verdicts)
    }

    @Test
    fun `other evidence of the truck starts the count again`() {
        detector.onReading(connected = false)

        // A connect event arrived between the two readings.
        detector.reset()

        assertEquals(false, detector.onReading(connected = false))
    }

    @Test
    fun `after a disconnect is reported the count starts again`() {
        detector.onReading(connected = false)
        detector.onReading(connected = false)

        assertEquals(false, detector.onReading(connected = false))
        assertEquals(true, detector.onReading(connected = false))
    }

    @Test
    fun `connected readings never report a disconnect`() {
        val verdicts = List(5) { detector.onReading(connected = true) }

        assertEquals(List(5) { false }, verdicts)
    }
}

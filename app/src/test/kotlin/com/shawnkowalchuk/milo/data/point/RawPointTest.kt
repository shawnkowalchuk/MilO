package com.shawnkowalchuk.milo.data.point

import com.shawnkowalchuk.milo.core.trip.DistanceCalculator
import org.junit.Assert.assertEquals
import org.junit.Test

class RawPointTest {
    private val stored =
        RawPoint(
            id = 7,
            tripId = 3,
            wallClockMs = 1_791_028_800_000,
            elapsedRealtimeMs = 5_000,
            latitude = 53.5,
            longitude = -113.5,
            accuracyMetres = 4.5f,
            speedMetresPerSecond = 12f,
        )

    @Test
    fun `a stored point becomes the same fix for the trip rules`() {
        val fix = stored.toTrackPoint()

        assertEquals(stored.wallClockMs, fix.wallClockMs)
        assertEquals(stored.elapsedRealtimeMs, fix.elapsedRealtimeMs)
        assertEquals(stored.latitude, fix.latitude, 0.0)
        assertEquals(stored.longitude, fix.longitude, 0.0)
        assertEquals(4.5f, fix.accuracyMetres, 0f)
    }

    @Test
    fun `a stored point with no accuracy is one the distance calculation rejects`() {
        val fix = stored.copy(accuracyMetres = null).toTrackPoint()

        val result = DistanceCalculator.measure(listOf(fix))

        assertEquals(1, result.rejectedForAccuracy)
        assertEquals(0, result.acceptedCount)
    }
}

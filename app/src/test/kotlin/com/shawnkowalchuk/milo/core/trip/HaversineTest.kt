package com.shawnkowalchuk.milo.core.trip

import org.junit.Assert.assertEquals
import org.junit.Test

/** One degree along a great circle of the mean-radius sphere: 2 x pi x 6 371 008.8 m / 360. */
private const val METRES_PER_DEGREE = 111_195.08

class HaversineTest {
    @Test
    fun `the same point is zero metres away`() {
        assertEquals(0.0, haversineMetres(53.5, -113.5, 53.5, -113.5), 0.0)
    }

    @Test
    fun `one degree of latitude is 111 point 195 km`() {
        assertEquals(METRES_PER_DEGREE, haversineMetres(10.0, 20.0, 11.0, 20.0), 0.01)
    }

    @Test
    fun `one degree of longitude on the equator is the same`() {
        assertEquals(METRES_PER_DEGREE, haversineMetres(0.0, 20.0, 0.0, 21.0), 0.01)
    }

    @Test
    fun `one degree of longitude at 60 degrees north is half that`() {
        // cos(60) = 0.5, less a few metres because the great circle cuts the corner.
        assertEquals(METRES_PER_DEGREE / 2, haversineMetres(60.0, 20.0, 60.0, 21.0), 5.0)
    }

    @Test
    fun `the distance is the same in both directions`() {
        val there = haversineMetres(53.5461, -113.4938, 51.0447, -114.0719)
        val back = haversineMetres(51.0447, -114.0719, 53.5461, -113.4938)
        assertEquals(there, back, 1e-6)
    }

    @Test
    fun `matches a known city pair`() {
        // Edmonton to Calgary city centres: 281 km on the sphere, give or take rounding.
        val metres = haversineMetres(53.5461, -113.4938, 51.0447, -114.0719)
        assertEquals(281_000.0, metres, 1_000.0)
    }

    @Test
    fun `crossing the date line takes the short way round`() {
        assertEquals(2 * METRES_PER_DEGREE, haversineMetres(0.0, 179.0, 0.0, -179.0), 0.01)
    }

    @Test
    fun `opposite sides of the Earth are half the circumference apart, not NaN`() {
        assertEquals(180 * METRES_PER_DEGREE, haversineMetres(0.0, 0.0, 0.0, 180.0), 1.0)
        assertEquals(180 * METRES_PER_DEGREE, haversineMetres(90.0, 0.0, -90.0, 0.0), 1.0)
    }
}

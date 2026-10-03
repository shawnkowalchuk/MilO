package com.shawnkowalchuk.milo.core.trip

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** The mean radius of the Earth (IUGG), the usual choice for great-circle distances. */
const val EARTH_RADIUS_METRES = 6_371_008.8

/**
 * The great-circle distance in metres between two positions given in degrees.
 *
 * This is the haversine formula, which treats the Earth as a sphere. Android's own
 * `Location.distanceTo` uses the more exact WGS84 ellipsoid, but it is a framework call and cannot
 * run in a plain JVM unit test. The two differ by at most about 0.5 %, and over the few dozen
 * metres between two GPS fixes that is far below the error of the fixes themselves
 * (docs/research/2026-10-03-location-and-car.md, finding A14).
 */
fun haversineMetres(
    fromLatitude: Double,
    fromLongitude: Double,
    toLatitude: Double,
    toLongitude: Double,
): Double {
    val fromLatitudeRadians = Math.toRadians(fromLatitude)
    val toLatitudeRadians = Math.toRadians(toLatitude)
    val halfLatitudeChange = (toLatitudeRadians - fromLatitudeRadians) / 2
    val halfLongitudeChange = Math.toRadians(toLongitude - fromLongitude) / 2

    val haversine =
        sin(halfLatitudeChange) * sin(halfLatitudeChange) +
            cos(fromLatitudeRadians) * cos(toLatitudeRadians) *
            sin(halfLongitudeChange) * sin(halfLongitudeChange)

    // Rounding can push the value a hair above 1 for two points on opposite sides of the Earth,
    // and asin of anything above 1 is not a number.
    return 2 * EARTH_RADIUS_METRES * asin(min(1.0, sqrt(haversine)))
}

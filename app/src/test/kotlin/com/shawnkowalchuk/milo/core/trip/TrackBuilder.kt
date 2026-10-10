package com.shawnkowalchuk.milo.core.trip

import kotlin.math.cos

/** Where the test tracks start. Any mid-latitude place would do. */
private const val ORIGIN_LATITUDE = 53.5
private const val ORIGIN_LONGITUDE = -113.5

/** One degree of latitude in metres on the sphere [haversineMetres] uses. */
private const val METRES_PER_DEGREE = 111_195.08

/** The wall clock at elapsed time zero in the test tracks: 2026-10-03 12:00 UTC. */
const val TRACK_START_WALL_CLOCK_MS = 1_791_028_800_000L

/**
 * The interval the test tracks are built at: what MilO asked the phone for during a trip until
 * 2026-10-09, and still asks for beside a parked truck after a report of getting into a vehicle.
 * A trip is recorded every 2 seconds now. The trip rules count time and metres, not fixes, so
 * the tracks stand as they are.
 */
const val FIX_INTERVAL_SECONDS = 5

/** A typical open-sky accuracy. */
const val GOOD_ACCURACY_METRES = 5f

/**
 * A GPS fix a given number of metres north and east of the test origin, a given number of seconds
 * into the track. Tests describe a drive in metres and seconds and leave the degrees to this.
 * The phone's own speed reading is left out unless a test gives one.
 */
fun fixAt(
    northMetres: Double,
    eastMetres: Double = 0.0,
    second: Int,
    accuracyMetres: Float = GOOD_ACCURACY_METRES,
    speedMetresPerSecond: Float? = null,
): TrackPoint = TrackPoint(
    wallClockMs = TRACK_START_WALL_CLOCK_MS + second * 1000L,
    elapsedRealtimeMs = second * 1000L,
    latitude = ORIGIN_LATITUDE + northMetres / METRES_PER_DEGREE,
    longitude =
        ORIGIN_LONGITUDE +
            eastMetres / (METRES_PER_DEGREE * cos(Math.toRadians(ORIGIN_LATITUDE))),
    accuracyMetres = accuracyMetres,
    speedMetresPerSecond = speedMetresPerSecond,
)

/**
 * A drive due north at a steady speed with a fix every [FIX_INTERVAL_SECONDS]: [fixCount] fixes,
 * the first at [fromNorthMetres] and [fromSecond].
 */
fun driveNorth(
    fixCount: Int,
    metresPerFix: Double,
    fromNorthMetres: Double = 0.0,
    fromSecond: Int = 0,
): List<TrackPoint> = List(fixCount) { index ->
    fixAt(
        northMetres = fromNorthMetres + index * metresPerFix,
        second = fromSecond + index * FIX_INTERVAL_SECONDS,
    )
}

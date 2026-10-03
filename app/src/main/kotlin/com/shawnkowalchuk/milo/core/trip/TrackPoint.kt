package com.shawnkowalchuk.milo.core.trip

/**
 * One GPS fix as the trip rules see it: the stored raw point without its database ids.
 *
 * It carries two clocks on purpose:
 * - [elapsedRealtimeMs] is the time since the phone booted. It only ever moves forward, so it is
 *   the clock used to work out the speed between two fixes.
 * - [wallClockMs] is the time of day (milliseconds since 1970). It can jump when the phone
 *   corrects its clock, so it is never used for speed. It is what a trip's start and end times
 *   are written in, and what a fix is compared with when a trip is closed.
 *
 * @param accuracyMetres the radius the phone is 68 % sure the true position lies within. A fix
 * that reported no accuracy is given [Float.POSITIVE_INFINITY], so it is rejected the same way
 * as any other fix that cannot be trusted.
 */
data class TrackPoint(
    val wallClockMs: Long,
    val elapsedRealtimeMs: Long,
    val latitude: Double,
    val longitude: Double,
    val accuracyMetres: Float,
)

/** The straight-line distance in metres from this fix to [other]. */
fun TrackPoint.metresTo(other: TrackPoint): Double =
    haversineMetres(latitude, longitude, other.latitude, other.longitude)

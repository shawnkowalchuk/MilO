package com.shawnkowalchuk.milo.data.report

import com.shawnkowalchuk.milo.core.schedule.TripCategory
import com.shawnkowalchuk.milo.core.trip.TripStartCause
import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.data.trip.Trip
import java.time.LocalDateTime
import java.time.ZoneId

// What the two tests of the report's selection share: a time zone like the phone's, and stored
// trips as they are after they have closed.

/**
 * Edmonton is the kind of zone the phone is in: seven hours behind UTC in winter, six in
 * summer, with the clocks changing on 8 March and 1 November 2026.
 */
internal val EDMONTON: ZoneId = ZoneId.of("America/Edmonton")

/** A local wall-clock time in [zone], as stored time. */
internal fun at(text: String, zone: ZoneId = EDMONTON): Long =
    LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()

/**
 * A stored trip that started at [start], Edmonton time, with a position at both ends like
 * every trip MilO records.
 */
internal fun storedTrip(
    start: String,
    status: TripStatus = TripStatus.FINISHED,
    category: TripCategory? = TripCategory.BUSINESS,
    startAddress: String? = "12 Shop Rd, Edmonton",
    endAddress: String? = "48 Main St, Leduc",
    metres: Double = 12_340.0,
    id: Long = 0,
): Trip {
    val startedAtMs = at(start)
    return Trip(
        id = id,
        startedAtMs = startedAtMs,
        endedAtMs = (startedAtMs + 20 * 60_000).takeUnless { status == TripStatus.OPEN },
        status = status,
        startedBy = TripStartCause.TRUCK,
        truckSeen = true,
        distanceMetres = metres,
        startLatitude = 53.5444,
        startLongitude = -113.4909,
        endLatitude = 53.2594,
        endLongitude = -113.5491,
        startAddress = startAddress,
        endAddress = endAddress,
        category = category,
    )
}

/** When the listed trips started, in the order the report lists them. */
internal fun starts(selection: ReportSelection): List<Long> = selection.trips.map { it.startedAtMs }

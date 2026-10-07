package com.shawnkowalchuk.milo.core.trip

import kotlin.math.max

/**
 * How long a wait beside a parked, connected truck reads GPS before the phone's motion sensor
 * takes over (Shawn's choice of 2026-10-07: "GPS 1 hour, then sensor"). His truck keeps its
 * Bluetooth connection for as long as it stands in range, so a wait can last a night or a
 * weekend, and a fix every 30 seconds for all of it is the battery's largest cost.
 *
 * The hour covers the short stops of a working day, where the next drive is caught within about
 * half a minute: the stop of 2026-10-07 that lost its next trip lasted 15 minutes. After it,
 * GPS is off, and the phone's report of entering a vehicle turns it on again
 * ([GPS_AFTER_VEHICLE_REPORT_MS]). That report comes about a minute into the drive; the trip it
 * leads to still starts at the parked place, so the stretch driven before it is counted, as one
 * straight line.
 */
const val PARKED_GPS_MS = 60L * 60L * 1000L

/**
 * After the phone reports entering a vehicle, a wait reads GPS again for this long, every 5
 * seconds ([ParkedGps.fastUntilMs]). The first fixes decide: a truck that is driven off is seen
 * moving at 15 km/h or more ([DRIVING_OFF_KMH]), and the trip starts. Ten minutes leave room
 * for a slow first fix, for the engine running before the truck moves (on 2026-10-07 the report
 * came two minutes before a hop at 12:18) and for the traffic lights of a yard's exit; a report
 * that was wrong, or a drive in another vehicle, costs no more than that.
 */
const val GPS_AFTER_VEHICLE_REPORT_MS = 10L * 60L * 1000L

/**
 * Until when a wait beside the parked truck reads GPS: the first [PARKED_GPS_MS] of the wait,
 * and [GPS_AFTER_VEHICLE_REPORT_MS] after the phone last reported entering a vehicle, whichever
 * is later. Past it, GPS is off until a new report.
 *
 * A null answer means GPS for as long as the wait lasts, as before 2026-10-07. That is the
 * answer whenever the phone does not report driving to MilO, because nothing else would turn
 * GPS on again: the Physical activity permission is missing, or the request for reports is not
 * in place. The driving alert owns that request (`DrivingAlert`), so switching the alert off in
 * Settings switches the motion sensor off here too.
 *
 * @param waitingSinceMs when the wait began ([Parked.sinceMs]), stored with it, so a restart of
 * MilO's process does not give a wait a second hour.
 * @param vehicleEnteredAtMs when the phone last reported entering a vehicle, or null if it has
 * not since MilO's process started. A report from before the wait counts too: the hour of the
 * wait outlasts it.
 * @param sensorWatching whether the phone reports entering a vehicle to MilO at all.
 */
fun parkedGpsUntilMs(
    waitingSinceMs: Long,
    vehicleEnteredAtMs: Long?,
    sensorWatching: Boolean,
): Long? {
    if (!sensorWatching) return null
    val firstHour = waitingSinceMs + PARKED_GPS_MS
    val afterReport = vehicleEnteredAtMs?.plus(GPS_AFTER_VEHICLE_REPORT_MS) ?: return firstHour
    return max(firstHour, afterReport)
}

/**
 * How a wait beside the parked truck reads GPS.
 *
 * @param untilMs when GPS goes off ([parkedGpsUntilMs]), or null to keep it on.
 * @param fastUntilMs until when it is read every 5 seconds, the rate of a trip, instead of
 * every 30: [GPS_AFTER_VEHICLE_REPORT_MS] after the phone last reported getting into a vehicle,
 * or null without such a report (Shawn's choice of 2026-10-07: "Yes, every 5 s"). The trip then
 * starts within seconds of pulling out, with its real start and the road's distance. At one
 * fix every 30 seconds, the hop of 2026-10-07 at 12:18 fell between two fixes and was recorded
 * as a straight line, dated when the truck had already arrived; and under the speed rule
 * (amendment 31) such a hop, never seen moving fast, starts no trip of its own at all.
 */
data class ParkedGps(val untilMs: Long? = null, val fastUntilMs: Long? = null)

/**
 * How a wait that began at [waitingSinceMs] reads GPS now: see [parkedGpsUntilMs] and
 * [ParkedGps.fastUntilMs]. A report from before the wait counts too: one that comes as the
 * truck pulls out, in the very moment the parked rule closes its trip, makes the wait's first
 * minutes fast.
 */
fun parkedGps(
    waitingSinceMs: Long,
    vehicleEnteredAtMs: Long?,
    sensorWatching: Boolean,
): ParkedGps = ParkedGps(
    untilMs = parkedGpsUntilMs(waitingSinceMs, vehicleEnteredAtMs, sensorWatching),
    fastUntilMs = vehicleEnteredAtMs?.plus(GPS_AFTER_VEHICLE_REPORT_MS),
)

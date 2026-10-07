package com.shawnkowalchuk.milo.platform.trip

import com.shawnkowalchuk.milo.core.trip.ParkedWatch
import com.shawnkowalchuk.milo.core.trip.TrackPoint
import com.shawnkowalchuk.milo.core.trip.TripProgress
import com.shawnkowalchuk.milo.data.point.RawPoint
import com.shawnkowalchuk.milo.data.point.RawPointRepository
import com.shawnkowalchuk.milo.data.settings.ParkedPlace
import com.shawnkowalchuk.milo.data.settings.ParkedTruck
import com.shawnkowalchuk.milo.data.settings.SettingsStore
import com.shawnkowalchuk.milo.data.settings.setParkedTruck

/**
 * The storage side of the wait beside a parked truck (ADR-002, amendment 28), as [TripLedger] is
 * the storage side of a trip: it keeps the wait in the settings file, so that a restart finds
 * it, holds the watch on the truck's position in memory, and hands the next trip its first
 * points. Whether the truck has moved is decided by `ParkedWatch` in `core/trip/`; what follows
 * from that, by the trip rules.
 *
 * While MilO waits no trip is open, so the fixes that arrive are stored nowhere. Only the ones
 * that show the truck driving off are kept, and they are stored when the trip they start is
 * opened. Not thread-safe: only the controller's worker calls it, through the ledger.
 */
internal class TripParking(
    private val settings: SettingsStore,
    private val points: RawPointRepository,
) {
    private var watch: ParkedWatch? = null

    /**
     * The fixes the watch counts as showing the movement (`ParkedWatch.sinceStirred`), as they
     * arrived, to be stored as they were: a missing accuracy as missing, not as the "infinitely
     * bad" the watch makes of it.
     */
    private var stirred: List<RawPoint> = emptyList()

    /** Where the truck stands after the trip that was closed in this step. */
    private var justEndedAt: TrackPoint? = null

    /**
     * A trip was closed, and the truck stands at [place] (`ParkedWatch.placeAfter`), or nobody
     * knows where: the trip had no usable fix at all. A wait may begin there.
     */
    fun tripEndedAt(place: TrackPoint?) {
        justEndedAt = place
    }

    /** The wait begins: it is stored with the place, and the watch starts from that place. */
    suspend fun begin(sinceMs: Long) {
        val place = justEndedAt
        val stored =
            place?.let { ParkedPlace(it.wallClockMs, it.latitude, it.longitude, it.accuracyMetres) }
        settings.setParkedTruck(ParkedTruck(sinceMs, stored))
        watchFrom(place)
    }

    /**
     * The wait was found in storage after a restart. The place's time since boot is not stored
     * and may be from before a reboot, so it is given none: the first fix is then never taken
     * for an impossible jump from it, which is right after any length of standing.
     *
     * TODO(debt): if the truck was driven while MilO's process was dead, the first fixes after
     *  this are far from the stored place and start a trip dated now, which holds the straight
     *  line between the two places. Whether to keep, mark or drop such a trip is the owner's
     *  to decide. See docs/FINDINGS_LOG.md, 2026-10-06 (evening).
     */
    fun resume(stored: ParkedTruck?) {
        val place =
            stored?.place?.let {
                TrackPoint(it.atMs, NO_ELAPSED_TIME, it.latitude, it.longitude, it.accuracyMetres)
            }
        watchFrom(place)
    }

    /**
     * The wait is over.
     *
     * @param tripFollows true if the truck moved and [startTrip] is called next, which needs the
     * fixes the watch has kept.
     */
    suspend fun end(tripFollows: Boolean) {
        settings.setParkedTruck(null)
        if (!tripFollows) forget()
    }

    /**
     * One fix while waiting.
     *
     * @return when the truck was first seen to have moved, once that is certain; otherwise null.
     */
    fun onFix(fix: RawPoint): Long? {
        val watched = (watch ?: ParkedWatch()).plus(fix.toTrackPoint())
        watch = watched
        // The watch alone says which fixes show the movement: always the newest ones, and
        // none once it has gone back to the parked place. Only those are kept.
        stirred = (stirred + fix).takeLast(watched.sinceStirred.size)
        return watched.movedAtMs
    }

    /**
     * Gives the trip that the movement started its first points, stored under [tripId]. Which
     * points those are is the watch's decision (`ParkedWatch.tripStart`): the parked place, then
     * the fixes that showed the truck driving off.
     *
     * @return the running picture of that trip, built from those points.
     */
    suspend fun startTrip(tripId: Long): TripProgress {
        val first = watch?.tripStart.orEmpty()
        val asRecorded = stirred.associateBy { it.toTrackPoint() }
        // A fix is stored as it arrived. The parked place is no fix, and is stored as one
        // without a speed.
        first.forEach { points.add((asRecorded[it] ?: it.asFix()).copy(tripId = tripId)) }
        forget()
        return TripProgress.of(first)
    }

    /** Forgets the watch. Storage is not touched. */
    fun forget() {
        watch = null
        stirred = emptyList()
        justEndedAt = null
    }

    private fun watchFrom(place: TrackPoint?) {
        watch = ParkedWatch.at(place)
        stirred = emptyList()
        justEndedAt = null
    }

    /** The parked place as a stored point of the next trip. It has no speed: it is not a fix. */
    private fun TrackPoint.asFix(): RawPoint = RawPoint(
        tripId = 0,
        wallClockMs = wallClockMs,
        elapsedRealtimeMs = elapsedRealtimeMs,
        latitude = latitude,
        longitude = longitude,
        accuracyMetres = accuracyMetres,
        speedMetresPerSecond = null,
    )

    private companion object {
        const val NO_ELAPSED_TIME = 0L
    }
}

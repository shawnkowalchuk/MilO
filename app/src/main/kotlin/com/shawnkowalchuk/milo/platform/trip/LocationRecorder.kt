package com.shawnkowalchuk.milo.platform.trip

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.google.android.gms.location.LocationAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.shawnkowalchuk.milo.data.point.RawPoint

/** A fix every 5 seconds, as the brief asks. */
private const val FIX_INTERVAL_MS = 5_000L

/** A loss of location is written to the event log once it has lasted this long: two fixes. */
private const val LOSS_WORTH_LOGGING_MS = 10_000L

private const val NANOS_PER_MILLI = 1_000_000L
private const val MILLIS_PER_SECOND = 1000.0

/**
 * Asks the fused location provider for GPS fixes while a trip is recorded, and hands each one
 * on as a [RawPoint]. The trip service owns it: it starts it when recording begins and stops it
 * when the trip ends.
 *
 * The request (docs/research/2026-10-03-location-and-car.md, findings A1 to A5):
 * - **High accuracy, every 5 seconds, no minimum distance.** The provider combines interval and
 *   distance as "and", so asking for 10 m as well would deliver nothing while the truck stands
 *   still. The 10 m rule lives in the distance calculation, and every fix is stored.
 * - **Never faster than 5 seconds.** Without this, another app asking for faster fixes (Maps
 *   navigating) would double MilO's rate.
 * - **No batching**, so fixes arrive one at a time and in order.
 * - **No cached position.** The first fix must be a new one, not where the phone was earlier:
 *   a stale first fix would become the trip's start position.
 *
 * The callback, and both functions here, run on the main thread. All they do is hand the fix on.
 *
 * @param clock wall-clock milliseconds.
 * @param onFix called for every fix, used or not. Its trip id is 0: the controller fills it in.
 * @param onNote a line for the event log: the time to the first fix, and location being lost
 * for more than a moment and coming back.
 */
class LocationRecorder(
    private val context: Context,
    private val clock: () -> Long,
    private val onFix: (RawPoint) -> Unit,
    private val onNote: (String) -> Unit,
) {
    private val client = LocationServices.getFusedLocationProviderClient(context)

    /** Elapsed-realtime clock at the moment fixes were asked for, or null while stopped. */
    private var requestedAtElapsedMs: Long? = null
    private var firstFixSeen = false

    private val callback =
        object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.forEach(::deliver)
            }

            override fun onLocationAvailability(availability: LocationAvailability) {
                if (availability.isLocationAvailable) locationCameBack() else locationWentAway()
            }
        }

    /** Writes "location lost" to the event log once the loss has lasted [LOSS_WORTH_LOGGING_MS]. */
    private val reportLoss =
        Runnable {
            lossReported = true
            onNote(
                "Location has not been available for ${LOSS_WORTH_LOGGING_MS / MILLIS_PER_SECOND} s",
            )
        }
    private val mainThread = Handler(Looper.getMainLooper())
    private var lossReported = false

    /** Starts the fixes. Calling it again while they are running does nothing. */
    fun start() {
        if (requestedAtElapsedMs != null) return
        // The preflight checked this before the service was started. It is checked again here
        // because the permission can be taken away at any time, and the request would throw.
        val granted =
            context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        if (!granted) {
            onNote("Location fixes were not requested: the location permission is gone")
            return
        }
        val request =
            LocationRequest
                .Builder(Priority.PRIORITY_HIGH_ACCURACY, FIX_INTERVAL_MS)
                .setMinUpdateIntervalMillis(FIX_INTERVAL_MS)
                .setMinUpdateDistanceMeters(0f)
                .setMaxUpdateDelayMillis(0)
                .setMaxUpdateAgeMillis(0)
                .setWaitForAccurateLocation(true)
                .build()
        requestedAtElapsedMs = SystemClock.elapsedRealtime()
        firstFixSeen = false
        client
            .requestLocationUpdates(request, context.mainExecutor, callback)
            .addOnFailureListener { failure ->
                requestedAtElapsedMs = null
                onNote("Location fixes could not be requested: $failure")
            }
        onNote("Location fixes requested, one every ${FIX_INTERVAL_MS / MILLIS_PER_SECOND} s")
    }

    /** Stops the fixes. Safe to call when they are not running. */
    fun stop() {
        if (requestedAtElapsedMs == null) return
        requestedAtElapsedMs = null
        client.removeLocationUpdates(callback)
        mainThread.removeCallbacks(reportLoss)
        lossReported = false
        onNote("Location fixes stopped")
    }

    /**
     * The provider says location is not available. That is only worth a line if it lasts: the
     * emulator reports "not available" and "available" again around every single fix, and a
     * line for each would bury everything else in the event log.
     */
    private fun locationWentAway() {
        mainThread.removeCallbacks(reportLoss)
        if (!lossReported) mainThread.postDelayed(reportLoss, LOSS_WORTH_LOGGING_MS)
    }

    private fun locationCameBack() {
        mainThread.removeCallbacks(reportLoss)
        if (!lossReported) return
        lossReported = false
        onNote("Location is available again")
    }

    private fun deliver(location: Location) {
        val requestedAt = requestedAtElapsedMs ?: return
        val fixElapsedMs = location.elapsedRealtimeNanos / NANOS_PER_MILLI
        if (!firstFixSeen) {
            firstFixSeen = true
            val seconds = (fixElapsedMs - requestedAt) / MILLIS_PER_SECOND
            val accuracy = if (location.hasAccuracy()) "${location.accuracy} m" else "unknown"
            onNote("First fix after $seconds s, accuracy $accuracy")
        }
        onFix(
            RawPoint(
                tripId = 0,
                // The time of day is worked out from the phone's own clock and the fix's age,
                // not taken from the fix. The trip rules compare fix times with times read from
                // the phone's clock, and the time inside a fix comes from the satellites, which
                // on some phones is off by seconds or, with a faulty receiver, by years.
                wallClockMs = clock() - (SystemClock.elapsedRealtime() - fixElapsedMs),
                elapsedRealtimeMs = fixElapsedMs,
                latitude = location.latitude,
                longitude = location.longitude,
                accuracyMetres = if (location.hasAccuracy()) location.accuracy else null,
                speedMetresPerSecond = if (location.hasSpeed()) location.speed else null,
            ),
        )
    }
}

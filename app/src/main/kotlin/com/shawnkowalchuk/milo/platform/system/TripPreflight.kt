package com.shawnkowalchuk.milo.platform.system

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager

/** One thing that would stop a trip from being recorded if the service were started now. */
enum class PreflightProblem {
    /** Precise location is not granted. Without it the service may not use the location type. */
    LOCATION_PERMISSION_MISSING,

    /**
     * Location is not set to "Allow all the time". On Android 14 a location service started
     * while MilO is in the background then throws instead of starting.
     */
    BACKGROUND_LOCATION_MISSING,

    /** Location is switched off for the whole phone. */
    LOCATION_SWITCHED_OFF,

    /** The battery setting "Restricted": Android quietly refuses foreground services. */
    BACKGROUND_RESTRICTED,

    /**
     * The Bluetooth permission ("Nearby devices") is not granted. Without it MilO hears no
     * Bluetooth event and cannot ask whether the truck is connected, so a trip would neither
     * start nor end by itself.
     */
    BLUETOOTH_PERMISSION_MISSING,
}

/** What the phone says about the things the preflight checks. Read fresh at every start. */
data class PreflightFacts(
    val fineLocationGranted: Boolean,
    val backgroundLocationGranted: Boolean,
    val locationSwitchedOn: Boolean,
    val backgroundRestricted: Boolean,
    val bluetoothGranted: Boolean,
)

/** Everything in [facts] that stands in the way of recording. Empty means: go ahead. */
fun preflightProblems(facts: PreflightFacts): List<PreflightProblem> = buildList {
    if (!facts.fineLocationGranted) add(PreflightProblem.LOCATION_PERMISSION_MISSING)
    if (!facts.backgroundLocationGranted) add(PreflightProblem.BACKGROUND_LOCATION_MISSING)
    if (!facts.locationSwitchedOn) add(PreflightProblem.LOCATION_SWITCHED_OFF)
    if (facts.backgroundRestricted) add(PreflightProblem.BACKGROUND_RESTRICTED)
    if (!facts.bluetoothGranted) add(PreflightProblem.BLUETOOTH_PERMISSION_MISSING)
}

/**
 * The check ADR-002 runs before the trip service is started. Starting a service that cannot
 * enter the foreground throws, so the known reasons are looked at first and reported by name.
 *
 * Background location is required for every start, including a press of Start with the app
 * open. That is stricter than Android itself, and deliberate: MilO's purpose is the start nobody
 * presses, and a phone on which only manual trips work should say so at once. The Bluetooth
 * permission is required for every start for the same reason.
 */
class TripPreflight(private val context: Context) {
    fun problems(): List<PreflightProblem> = preflightProblems(facts())

    /**
     * What the phone says right now. The setup checklist shows the same five facts as rows, so
     * it reads them here and the two can never disagree about what stops a trip.
     */
    fun facts(): PreflightFacts = PreflightFacts(
        fineLocationGranted = isGranted(Manifest.permission.ACCESS_FINE_LOCATION),
        // Asked for by name: the fine-location check also passes when location is allowed
        // only while the app is in use.
        backgroundLocationGranted = isGranted(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
        locationSwitchedOn =
            context.getSystemService(LocationManager::class.java).isLocationEnabled,
        backgroundRestricted =
            context.getSystemService(ActivityManager::class.java).isBackgroundRestricted,
        bluetoothGranted = isGranted(Manifest.permission.BLUETOOTH_CONNECT),
    )

    private fun isGranted(permission: String): Boolean =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
}

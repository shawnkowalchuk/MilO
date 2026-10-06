package com.shawnkowalchuk.milo.data.settings

/** ADR-002: a trip waits 2 minutes for the truck to reconnect before it is closed. */
const val DEFAULT_GRACE_PERIOD_SECONDS = 120

/** A trip shorter than 0.3 km is discarded, for example moving the truck around the yard. */
const val DEFAULT_MINIMUM_TRIP_DISTANCE_METRES = 300

/**
 * Everything the settings store holds, read in one piece.
 *
 * The last two fields are not settings Shawn chooses. They are small pieces of state that must
 * outlive the process, and the settings store is where such values live.
 *
 * @param truckAddress the Bluetooth address of the paired truck, or null before pairing.
 * @param truckName the truck's name as the phone shows it, for display only.
 * @param truckAssociationId the id of the companion device association, or null without one.
 * @param gracePeriodSeconds how long a trip waits after the truck disconnects.
 * @param minimumTripDistanceMetres trips shorter than this are discarded.
 * @param soundEnabled whether the trip-start sound plays.
 * @param customSoundUri the audio file Shawn chose, or null for the bundled chirp.
 * @param autoStartHeldOff the hold-off latch of ADR-002: true after a trip was ended by hand
 * with the truck still connected, until the truck is next seen disconnected.
 * @param lastProcessExitImportedAtMs the time of the newest process-exit record already copied
 * into the event log, so the same record is not copied again at the next start.
 */
data class MiloSettings(
    val truckAddress: String? = null,
    val truckName: String? = null,
    val truckAssociationId: Int? = null,
    val gracePeriodSeconds: Int = DEFAULT_GRACE_PERIOD_SECONDS,
    val minimumTripDistanceMetres: Int = DEFAULT_MINIMUM_TRIP_DISTANCE_METRES,
    val soundEnabled: Boolean = true,
    val customSoundUri: String? = null,
    val autoStartHeldOff: Boolean = false,
    val lastProcessExitImportedAtMs: Long = 0L,
)

package com.shawnkowalchuk.milo.data.settings

/** ADR-002: a trip waits 2 minutes for the truck to reconnect before it is closed. */
const val DEFAULT_GRACE_PERIOD_SECONDS = 120

/** A trip shorter than 0.3 km is discarded, for example moving the truck around the yard. */
const val DEFAULT_MINIMUM_TRIP_DISTANCE_METRES = 300

/**
 * A step of the setup checklist that MilO cannot read from the phone, so Shawn confirms it by
 * hand. The checklist stores the time of each confirmation.
 *
 * @param key what the confirmation is stored under. It is written to the settings file, so it
 * must never change, even if the constant is renamed.
 */
enum class ConfirmedStep(val key: String) {
    /** HyperOS "Background autostart". Asked for only when the unofficial reading fails. */
    XIAOMI_AUTOSTART("confirmed_xiaomi_autostart_at_ms"),

    /** HyperOS per-app Battery saver set to "No restrictions". */
    XIAOMI_BATTERY_SAVER("confirmed_xiaomi_battery_saver_at_ms"),

    /** HyperOS "Other permissions": lock screen, background windows, permanent notification. */
    XIAOMI_OTHER_PERMISSIONS("confirmed_xiaomi_other_permissions_at_ms"),

    /** MilO locked in the recent apps, so the cleaners leave it alone. */
    XIAOMI_RECENTS_LOCK("confirmed_xiaomi_recents_lock_at_ms"),
}

/**
 * Everything the settings store holds, read in one piece.
 *
 * The last three fields are not settings Shawn chooses. They are small pieces of state that must
 * outlive the process, and the settings store is where such values live.
 *
 * @param truckAddress the Bluetooth address of the paired truck, or null before pairing.
 * @param truckName the truck's name as the phone shows it, for display only.
 * @param truckAssociationId the id of the companion device association, or null without one.
 * @param gracePeriodSeconds how long a trip waits after the truck disconnects.
 * @param minimumTripDistanceMetres trips shorter than this are discarded.
 * @param soundEnabled whether the trip-start sound plays.
 * @param customSoundUri the audio file Shawn chose, or null for the bundled chirp.
 * @param autoStartHeldOffSinceMs the hold-off of ADR-002: when a trip was ended by hand with
 * the truck still connected, or null when automatic start is not held off. The time is kept
 * because two of the three things that release the hold-off are measured from it.
 * @param lastProcessExitImportedAtMs the time of the newest process-exit record already copied
 * into the event log, so the same record is not copied again at the next start.
 * @param confirmedAtMs when Shawn confirmed each setup step that MilO cannot read. A step that
 * is not in the map is not confirmed.
 */
data class MiloSettings(
    val truckAddress: String? = null,
    val truckName: String? = null,
    val truckAssociationId: Int? = null,
    val gracePeriodSeconds: Int = DEFAULT_GRACE_PERIOD_SECONDS,
    val minimumTripDistanceMetres: Int = DEFAULT_MINIMUM_TRIP_DISTANCE_METRES,
    val soundEnabled: Boolean = true,
    val customSoundUri: String? = null,
    val autoStartHeldOffSinceMs: Long? = null,
    val lastProcessExitImportedAtMs: Long = 0L,
    val confirmedAtMs: Map<ConfirmedStep, Long> = emptyMap(),
)

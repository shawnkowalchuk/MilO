package com.shawnkowalchuk.milo.data.settings

import com.shawnkowalchuk.milo.core.schedule.DEFAULT_WORK_SCHEDULE
import com.shawnkowalchuk.milo.core.schedule.WorkSchedule

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
 * Seven fields are not settings Shawn chooses: [lastDrivingAlertAtMs], [reminderShown],
 * [lastExport] and the last four. They are small pieces of state that must outlive the process,
 * and the settings store is where such values live.
 *
 * @param truckAddress the Bluetooth address of the paired truck, or null before pairing.
 * @param truckName the truck's name as the phone shows it, for display only.
 * @param truckAssociationId the id of the companion device association, or null without one.
 * @param gracePeriodSeconds how long a trip waits after the truck disconnects.
 * @param minimumTripDistanceMetres trips shorter than this are discarded.
 * @param soundEnabled whether the trip-start sound plays.
 * @param customSoundUri the audio file Shawn chose, or null for the bundled chirp. It names
 * MilO's own copy of the file (`data/sound/OwnSoundStore`), never the file he picked.
 * @param customSoundName what the file he picked was called, for display only. Null with no
 * custom sound, and when the phone gave no name for the file.
 * @param schedule the work schedule: which days are tracked, and each day's hours. A trip that
 * starts inside it is saved as Business. It sorts a trip when the trip is finalised and never
 * before, and it has no say in whether a trip starts. The driving alert reads it too, to keep
 * quiet outside the work hours.
 * @param ignoreTripsOutsideSchedule what becomes of a trip that started outside the schedule:
 * false saves it as Personal, true has it stored as discarded when it is finalised.
 * @param drivingAlertEnabled whether MilO sends a notification when the phone reports driving
 * during the work hours with no trip being recorded and the truck not connected. The alert only
 * notifies: a trip starts when the notification is tapped, never by itself.
 * @param lastDrivingAlertAtMs when the driving alert was last posted, or null if it never was.
 * No second alert is posted for a while after it, and that must hold when MilO's process was
 * restarted in between.
 * @param reportName Shawn's name as the report for the accountant prints it, or null while it
 * is not set. A report cannot be made without it.
 * @param reportCompany his company, for the report's heading, or null to leave the line out.
 * @param reportVehicle a description of the vehicle, for the report's heading, or null to
 * leave the line out.
 * @param accountantEmail where the report is sent: the address the email app is opened with,
 * or null while it is not set. MilO itself sends nothing.
 * @param reportHandOver the report that was handed to the email app and not answered for
 * yet, or null when no question is waiting. Not a setting either: see [ReportHandOver].
 * @param reminderEnabled whether MilO reminds Shawn, with a notification, that last month's
 * report has not been sent.
 * @param reminderDay the day of the month from which it does so, 1 to 31. In a month with
 * fewer days it is that month's last day.
 * @param reminderShown the month the reminder was last shown for and the day it was shown on,
 * or null if it never was. Not a setting: see [ReminderShown].
 * @param lastExport when all of MilO's data was last written to an export file, or null if it
 * never was. Not a setting: see [LastExport].
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
    val customSoundName: String? = null,
    val schedule: WorkSchedule = DEFAULT_WORK_SCHEDULE,
    val ignoreTripsOutsideSchedule: Boolean = false,
    val drivingAlertEnabled: Boolean = true,
    val lastDrivingAlertAtMs: Long? = null,
    val reportName: String? = null,
    val reportCompany: String? = null,
    val reportVehicle: String? = null,
    val accountantEmail: String? = null,
    val reportHandOver: ReportHandOver? = null,
    val reminderEnabled: Boolean = true,
    val reminderDay: Int = DEFAULT_REMINDER_DAY,
    val reminderShown: ReminderShown? = null,
    val lastExport: LastExport? = null,
    val autoStartHeldOffSinceMs: Long? = null,
    val lastProcessExitImportedAtMs: Long = 0L,
    val confirmedAtMs: Map<ConfirmedStep, Long> = emptyMap(),
)

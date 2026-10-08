package com.shawnkowalchuk.milo.data.settings

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import com.shawnkowalchuk.milo.core.schedule.WorkSchedule

// What of the settings file travels with an export, what an import and a restore write to it,
// and the record of the last export. The settings file itself is restored whole by Android's
// backup; what must not be believed on another phone is taken out again here.

/**
 * When all of MilO's data was last written to an export file. It is not a setting Shawn
 * chooses: like the record of the last reminder, it is a small piece of state that must outlive
 * the process. The Settings screen shows it, so that an export that is months old is seen.
 *
 * @param withPoints whether the raw GPS points were in that file.
 */
data class LastExport(val atMs: Long, val withPoints: Boolean)

/**
 * The truck as it travels in an export: which device it is, and what it is called. The
 * companion device association is left behind on purpose: it is Android's, on one phone, and
 * means nothing on another.
 */
data class TransferredTruck(val address: String, val name: String?)

/**
 * The settings that mean the same on another phone, which is what an export holds of them.
 *
 * Left out, because each is about one phone or one moment: the companion association, MilO's
 * copy of a chosen trip-start sound, the confirmations of the setup checklist, the hold-off
 * after a manual end, the wait beside a parked truck, the times of the last driving alert and
 * the last reminder, the report that waits for "Did you send it?", and how far the process-exit
 * records were imported.
 *
 * Three settings are missing that do mean the same anywhere: the switch and the time of the
 * daily "nothing recorded" check, and the parked limit. That is a gap, not a rule
 * (`NothingRecordedStorage.kt` says why for the first two, the note below for the third): an
 * import leaves them as this phone has them.
 *
 * TODO(debt): the parked limit ([MiloSettings.parkedLimitSeconds]) is a setting that would mean
 *  the same on another phone, and it is not here: adding it changes the export file's form,
 *  which needs a new format version and a reader for both. An import leaves the phone's own
 *  value in place. See docs/FINDINGS_LOG.md, 2026-10-06 (evening). The odometer readings
 *  ([MiloSettings.odometerReadings], 2026-10-07) and the widget's rate
 *  ([MiloSettings.homeWidgetCentsPerKm], 2026-10-07) are left out on the same terms: Android's
 *  backup carries them, an export file does not. See docs/FINDINGS_LOG.md, 2026-10-07. So is
 *  the unit distances are shown in ([MiloSettings.distanceUnit], 2026-10-07 (evening)): an
 *  import leaves this phone's choice of kilometres or miles as it is. Format 2 of the export
 *  file was made for one thing only, the unit a sent report was printed in, which is a record
 *  and not a setting; these four settings still wait for the format that takes them all.
 */
data class TransferredSettings(
    val truck: TransferredTruck?,
    val gracePeriodSeconds: Int,
    val minimumTripDistanceMetres: Int,
    val soundEnabled: Boolean,
    val schedule: WorkSchedule,
    val ignoreTripsOutsideSchedule: Boolean,
    val drivingAlertEnabled: Boolean,
    val reportName: String?,
    val reportCompany: String?,
    val reportVehicle: String?,
    val accountantEmail: String?,
    val reminderEnabled: Boolean,
    val reminderDay: Int,
)

/** The part of the stored settings that an export carries. */
fun MiloSettings.transferred(): TransferredSettings = TransferredSettings(
    truck = truckAddress?.let { TransferredTruck(it, truckName) },
    gracePeriodSeconds = gracePeriodSeconds,
    minimumTripDistanceMetres = minimumTripDistanceMetres,
    soundEnabled = soundEnabled,
    schedule = schedule,
    ignoreTripsOutsideSchedule = ignoreTripsOutsideSchedule,
    drivingAlertEnabled = drivingAlertEnabled,
    reportName = reportName,
    reportCompany = reportCompany,
    reportVehicle = reportVehicle,
    accountantEmail = accountantEmail,
    reminderEnabled = reminderEnabled,
    reminderDay = reminderDay,
)

/**
 * Whether these values could have been stored by the setters of [SettingsStore]: the last
 * check before an import writes them, in one piece, past those setters.
 */
fun TransferredSettings.isStorable(): Boolean = gracePeriodSeconds >= 0 &&
    minimumTripDistanceMetres >= 0 &&
    reminderDay in REMINDER_DAYS &&
    listOf(reportName, reportCompany, reportVehicle).all { it == null || isReportText(it) } &&
    (accountantEmail == null || isEmailAddress(accountantEmail)) &&
    (truck == null || (truck.address.isNotBlank() && truck.name?.isBlank() != true))

/** What an import does to the truck that is stored on this phone. */
sealed interface TruckChange {
    /** The stored truck stays as it is, association and all. */
    data object Keep : TruckChange

    /**
     * This truck is stored, with no association: Android on this phone holds none for it yet,
     * or the pairing check is left to find the one it holds.
     */
    data class Store(val truck: TransferredTruck) : TruckChange
}

// The key names are what is written to the file; renaming one silently resets that value.
private val LAST_EXPORT_AT_MS = longPreferencesKey("last_export_at_ms")
private val LAST_EXPORT_WITH_POINTS = booleanPreferencesKey("last_export_with_points")

/** The stored record of the last export, or null if there was none. */
internal fun Preferences.readLastExport(): LastExport? {
    val atMs = this[LAST_EXPORT_AT_MS]?.takeIf { it >= 0 } ?: return null
    return LastExport(atMs, withPoints = this[LAST_EXPORT_WITH_POINTS] ?: false)
}

/** Stores that an export was written. */
suspend fun SettingsStore.setLastExport(export: LastExport) {
    require(export.atMs >= 0) { "A timestamp cannot be negative: ${export.atMs} ms" }
    dataStore.edit {
        it[LAST_EXPORT_AT_MS] = export.atMs
        it[LAST_EXPORT_WITH_POINTS] = export.withPoints
    }
}

/**
 * Stores the settings an import brought, all of them in one write: either every one of them is
 * in force afterwards or none is. What this phone knows about itself (the list in
 * [TransferredSettings]) is left as it is, with one exception: the report that waited for "Did
 * you send it?" is forgotten, because the trips it was made of have just been replaced.
 *
 * @throws IllegalArgumentException for values no setter would take ([isStorable]). Nothing is
 * written then.
 */
suspend fun SettingsStore.replaceTransferred(arrived: TransferredSettings, truck: TruckChange) {
    require(arrived.isStorable()) { "Settings that no setter would take cannot be imported" }
    dataStore.edit { stored ->
        stored[SettingsStore.GRACE_PERIOD_SECONDS] = arrived.gracePeriodSeconds
        stored[SettingsStore.MINIMUM_TRIP_DISTANCE_METRES] = arrived.minimumTripDistanceMetres
        stored[SettingsStore.SOUND_ENABLED] = arrived.soundEnabled
        stored.writeSchedule(arrived.schedule)
        stored[SettingsStore.IGNORE_TRIPS_OUTSIDE_SCHEDULE] = arrived.ignoreTripsOutsideSchedule
        stored[SettingsStore.DRIVING_ALERT_ENABLED] = arrived.drivingAlertEnabled
        stored.setOrRemove(SettingsStore.REPORT_NAME, arrived.reportName)
        stored.setOrRemove(SettingsStore.REPORT_COMPANY, arrived.reportCompany)
        stored.setOrRemove(SettingsStore.REPORT_VEHICLE, arrived.reportVehicle)
        stored.setOrRemove(SettingsStore.ACCOUNTANT_EMAIL, arrived.accountantEmail)
        stored.writeReminderEnabled(arrived.reminderEnabled)
        stored.writeReminderDay(arrived.reminderDay)
        stored.writeReportHandOver(null)
        if (truck is TruckChange.Store) {
            stored[SettingsStore.TRUCK_ADDRESS] = truck.truck.address
            stored.setOrRemove(SettingsStore.TRUCK_NAME, truck.truck.name)
            stored.remove(SettingsStore.TRUCK_ASSOCIATION_ID)
        }
    }
}

/**
 * Takes out of a settings file that Android restored what was only ever true of the
 * installation it was backed up from. The four confirmations of the setup checklist go in any
 * case: they are Shawn's word about switches of that phone, and an uninstall resets the same
 * switches on this one. So does a wait beside the parked truck.
 *
 * @param dropAssociation true if Android on this phone holds no association for the stored
 * truck. The truck's address and name stay, so that the phone knows which device to pair again.
 * @param dropOwnSound true if MilO's copies of the trip-start sounds of Shawn's own are not on
 * this phone. They never are after a restore: the copies are kept out of every backup. The
 * list of them goes too, and the built-in chirp plays.
 */
suspend fun SettingsStore.forgetOtherInstallation(dropAssociation: Boolean, dropOwnSound: Boolean) {
    dataStore.edit { stored ->
        for (step in ConfirmedStep.entries) stored.remove(longPreferencesKey(step.key))
        // A wait beside the truck was true of the moment the backup was made, nothing more; so
        // was the trip that a parked truck's moving had started.
        stored.forgetParkedTruck()
        stored.forgetDrivenOffTrip()
        if (dropAssociation) stored.remove(SettingsStore.TRUCK_ASSOCIATION_ID)
        if (dropOwnSound) stored.forgetOwnSounds()
    }
}

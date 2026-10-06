package com.shawnkowalchuk.milo.platform.driving

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.shawnkowalchuk.milo.core.schedule.DEFAULT_WORK_SCHEDULE
import com.shawnkowalchuk.milo.core.schedule.WorkSchedule
import com.shawnkowalchuk.milo.data.crash.CrashFileStore
import com.shawnkowalchuk.milo.data.eventlog.EventLogDao
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.trip.TripRepository
import com.shawnkowalchuk.milo.platform.bluetooth.TruckReading
import com.shawnkowalchuk.milo.platform.trip.FakeSettingsFile
import com.shawnkowalchuk.milo.platform.trip.FakeWorld
import com.shawnkowalchuk.milo.platform.trip.TripController
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent

// What the tests of the driving alert share: the reports, the moment the rules are asked about,
// and the stand-ins the alert is built on.

internal const val MINUTE_MS = 60_000L
internal const val DAY_MS = 24 * 60 * MINUTE_MS

internal val ENTERED = VehicleReport(entered = true, ageMs = 4_000)
internal val LEFT = VehicleReport(entered = false, ageMs = 4_000)

internal val EDMONTON: ZoneId = ZoneId.of("America/Edmonton")

/** Wall-clock milliseconds of a date and time in Edmonton. October 2026: the 6th is a Tuesday. */
internal fun at(day: Int, hour: Int, minute: Int, second: Int = 0): Long = LocalDateTime.of(
    2026,
    10,
    day,
    hour,
    minute,
    second,
).atZone(EDMONTON).toInstant().toEpochMilli()

/** The Tuesday morning inside the work hours that the tests of the rules start from. */
internal val TUESDAY_MORNING = at(day = 6, hour = 10, minute = 15)

/**
 * The moment the alert exists for: a Tuesday morning inside the work hours, a fresh report of
 * entering a vehicle, the alert switched on, a truck paired and not connected, no trip being
 * recorded, none that ended lately and no alert before. A test changes what it is about.
 */
internal fun drivingMoment(
    reports: List<VehicleReport> = listOf(ENTERED),
    alertEnabled: Boolean = true,
    tripInProgress: Boolean = false,
    truckPaired: Boolean = true,
    truck: TruckReading.Answer = TruckReading.Answer.NOT_CONNECTED,
    lastTripEndedAtMs: Long? = null,
    lastAlertAtMs: Long? = null,
    nowMs: Long = TUESDAY_MORNING,
    schedule: WorkSchedule = DEFAULT_WORK_SCHEDULE,
    zone: ZoneId = EDMONTON,
) = DrivingMoment(
    reports = reports,
    alertEnabled = alertEnabled,
    tripInProgress = tripInProgress,
    truckPaired = truckPaired,
    truck = truck,
    lastTripEndedAtMs = lastTripEndedAtMs,
    lastAlertAtMs = lastAlertAtMs,
    nowMs = nowMs,
    schedule = schedule,
    zone = zone,
)

/** Stands in for the phone's driving detection: what it was asked, and what it answers. */
internal class FakeDetection : DrivingDetection {
    var granted = true

    /** Set to make every request fail, as a phone without Play services does. */
    var refuseWith: String? = null

    /** Set to make the next request throw once, as a fault inside Play services would. */
    var throwOnce: Exception? = null
    val asked = mutableListOf<String>()

    override fun permissionGranted(): Boolean = granted

    override suspend fun watch(): String? {
        asked += "watch"
        throwOnce?.let { failure ->
            throwOnce = null
            throw failure
        }
        return refuseWith
    }

    override suspend fun stopWatching(): String? {
        asked += "stop"
        return refuseWith
    }
}

/** Stands in for the notification: whether the alert is showing, and how often it was posted. */
internal class FakeNotification {
    var showing = false
    var posted = 0

    /** Set to false for a phone on which MilO's notifications are switched off. */
    var visible = true

    /** Set to make taking the alert away fail once. */
    var failNextWithdrawal: Exception? = null

    fun show(): Boolean {
        showing = true
        posted++
        return visible
    }

    fun withdraw() {
        failNextWithdrawal?.let { failure ->
            failNextWithdrawal = null
            throw failure
        }
        showing = false
    }
}

/**
 * The stand-ins of trip recording ([FakeWorld]) on the day the alert exists for. The world's
 * clock starts on Saturday 2026-10-03 at 12:00 UTC; three days on it is a Tuesday noon, inside
 * the work hours MilO starts out with.
 *
 * @param truckPaired false for a phone that has never been paired with a truck, and for a
 * settings file that cannot be written.
 */
internal fun workdayWorld(
    settingsFile: DataStore<Preferences> = FakeSettingsFile(),
    truckPaired: Boolean = true,
): FakeWorld = FakeWorld(settingsFile).apply {
    nowMs += 3 * DAY_MS
    if (truckPaired) runBlocking { settings.setTruck("AA:BB:CC:DD:EE:FF", "Work truck", 7) }
}

/**
 * A driving alert as the app builds it, on stand-ins and beside a real trip controller. Its
 * coroutines run in the test's scheduler.
 *
 * @param openTripStored and [lastTripEndedAtMs]: the two questions about the stored trips. A
 * test replaces one to make storage fail, or to have something happen while it is read.
 * @param log the event log's table. A test passes one that cannot be written.
 */
// runCurrent() is how a test lets the alert's coroutines run. The API is marked experimental by
// the coroutines library; there is no stable equivalent.
@OptIn(ExperimentalCoroutinesApi::class)
internal fun TestScope.drivingAlert(
    controller: TripController,
    world: FakeWorld,
    detection: FakeDetection,
    notification: FakeNotification,
    crashFiles: CrashFileStore,
    openTripStored: suspend () -> Boolean = { world.openTrips.isNotEmpty() },
    lastTripEndedAtMs: suspend () -> Long? = TripRepository(world.trips)::findNewestTripEndMs,
    log: EventLogDao = world.log,
): DrivingAlert = DrivingAlert(
    detection = detection,
    showAlert = notification::show,
    withdrawAlert = notification::withdraw,
    settings = world.settings,
    truck = world.truck,
    openTripStored = openTripStored,
    lastTripEndedAtMs = lastTripEndedAtMs,
    tripActivity = controller.activity,
    whenTripsCaughtUp = controller::whenCaughtUp,
    eventLog = EventLogRepository(log),
    crashFileStore = crashFiles,
    clock = { world.nowMs },
    zone = { world.zone },
    scope = backgroundScope,
).also { runCurrent() }

package com.shawnkowalchuk.milo.platform.nothingrecorded

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.shawnkowalchuk.milo.core.trip.TripStartCause
import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.data.crash.CrashFileStore
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogDao
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.settings.SettingsStore
import com.shawnkowalchuk.milo.data.trip.Trip
import com.shawnkowalchuk.milo.platform.clock.AskedAlarm
import com.shawnkowalchuk.milo.platform.trip.CurrentTrip
import com.shawnkowalchuk.milo.platform.trip.FakeEventLogDao
import com.shawnkowalchuk.milo.platform.trip.FakeSettingsFile
import com.shawnkowalchuk.milo.platform.trip.TripActivity
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.Rule
import org.junit.rules.TemporaryFolder

/**
 * What the tests of the "nothing recorded" check share: stand-ins for the alarm, the
 * notification, the settings file, the stored trips and the trip controller, and a check built
 * on them. It starts on Tuesday 6 October 2026 at 12:30 in Edmonton, a work day out of the box,
 * with the check set to noon and no trip stored: a notification is due.
 */
abstract class NothingRecordedCheckFixture {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    protected val zone: ZoneId = ZoneId.of("America/Edmonton")
    protected val tuesday: LocalDate = LocalDate.of(2026, 10, 6)
    protected val log = FakeEventLogDao()
    protected val settingsFile = FakeSettingsFile()
    protected val settings = SettingsStore(settingsFile)

    protected var nowMs = at("2026-10-06T12:30")

    /** The check's clock. A test that needs every reading to differ replaces it. */
    protected var clock: () -> Long = { nowMs }

    /** Whether MilO's clock is the phone's. A test of a phone whose date is set ahead replaces it. */
    protected var phoneClockAgrees: () -> Boolean = { true }

    /** Whether MilO's clock is on probation. A test of a first start of a boot replaces it. */
    protected var clockOnProbation: () -> Boolean = { false }

    /** Every time the daily alarm was asked for at a time of day, and for when. */
    protected val alarmsAskedFor = mutableListOf<Long>()

    /**
     * Every time it was asked for on the clock that counts from boot, and how far ahead: what
     * the check does while MilO's clock and the phone's disagree.
     */
    protected val alarmsAskedAfter = mutableListOf<Long>()
    protected var alarmsCancelled = 0

    /** The request Android holds now: the newest of either kind, or none. */
    protected var alarmHeld: AskedAlarm? = null

    /** Time since boot, for a request that counts from it. A test with a phone replaces it. */
    protected var elapsedNow: () -> Long = { 0L }

    /** Set to make every request for the alarm fail. */
    protected var failAskingForAlarm: Exception? = null

    /** How many times the notification was posted, and taken away. */
    protected var shown = 0
    protected var withdrawn = 0

    /** Whether a posted notification can be seen: false stands for notifications switched off. */
    protected var seen = true
    protected var trips = emptyList<Trip>()

    /** Set to make every read of the trips fail. */
    protected var failReadingTrips: Exception? = null

    /** How many times the stored trips were read: once for each time the check asked. */
    protected var tripReads = 0

    /** What the trip controller publishes. A test sets a trip here to have one begin. */
    protected val tripActivity = MutableStateFlow(TripActivity())

    /** False stands for a trip controller that never says it has caught up. */
    protected var controllerAnswers = true

    protected val crashFiles: CrashFileStore
        get() = CrashFileStore(File(temporaryFolder.root, "crashes"))

    /** A local date and time in Edmonton, as stored time. */
    protected fun at(text: String): Long =
        LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()

    /** A trip the truck started at [start]: twenty minutes long, or still being recorded. */
    protected fun trip(start: String, status: TripStatus = TripStatus.FINISHED): Trip {
        val startedAtMs = at(start)
        return Trip(
            id = startedAtMs,
            startedAtMs = startedAtMs,
            endedAtMs = if (status == TripStatus.OPEN) null else startedAtMs + 1_200_000,
            status = status,
            startedBy = TripStartCause.TRUCK,
            truckSeen = true,
        )
    }

    /** What the trip controller publishes while [trip] is being recorded. */
    protected fun recording(trip: Trip) = TripActivity(
        trip =
            CurrentTrip(
                tripId = trip.id,
                startedAtMs = trip.startedAtMs,
                startedBy = trip.startedBy,
                distanceMetres = 0.0,
                waitingForTruck = false,
            ),
    )

    /** A check as a fresh process would build it, its coroutines in the test's scheduler. */
    protected fun TestScope.check(
        file: DataStore<Preferences> = settingsFile,
        eventLog: EventLogDao = log,
    ) = NothingRecordedCheck(
        alarm =
            object : NothingRecordedAlarm {
                override fun setFor(atMs: Long) {
                    failAskingForAlarm?.let { throw it }
                    alarmsAskedFor += atMs
                    alarmHeld = AskedAlarm.AtTimeOfDay(atMs)
                }

                override fun setAfter(delayMs: Long) {
                    failAskingForAlarm?.let { throw it }
                    alarmsAskedAfter += delayMs
                    alarmHeld = AskedAlarm.CountedFromBoot(elapsedNow() + delayMs)
                }

                override fun cancel() {
                    alarmsCancelled++
                    alarmHeld = null
                }
            },
        show = {
            shown++
            seen
        },
        withdraw = { withdrawn++ },
        settings = SettingsStore(file),
        tripsStartedBetween = { fromMs, untilMs ->
            tripReads++
            failReadingTrips?.let { throw it }
            trips.filter { it.startedAtMs in fromMs until untilMs }
        },
        openTrip = { trips.firstOrNull { it.status == TripStatus.OPEN } },
        tripActivity = tripActivity,
        whenTripsCaughtUp = { done -> if (controllerAnswers) done() },
        eventLog = EventLogRepository(eventLog),
        crashFileStore = crashFiles,
        clock = { clock() },
        phoneClockAgrees = { phoneClockAgrees() },
        clockOnProbation = { clockOnProbation() },
        zone = { zone },
        scope = backgroundScope,
    )

    /**
     * Lets a look run to its end. A look that finds no trip waits, and looks once more, before
     * it notifies ([TRIP_START_WAIT_MS]); this moves the test's time past that wait. Where a
     * test says `runCurrent()` instead, nothing is due and the look is over at once.
     */
    // advanceTimeBy() and runCurrent() are how a test lets the check's coroutines run. The API
    // is marked experimental by the coroutines library; there is no stable equivalent.
    @OptIn(ExperimentalCoroutinesApi::class)
    protected fun TestScope.letItLook() {
        advanceTimeBy(TRIP_START_WAIT_MS)
        runCurrent()
    }

    /** The messages written to the event log under [category], in order. */
    protected fun lines(category: EventCategory = EventCategory.TRIP): List<String> =
        log.entries.filter { it.category == category }.map { it.message }
}

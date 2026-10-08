package com.shawnkowalchuk.milo.platform.trip

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.shawnkowalchuk.milo.core.trip.ParkedGps
import com.shawnkowalchuk.milo.core.trip.TrackPoint
import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.core.trip.driveNorth
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogDao
import com.shawnkowalchuk.milo.data.eventlog.EventLogEntry
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.point.RawPoint
import com.shawnkowalchuk.milo.data.point.RawPointDao
import com.shawnkowalchuk.milo.data.point.RawPointRepository
import com.shawnkowalchuk.milo.data.settings.DEFAULT_PARKED_LIMIT_SECONDS
import com.shawnkowalchuk.milo.data.settings.SettingsStore
import com.shawnkowalchuk.milo.data.settings.TripSound
import com.shawnkowalchuk.milo.data.trip.TripRepository
import com.shawnkowalchuk.milo.platform.bluetooth.TruckConnectionSource
import com.shawnkowalchuk.milo.platform.bluetooth.TruckReading
import java.io.IOException
import java.time.ZoneId
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.yield

/** The parked limit the controller runs with out of the box: 10 minutes. */
const val PARKED_LIMIT_MS = DEFAULT_PARKED_LIMIT_SECONDS * 1000L

/**
 * Everything the trip controller touches, replaced by stand-ins held in memory: the two
 * databases, the settings file, the truck, the clock and the trip service. The storage outlives
 * a controller, so a test can "kill the process" by building a second controller on the same
 * world.
 *
 * @param settingsFile the settings file. A test passes one that cannot be read to see what the
 * controller does without its settings.
 */
class FakeWorld(settingsFile: DataStore<Preferences> = FakeSettingsFile()) {
    val trips = FakeTripDao()
    val points = FakeRawPointDao()
    val log = FakeEventLogDao()
    val settings = SettingsStore(settingsFile)
    val truck = FakeTruck()

    /** The phone's clock. Tests move it by hand. It starts at 2026-10-03 12:00 UTC. */
    var nowMs = 1_791_028_800_000L

    /**
     * The phone's time zone. A trip is sorted into Business or Personal by the day and the time
     * of day it started in this zone; the clock above starts on a Saturday noon in it.
     */
    var zone: ZoneId = ZoneId.of("UTC")

    /**
     * Whether the phone reports getting into a vehicle to MilO. Off unless a test turns it on,
     * so that a wait beside the parked truck reads GPS throughout, as before 2026-10-07.
     */
    var motionSensorWatching = false

    /** A controller as a fresh process would build it, and the service that goes with it. */
    fun newProcess(scope: CoroutineScope): Pair<TripController, FakeService> {
        val service = FakeService()
        val controller =
            TripController(
                trips = TripRepository(trips),
                points = RawPointRepository(points),
                eventLog = EventLogRepository(log),
                settings = settings,
                truck = truck,
                starter = service,
                motionSensorWatching = { motionSensorWatching },
                clock = { nowMs },
                zone = { zone },
                scope = scope,
            )
        service.controller = controller
        return controller to service
    }

    val openTrips get() = trips.rows.filter { it.status == TripStatus.OPEN }

    fun logged(category: EventCategory): List<String> =
        log.entries.filter { it.category == category }.map { it.message }
}

/** The answer to "is the truck connected right now?". Tests set it. */
class FakeTruck : TruckConnectionSource {
    var connected = false

    /** Set to make the phone unable to say, as it is without the Bluetooth permission. */
    var unreadable = false

    /**
     * Set to make a reading take a moment, as asking the phone's Bluetooth does: whatever else
     * is ready to run gets its turn before the answer comes.
     */
    var takesAMoment = false

    override suspend fun read(): TruckReading {
        if (takesAMoment) yield()
        return when {
            unreadable -> TruckReading.unknown("the test took Bluetooth away")
            connected -> TruckReading.connected("the test says so")
            else -> TruckReading.notConnected("the test says so")
        }
    }
}

/**
 * Stands in for Android and the trip service together. A request to start is remembered; the
 * test decides when (and whether) the service reaches the foreground with [comeUp].
 *
 * Its functions are synchronized because the real ones are called from any thread, and one test
 * does exactly that.
 */
class FakeService :
    RecordingStarter,
    TripRecorder {
    lateinit var controller: TripController

    /** Set to make every start fail, as the preflight or Android would. */
    var refuseWith: StartFailure? = null

    /** When true the service reaches the foreground the moment it is asked for. */
    var comesUpAtOnce = false

    val startRequests = mutableListOf<StartRequest>()
    private val waiting = mutableListOf<StartRequest>()

    /** What the controller last asked for: true while it wants recording, false after a stop. */
    var recording = false

    /** True while the controller wants the parked truck watched, with no trip open. */
    var watchingParked = false
    var stops = 0
    var tripStartsAnnounced = 0

    /** How often the trip-start sound was asked for: a trip seen driving off. */
    var drivingOffsAnnounced = 0

    /** Every sound asked for, in order. */
    val sounds = mutableListOf<TripSound>()
    var checkAtMs: Long? = null

    /** Beside the parked truck: when the controller last said GPS goes off, or null for never. */
    var gpsUntilMs: Long? = null

    /** And until when it said GPS is read every 5 seconds, or null. */
    var fastGpsUntilMs: Long? = null

    @Synchronized
    override fun start(request: StartRequest): StartFailure? {
        startRequests += request
        refuseWith?.let { return it }
        waiting += request
        if (comesUpAtOnce) comeUp()
        return null
    }

    /** The service enters the foreground and hands over the triggers it was started with. */
    @Synchronized
    fun comeUp() {
        val handOver = waiting.toList()
        waiting.clear()
        handOver.forEach { controller.onServiceStarted(this, it) }
    }

    @Synchronized
    override fun record(checkAtMs: Long?, sounds: List<TripSound>) {
        recording = true
        watchingParked = false
        this.checkAtMs = checkAtMs
        this.sounds += sounds
        if (TripSound.CONNECT in sounds) tripStartsAnnounced++
        if (TripSound.DRIVING_OFF in sounds) drivingOffsAnnounced++
    }

    @Synchronized
    override fun watchParked(checkAtMs: Long?, gps: ParkedGps) {
        recording = false
        watchingParked = true
        this.checkAtMs = checkAtMs
        gpsUntilMs = gps.untilMs
        fastGpsUntilMs = gps.fastUntilMs
    }

    @Synchronized
    override fun stop() {
        recording = false
        watchingParked = false
        stops++
    }
}

/** A fix as the location recorder hands it over: no trip id yet. */
fun TrackPoint.asFix(): RawPoint = RawPoint(
    tripId = 0,
    wallClockMs = wallClockMs,
    elapsedRealtimeMs = elapsedRealtimeMs,
    latitude = latitude,
    longitude = longitude,
    accuracyMetres = accuracyMetres,
    speedMetresPerSecond = speedMetresPerSecond,
)

class FakeRawPointDao : RawPointDao {
    val rows = mutableListOf<RawPoint>()

    /** Set to make the next insert fail once, as a full disk would. */
    var failNextInsert: Exception? = null

    override suspend fun insert(point: RawPoint): Long {
        failNextInsert?.let { failure ->
            failNextInsert = null
            throw failure
        }
        val id = rows.size + 1L
        rows += point.copy(id = id)
        return id
    }

    override suspend fun findForTrip(tripId: Long): List<RawPoint> =
        rows.filter { it.tripId == tripId }

    override suspend fun deleteForTrip(tripId: Long): Int {
        val before = rows.size
        rows.removeAll { it.tripId == tripId }
        return before - rows.size
    }
}

class FakeEventLogDao : EventLogDao {
    /** Safe to read from a test thread while the controller's worker is still writing. */
    val entries = CopyOnWriteArrayList<EventLogEntry>()

    private var inserted = 0L

    /** Each line gets the next id, as the table gives it, so that lines can be told apart. */
    override suspend fun insert(entry: EventLogEntry): Long {
        val id = ++inserted
        entries += entry.copy(id = id)
        return id
    }

    override fun observeNewest(limit: Int): Flow<List<EventLogEntry>> =
        flowOf(newestFirst().take(limit))

    override fun observeNewestOf(
        categories: List<EventCategory>,
        limit: Int,
    ): Flow<List<EventLogEntry>> =
        flowOf(newestFirst().filter { it.category in categories }.take(limit))

    override suspend fun readAfter(afterAtMs: Long, afterId: Long, limit: Int) = entries
        .sortedWith(compareBy<EventLogEntry> { it.atMs }.thenBy { it.id })
        .filter { it.atMs > afterAtMs || (it.atMs == afterAtMs && it.id > afterId) }
        .take(limit)

    override suspend fun count(): Int = entries.size

    override suspend fun atMsOfEntryBehind(newerEntries: Int): Long? =
        newestFirst().getOrNull(newerEntries)?.atMs

    override suspend fun deleteOlderThan(beforeMs: Long): Int {
        val old = entries.filter { it.atMs < beforeMs }
        entries.removeAll(old)
        return old.size
    }

    private fun newestFirst(): List<EventLogEntry> = entries.sortedWith(
        compareByDescending<EventLogEntry> {
            it.atMs
        }.thenByDescending { it.id },
    )
}

class FakeSettingsFile : DataStore<Preferences> {
    private val stored = MutableStateFlow(emptyPreferences())

    override val data: Flow<Preferences> = stored

    override suspend fun updateData(
        transform: suspend (t: Preferences) -> Preferences,
    ): Preferences = transform(stored.value).also { stored.value = it }
}

/** A settings file whose every read and write fails, as a damaged one does. */
object UnreadableSettingsFile : DataStore<Preferences> {
    override val data: Flow<Preferences> = flow { throw IOException("the file is damaged") }

    override suspend fun updateData(
        transform: suspend (t: Preferences) -> Preferences,
    ): Preferences = throw IOException("the file is damaged")
}

/** A controller as a fresh process would build it, its worker running in the test's scheduler. */
// runCurrent() is how a test lets the controller's worker run. The API is marked experimental
// by the coroutines library; there is no stable equivalent.
@OptIn(ExperimentalCoroutinesApi::class)
fun TestScope.process(world: FakeWorld): Pair<TripController, FakeService> =
    world.newProcess(backgroundScope).also { runCurrent() }

/** A manual trip that is being recorded: Start pressed, service up. */
@OptIn(ExperimentalCoroutinesApi::class)
fun TestScope.recordingManualTrip(world: FakeWorld): Pair<TripController, FakeService> {
    val (controller, service) = process(world)
    service.comesUpAtOnce = true
    controller.onTrigger(TripTrigger.MANUAL_START, "Start button")
    runCurrent()
    return controller to service
}

/** Feeds a drive due north, one fix every five seconds, and moves the clock along with it. */
@OptIn(ExperimentalCoroutinesApi::class)
fun TestScope.drive(
    world: FakeWorld,
    controller: TripController,
    fixCount: Int,
    metresPerFix: Double,
) {
    val fixes = driveNorth(fixCount, metresPerFix)
    fixes.forEach { controller.onFix(it.asFix()) }
    world.nowMs = fixes.last().wallClockMs
    runCurrent()
}

package com.shawnkowalchuk.milo.data.trip

import com.shawnkowalchuk.milo.core.schedule.DEFAULT_WORK_SCHEDULE
import com.shawnkowalchuk.milo.core.schedule.TripCategory
import com.shawnkowalchuk.milo.core.schedule.classifyTrip
import com.shawnkowalchuk.milo.core.trip.TripStartCause
import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.data.crash.CrashFileStore
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.settings.SettingsStore
import com.shawnkowalchuk.milo.platform.trip.FakeEventLogDao
import com.shawnkowalchuk.milo.platform.trip.FakeSettingsFile
import com.shawnkowalchuk.milo.platform.trip.FakeTripDao
import com.shawnkowalchuk.milo.platform.trip.UnreadableSettingsFile
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private const val MINUTE_MS = 60_000L

/**
 * The catch-up that sorts the trips recorded before MilO had a work schedule: what a pass
 * writes, what it never does, and what it says. It runs against the stand-in trips table, whose
 * conditions are written out like the real queries'. Which trips it may touch at all is in
 * `TripCategorySelectionTest`.
 */
// runCurrent() is how a test lets the catch-up's coroutine run. The API is marked experimental
// by the coroutines library; there is no stable equivalent.
@OptIn(ExperimentalCoroutinesApi::class)
class TripCategoryCatchUpTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val edmonton = ZoneId.of("America/Edmonton")
    private val trips = FakeTripDao()
    private val log = FakeEventLogDao()
    private val settings = SettingsStore(FakeSettingsFile())
    private val nowMs = 1_791_500_000_000L

    private fun TestScope.catchUp(store: SettingsStore = settings): TripCategoryCatchUp =
        TripCategoryCatchUp(
            trips = TripRepository(trips),
            settings = store,
            eventLog = EventLogRepository(log),
            crashFileStore = CrashFileStore(folder.root),
            zone = { edmonton },
            clock = { nowMs },
            scope = backgroundScope,
        )

    /** Stores a trip that started at an Edmonton wall-clock time and lasted twenty minutes. */
    private fun stored(
        start: String,
        status: TripStatus = TripStatus.FINISHED,
        category: TripCategory? = null,
        setByHand: Boolean = false,
        minutes: Long = 20,
    ): Trip {
        val startedAtMs = LocalDateTime.parse(start).atZone(edmonton).toInstant().toEpochMilli()
        val open = status == TripStatus.OPEN
        val trip =
            Trip(
                id = trips.rows.size + 1L,
                startedAtMs = startedAtMs,
                endedAtMs = (startedAtMs + minutes * MINUTE_MS).takeUnless { open },
                status = status,
                startedBy = TripStartCause.TRUCK,
                truckSeen = true,
                distanceMetres = 12_340.0,
                category = category,
                categorySetByHand = setByHand,
            )
        trips.rows += trip
        return trip
    }

    private fun row(trip: Trip): Trip = trips.rows.single { it.id == trip.id }

    private fun logged(category: EventCategory): List<String> =
        log.entries.filter { it.category == category }.map { it.message }

    // ---- What a pass does -------------------------------------------------------------------------

    @Test
    fun `trips recorded earlier are sorted by the schedule as it is now`() = runTest {
        // 5 October 2026 is a Monday.
        val inside = stored("2026-10-05T10:00")
        val early = stored("2026-10-05T07:55")
        val saturday = stored("2026-10-10T10:00")
        val late = stored("2026-10-05T16:20")

        catchUp().catchUp("process start")
        runCurrent()

        assertEquals(TripCategory.BUSINESS, row(inside).category)
        assertEquals(TripCategory.PERSONAL, row(early).category)
        assertEquals(TripCategory.PERSONAL, row(saturday).category)
        // 16:20 to 16:40: Business, and still running at 16:30.
        assertEquals(TripCategory.BUSINESS, row(late).category)
        assertTrue(row(late).ranPastSchedule)
        assertFalse(row(inside).ranPastSchedule)
        // None of them counts as set by hand: the schedule decided.
        assertTrue(trips.rows.none { it.categorySetByHand })
    }

    @Test
    fun `the schedule in the settings is the one that is used`() = runTest {
        val saturday = stored("2026-10-10T10:00")
        settings.setSchedule(DEFAULT_WORK_SCHEDULE.withTracked(DayOfWeek.SATURDAY, true))

        catchUp().catchUp("process start")
        runCurrent()

        assertEquals(TripCategory.BUSINESS, row(saturday).category)
    }

    @Test
    fun `nothing but the category and the flag is written`() = runTest {
        val finished = stored("2026-10-05T10:00")
        val discarded = stored("2026-10-05T11:00", TripStatus.DISCARDED)
        val deleted = stored("2026-10-05T16:20", TripStatus.DELETED)

        catchUp().catchUp("process start")
        runCurrent()

        assertEquals(finished.copy(category = TripCategory.BUSINESS), row(finished))
        // Sorted too, so that it has its category if it is counted or restored.
        assertEquals(discarded.copy(category = TripCategory.BUSINESS), row(discarded))
        assertEquals(
            deleted.copy(category = TripCategory.BUSINESS, ranPastSchedule = true),
            row(deleted),
        )
    }

    @Test
    fun `with ignore chosen an earlier Personal trip is sorted and never discarded`() = runTest {
        // "Ignore them" is applied once, when a trip is finalised. A trip that was kept then
        // stays kept.
        settings.setIgnoreTripsOutsideSchedule(true)
        val saturday = stored("2026-10-10T10:00")

        catchUp().catchUp("process start")
        runCurrent()

        assertEquals(TripStatus.FINISHED, row(saturday).status)
        assertEquals(TripCategory.PERSONAL, row(saturday).category)
        assertFalse(row(saturday).ignoredOutsideSchedule)
    }

    @Test
    fun `a trip set by hand keeps its category, whatever the schedule says`() = runTest {
        // Inside the hours, and marked Personal by Shawn; outside them, and marked Business.
        val markedPersonal =
            stored("2026-10-05T10:00", category = TripCategory.PERSONAL, setByHand = true)
        val markedBusiness =
            stored("2026-10-10T10:00", category = TripCategory.BUSINESS, setByHand = true)

        catchUp().catchUp("process start")
        runCurrent()

        assertEquals(markedPersonal, row(markedPersonal))
        assertEquals(markedBusiness, row(markedBusiness))
        assertEquals(emptyList<String>(), logged(EventCategory.TRIP))
    }

    @Test
    fun `a later change of the schedule changes no trip that is already sorted`() = runTest {
        val monday = stored("2026-10-05T10:00")
        val catchUp = catchUp()
        catchUp.catchUp("process start")
        runCurrent()
        assertEquals(TripCategory.BUSINESS, row(monday).category)

        // Mondays are no longer tracked. The next start runs the pass again.
        settings.setSchedule(DEFAULT_WORK_SCHEDULE.withTracked(DayOfWeek.MONDAY, false))
        catchUp.catchUp("process start")
        runCurrent()

        assertEquals(TripCategory.BUSINESS, row(monday).category)
        assertEquals(1, logged(EventCategory.TRIP).size)
    }

    @Test
    fun `a trip marked by hand while the pass is under way is not overwritten`() = runTest {
        val monday = stored("2026-10-05T10:00")
        val repository = TripRepository(trips)
        val found = repository.findUnsortedTrips()
        // Shawn's press lands between the pass reading the trips and its write.
        repository.setCategoryByHand(monday.id, TripCategory.PERSONAL)

        val written = found.map { repository.sortUnsorted(it.id, businessFor(it)) }

        assertEquals(listOf(false), written)
        assertEquals(TripCategory.PERSONAL, row(monday).category)
        assertTrue(row(monday).categorySetByHand)
    }

    private fun businessFor(trip: Trip) =
        classifyTrip(trip.startedAtMs, trip.endedAtMs, DEFAULT_WORK_SCHEDULE, edmonton)

    // ---- What it says -----------------------------------------------------------------------------

    @Test
    fun `a pass that sorted trips leaves one line, with each trip and what it went by`() = runTest {
        stored("2026-10-05T10:00")
        stored("2026-10-05T16:20")
        stored("2026-10-10T10:00")

        catchUp().catchUp("process start")
        runCurrent()

        val line = log.entries.single()
        assertEquals(EventCategory.TRIP, line.category)
        assertEquals(nowMs, line.atMs)
        assertEquals(
            "Business or Personal set for 3 trips recorded before they were sorted " +
                "(process start), by the work schedule as it is now: 2 Business (1 ran past " +
                "schedule), 1 Personal. No trip was discarded or otherwise changed.",
            line.message,
        )
        assertEquals(
            listOf(
                "Trip 1: Business: it started on a Mon at 10:00:00; that day's hours are 08:00 " +
                    "to 16:30 (America/Edmonton)",
                "Trip 2: Business, ran past schedule: it started on a Mon at 16:20:00; that " +
                    "day's hours are 08:00 to 16:30 (America/Edmonton)",
                "Trip 3: Personal: it started on a Sat at 10:00:00; Sat is not a tracked day " +
                    "(America/Edmonton)",
            ),
            line.detail?.lines(),
        )
    }

    @Test
    fun `a pass with nothing to sort writes nothing`() = runTest {
        stored("2026-10-05T10:00", category = TripCategory.BUSINESS)
        stored("2026-10-05T11:00", TripStatus.OPEN)

        catchUp().catchUp("process start")
        runCurrent()

        assertTrue(log.entries.isEmpty())
    }

    // ---- When it cannot be done -------------------------------------------------------------------

    @Test
    fun `with settings that cannot be read the trips stay unsorted and the log says so`() =
        runTest {
            val monday = stored("2026-10-05T10:00")

            catchUp(SettingsStore(UnreadableSettingsFile)).catchUp("process start")
            runCurrent()

            // Not sorted by a schedule that is not Shawn's: the next start tries again.
            assertEquals(monday, row(monday))
            val said = "Sorting earlier trips into Business and Personal failed. They stay unsorted"
            assertEquals(listOf(said), logged(EventCategory.ERROR))
        }

    @Test
    fun `a write that fails ends the pass, and the next pass carries on`() = runTest {
        val first = stored("2026-10-05T10:00")
        val second = stored("2026-10-06T10:00")
        trips.failNextCategoryWrite = IllegalStateException("the disk is full")
        val catchUp = catchUp()

        catchUp.catchUp("process start")
        runCurrent()

        assertEquals(first, row(first))
        assertEquals(1, logged(EventCategory.ERROR).size)

        catchUp.catchUp("process start")
        runCurrent()

        assertEquals(TripCategory.BUSINESS, row(first).category)
        assertEquals(TripCategory.BUSINESS, row(second).category)
    }
}

package com.shawnkowalchuk.milo.feature.tripedit

import com.shawnkowalchuk.milo.core.schedule.DEFAULT_WORK_SCHEDULE
import com.shawnkowalchuk.milo.core.schedule.TripCategory
import com.shawnkowalchuk.milo.core.trip.TripStartCause
import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.settings.SettingsStore
import com.shawnkowalchuk.milo.data.trip.Trip
import com.shawnkowalchuk.milo.data.trip.TripRepository
import com.shawnkowalchuk.milo.platform.trip.FakeEventLogDao
import com.shawnkowalchuk.milo.platform.trip.FakeSettingsFile
import com.shawnkowalchuk.milo.platform.trip.FakeTripDao
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * What the tests of [TripEditing] stand on: the stand-in trips table and event log, behind the
 * repositories the screen uses, and a clock that says Monday 5 October 2026, 14:00 in Edmonton.
 * Each test class gets a fresh one for every test, as JUnit builds the class anew each time.
 */
abstract class TripEditingFixture {
    protected val edmonton: ZoneId = ZoneId.of("America/Edmonton")
    protected val schedule = DEFAULT_WORK_SCHEDULE
    protected val trips = FakeTripDao()
    protected val log = FakeEventLogDao()

    protected fun local(dateTime: String): Long =
        LocalDateTime.parse(dateTime).atZone(edmonton).toInstant().toEpochMilli()

    protected val nowMs = local("2026-10-05T14:00:00")
    protected val editing =
        TripEditing(
            trips = TripRepository(trips),
            settings = SettingsStore(FakeSettingsFile()),
            eventLog = EventLogRepository(log),
            clock = { nowMs },
        )

    /** Stores a trip as MilO recorded it this morning, and returns it. */
    protected fun recorded(status: TripStatus = TripStatus.FINISHED): Trip {
        val open = status == TripStatus.OPEN
        val trip =
            Trip(
                id = trips.rows.size + 1L,
                startedAtMs = local("2026-10-05T08:14:27"),
                endedAtMs = local("2026-10-05T08:39:02").takeUnless { open },
                status = status,
                startedBy = TripStartCause.TRUCK,
                truckSeen = true,
                distanceMetres = if (open) 0.0 else 12_344.7,
                startLatitude = 53.5461.takeUnless { open },
                startLongitude = (-113.4938).takeUnless { open },
                endLatitude = 53.2594.takeUnless { open },
                endLongitude = (-113.5492).takeUnless { open },
                startAddress = "12 Shop Rd, Edmonton".takeUnless { open },
                endAddress = "48 Main St, Leduc".takeUnless { open },
                category = TripCategory.BUSINESS.takeUnless { open },
            )
        trips.rows += trip
        return trip
    }

    protected fun row(id: Long): Trip = trips.rows.single { it.id == id }

    protected fun logged(category: EventCategory): List<String> =
        log.entries.filter { it.category == category }.map { it.message }

    /** Opens the form for [stored], makes [change] in it, and saves. */
    protected suspend fun save(stored: Trip, change: (TripForm) -> TripForm): SaveResult =
        editing.save(stored, change(formFor(stored, edmonton)), schedule, edmonton)

    /** A missed trip, filled in properly. */
    protected val missed =
        TripForm(
            date = LocalDate.of(2026, 10, 5),
            start = LocalTime.of(10, 0),
            end = LocalTime.of(10, 40),
            from = "Shop",
            to = "Site 7",
            kilometres = "23.4",
        )
}

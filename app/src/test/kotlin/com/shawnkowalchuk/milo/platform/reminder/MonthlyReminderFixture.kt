package com.shawnkowalchuk.milo.platform.reminder

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.shawnkowalchuk.milo.core.report.ReportPeriod
import com.shawnkowalchuk.milo.core.schedule.TripCategory
import com.shawnkowalchuk.milo.core.trip.TripStartCause
import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.data.crash.CrashFileStore
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.report.SentReport
import com.shawnkowalchuk.milo.data.report.kind
import com.shawnkowalchuk.milo.data.settings.SettingsStore
import com.shawnkowalchuk.milo.data.trip.Trip
import com.shawnkowalchuk.milo.platform.clock.AskedAlarm
import com.shawnkowalchuk.milo.platform.trip.FakeEventLogDao
import com.shawnkowalchuk.milo.platform.trip.FakeSettingsFile
import java.io.File
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.test.TestScope
import org.junit.Rule
import org.junit.rules.TemporaryFolder

/**
 * What the tests of the monthly reminder share: stand-ins for the alarm, the notification, the
 * settings file, the list of sent reports and the trips, and a reminder built on them. It
 * starts on Tuesday 6 October 2026 at 10:00 in Edmonton, with a September that has two
 * Business trips and no report sent: a reminder is due.
 */
abstract class MonthlyReminderFixture {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    protected val zone: ZoneId = ZoneId.of("America/Edmonton")
    protected val september: YearMonth = YearMonth.of(2026, 9)
    protected val log = FakeEventLogDao()
    protected val settingsFile = FakeSettingsFile()
    protected val settings = SettingsStore(settingsFile)

    protected var nowMs = at("2026-10-06T10:00")

    /** The reminder's clock. A test of a phone whose date is set ahead replaces it. */
    protected var clock: () -> Long = { nowMs }

    /** Whether MilO's clock is the phone's. The same kind of test replaces it. */
    protected var phoneClockAgrees: () -> Boolean = { true }

    /** Whether MilO's clock is on probation. A test of a first start of a boot replaces it. */
    protected var clockOnProbation: () -> Boolean = { false }

    /** Every time the daily alarm was asked for at a time of day, and for when. */
    protected val alarmsAskedFor = mutableListOf<Long>()

    /**
     * Every time it was asked for on the clock that counts from boot, and how far ahead: what
     * the reminder does while MilO's clock and the phone's disagree.
     */
    protected val alarmsAskedAfter = mutableListOf<Long>()

    /** The request Android holds now: the newest of either kind, or none. */
    protected var alarmHeld: AskedAlarm? = null

    /** Time since boot, for a request that counts from it. A test with a phone replaces it. */
    protected var elapsedNow: () -> Long = { 0L }

    /** Every month the notification was posted for. */
    protected val shownFor = mutableListOf<YearMonth>()
    protected var withdrawn = 0

    /** Whether a posted notification can be seen: false stands for notifications switched off. */
    protected var seen = true
    protected var sent = emptyList<SentReport>()
    protected var trips = listOf(businessTrip("2026-09-14T08:10"), businessTrip("2026-09-15T09:00"))

    /** Set to make every read of the trips fail. */
    protected var failReadingTrips: Exception? = null

    protected val crashFiles: CrashFileStore
        get() = CrashFileStore(File(temporaryFolder.root, "crashes"))

    /** A local date and time in Edmonton, as stored time. */
    protected fun at(text: String): Long =
        LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()

    /** A finished trip of twenty minutes and 12.3 km that started at [start]. */
    protected fun businessTrip(
        start: String,
        category: TripCategory? = TripCategory.BUSINESS,
    ): Trip {
        val startedAtMs = at(start)
        return Trip(
            id = startedAtMs,
            startedAtMs = startedAtMs,
            endedAtMs = startedAtMs + 1_200_000,
            status = TripStatus.FINISHED,
            startedBy = TripStartCause.TRUCK,
            truckSeen = true,
            distanceMetres = 12_300.0,
            category = category,
        )
    }

    /** The row "I sent it" stores for a report of the whole of [month]. */
    protected fun sentFor(month: YearMonth): SentReport {
        val period = ReportPeriod.Month(month)
        return SentReport(
            id = 1,
            kind = period.kind,
            firstDay = period.firstDay.toEpochDay(),
            lastDay = period.lastDay.toEpochDay(),
            sentAtMs = at("2026-10-06T12:00"),
            tripCount = 2,
            distanceMetres = 24_600.0,
            revision = 0,
        )
    }

    /** A reminder as a fresh process would build it, its coroutines in the test's scheduler. */
    protected fun TestScope.reminder(file: DataStore<Preferences> = settingsFile) = MonthlyReminder(
        alarm =
            object : ReminderAlarm {
                override fun setFor(atMs: Long) {
                    alarmsAskedFor += atMs
                    alarmHeld = AskedAlarm.AtTimeOfDay(atMs)
                }

                override fun setAfter(delayMs: Long) {
                    alarmsAskedAfter += delayMs
                    alarmHeld = AskedAlarm.CountedFromBoot(elapsedNow() + delayMs)
                }
            },
        show = { month ->
            shownFor += month
            seen
        },
        withdraw = { withdrawn++ },
        settings = SettingsStore(file),
        sentReports = { sent },
        tripsStartedBetween = { fromMs, untilMs ->
            failReadingTrips?.let { throw it }
            trips.filter { it.startedAtMs in fromMs until untilMs }
        },
        eventLog = EventLogRepository(log),
        crashFileStore = crashFiles,
        clock = { clock() },
        phoneClockAgrees = { phoneClockAgrees() },
        clockOnProbation = { clockOnProbation() },
        zone = { zone },
        scope = backgroundScope,
    )

    /** The messages written to the event log under [category], in order. */
    protected fun lines(category: EventCategory = EventCategory.REPORT): List<String> =
        log.entries.filter { it.category == category }.map { it.message }
}

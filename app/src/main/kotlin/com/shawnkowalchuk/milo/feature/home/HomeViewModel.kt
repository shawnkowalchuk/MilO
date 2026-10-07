package com.shawnkowalchuk.milo.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shawnkowalchuk.milo.core.util.localDateOf
import com.shawnkowalchuk.milo.core.util.monthSpan
import com.shawnkowalchuk.milo.data.report.SentReport
import com.shawnkowalchuk.milo.data.settings.MiloSettings
import com.shawnkowalchuk.milo.data.trip.TripRepository
import com.shawnkowalchuk.milo.platform.address.OpenTripStart
import com.shawnkowalchuk.milo.platform.reminder.monthToRemindOf
import com.shawnkowalchuk.milo.platform.system.SetupChecklist
import com.shawnkowalchuk.milo.platform.system.needsAttention
import com.shawnkowalchuk.milo.platform.trip.TripActivity
import com.shawnkowalchuk.milo.platform.trip.TripController
import com.shawnkowalchuk.milo.platform.trip.TripTrigger
import java.io.IOException
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** How long the state stays current after the screen stopped watching it (a rotation). */
private const val KEEP_WATCHING_MS = 5_000L

/** What the event log calls the occasion on which this screen asks for missing addresses. */
private const val CAME_TO_FRONT = "the Home screen came to the front"

/**
 * What Home is handed besides the trip controller, the checklist and the stored trips.
 *
 * @param settings the stored settings. Home reads three things from them and writes none:
 * which truck is paired, whether automatic start is held off, and the monthly reminder's two.
 * @param sentReports every report recorded as sent.
 * @param openTripStart where the trip in progress started, from the address lookup.
 * @param lookUpAddresses asks for the addresses that finished trips still lack, with the reason
 * in words for the event log. A plain function, like the ones for navigation.
 */
class HomeSources(
    val settings: Flow<MiloSettings>,
    val sentReports: Flow<List<SentReport>>,
    val openTripStart: StateFlow<OpenTripStart?>,
    val lookUpAddresses: (reason: String) -> Unit,
)

/**
 * The home screen's link to trip recording and to what its tiles show. It keeps no state of
 * its own: the trip in progress is the controller's [TripActivity], the two buttons are two
 * triggers, and every figure is worked out from storage by a pure function (`HomeFigures.kt`,
 * `TruckState.kt`). The Android Auto screen shows the same trip state and sends the same
 * triggers.
 *
 * It asks the setup checklist itself whether it needs attention, so the home screen's warning
 * and the Setup screen cannot disagree; it counts today's and the month's trips by the rules
 * the Trips screen counts by; and it asks the monthly reminder's rule whether last month's
 * report is waiting.
 *
 * @param clock and [zone] are read again each time the screen comes to the front: the phone can
 * cross midnight, the end of a month or a time zone while MilO is open.
 */
class HomeViewModel(
    private val controller: TripController,
    private val checklist: SetupChecklist,
    private val trips: TripRepository,
    private val sources: HomeSources,
    private val clock: () -> Long,
    private val zone: () -> ZoneId,
) : ViewModel() {
    /** The moment the screen last came to the front, and the day that was. */
    private data class Moment(val nowMs: Long, val zone: ZoneId) {
        val date: LocalDate get() = localDateOf(nowMs, zone)
    }

    private val moment = MutableStateFlow(Moment(clock(), zone()))

    /**
     * The same, passed on only when the day or the time zone has changed: storage is read
     * again for a new day, not every time the screen comes to the front.
     */
    private val day: Flow<Moment> = moment.distinctUntilChangedBy { it.date to it.zone }

    /** False until the phone has been read: no warning is better than one that flashes. */
    private val setupNeedsAttention: Flow<Boolean> =
        checklist.rows.map { rows -> rows != null && needsAttention(rows) }

    /**
     * The settings, or null while they have not been read or cannot be. The file is never reset
     * (see `buildSettingsStore`); the trip controller logs an unreadable one, and Home carries
     * on without what it would have read from it.
     */
    private val stored: StateFlow<MiloSettings?> =
        sources.settings
            .map<MiloSettings, MiloSettings?> { it }
            .catch { failure -> if (failure is IOException) emit(null) else throw failure }
            .whileWatched(null)

    /**
     * Today's finished trips and the month's figures, null until they have been read. They
     * follow storage: a trip that ends, or is changed on the Trips screen, shows by itself.
     * Only the trips that started in this month are read.
     */
    // flatMapLatest is how a Flow switches to a new query when the day changes. It is marked
    // experimental by the coroutines library and has no stable equivalent.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val figures: StateFlow<HomeTrips?> =
        day
            .flatMapLatest { now ->
                val span = monthSpan(YearMonth.from(now.date), now.zone)
                trips
                    .observeTripsStartedBetween(span.fromMs, span.untilMs)
                    .map { stored -> homeTrips(now.date, now.zone, stored) }
            }.whileWatched(null)

    /** The month whose report is waiting to be sent, or null: see [reportWaiting]. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val report: StateFlow<YearMonth?> =
        day
            .flatMapLatest { now ->
                val span = monthSpan(monthToRemindOf(now.nowMs, now.zone), now.zone)
                combine(
                    stored,
                    sources.sentReports,
                    trips.observeTripsStartedBetween(span.fromMs, span.untilMs),
                ) { settings, sent, lastMonthTrips ->
                    reportWaiting(now.nowMs, now.zone, settings, sent, lastMonthTrips)
                }
            }.whileWatched(null)

    /**
     * The time, for how long the trip in progress has been running. While a trip is open it
     * moves on each time another whole minute of the trip has passed, which is as often as the
     * figure on the screen changes.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val nowMs: Flow<Long> =
        controller.activity
            .map { it.trip?.startedAtMs }
            .distinctUntilChanged()
            .flatMapLatest { startedAtMs ->
                if (startedAtMs == null) flowOf(clock()) else minutesOf(startedAtMs)
            }

    /**
     * What the screen draws. Like everything here it is only kept up while the screen is
     * watching: with nobody looking, nothing is read and no clock ticks.
     *
     * The trip in progress never waits for storage: each thing that is read stands in as "not
     * read yet" until it arrives, so the screen is put together from the first moment on. And
     * what was read once is kept, so coming back to Home shows the tiles as they were and not
     * a moment of dashes.
     */
    internal val ui: StateFlow<HomeUi> =
        combine(
            combine(
                moment.map { it.date }.distinctUntilChanged(),
                controller.activity,
                setupNeedsAttention,
                stored,
                ::HomeNow,
            ),
            combine(figures, report, sources.openTripStart, nowMs, ::HomeRead),
            ::homeUi,
        ).whileWatched(
            homeUi(
                HomeNow(moment.value.date, controller.activity.value, false, stored = null),
                HomeRead(figures = null, reportWaiting = null, openTripStart = null, clock()),
            ),
        )

    /**
     * A permission or a setting can change while MilO is in the background; nothing says so.
     * And "today" may have become another day. The missing addresses are asked for as well:
     * Home writes where the last trip went, and the phone may have had no network when it
     * ended.
     */
    fun onCameToFront() {
        checklist.refresh()
        moment.value = Moment(clock(), zone())
        sources.lookUpAddresses(CAME_TO_FRONT)
    }

    fun onStartPressed() {
        controller.onTrigger(TripTrigger.MANUAL_START, "Start button")
    }

    fun onEndPressed() {
        controller.onTrigger(TripTrigger.MANUAL_END, "End button")
    }

    /** Kept up while something is watching, and for a moment after (a rotation). */
    private fun <T> Flow<T>.whileWatched(initial: T): StateFlow<T> =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(KEEP_WATCHING_MS), initial)

    private fun minutesOf(startedAtMs: Long): Flow<Long> = flow {
        while (true) {
            val now = clock()
            emit(now)
            delay(msUntilNextMinute(now - startedAtMs))
        }
    }
}

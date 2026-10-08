package com.shawnkowalchuk.milo.platform.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import com.shawnkowalchuk.milo.core.util.DistanceUnit
import com.shawnkowalchuk.milo.core.util.localDateOf
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.settings.MiloSettings
import com.shawnkowalchuk.milo.data.settings.SettingsStore
import com.shawnkowalchuk.milo.data.trip.Trip
import com.shawnkowalchuk.milo.data.trip.TripRepository
import com.shawnkowalchuk.milo.platform.car.differsOnlyInTripFigures
import com.shawnkowalchuk.milo.platform.trip.TripActivity
import java.io.IOException
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** The running figures of a trip are drawn at most this often; anything else at once. */
private const val FIGURES_MIN_GAP_MS = 15_000L

/** How often the month in force is looked at again: the turn of a month shows within this. */
private const val CALENDAR_LOOK_MS = 60 * 60_000L

/**
 * MilO's home-screen widget (Shawn's request of 2026-10-07): the trip in progress with its
 * kilometres and its running time, a Start or End button, and this month's and this year's
 * business kilometres priced at the rate set in Settings, for reference.
 *
 * It decides nothing about a trip. It draws what the trip controller publishes, and its two
 * buttons send the triggers the app's own buttons send ([WIDGET_START_SOURCE],
 * [WIDGET_END_SOURCE]).
 *
 * **The switch in Settings is for the whole widget** (his choice): switched off, every widget on
 * the home screen is drawn as switched off, and then the widget is withdrawn from the phone
 * (its component disabled), so that it is no longer in the list of widgets. What the home
 * screen then does with one it already shows is the home screen's: Android removes it, or shows
 * that it cannot be loaded.
 *
 * @param activity what the trip controller publishes.
 * @param shownUnit the unit chosen in Settings, as the whole app holds it. The trip's distance
 * follows it, and a change is drawn at once. The dollars do not depend on it.
 * @param clock wall-clock milliseconds.
 * @param zone the phone's time zone, for the month and the year.
 * @param scope the application scope: the widget is followed for the life of the process.
 */
class HomeWidget(
    private val context: Context,
    private val activity: StateFlow<TripActivity>,
    private val trips: TripRepository,
    private val settings: SettingsStore,
    private val shownUnit: StateFlow<DistanceUnit>,
    private val eventLog: EventLogRepository,
    private val clock: () -> Long,
    private val zone: () -> ZoneId,
    private val scope: CoroutineScope,
) {
    private val manager = AppWidgetManager.getInstance(context)
    private val provider = ComponentName(context, HomeWidgetProvider::class.java)

    /** The switch as last applied. Until the settings are read, the widget is drawn. */
    @Volatile
    private var enabled = true

    /**
     * Applies the stored switch, then keeps every widget on the home screen in step with the
     * trip and this year's trips for the life of the process. Called once, at the start of it.
     */
    fun start() {
        scope.launch {
            val stored =
                try {
                    settings.current().homeWidgetEnabled
                } catch (unreadable: IOException) {
                    failed("reading the settings", unreadable)
                    true
                }
            applySwitch(stored)
            follow()
        }
    }

    /**
     * Draws the widgets once, now, from what is stored. For the home screen's own requests,
     * which can come to a process that has only just started.
     *
     * @param done called when it is drawn, whatever happened.
     */
    fun refresh(done: () -> Unit) {
        scope.launch {
            try {
                val span = yearSpanOf(clock(), zone())
                val yearTrips =
                    try {
                        trips.observeTripsStartedBetween(span.fromMs, span.untilMs).first()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        failed("reading this year's trips", failure)
                        null
                    }
                val centsPerKm =
                    try {
                        settings.current().homeWidgetCentsPerKm
                    } catch (unreadable: IOException) {
                        failed("reading the settings", unreadable)
                        null
                    }
                draw(
                    homeWidgetContent(
                        activity.value,
                        yearTrips,
                        centsPerKm,
                        clock(),
                        zone(),
                        locale(),
                        shownUnit.value,
                    ),
                )
            } finally {
                done()
            }
        }
    }

    /**
     * Offers the widget and draws it, or draws every one on the home screen as switched off and
     * withdraws it.
     *
     * Switched off, the widgets are drawn first, with no button: a home screen that keeps
     * showing a widget whose component is gone keeps its last drawing, and the buttons of a
     * drawing of the trip would still reach the trip service.
     */
    fun applySwitch(on: Boolean) {
        enabled = on
        if (!on) {
            val shown = manager.getAppWidgetIds(provider)
            if (shown.isNotEmpty()) manager.updateAppWidget(shown, switchedOffViews(context))
        }
        val state =
            if (on) {
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
        // At every start of the process the switch is applied again: written only if it moved.
        val packages = context.packageManager
        if (packages.getComponentEnabledSetting(provider) != state) {
            packages.setComponentEnabledSetting(provider, state, PackageManager.DONT_KILL_APP)
        }
        if (on) refresh {}
    }

    /**
     * Whether the phone's home screen can be asked to add a widget with one tap. HyperOS's can;
     * some home screens cannot, and then only the way by hand is offered.
     */
    fun homeScreenTakesRequests(): Boolean = manager.isRequestPinAppWidgetSupported

    /** Asks the home screen to add the widget, if it is on. The home screen asks Shawn itself. */
    fun askToAdd() {
        if (enabled && homeScreenTakesRequests()) manager.requestPinAppWidget(provider, null, null)
    }

    private suspend fun follow() {
        var drawn: HomeWidgetContent? = null
        var drawnAtMs = 0L
        // The month is followed too, though nothing is read for it: its turn changes the figures.
        combine(
            activity,
            yearTrips(),
            centsPerKm(),
            shownUnit,
            months(),
        ) { now, year, rate, unit, _ ->
            homeWidgetContent(now, year, rate, clock(), zone(), locale(), unit)
        }.distinctUntilChanged()
            .collectLatest { content ->
                val before = drawn
                if (before != null && before.screen.differsOnlyInTripFigures(content.screen) &&
                    before.dollars == content.dollars
                ) {
                    // collectLatest cancels this wait when newer figures arrive: the gap holds.
                    delay(drawnAtMs + FIGURES_MIN_GAP_MS - SystemClock.elapsedRealtime())
                }
                if (draw(content)) {
                    drawn = content
                    drawnAtMs = SystemClock.elapsedRealtime()
                }
            }
    }

    /** The month in force, looked at every [CALENDAR_LOOK_MS]; a new one is emitted at its turn. */
    private fun months(): Flow<YearMonth> = flow {
        while (true) {
            emit(YearMonth.from(localDateOf(clock(), zone())))
            delay(CALENDAR_LOOK_MS)
        }
    }.distinctUntilChanged()

    // flatMapLatest switches to the new year's query at the turn of a year. It is marked
    // experimental by the coroutines library and has no stable equivalent.
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun yearTrips(): Flow<List<Trip>?> = months()
        .map { yearSpanOf(clock(), zone()) }
        .distinctUntilChanged()
        .flatMapLatest { span ->
            trips.observeTripsStartedBetween(span.fromMs, span.untilMs)
                .map<List<Trip>, List<Trip>?> { it }
                .catch { failure ->
                    failed("reading this year's trips", failure)
                    emit(null)
                }
        }

    /** The rate set in Settings, or null once the settings file has turned out to be unreadable. */
    private fun centsPerKm(): Flow<Int?> = settings.settings
        .map<MiloSettings, Int?> { it.homeWidgetCentsPerKm }
        .distinctUntilChanged()
        .catch { unreadable ->
            if (unreadable !is IOException) throw unreadable
            failed("reading the settings", unreadable)
            emit(null)
        }

    /** Draws [content] on every widget on the home screen, if there is one and it is on. */
    private fun draw(content: HomeWidgetContent): Boolean {
        if (!enabled) return false
        val shown = manager.getAppWidgetIds(provider)
        if (shown.isEmpty()) return false
        manager.updateAppWidget(shown, homeWidgetViews(context, content))
        return true
    }

    private suspend fun failed(doing: String, failure: Throwable) {
        val what = "The home-screen widget failed while $doing"
        eventLog.add(clock(), EventCategory.ERROR, what, failure.stackTraceToString())
    }

    private fun locale(): Locale = context.resources.configuration.locales[0]
}

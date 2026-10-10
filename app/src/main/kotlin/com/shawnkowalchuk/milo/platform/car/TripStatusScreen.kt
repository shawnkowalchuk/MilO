package com.shawnkowalchuk.milo.platform.car

import android.os.SystemClock
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.HostException
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Header
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.text.distanceRes
import com.shawnkowalchuk.milo.core.util.DistanceUnit
import com.shawnkowalchuk.milo.core.util.localDateOf
import com.shawnkowalchuk.milo.core.util.monthSpan
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.settings.MiloSettings
import com.shawnkowalchuk.milo.data.trip.TripRepository
import com.shawnkowalchuk.milo.platform.system.SetupChecklist
import com.shawnkowalchuk.milo.platform.system.needsAttention
import com.shawnkowalchuk.milo.platform.trip.TripController
import java.io.IOException
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * The running figures of a trip are redrawn at most this often. At road speed the kilometres
 * change with nearly every GPS fix, and a number that ticks every five seconds is a distraction;
 * the car may not limit redraws itself (the research, "Adaptive task limits"). The trip
 * notification on the phone keeps the same gap.
 */
private const val FIGURES_MIN_GAP_MS = 15_000L

/** How often the screen looks at the clock: for the trip's whole minutes, and for midnight. */
private const val CLOCK_TICK_MS = 10_000L

/**
 * MilO's one screen on the car's display: whether a trip is being recorded, the trip's
 * kilometres and time, what Home shows of today and the month, and one button that starts or
 * ends a trip by hand.
 *
 * **It fits one screen without scrolling** (Shawn, 2026-10-09, looking at it in Google's
 * desktop head unit: "it needs to be on one screen no scrollable"). Two rows of one line each
 * and the button fit the smallest display a car may have, 800 by 480; the three rows it had
 * until then did not, and neither did two rows with a line that wrapped.
 *
 * It has no ViewModel and decides nothing (ARCHITECTURE section 3). It shows the app-wide
 * [TripController]'s state, and its button sends the triggers the phone's button sends.
 *
 * **Every redraw must be a refresh.** A car allows an app five steps and then closes it. A new
 * template is a refresh, and not a step, only while the header title, the number of rows and
 * every row title stay the same. So there are always exactly two rows with fixed titles, the
 * changing words go in the text under each title, and no other screen is ever pushed.
 *
 * **It is only kept current while the car shows it.** Android Auto creates and destroys the
 * session as it likes, so nothing here is relied on to keep a trip going.
 *
 * Everything in this class runs on the main thread: the library calls it there, and
 * `lifecycleScope` runs there.
 *
 * @param settings the stored settings: the rate the month's dollars are priced at. Read only,
 * and only while the car shows the screen.
 * @param shownUnit the unit chosen in Settings (`ShownUnit`). A change of it is drawn at once.
 */
class TripStatusScreen(
    carContext: CarContext,
    private val controller: TripController,
    private val trips: TripRepository,
    private val checklist: SetupChecklist,
    private val settings: Flow<MiloSettings>,
    private val shownUnit: StateFlow<DistanceUnit>,
    private val clock: () -> Long,
) : Screen(carContext) {
    /** The Business row's figures, or null until storage has answered. Kept between visits. */
    private val business = MutableStateFlow<BusinessFigures?>(null)

    /** The app's accent, for the two rows' icons and the button. */
    private val accent: CarColor =
        carContext.getColor(R.color.milo_car_accent).let { CarColor.createCustom(it, it) }

    /** The newest content. [onGetTemplate] only ever draws this; it reads nothing itself. */
    private var latest: CarScreenContent =
        carScreenContent(
            activity = controller.activity.value,
            business = null,
            setupNeedsAttention = false,
            nowMs = clock(),
            locale = locale(),
            unit = shownUnit.value,
        )

    /** What the car was last handed, and when (time since boot). Null: draw the next at once. */
    private var sent: CarScreenContent? = null
    private var sentAtMs = 0L

    init {
        lifecycleScope.launch {
            // Started means the car is showing the screen. The block is cancelled when it stops
            // and run again when it comes back, so nothing is read or redrawn for a hidden screen.
            repeatOnLifecycle(Lifecycle.State.STARTED) { followWhileShown() }
        }
    }

    override fun onGetTemplate(): Template {
        val content = latest
        sent = content
        sentAtMs = SystemClock.elapsedRealtime()
        val pane =
            Pane
                .Builder()
                .addRow(row(R.string.car_row_this_trip, R.drawable.ic_car_trip, tripLine(content)))
                .addRow(
                    row(
                        R.string.car_row_business,
                        R.drawable.ic_car_business,
                        businessLine(content.business, content.unit),
                    ),
                ).addAction(button(content.action))
                .build()
        val header =
            Header
                .Builder()
                .setTitle(carContext.getString(R.string.app_name))
                .setStartHeaderAction(Action.APP_ICON)
                .build()
        return PaneTemplate.Builder(pane).setHeader(header).build()
    }

    /** One row: an icon and a title that never change, and the changing words under them. */
    private fun row(titleRes: Int, iconRes: Int, words: String): Row {
        val icon =
            CarIcon
                .Builder(IconCompat.createWithResource(carContext, iconRes))
                .setTint(accent)
                .build()
        return Row
            .Builder()
            .setTitle(carContext.getString(titleRes))
            .addText(words)
            .setImage(icon, Row.IMAGE_TYPE_ICON)
            .build()
    }

    /**
     * The button, in the app's accent. The car may paint it in a colour of its own instead:
     * it decides whether a custom colour is readable on its display.
     */
    private fun button(action: CarAction): Action = Action
        .Builder()
        .setTitle(carContext.getString(action.labelRes))
        .setFlags(Action.FLAG_PRIMARY)
        .setBackgroundColor(accent)
        // A plain listener, not a parked-only one: the override has to work while driving. The
        // press does what the button said when it was drawn, even if the trip has ended since.
        .setOnClickListener { controller.onTrigger(action.trigger, action.source) }
        .build()

    /** A figure with its unit, as every screen writes a distance: "12.3 km". */
    private fun distance(figure: String, unit: DistanceUnit): String =
        carContext.getString(distanceRes(unit), figure)

    /** Two parts of a line with the dot between them that every line of the screen uses. */
    private fun joined(first: String, second: String): String =
        carContext.getString(R.string.car_joined, first, second)

    /**
     * The "This trip" row: the status line alone while no trip is open ("Truck parked. A trip
     * starts when it moves"), and the status with the trip's figures while one is ("Recording ·
     * 12.3 km · 23 min").
     */
    private fun tripLine(content: CarScreenContent): String {
        val status = carContext.getString(content.status.textRes)
        val trip = content.trip ?: return status
        val shown = distance(trip.kilometres, content.unit)
        val figures =
            if (trip.hours == 0L) {
                carContext.getString(R.string.car_trip_minutes, shown, trip.minutes)
            } else {
                carContext.getString(
                    R.string.car_trip_hours_minutes,
                    shown,
                    trip.hours,
                    trip.minutes,
                )
            }
        return joined(status, figures)
    }

    /**
     * The "Business" row: "Today 17.9 km · October 412.3 km, $289". Kept to what fits one line
     * of the smallest display with a busy month's figures in it.
     */
    private fun businessLine(business: BusinessFigures?, unit: DistanceUnit): String {
        if (business == null) return carContext.getString(R.string.car_business_unknown)
        val today =
            carContext.getString(R.string.car_business_today, distance(business.today, unit))
        val monthShown = distance(business.month, unit)
        val month =
            if (business.dollars == null) {
                carContext.getString(R.string.car_business_month, business.monthName, monthShown)
            } else {
                carContext.getString(
                    R.string.car_business_month_dollars,
                    business.monthName,
                    monthShown,
                    business.dollars,
                )
            }
        return joined(today, month)
    }

    // ---- Following the trip while the car shows the screen --------------------------------------

    private suspend fun followWhileShown() {
        // The checklist reads the phone only when a screen asks. This is the car screen asking.
        checklist.refresh()
        // What the car last drew may be minutes old, so the first content is drawn at once.
        sent = null
        try {
            coroutineScope {
                launch { followBusiness() }
                followTrip()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            // Every kind of failure, on purpose. An exception that escaped from here would end
            // the process, and the process is the one recording the trip. Closing MilO on the
            // car is better than leaving a screen that no longer follows it.
            val what = "The Android Auto screen failed and was closed"
            controller.note(EventCategory.ERROR, what, failure.stackTraceToString())
            askHost("close itself") { carContext.finishCarApp() }
        }
    }

    private suspend fun followTrip() {
        var statusBefore: CarStatus? = null
        contents().collectLatest { content ->
            latest = content
            // Only a refusal that happens while the screen is shown: one that was already
            // standing when the screen opened is in the Status row, and that is enough.
            val refusedJustNow = statusBefore != null && statusBefore != content.status
            if (content.status.startRefused && refusedJustNow) sayStartRefused(content.status)
            statusBefore = content.status

            if (content == sent) return@collectLatest
            if (sent.differsOnlyInTripFigures(content)) {
                // collectLatest cancels this wait when newer figures arrive and starts again
                // with those, measured from the same last redraw: the gap holds, the newest win.
                delay(sentAtMs + FIGURES_MIN_GAP_MS - SystemClock.elapsedRealtime())
            }
            askHost("redraw") { invalidate() }
        }
    }

    /**
     * The content for every change of the trip, of the Business row and of the setup checklist,
     * and for every tick of the clock. Most of them print the same as the one before, and those are
     * dropped here.
     */
    private fun contents(): Flow<CarScreenContent> {
        // False until the phone has been read: no warning is better than one that flashes.
        val setupOpen = checklist.rows.map { rows -> rows != null && needsAttention(rows) }
        return combine(
            controller.activity,
            business,
            setupOpen,
            shownUnit,
            ticks(),
        ) { trip, figures, open, shownIn, _ ->
            carScreenContent(trip, figures, open, clock(), locale(), shownIn)
        }.distinctUntilChanged()
    }

    /**
     * Keeps [business] in step with storage and the settings, and moves on to the new day at
     * midnight and to the new month at its turn.
     */
    private suspend fun followBusiness() {
        days().collectLatest { (date, zone) ->
            val span = monthSpan(YearMonth.from(date), zone)
            try {
                combine(
                    trips.observeTripsStartedBetween(span.fromMs, span.untilMs),
                    storedSettings(),
                    shownUnit,
                ) { monthTrips, stored, unit ->
                    val input = BusinessInput(date, zone, monthTrips, stored?.homeWidgetCentsPerKm)
                    businessFigures(input, locale(), unit)
                }.collect { business.value = it }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                // Storage failed, whatever it threw. The trip is still followed; the Business
                // row says that its figures are not available.
                business.value = null
                val what = "The Android Auto screen could not read the month's trips"
                controller.note(EventCategory.ERROR, what, failure.stackTraceToString())
            }
        }
    }

    /**
     * The settings, or null if the file cannot be read. It is never reset (`buildSettingsStore`);
     * the trip controller logs an unreadable one, and the row carries on without the dollars.
     */
    private fun storedSettings(): Flow<MiloSettings?> = settings
        .map<MiloSettings, MiloSettings?> { it }
        .catch { failure -> if (failure is IOException) emit(null) else throw failure }

    /** Today's date and the phone's time zone, again whenever either changes. */
    private fun days(): Flow<Pair<LocalDate, ZoneId>> = ticks()
        .map {
            val zone = ZoneId.systemDefault()
            localDateOf(clock(), zone) to zone
        }.distinctUntilChanged()

    private fun ticks(): Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(CLOCK_TICK_MS)
        }
    }

    // ---- Talking to the car ---------------------------------------------------------------------

    /** A short message on the car's display. The Status row keeps saying why. */
    private fun sayStartRefused(status: CarStatus) {
        val what = "Android Auto screen: said that the trip could not start ($status)"
        controller.note(EventCategory.ANDROID_AUTO, what)
        askHost("show a message") {
            CarToast.makeText(carContext, R.string.start_problem_title, CarToast.LENGTH_LONG).show()
        }
    }

    /**
     * A call to the car can fail: the library throws when Android Auto answers with an error.
     * That must not take MilO down while it records a trip, so it is logged and the screen
     * carries on. (A car that has simply gone is already ignored by the library.)
     */
    private fun askHost(toDoWhat: String, call: () -> Unit) {
        try {
            call()
        } catch (failure: RuntimeException) {
            // The library reports a failing host with one of these two and nothing else.
            if (failure !is HostException && failure !is SecurityException) throw failure
            val what = "The Android Auto screen could not $toDoWhat"
            controller.note(EventCategory.ERROR, what, failure.stackTraceToString())
        }
    }

    /** The phone's language, for the decimal separator. The words come from the same context. */
    private fun locale(): Locale = carContext.resources.configuration.locales[0]
}

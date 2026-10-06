package com.shawnkowalchuk.milo.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shawnkowalchuk.milo.core.util.TimeSpan
import com.shawnkowalchuk.milo.core.util.daySpan
import com.shawnkowalchuk.milo.core.util.localDateOf
import com.shawnkowalchuk.milo.data.trip.TodayTrips
import com.shawnkowalchuk.milo.data.trip.TripRepository
import com.shawnkowalchuk.milo.data.trip.todayTrips
import com.shawnkowalchuk.milo.platform.system.SetupChecklist
import com.shawnkowalchuk.milo.platform.system.needsAttention
import com.shawnkowalchuk.milo.platform.trip.TripActivity
import com.shawnkowalchuk.milo.platform.trip.TripController
import com.shawnkowalchuk.milo.platform.trip.TripTrigger
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** How long the state stays current after the screen stopped watching it (a rotation). */
private const val KEEP_WATCHING_MS = 5_000L

/**
 * The home screen's link to trip recording. It keeps no state of its own: what the screen shows
 * is the controller's [TripActivity], and the two buttons are two triggers. The Android Auto
 * screen shows the same state and sends the same triggers.
 *
 * It also says whether the setup checklist needs attention, by asking the checklist itself, so
 * the home screen's warning and the Setup screen cannot disagree; and it reads today's finished
 * trips, counted by the one rule the Trips screen and the Android Auto screen count by.
 *
 * @param clock and [zone] are read again each time the screen comes to the front: the phone can
 * cross midnight or a time zone while MilO is open.
 */
class HomeViewModel(
    private val controller: TripController,
    private val checklist: SetupChecklist,
    private val trips: TripRepository,
    private val clock: () -> Long,
    private val zone: () -> ZoneId,
) : ViewModel() {
    val activity: StateFlow<TripActivity> = controller.activity

    /** False until the phone has been read: no warning is better than one that flashes. */
    val setupNeedsAttention: StateFlow<Boolean> =
        checklist.rows
            .map { rows -> rows != null && needsAttention(rows) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(KEEP_WATCHING_MS), false)

    /** Today as a span of stored time. Only the trips that started in it are read. */
    private val day = MutableStateFlow(dayNow())

    /**
     * Today's finished trips, or null while they are being read. It follows storage: a trip that
     * ends, or is deleted or restored on the Trips screen, shows here by itself.
     */
    // flatMapLatest is how a Flow switches to a new query when the day changes. It is marked
    // experimental by the coroutines library and has no stable equivalent.
    @OptIn(ExperimentalCoroutinesApi::class)
    val today: StateFlow<TodayTrips?> =
        day
            .flatMapLatest { span -> trips.observeTripsStartedBetween(span.fromMs, span.untilMs) }
            .map(::todayTrips)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(KEEP_WATCHING_MS), null)

    /**
     * A permission or a setting can change while MilO is in the background; nothing says so.
     * And "today" may have become another day.
     */
    fun onCameToFront() {
        checklist.refresh()
        day.value = dayNow()
    }

    fun onStartPressed() {
        controller.onTrigger(TripTrigger.MANUAL_START, "Start button")
    }

    fun onEndPressed() {
        controller.onTrigger(TripTrigger.MANUAL_END, "End button")
    }

    private fun dayNow(): TimeSpan {
        val zoneNow = zone()
        return daySpan(localDateOf(clock(), zoneNow), zoneNow)
    }
}

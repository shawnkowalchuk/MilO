package com.shawnkowalchuk.milo.feature.trips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shawnkowalchuk.milo.core.schedule.TripCategory
import com.shawnkowalchuk.milo.core.util.monthOf
import com.shawnkowalchuk.milo.core.util.monthSpan
import com.shawnkowalchuk.milo.data.trip.Trip
import com.shawnkowalchuk.milo.data.trip.TripCorrection
import com.shawnkowalchuk.milo.data.trip.TripRepository
import com.shawnkowalchuk.milo.platform.address.OpenTripStart
import com.shawnkowalchuk.milo.platform.trip.TripActivity
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** How long the state stays current after the screen stopped watching it (a rotation). */
private const val KEEP_WATCHING_MS = 5_000L

// What the event log calls the two occasions on which this screen asks for missing addresses.
private const val CAME_TO_FRONT = "the Trips screen came to the front"
private const val TRIP_COUNTED_AGAIN = "a trip was restored or counted on the Trips screen"

/**
 * What the Trips screen shows.
 *
 * @param month the month on screen.
 * @param zone the time zone the month and its days are worked out in.
 * @param canStepForward false on the current month: there is nothing later to show.
 * @param showLeftOut whether the deleted and the discarded trips are listed.
 * @param changeFailed true after a change to a trip that was not made: a delete, a restore, a
 * "count this trip", or a marking as Business or Personal. The list shows what is stored either
 * way; this only says that the press did nothing.
 * @param summary the month's trips, or null while they are being read.
 */
data class TripsUiState(
    val month: YearMonth,
    val zone: ZoneId,
    val canStepForward: Boolean,
    val showLeftOut: Boolean,
    val changeFailed: Boolean,
    val summary: MonthSummary?,
)

/**
 * The Trips screen's link to the stored trips. It opens on the current month and steps one
 * month at a time. Only the month on screen is read from storage, by its span of time, so a
 * year of trips costs no more than a week of them.
 *
 * @param corrections makes Shawn's changes to a closed trip, and logs them.
 * @param tripActivity the trip controller's state, for the running distance of a trip in
 * progress.
 * @param openTripStart where the trip in progress started, from the address lookup.
 * @param lookUpAddresses asks for the addresses that finished trips still lack, with the reason
 * in words for the event log. Called each time the screen comes to the front, and when a trip
 * becomes a finished one again; a plain function, like the ones for navigation.
 * @param clock and [zone] are read again each time the screen comes to the front: the phone can
 * cross midnight, the end of a month or a time zone while MilO is open.
 */
class TripsViewModel(
    private val trips: TripRepository,
    private val corrections: TripCorrections,
    tripActivity: StateFlow<TripActivity>,
    openTripStart: StateFlow<OpenTripStart?>,
    private val lookUpAddresses: (reason: String) -> Unit,
    private val clock: () -> Long,
    private val zone: () -> ZoneId,
) : ViewModel() {
    /** What Shawn has chosen, and the month and zone it is measured against. */
    private data class Choice(
        val shown: YearMonth,
        val current: YearMonth,
        val zone: ZoneId,
        val showLeftOut: Boolean = false,
        val changeFailed: Boolean = false,
    )

    /** The trips of one month, labelled with the month and zone they were read for. */
    private data class MonthTrips(val month: YearMonth, val zone: ZoneId, val trips: List<Trip>)

    private val choice = MutableStateFlow(openingChoice())

    // flatMapLatest is how a Flow switches to a new query when the month changes. It is marked
    // experimental by the coroutines library and has no stable equivalent.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val monthTrips: Flow<MonthTrips> =
        choice
            .map { it.shown to it.zone }
            .distinctUntilChanged()
            .flatMapLatest { (month, zone) ->
                val span = monthSpan(month, zone)
                trips
                    .observeTripsStartedBetween(span.fromMs, span.untilMs)
                    .map { MonthTrips(month, zone, it) }
            }

    val state: StateFlow<TripsUiState> =
        combine(choice, monthTrips, tripActivity, openTripStart) { chosen, read, activity, start ->
            chosen.toUiState(
                // Right after a step the trips in hand are still the previous month's. They are
                // not shown under the new month's name: the screen says it is reading.
                summary =
                    if (read.month == chosen.shown && read.zone == chosen.zone) {
                        monthSummary(
                            trips = read.trips,
                            zone = chosen.zone,
                            showLeftOut = chosen.showLeftOut,
                            liveTripId = activity.trip?.tripId,
                            liveDistanceMetres = activity.trip?.distanceMetres,
                            liveStart = start,
                        )
                    } else {
                        null
                    },
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(KEEP_WATCHING_MS),
            choice.value.toUiState(summary = null),
        )

    fun onPreviousMonth() {
        choice.update { it.copy(shown = stepMonth(it.shown, months = -1, current = it.current)) }
    }

    fun onNextMonth() {
        choice.update { it.copy(shown = stepMonth(it.shown, months = 1, current = it.current)) }
    }

    /**
     * A trip was saved on the edit screen and starts at [startedAtMs] now: its month is shown,
     * so that the trip is in the list Shawn comes back to, whichever month he left.
     */
    fun onTripSaved(startedAtMs: Long) {
        choice.update { it.copy(shown = monthOfSavedTrip(startedAtMs, it.zone, it.current)) }
    }

    fun onShowLeftOut(show: Boolean) {
        choice.update { it.copy(showLeftOut = show) }
    }

    /**
     * Deletes, restores or counts a trip. The list follows storage, so a change that was made
     * shows by itself. A trip that is finished again (restored, or counted after all) may lack
     * its addresses, so they are asked for at once.
     */
    fun onCorrect(tripId: Long, correction: TripCorrection) {
        viewModelScope.launch {
            val made = corrections.apply(tripId, correction)
            choice.update { it.copy(changeFailed = !made) }
            if (made && correction != TripCorrection.DELETE) lookUpAddresses(TRIP_COUNTED_AGAIN)
        }
    }

    /**
     * Marks a finished trip as Business or as Personal by Shawn's own choice. The row, the
     * day's heading and the month's totals follow storage.
     */
    fun onMark(tripId: Long, category: TripCategory) {
        viewModelScope.launch {
            val made = corrections.mark(tripId, category)
            choice.update { it.copy(changeFailed = !made) }
        }
    }

    /**
     * Works out again which month is the current one; the month on screen stays where it is.
     * Also has the missing addresses looked up: this screen is where they are read, and the
     * phone may have had no network when the trips ended.
     */
    fun onCameToFront() {
        lookUpAddresses(CAME_TO_FRONT)
        val now = openingChoice()
        choice.update {
            it.copy(current = now.current, zone = now.zone, shown = minOf(it.shown, now.current))
        }
    }

    private fun openingChoice(): Choice {
        val zoneNow = zone()
        val current = monthOf(clock(), zoneNow)
        return Choice(shown = current, current = current, zone = zoneNow)
    }

    private fun Choice.toUiState(summary: MonthSummary?) = TripsUiState(
        month = shown,
        zone = zone,
        canStepForward = canStepForward(shown, current),
        showLeftOut = showLeftOut,
        changeFailed = changeFailed,
        summary = summary,
    )
}

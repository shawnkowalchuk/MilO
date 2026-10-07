package com.shawnkowalchuk.milo.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shawnkowalchuk.milo.core.odometer.OdometerFigure
import com.shawnkowalchuk.milo.core.odometer.OdometerReading
import com.shawnkowalchuk.milo.core.odometer.odometerAt
import com.shawnkowalchuk.milo.core.odometer.parseOdometerKm
import com.shawnkowalchuk.milo.core.util.localDateOf
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.settings.MiloSettings
import com.shawnkowalchuk.milo.data.settings.SettingsStore
import com.shawnkowalchuk.milo.data.settings.addOdometerReading
import com.shawnkowalchuk.milo.data.trip.Trip
import com.shawnkowalchuk.milo.data.trip.TripRepository
import com.shawnkowalchuk.milo.data.trip.drivenTrips
import java.io.IOException
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** How long the state stays current after the screen stopped watching it (a rotation). */
private const val KEEP_WATCHING_MS = 5_000L

/**
 * What the odometer's tile shows.
 *
 * @param figure the odometer now, or null before the first reading has been typed in.
 * @param zone the phone's time zone, in which the day of the reading is written.
 * @param couldNotSave true if the last reading could not be stored. Said on the tile.
 */
data class OdometerCardState(
    val figure: OdometerFigure?,
    val zone: ZoneId,
    val couldNotSave: Boolean,
)

/**
 * The tile as the stored readings and trips make it now. Pure, so it is tested without a phone.
 *
 * @param finished every finished trip; the ones that moved the odometer are picked out here.
 */
fun odometerCardState(
    readings: List<OdometerReading>,
    finished: List<Trip>,
    nowMs: Long,
    zone: ZoneId,
    couldNotSave: Boolean,
): OdometerCardState = OdometerCardState(
    figure = odometerAt(nowMs, localDateOf(nowMs, zone), zone, readings, drivenTrips(finished)),
    zone = zone,
    couldNotSave = couldNotSave,
)

/**
 * The Settings screen's tile for the truck's odometer (Shawn's decisions of 2026-10-07): the
 * figure MilO works out from his last reading and the truck trips since, and the way to type a
 * new reading. A reading is never changed or taken back: a new one is added beside it, with its
 * own date.
 *
 * It has a ViewModel of its own, like the daily check's tile: the screen's own one is at its
 * size limit, and this tile reads the trips, which no other tile of the screen does.
 *
 * [state] is null until the settings have been read, and while they cannot be: the screen
 * itself says that, above the tiles.
 *
 * @param clock wall-clock milliseconds: the time of a reading, and the moment the figure is for.
 * @param zone the phone's time zone.
 */
class OdometerViewModel(
    private val settings: SettingsStore,
    trips: TripRepository,
    private val eventLog: EventLogRepository,
    private val clock: () -> Long,
    private val zone: () -> ZoneId,
) : ViewModel() {
    private val couldNotSave = MutableStateFlow(false)

    val state: StateFlow<OdometerCardState?> =
        combine(readings(), trips.observeFinishedTrips(), couldNotSave) {
                stored,
                finished,
                failed,
            ->
            stored?.let { odometerCardState(it, finished, clock(), zone(), failed) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(KEEP_WATCHING_MS), null)

    /**
     * Stores what Shawn typed as the reading on the dashboard now.
     *
     * @return false, and nothing is stored, if it is not a reading; the tile then says so under
     * the field. True once it is on its way to storage.
     */
    fun onSaveReading(typed: String): Boolean {
        val km = parseOdometerKm(typed) ?: return false
        viewModelScope.launch {
            try {
                settings.addOdometerReading(OdometerReading(clock(), km))
                couldNotSave.value = false
            } catch (notStored: IOException) {
                couldNotSave.value = true
                val what = "The Settings screen could not store an odometer reading"
                eventLog.add(clock(), EventCategory.ERROR, what, notStored.stackTraceToString())
            }
        }
        return true
    }

    /** The stored readings, or null once the settings file has turned out to be unreadable. */
    private fun readings(): Flow<List<OdometerReading>?> = settings.settings
        .map<MiloSettings, List<OdometerReading>?> { it.odometerReadings }
        .catch { unreadable -> if (unreadable is IOException) emit(null) else throw unreadable }
}

package com.shawnkowalchuk.milo.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shawnkowalchuk.milo.core.odometer.OdometerFigure
import com.shawnkowalchuk.milo.core.odometer.OdometerReading
import com.shawnkowalchuk.milo.core.odometer.odometerAt
import com.shawnkowalchuk.milo.core.odometer.parseOdometer
import com.shawnkowalchuk.milo.core.util.DistanceUnit
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
 * @param unit the unit chosen in Settings: the figure is in it, and a reading typed now is a
 * reading in it, as a dashboard in that unit shows it.
 */
data class OdometerCardState(
    val figure: OdometerFigure?,
    val zone: ZoneId,
    val couldNotSave: Boolean,
    val unit: DistanceUnit,
)

/**
 * The tile as the stored readings and trips make it now. Pure, so it is tested without a phone.
 *
 * @param finished every finished trip; the ones that moved the odometer are picked out here.
 * @param unit the unit chosen in Settings, which the figure is worked out and shown in.
 */
fun odometerCardState(
    readings: List<OdometerReading>,
    finished: List<Trip>,
    nowMs: Long,
    zone: ZoneId,
    couldNotSave: Boolean,
    unit: DistanceUnit,
): OdometerCardState = OdometerCardState(
    figure =
        odometerAt(
            nowMs,
            localDateOf(nowMs, zone),
            zone,
            readings,
            drivenTrips(finished),
            unit,
        ),
    zone = zone,
    couldNotSave = couldNotSave,
    unit = unit,
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
        combine(stored(), trips.observeFinishedTrips(), couldNotSave) {
                stored,
                finished,
                failed,
            ->
            stored?.let {
                odometerCardState(
                    it.odometerReadings,
                    finished,
                    clock(),
                    zone(),
                    failed,
                    it.distanceUnit,
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(KEEP_WATCHING_MS), null)

    /**
     * Stores what Shawn typed as the reading on the dashboard now.
     *
     * @param unit the unit the tile named when he typed it. The reading is kept as typed with
     * this unit beside it, and is never converted in storage, so it comes back exactly.
     * @return false, and nothing is stored, if it is not a reading; the tile then says so under
     * the field. True once it is on its way to storage.
     */
    fun onSaveReading(typed: String, unit: DistanceUnit): Boolean {
        val value = parseOdometer(typed) ?: return false
        viewModelScope.launch {
            try {
                settings.addOdometerReading(OdometerReading(clock(), value, unit))
                couldNotSave.value = false
            } catch (notStored: IOException) {
                couldNotSave.value = true
                val what = "The Settings screen could not store an odometer reading"
                eventLog.add(clock(), EventCategory.ERROR, what, notStored.stackTraceToString())
            }
        }
        return true
    }

    /**
     * The stored settings, of which the tile reads the readings and the unit together, or null
     * once the settings file has turned out to be unreadable.
     */
    private fun stored(): Flow<MiloSettings?> = settings.settings
        .map<MiloSettings, MiloSettings?> { it }
        .catch { unreadable -> if (unreadable is IOException) emit(null) else throw unreadable }
}

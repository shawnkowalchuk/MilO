package com.shawnkowalchuk.milo.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.odometer.OdometerFigure
import com.shawnkowalchuk.milo.core.odometer.OdometerReading
import com.shawnkowalchuk.milo.core.odometer.parseOdometer
import com.shawnkowalchuk.milo.core.util.DistanceUnit
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.settings.MiloSettings
import com.shawnkowalchuk.milo.data.settings.SettingsStore
import com.shawnkowalchuk.milo.data.settings.StoredVehicle
import com.shawnkowalchuk.milo.data.settings.addOdometerReading
import com.shawnkowalchuk.milo.data.trip.Trip
import com.shawnkowalchuk.milo.data.trip.TripRepository
import com.shawnkowalchuk.milo.data.trip.TripSoFar
import com.shawnkowalchuk.milo.data.trip.VehicleOdometer
import com.shawnkowalchuk.milo.data.trip.odometerNow
import com.shawnkowalchuk.milo.data.trip.vehicleOdometers
import com.shawnkowalchuk.milo.platform.trip.TripActivity
import com.shawnkowalchuk.milo.platform.trip.tripSoFar
import java.io.IOException
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
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
 * @param vehicle the paired vehicle whose odometer it is (since 2026-10-08), or null with none
 * paired; a reading typed on the tile is that vehicle's.
 * @param vehicleName its name, for the tile's heading, where more than one vehicle is paired.
 */
data class OdometerCardState(
    val figure: OdometerFigure?,
    val zone: ZoneId,
    val couldNotSave: Boolean,
    val unit: DistanceUnit,
    val vehicle: String? = null,
    val vehicleName: String? = null,
)

/**
 * The sentence before the first reading. With miles chosen it names the unit: the number typed
 * is stored as miles whatever the dashboard shows, and a reading in the wrong unit would stand
 * on the report for the accountant. In kilometres it is the sentence it always was.
 */
fun odometerFirstReadingRes(unit: DistanceUnit): Int = when (unit) {
    DistanceUnit.KILOMETRES -> R.string.settings_odometer_none
    DistanceUnit.MILES -> R.string.settings_odometer_none_miles
}

/** The label of the field a reading is typed in, on the same terms. */
fun odometerFieldRes(unit: DistanceUnit): Int = when (unit) {
    DistanceUnit.KILOMETRES -> R.string.settings_odometer_field
    DistanceUnit.MILES -> R.string.settings_odometer_field_miles
}

/**
 * The tile as the stored readings and trips make it now. Pure, so it is tested without a phone.
 * The figure is the one Home shows too ([odometerNow], 2026-10-09).
 *
 * @param finished every finished trip; the ones that moved the odometer are picked out there.
 * @param unit the unit chosen in Settings, which the figure is worked out and shown in.
 * @param vehicle the paired vehicle whose odometer it is, or null for every reading and trip
 * (no vehicle paired). [isFirst] says whether it is the first vehicle, whose odometer also
 * takes the readings and trips that name none (`ofVehicle`).
 */
fun odometerCardState(
    readings: List<OdometerReading>,
    finished: List<Trip>,
    nowMs: Long,
    zone: ZoneId,
    couldNotSave: Boolean,
    unit: DistanceUnit,
    vehicle: StoredVehicle? = null,
    isFirst: Boolean = true,
    named: Boolean = false,
): OdometerCardState = cardOf(
    VehicleOdometer(
        vehicle = vehicle,
        named = named,
        figure = odometerNow(readings, finished, nowMs, zone, unit, vehicle?.address, isFirst),
    ),
    zone,
    couldNotSave,
    unit,
)

/**
 * One tile for each paired vehicle (since 2026-10-08), the first first, each named where there
 * are several; one unnamed tile of every reading and trip while none is paired. The odometers
 * are the ones Home shows ([vehicleOdometers]).
 *
 * @param soFar the trip being recorded, which its vehicle's tile counts while it is driven (since
 * 2026-10-09), as Home's does.
 */
fun odometerCardStates(
    settings: MiloSettings,
    finished: List<Trip>,
    nowMs: Long,
    zone: ZoneId,
    couldNotSave: Boolean,
    soFar: TripSoFar? = null,
): List<OdometerCardState> = vehicleOdometers(settings, finished, nowMs, zone, soFar).map {
    cardOf(it, zone, couldNotSave, settings.distanceUnit)
}

private fun cardOf(
    odometer: VehicleOdometer,
    zone: ZoneId,
    couldNotSave: Boolean,
    unit: DistanceUnit,
) = OdometerCardState(
    figure = odometer.figure,
    zone = zone,
    couldNotSave = couldNotSave,
    unit = unit,
    vehicle = odometer.vehicle?.address,
    vehicleName = odometer.shownName,
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
 * @param activity the trip controller's state: the trip being recorded is counted while it is
 * driven (since 2026-10-09), as on Home.
 * @param clock wall-clock milliseconds: the time of a reading, and the moment the figure is for.
 * @param zone the phone's time zone.
 */
class OdometerViewModel(
    private val settings: SettingsStore,
    trips: TripRepository,
    activity: Flow<TripActivity>,
    private val eventLog: EventLogRepository,
    private val clock: () -> Long,
    private val zone: () -> ZoneId,
) : ViewModel() {
    private val couldNotSave = MutableStateFlow(false)

    /** One tile for each paired vehicle ([odometerCardStates]), or null until they are read. */
    val state: StateFlow<List<OdometerCardState>?> =
        combine(
            stored(),
            trips.observeFinishedTrips(),
            activity.map { it.tripSoFar() }.distinctUntilChanged(),
            couldNotSave,
        ) { stored, finished, soFar, failed ->
            stored?.let { odometerCardStates(it, finished, clock(), zone(), failed, soFar) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(KEEP_WATCHING_MS), null)

    /**
     * Stores what Shawn typed as the reading on the dashboard now.
     *
     * @param unit the unit the tile named when he typed it. The reading is kept as typed with
     * this unit beside it, and is never converted in storage, so it comes back exactly.
     * @param vehicle the vehicle whose tile it was typed on, or null with none paired.
     * @return false, and nothing is stored, if it is not a reading; the tile then says so under
     * the field. True once it is on its way to storage.
     */
    fun onSaveReading(typed: String, unit: DistanceUnit, vehicle: String? = null): Boolean {
        val value = parseOdometer(typed) ?: return false
        viewModelScope.launch {
            try {
                settings.addOdometerReading(OdometerReading(clock(), value, unit, vehicle))
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

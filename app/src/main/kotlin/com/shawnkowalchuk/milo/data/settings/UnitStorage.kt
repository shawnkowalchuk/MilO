package com.shawnkowalchuk.milo.data.settings

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.shawnkowalchuk.milo.core.util.DistanceUnit
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

// The unit distances are shown in (Shawn's request of 2026-10-07): one value in the settings
// file. Nothing is stored until he picks one, and kilometres are read until then.
//
// It changes what is shown and nothing that is stored: every trip stays in metres, and so do
// the three settings that are distances or are priced by one (the shortest trip that counts,
// the parked limit's place, the widget's rate per kilometre).

private val DISTANCE_UNIT = stringPreferencesKey("distance_unit")

// What is written to the file. Spelled here and never taken from a constant's own name, so
// that nothing renamed in code can turn his miles back into kilometres.
private const val WORD_KILOMETRES = "km"
private const val WORD_MILES = "mi"

/** The word a unit is stored under, in the settings file and beside a stored figure. */
internal val DistanceUnit.storedWord: String
    get() = when (this) {
        DistanceUnit.KILOMETRES -> WORD_KILOMETRES
        DistanceUnit.MILES -> WORD_MILES
    }

/** The unit [word] stands for, or null for a word MilO does not write. */
internal fun distanceUnitOf(word: String?): DistanceUnit? = when (word) {
    WORD_KILOMETRES -> DistanceUnit.KILOMETRES
    WORD_MILES -> DistanceUnit.MILES
    else -> null
}

/**
 * The unit picked in Settings, or kilometres until one is. A stored word MilO could not have
 * written is read as kilometres too, never guessed at.
 */
internal fun Preferences.readDistanceUnit(): DistanceUnit =
    distanceUnitOf(this[DISTANCE_UNIT]) ?: DistanceUnit.KILOMETRES

/** Stores the unit. Every screen follows the settings, so it shows at once. */
suspend fun SettingsStore.setDistanceUnit(unit: DistanceUnit) {
    dataStore.edit { it[DISTANCE_UNIT] = unit.storedWord }
}

/**
 * The unit in force, kept in memory for the life of the process: the one place every surface
 * reads it from, so that the phone's screens, the Android Auto screen, the widget and the
 * trip's notification change together and cannot name different units.
 *
 * It is kept in memory because two of them cannot wait for the settings file: the notification
 * of a trip is built in the moment the trip starts, and the car's screen draws when the car
 * asks. Until the file has been read, which takes a moment after the process starts, the unit
 * is kilometres, the unit of a phone on which none was picked; whoever drew in that moment
 * draws again when the real one arrives, because this is a flow.
 *
 * @param settings the stored settings, and again each time one changes.
 * @param scope the application scope: the unit is followed for the life of the process.
 * @param onUnreadable called once if the settings file turns out to be unreadable. The unit
 * then stays what it was, kilometres if it was never read.
 */
class ShownUnit(
    private val settings: Flow<MiloSettings>,
    private val scope: CoroutineScope,
    private val onUnreadable: suspend (IOException) -> Unit,
) {
    private val shown = MutableStateFlow(DistanceUnit.KILOMETRES)

    /** The unit in force, and again each time another is chosen. */
    val unit: StateFlow<DistanceUnit> = shown.asStateFlow()

    /** Reads the unit and follows it from then on. Called once, at the start of the process. */
    fun start() {
        scope.launch {
            try {
                settings.map { it.distanceUnit }.collect { shown.value = it }
            } catch (unreadable: IOException) {
                onUnreadable(unreadable)
            }
        }
    }
}

/**
 * Builds [ShownUnit] on the settings file, with an unreadable file written to the event log.
 *
 * @param eventLog asked for only if the file cannot be read, so that building this opens no
 * database.
 * @param clock wall-clock milliseconds, for that line.
 */
fun buildShownUnit(
    settings: SettingsStore,
    eventLog: () -> EventLogRepository,
    scope: CoroutineScope,
    clock: () -> Long = System::currentTimeMillis,
): ShownUnit = ShownUnit(settings.settings, scope) { unreadable ->
    val what = "The unit distances are shown in could not be read. Kilometres are shown"
    eventLog().add(clock(), EventCategory.ERROR, what, unreadable.stackTraceToString())
}

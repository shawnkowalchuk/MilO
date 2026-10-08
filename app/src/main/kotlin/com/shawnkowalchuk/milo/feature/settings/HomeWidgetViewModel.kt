package com.shawnkowalchuk.milo.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shawnkowalchuk.milo.core.allowance.parseCentsPerKm
import com.shawnkowalchuk.milo.core.util.DistanceUnit
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.settings.MiloSettings
import com.shawnkowalchuk.milo.data.settings.SettingsStore
import com.shawnkowalchuk.milo.data.settings.setHomeWidgetCentsPerKm
import com.shawnkowalchuk.milo.data.settings.setHomeWidgetEnabled
import java.io.IOException
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
 * What the widget's tile shows.
 *
 * @param enabled whether the widget is offered (the switch).
 * @param canAskToAdd whether the home screen can be asked to add it with one tap.
 * @param centsPerKm the rate the widget's dollars are priced at, in cents a kilometre.
 * @param couldNotSave true if the last press could not be stored.
 * @param unit the unit distances are shown in. The rate stays a rate per kilometre in either;
 * with miles the tile also says, for reading only, what it comes to per mile.
 */
data class HomeWidgetCardState(
    val enabled: Boolean,
    val canAskToAdd: Boolean,
    val centsPerKm: Int,
    val couldNotSave: Boolean,
    val unit: DistanceUnit,
)

/**
 * The Settings screen's tile for the home-screen widget (2026-10-07): its switch, which is for
 * the whole widget, the rate its dollars are priced at, and a button that asks the home screen
 * to add it. A ViewModel of its own, like the other tiles' that have one: the screen's own is at
 * its size limit.
 *
 * **A press is stored first, then applied** ([applySwitch]): the widget is offered, or every
 * one on the home screen is drawn as switched off and the widget withdrawn.
 *
 * @param homeScreenTakesRequests whether the phone's home screen takes a request to add a
 * widget. Asked of the home screen, not of the widget: the switch shown is the stored one,
 * which is stored before it is applied.
 * @param askToAdd makes that request; the home screen asks Shawn itself.
 * @param clock wall-clock milliseconds, for the event log.
 */
class HomeWidgetViewModel(
    private val settings: SettingsStore,
    private val applySwitch: (Boolean) -> Unit,
    private val homeScreenTakesRequests: () -> Boolean,
    private val askToAdd: () -> Unit,
    private val eventLog: EventLogRepository,
    private val clock: () -> Long,
) : ViewModel() {
    private val couldNotSave = MutableStateFlow(false)

    val state: StateFlow<HomeWidgetCardState?> =
        combine(stored(), couldNotSave) { current, failed ->
            current?.let {
                HomeWidgetCardState(
                    enabled = it.homeWidgetEnabled,
                    canAskToAdd = it.homeWidgetEnabled && homeScreenTakesRequests(),
                    centsPerKm = it.homeWidgetCentsPerKm,
                    couldNotSave = failed,
                    unit = it.distanceUnit,
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(KEEP_WATCHING_MS), null)

    fun onEnabled(enabled: Boolean) {
        viewModelScope.launch {
            try {
                settings.setHomeWidgetEnabled(enabled)
                couldNotSave.value = false
                applySwitch(enabled)
            } catch (notStored: IOException) {
                couldNotSave.value = true
                val what = "The Settings screen could not store the widget's switch"
                eventLog.add(clock(), EventCategory.ERROR, what, notStored.stackTraceToString())
            }
        }
    }

    /**
     * Stores what Shawn typed as the widget's rate, in dollars a kilometre. The widget follows
     * the settings and is drawn again at the new rate by itself.
     *
     * @return false, and nothing is stored, if it is not a rate; the tile then says so under the
     * field. True once it is on its way to storage.
     */
    fun onSaveRate(typed: String): Boolean {
        val centsPerKm = parseCentsPerKm(typed) ?: return false
        viewModelScope.launch {
            try {
                settings.setHomeWidgetCentsPerKm(centsPerKm)
                couldNotSave.value = false
            } catch (notStored: IOException) {
                couldNotSave.value = true
                val what = "The Settings screen could not store the widget's rate"
                eventLog.add(clock(), EventCategory.ERROR, what, notStored.stackTraceToString())
            }
        }
        return true
    }

    fun onAddToHomeScreen() = askToAdd()

    /** The stored settings, or null once the settings file has turned out to be unreadable. */
    private fun stored(): Flow<MiloSettings?> = settings.settings
        .map<MiloSettings, MiloSettings?> { it }
        .catch { unreadable -> if (unreadable is IOException) emit(null) else throw unreadable }
}

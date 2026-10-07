package com.shawnkowalchuk.milo.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shawnkowalchuk.milo.core.allowance.CraRate
import com.shawnkowalchuk.milo.core.allowance.craRateFor
import com.shawnkowalchuk.milo.core.util.localDateOf
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.settings.MiloSettings
import com.shawnkowalchuk.milo.data.settings.SettingsStore
import com.shawnkowalchuk.milo.data.settings.setHomeWidgetEnabled
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
 * What the widget's tile shows.
 *
 * @param enabled whether the widget is offered (the switch).
 * @param canAskToAdd whether the home screen can be asked to add it with one tap.
 * @param rate the CRA rate the widget uses this year, for the tile's explanation.
 * @param couldNotSave true if the last press could not be stored.
 */
data class HomeWidgetCardState(
    val enabled: Boolean,
    val canAskToAdd: Boolean,
    val rate: CraRate,
    val couldNotSave: Boolean,
)

/**
 * The Settings screen's tile for the home-screen widget (2026-10-07): its switch, which is for
 * the whole widget, and a button that asks the home screen to add it. A ViewModel of its own,
 * like the other tiles' that have one: the screen's own is at its size limit.
 *
 * **A press is stored first, then applied** ([applySwitch]): the widget is offered, or every
 * one on the home screen is drawn as switched off and the widget withdrawn.
 *
 * @param homeScreenTakesRequests whether the phone's home screen takes a request to add a
 * widget. Asked of the home screen, not of the widget: the switch shown is the stored one,
 * which is stored before it is applied.
 * @param askToAdd makes that request; the home screen asks Shawn itself.
 * @param clock wall-clock milliseconds, for the year whose rate the tile names.
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
        combine(stored(), couldNotSave) { enabled, failed ->
            enabled?.let {
                HomeWidgetCardState(
                    enabled = it,
                    canAskToAdd = it && homeScreenTakesRequests(),
                    rate = craRateFor(localDateOf(clock(), ZoneId.systemDefault()).year),
                    couldNotSave = failed,
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

    fun onAddToHomeScreen() = askToAdd()

    /** The stored switch, or null once the settings file has turned out to be unreadable. */
    private fun stored(): Flow<Boolean?> = settings.settings
        .map<MiloSettings, Boolean?> { it.homeWidgetEnabled }
        .catch { unreadable -> if (unreadable is IOException) emit(null) else throw unreadable }
}

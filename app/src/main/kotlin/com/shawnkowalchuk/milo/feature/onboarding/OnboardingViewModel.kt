package com.shawnkowalchuk.milo.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.settings.FirstRunStage
import com.shawnkowalchuk.milo.data.settings.MiloSettings
import com.shawnkowalchuk.milo.data.settings.SettingsStore
import com.shawnkowalchuk.milo.data.settings.setFirstRunStage
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

/** How long the stage stays current after the app stopped watching it (a rotation). */
private const val KEEP_WATCHING_MS = 5_000L

/**
 * How far the first start has got (Shawn's request of 2026-10-08), for the app: the page that
 * says what MilO does while the stage is [FirstRunStage.INTRO], and Setup's Done button while it
 * is [FirstRunStage.SETUP].
 *
 * **A stage reached here counts at once,** whether or not it could be stored: a settings file
 * that refuses the write must not keep anyone on the first page. The failure goes to the event
 * log, and the first start then shows again at the next process start.
 *
 * **A settings file that cannot be read counts as [FirstRunStage.DONE],** for the same reason:
 * the app opens as it always did, and the Settings screen says that the file cannot be read.
 *
 * @param clock wall-clock milliseconds, for the event log.
 */
class OnboardingViewModel(
    private val settings: SettingsStore,
    private val eventLog: EventLogRepository,
    private val clock: () -> Long,
) : ViewModel() {
    /** The furthest stage reached in this process, while it is on its way to storage. */
    private val reached = MutableStateFlow<FirstRunStage?>(null)

    /** The stage, or null until the settings have been read. */
    val stage: StateFlow<FirstRunStage?> =
        combine(stored(), reached) { stored, here -> maxOf(stored, here ?: stored) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(KEEP_WATCHING_MS), null)

    /** OK on the first page: Setup is next, with its Done button. */
    fun onOk() = reach(FirstRunStage.SETUP)

    /** Done on Setup: the first start is over. */
    fun onSetupDone() = reach(FirstRunStage.DONE)

    private fun reach(stage: FirstRunStage) {
        reached.value = maxOf(stage, reached.value ?: stage)
        viewModelScope.launch {
            try {
                settings.setFirstRunStage(stage)
            } catch (notStored: IOException) {
                val what = "MilO could not store how far the first start has got ($stage)"
                eventLog.add(clock(), EventCategory.ERROR, what, notStored.stackTraceToString())
            }
        }
    }

    private fun stored(): Flow<FirstRunStage> = settings.settings
        .map(MiloSettings::firstRunStage)
        .catch { unreadable ->
            if (unreadable !is IOException) throw unreadable
            val what = "MilO could not read how far the first start has got; it is not shown"
            eventLog.add(clock(), EventCategory.ERROR, what, unreadable.stackTraceToString())
            emit(FirstRunStage.DONE)
        }
}

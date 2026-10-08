package com.shawnkowalchuk.milo.feature.whatsnew

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.settings.FirstRunStage
import com.shawnkowalchuk.milo.data.settings.MiloSettings
import com.shawnkowalchuk.milo.data.settings.SettingsStore
import com.shawnkowalchuk.milo.data.settings.setWhatsNewSeenVersion
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

/** How long the answer stays current after the app stopped watching it (a rotation). */
private const val KEEP_WATCHING_MS = 5_000L

/**
 * Whether the What's new screen is to open by itself, once after an update, as GopherForms
 * shows its "What's new" after a new version (2026-10-08).
 *
 * It is due when the first start is over and this phone has not been shown the list of the
 * version installed. **A fresh install never sees it:** the first start's page says what MilO
 * does, and its OK stores the version as seen ([onFirstStart]). A phone that had MilO before
 * this screen existed, and had gone through the first start, sees it once.
 *
 * **Once shown here it counts at once,** stored or not, as the first start's stages do: a
 * settings file that refuses the write must not open the screen over and over. A settings file
 * that cannot be read counts as seen.
 *
 * @param installedVersion the versionName on this phone.
 * @param clock wall-clock milliseconds, for the event log.
 */
class WhatsNewNoticeViewModel(
    private val settings: SettingsStore,
    private val installedVersion: String,
    private val eventLog: EventLogRepository,
    private val clock: () -> Long,
) : ViewModel() {
    /** True once this process has shown the screen, or stored that it need not. */
    private val settledHere = MutableStateFlow(false)

    /** True while the screen should open. The app opens it and calls [onShown]. */
    val due: StateFlow<Boolean> =
        combine(storedDue(), settledHere) { stored, settled -> stored && !settled }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(KEEP_WATCHING_MS), false)

    /** The screen was opened: it is not due again until the next version. */
    fun onShown() = settle()

    /** OK on the first start's page: this version's list is not shown to a fresh install. */
    fun onFirstStart() = settle()

    private fun settle() {
        settledHere.value = true
        viewModelScope.launch {
            try {
                settings.setWhatsNewSeenVersion(installedVersion)
            } catch (notStored: IOException) {
                val what = "MilO could not store that What's new was shown for $installedVersion"
                eventLog.add(clock(), EventCategory.ERROR, what, notStored.stackTraceToString())
            }
        }
    }

    private fun storedDue(): Flow<Boolean> = settings.settings
        .map { stored -> isDue(stored, installedVersion) }
        // The first start's ViewModel writes the line about a settings file that cannot be read.
        .catch { unreadable ->
            if (unreadable !is IOException) throw unreadable
            emit(false)
        }
}

/** Whether [settings] say that the list of [installedVersion] is still to be shown. */
internal fun isDue(settings: MiloSettings, installedVersion: String): Boolean =
    settings.firstRunStage == FirstRunStage.DONE &&
        settings.whatsNewSeenVersion != installedVersion

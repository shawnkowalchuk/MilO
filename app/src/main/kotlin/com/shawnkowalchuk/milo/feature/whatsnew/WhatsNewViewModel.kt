package com.shawnkowalchuk.milo.feature.whatsnew

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shawnkowalchuk.milo.data.changelog.Changelog
import com.shawnkowalchuk.milo.data.changelog.ChangelogUnreadableException
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.platform.system.InstalledVersion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The What's new screen's state (Shawn's request of 2026-10-08): the list of versions and their
 * changes that is built into the app, read once when the screen opens.
 *
 * @param changelog reads that list (`data/changelog/ChangelogSource`).
 * @param installed the version on this phone, whose heading says so.
 * @param clock wall-clock milliseconds, for the event log.
 */
class WhatsNewViewModel(
    private val changelog: suspend () -> Changelog,
    private val installed: InstalledVersion,
    private val eventLog: EventLogRepository,
    private val clock: () -> Long,
) : ViewModel() {
    private val shown = MutableStateFlow<WhatsNewUiState>(WhatsNewUiState.Reading)

    val state: StateFlow<WhatsNewUiState> = shown.asStateFlow()

    init {
        viewModelScope.launch {
            shown.value =
                try {
                    whatsNewState(changelog(), installed)
                } catch (unreadable: ChangelogUnreadableException) {
                    // Only a build made with a broken file gets here: CI checks the file.
                    val what = "The list of changes of MilO ${installed.name} cannot be read"
                    val detail = unreadable.stackTraceToString()
                    eventLog.add(clock(), EventCategory.ERROR, what, detail)
                    WhatsNewUiState.Unreadable
                }
        }
    }
}

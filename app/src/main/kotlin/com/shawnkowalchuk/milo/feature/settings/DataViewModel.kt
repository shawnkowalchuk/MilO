package com.shawnkowalchuk.milo.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shawnkowalchuk.milo.data.settings.LastExport
import com.shawnkowalchuk.milo.data.settings.MiloSettings
import com.shawnkowalchuk.milo.data.transfer.exportFileName
import com.shawnkowalchuk.milo.platform.transfer.DataTransfer
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** How long the state stays current after the screen stopped watching it (a rotation). */
private const val KEEP_WATCHING_MS = 5_000L

/**
 * The Settings screen's tile for backup, export and import: its link to [DataTransfer], which
 * does the work and keeps where it stands.
 *
 * It keeps nothing of an export or an import itself. Both run in the application's own scope,
 * so leaving the screen stops neither, and coming back shows where things are. All this class
 * holds is the switch for the GPS points, which is not stored: it is on whenever the screen is
 * opened, so that an export is whole unless Shawn says otherwise that time.
 *
 * It has a ViewModel of its own, beside the one of the other tiles, because that one is at
 * its size limit and this tile shares nothing with them but the screen.
 *
 * [state] is null until the tile's first real state is known. The tile says what the last
 * export or import came to and moves the screen to it when it changes; a made-up first state
 * would make whatever is still standing there from before look like news, each time Settings
 * is opened.
 *
 * @param settings the stored settings, for the date of the last export.
 * @param tripInProgress whether a trip is being recorded, and again each time that changes.
 * @param clock wall-clock milliseconds, and [zone] the phone's time zone: for the name an
 * export is offered under.
 */
class DataViewModel(
    private val transfer: DataTransfer,
    settings: Flow<MiloSettings>,
    tripInProgress: Flow<Boolean>,
    private val clock: () -> Long,
    private val zone: () -> ZoneId,
) : ViewModel() {
    /** What the tile shows that neither the settings nor [DataTransfer] hold. */
    private data class Passing(
        val includePoints: Boolean = true,
        val noFilePicker: Boolean = false,
        val safetyCopiesAtMs: List<Long> = emptyList(),
    )

    private val passing = MutableStateFlow(Passing())

    /**
     * The date of the last export, or null if there was none. An unreadable settings file is
     * said by the screen itself, above the tiles; here it only means "no date to show".
     */
    private val lastExport: Flow<LastExport?> =
        settings
            .map<MiloSettings, LastExport?> { it.lastExport }
            .catch { unreadable -> if (unreadable is IOException) emit(null) else throw unreadable }

    val state: StateFlow<DataCardState?> =
        combine(lastExport, transfer.status, tripInProgress, passing) { last, status, trip, now ->
            dataCardState(
                lastExport = last,
                status = status,
                tripInProgress = trip,
                includePoints = now.includePoints,
                safetyCopiesAtMs = now.safetyCopiesAtMs,
                noFilePicker = now.noFilePicker,
                zone = zone(),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(KEEP_WATCHING_MS), null)

    init {
        // A safety copy is written by an import and by nothing else, so the folder is looked
        // at when the screen opens and again each time an export or import has come to an end.
        viewModelScope.launch {
            transfer.status.collect { status ->
                if (status.working == null) {
                    val kept = transfer.safetyCopies().map { it.writtenAtMs }
                    passing.update { it.copy(safetyCopiesAtMs = kept) }
                }
            }
        }
    }

    /** The name an export is offered under in the file picker. */
    fun exportFileName(): String = exportFileName(clock(), zone())

    fun onIncludePoints(include: Boolean) {
        passing.update { it.copy(includePoints = include) }
    }

    /** Shawn has made the file to export to. */
    fun onExportTo(uri: String) {
        passing.update { it.copy(noFilePicker = false) }
        transfer.exportTo(uri, passing.value.includePoints)
    }

    /** Shawn has picked the file to import. */
    fun onImportFrom(uri: String) {
        passing.update { it.copy(noFilePicker = false) }
        transfer.offerImport(uri)
    }

    /** "Put that data back": the safety copy kept at that time is offered like a picked file. */
    fun onRestoreSafetyCopy(writtenAtMs: Long) {
        passing.update { it.copy(noFilePicker = false) }
        transfer.offerSafetyCopy(writtenAtMs)
    }

    fun onConfirmImport() = transfer.confirmImport()

    fun onDeclineImport() = transfer.declineImport()

    /** The phone could not show a file picker at all. */
    fun onNoFilePicker() {
        passing.update { it.copy(noFilePicker = true) }
    }
}

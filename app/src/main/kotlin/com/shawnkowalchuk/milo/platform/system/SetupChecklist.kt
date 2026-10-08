package com.shawnkowalchuk.milo.platform.system

import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.settings.ConfirmedStep
import com.shawnkowalchuk.milo.data.settings.MiloSettings
import com.shawnkowalchuk.milo.data.settings.SettingsStore
import com.shawnkowalchuk.milo.platform.bluetooth.PairingStatus
import com.shawnkowalchuk.milo.platform.bluetooth.sameAddress
import com.shawnkowalchuk.milo.platform.bluetooth.trucks
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** How long the rows stay current after the last screen stopped watching them. */
private const val KEEP_WATCHING_MS = 5_000L

/**
 * The setup checklist as the screens see it: every row with its current state.
 *
 * Three things feed it. What the phone reports is read when a screen asks ([refresh]), because
 * Android sends no event when a permission or a setting changes. Shawn's confirmations come from
 * the settings store, and the truck's pairing from `TruckPairing`; both of those report their
 * own changes. The Setup screen shows the rows and the home screen only asks [needsAttention]
 * of them, so the two cannot disagree.
 *
 * @param readFacts reads the phone. Called on [scope], never on the thread that asked.
 * @param pairing the last check of the truck's pairing, or null before the first one.
 */
class SetupChecklist(
    private val readFacts: () -> SetupFacts,
    private val settings: SettingsStore,
    pairing: StateFlow<PairingStatus?>,
    private val eventLog: EventLogRepository,
    private val clock: () -> Long,
    private val scope: CoroutineScope,
) {
    private val facts = MutableStateFlow<SetupFacts?>(null)

    /** The rows, or null until the phone has been read for the first time. */
    val rows: StateFlow<List<SetupRow>?> =
        combine(facts, readableSettings(), pairing) { read, stored, checked ->
            read?.let {
                setupRows(
                    facts = it,
                    pairing = checked?.state,
                    // Since 2026-10-08: of several vehicles, the one Android no longer watches
                    // for, if one is; otherwise the first, as it always was.
                    truckName =
                        checked?.missing?.firstOrNull()?.let { lost ->
                            stored.trucks().firstOrNull { sameAddress(it.address, lost) }?.name
                        } ?: stored.truckName,
                    confirmedAtMs = stored.confirmedAtMs,
                    drivingAlertEnabled = stored.drivingAlertEnabled,
                )
            }
        }.stateIn(scope, SharingStarted.WhileSubscribed(KEEP_WATCHING_MS), initialValue = null)

    /**
     * Reads the phone again. Called every time a screen that shows the rows comes to the front.
     *
     * The home screen calls this too, so a reading that fails must not take the app down with
     * it: the Start trip button is on that screen. Android's settings calls refuse with
     * unchecked exceptions of several kinds. Whichever it is, it is logged, and the rows stay as
     * they were.
     */
    fun refresh() {
        scope.launch {
            try {
                facts.value = readFacts()
            } catch (refused: RuntimeException) {
                val what = "The setup checklist could not read the phone's settings"
                eventLog.add(clock(), EventCategory.ERROR, what, refused.stackTraceToString())
            }
        }
    }

    /** Stores that Shawn has set [step] himself, with the time, or takes that back. */
    fun setConfirmed(step: ConfirmedStep, confirmed: Boolean) {
        scope.launch {
            try {
                settings.setConfirmedAtMs(step, if (confirmed) clock() else null)
            } catch (unwritable: IOException) {
                val what = "The setup checklist could not store the confirmation of $step"
                eventLog.add(clock(), EventCategory.ERROR, what, unwritable.stackTraceToString())
            }
        }
    }

    /**
     * The settings, or the defaults if the file cannot be read. The file is never reset (see
     * `buildSettingsStore`), so an unreadable one is logged and the checklist carries on without
     * the confirmations: the rows then ask for them again, which is the safe direction.
     */
    private fun readableSettings(): Flow<MiloSettings> = settings.settings.catch { failure ->
        if (failure !is IOException) throw failure
        val what = "The setup checklist could not read the settings"
        eventLog.add(clock(), EventCategory.ERROR, what, failure.stackTraceToString())
        emit(MiloSettings())
    }
}

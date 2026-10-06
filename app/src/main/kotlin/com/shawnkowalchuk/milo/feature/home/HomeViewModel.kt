package com.shawnkowalchuk.milo.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shawnkowalchuk.milo.platform.system.SetupChecklist
import com.shawnkowalchuk.milo.platform.system.needsAttention
import com.shawnkowalchuk.milo.platform.trip.TripActivity
import com.shawnkowalchuk.milo.platform.trip.TripController
import com.shawnkowalchuk.milo.platform.trip.TripTrigger
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** How long the warning stays current after the screen stopped watching it (a rotation). */
private const val KEEP_WATCHING_MS = 5_000L

/**
 * The home screen's link to trip recording. It keeps no state of its own: what the screen shows
 * is the controller's [TripActivity], and the two buttons are two triggers. The Android Auto
 * screen will show the same state and send the same triggers.
 *
 * It also says whether the setup checklist needs attention, by asking the checklist itself, so
 * the home screen's warning and the Setup screen cannot disagree.
 */
class HomeViewModel(
    private val controller: TripController,
    private val checklist: SetupChecklist,
) : ViewModel() {
    val activity: StateFlow<TripActivity> = controller.activity

    /** False until the phone has been read: no warning is better than one that flashes. */
    val setupNeedsAttention: StateFlow<Boolean> =
        checklist.rows
            .map { rows -> rows != null && needsAttention(rows) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(KEEP_WATCHING_MS), false)

    /** A permission or a setting can change while MilO is in the background; nothing says so. */
    fun onCameToFront() {
        checklist.refresh()
    }

    fun onStartPressed() {
        controller.onTrigger(TripTrigger.MANUAL_START, "Start button")
    }

    fun onEndPressed() {
        controller.onTrigger(TripTrigger.MANUAL_END, "End button")
    }
}

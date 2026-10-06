package com.shawnkowalchuk.milo.feature.home

import androidx.lifecycle.ViewModel
import com.shawnkowalchuk.milo.platform.trip.TripActivity
import com.shawnkowalchuk.milo.platform.trip.TripController
import com.shawnkowalchuk.milo.platform.trip.TripTrigger
import kotlinx.coroutines.flow.StateFlow

/**
 * The home screen's link to trip recording. It keeps no state of its own: what the screen shows
 * is the controller's [TripActivity], and the two buttons are two triggers. The Android Auto
 * screen will show the same state and send the same triggers.
 */
class HomeViewModel(private val controller: TripController) : ViewModel() {
    val activity: StateFlow<TripActivity> = controller.activity

    fun onStartPressed() {
        controller.onTrigger(TripTrigger.MANUAL_START, "Start button")
    }

    fun onEndPressed() {
        controller.onTrigger(TripTrigger.MANUAL_END, "End button")
    }
}

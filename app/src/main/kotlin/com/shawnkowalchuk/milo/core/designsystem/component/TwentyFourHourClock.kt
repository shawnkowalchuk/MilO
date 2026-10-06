package com.shawnkowalchuk.milo.core.designsystem.component

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

/**
 * Whether the phone is set to write times with 24 hours: "Use 24-hour format" in Android's date
 * and time settings. Until that switch has been touched, Android answers by the phone's
 * language.
 *
 * Every time of day a phone screen prints is written by it, and the time picker's dial is drawn
 * by it. MilO's times then read like the phone's own clock, and a time is never entered on a
 * dial with AM and PM by someone whose phone has neither.
 *
 * A screen calls this once and hands the answer down, like the language. The switch is changed
 * outside MilO, and Android tells a screen nothing when it is, so the answer is read again each
 * time the screen comes back.
 */
@Composable
fun rememberTwentyFourHourClock(): Boolean {
    val context = LocalContext.current
    var twentyFourHour by remember(context) { mutableStateOf(DateFormat.is24HourFormat(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        twentyFourHour = DateFormat.is24HourFormat(context)
    }
    return twentyFourHour
}

package com.shawnkowalchuk.milo.feature.greeting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import java.io.IOException
import kotlinx.coroutines.launch

/**
 * What the greeting tells the Log (ADR-007). The mascot is two pictures the app carries, read
 * by Android. If one of them cannot be read, that start goes without its greeting, MilO works
 * as ever, and the Log says why: once for that start, and nothing is tried again until MilO is
 * next opened.
 *
 * @param clock wall-clock milliseconds, for the event log.
 */
class GreetingViewModel(private val eventLog: EventLogRepository, private val clock: () -> Long) :
    ViewModel() {
    /** A picture of the mascot could not be read, and nothing was shown. */
    fun onUnreadable(why: IOException) {
        viewModelScope.launch {
            val what = "MilO did not greet: a picture of its mascot could not be read"
            eventLog.add(clock(), EventCategory.ERROR, what, why.stackTraceToString())
        }
    }
}

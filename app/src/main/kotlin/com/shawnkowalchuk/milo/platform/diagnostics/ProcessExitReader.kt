package com.shawnkowalchuk.milo.platform.diagnostics

import android.app.ActivityManager
import android.content.Context

/** `getHistoricalProcessExitReasons` reads "0" as "any process id of this app". */
private const val ANY_PID = 0

/** ...and "0" as "every record the system still holds" (it keeps a small number per app). */
private const val ALL_RECORDS = 0

/**
 * Reads the system's record of why earlier MilO processes ended.
 *
 * The system keeps these records itself, so they exist even when the process was killed with no
 * chance to run any code: a swipe from recents, a Xiaomi cleaner, a force stop.
 */
class ProcessExitReader(private val context: Context) {
    /** The exits recorded after [afterMs], oldest first. */
    fun exitsAfter(afterMs: Long): List<ProcessExit> {
        val activityManager = context.getSystemService(ActivityManager::class.java)
        return activityManager
            // A null package name means "this app".
            .getHistoricalProcessExitReasons(null, ANY_PID, ALL_RECORDS)
            .filter { it.timestamp > afterMs }
            .sortedBy { it.timestamp }
            .map {
                ProcessExit(
                    atMs = it.timestamp,
                    pid = it.pid,
                    reason = it.reason,
                    description = it.description,
                    importance = it.importance,
                    status = it.status,
                )
            }
    }
}

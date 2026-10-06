package com.shawnkowalchuk.milo.platform.diagnostics

import android.app.ApplicationExitInfo

/**
 * Why an earlier MilO process ended, as Android recorded it. This is the evidence for the
 * reliability requirement: when a trip was missed because the app was not running, this says
 * who stopped it.
 *
 * A plain value with no Android object inside, so turning it into event-log text is tested on
 * the JVM.
 *
 * @param atMs wall-clock time the process ended.
 * @param reason one of the `ApplicationExitInfo.REASON_` numbers.
 * @param description the system's free text, or null. On this phone it is the important part:
 * Xiaomi's cleaners all report the catch-all reason OTHER and put their own name here, such as
 * `SwipeUpClean` or `AutoIdleKill` (docs/research/2026-10-03-miui-background-limits.md).
 * @param importance how visible the process was when it ended (an `ActivityManager` importance
 * number). It tells a kill during a trip (foreground service) from one while idle.
 * @param status the exit code or signal number, when the reason has one.
 */
data class ProcessExit(
    val atMs: Long,
    val pid: Int,
    val reason: Int,
    val description: String?,
    val importance: Int,
    val status: Int,
) {
    /** The one-line message for the event log, with the description when there is one. */
    fun message(): String {
        val what = description?.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
        return "Process $pid ended: ${reasonName(reason)}$what"
    }

    /** The numbers behind the message, for the event log's detail field. */
    fun detail(): String = "reason=$reason importance=$importance status=$status"
}

/**
 * The name Android's documentation uses for an exit reason. A number this code does not know
 * (a newer Android version may add one) is shown as the number, never dropped.
 */
fun reasonName(reason: Int): String = when (reason) {
    ApplicationExitInfo.REASON_UNKNOWN -> "UNKNOWN"
    ApplicationExitInfo.REASON_EXIT_SELF -> "EXIT_SELF"
    ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED"
    ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY"
    ApplicationExitInfo.REASON_CRASH -> "CRASH"
    ApplicationExitInfo.REASON_CRASH_NATIVE -> "CRASH_NATIVE"
    ApplicationExitInfo.REASON_ANR -> "ANR"
    ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "INITIALIZATION_FAILURE"
    ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "PERMISSION_CHANGE"
    ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "EXCESSIVE_RESOURCE_USAGE"
    ApplicationExitInfo.REASON_USER_REQUESTED -> "USER_REQUESTED"
    ApplicationExitInfo.REASON_USER_STOPPED -> "USER_STOPPED"
    ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "DEPENDENCY_DIED"
    ApplicationExitInfo.REASON_OTHER -> "OTHER"
    ApplicationExitInfo.REASON_FREEZER -> "FREEZER"
    ApplicationExitInfo.REASON_PACKAGE_STATE_CHANGE -> "PACKAGE_STATE_CHANGE"
    ApplicationExitInfo.REASON_PACKAGE_UPDATED -> "PACKAGE_UPDATED"
    else -> "reason $reason"
}

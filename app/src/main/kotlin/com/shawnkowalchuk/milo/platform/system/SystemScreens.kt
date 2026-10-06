package com.shawnkowalchuk.milo.platform.system

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Process
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Opens the screens of the phone's settings that MilO's buttons lead to.
 *
 * Every way of opening a screen is tried inside a try/catch, in the order [screenIntents] gives
 * them, down to Android's own page for MilO. A screen that this build of HyperOS does not have
 * is therefore never a crash. It is written to the event log, because nobody would otherwise
 * learn that a button landed on the fallback page.
 *
 * Whether a screen exists is never asked beforehand: from Android 11 an app cannot see another
 * app's screens without declaring that app, so the question would be answered "no" on exactly
 * the phones that have the screen (the research, finding 30).
 */
class SystemScreens(
    context: Context,
    private val eventLog: EventLogRepository,
    private val clock: () -> Long,
    private val scope: CoroutineScope,
) {
    // The application context: a settings screen is opened as a task of its own, so that Back
    // returns to MilO whichever screen of MilO asked for it.
    private val appContext = context.applicationContext

    /** Opens [screen], or the nearest screen the phone has. Returns false if nothing opened. */
    fun open(screen: SystemScreen): Boolean {
        val app =
            AppIdentity(
                packageName = appContext.packageName,
                uid = Process.myUid(),
                label = appContext.applicationInfo.loadLabel(appContext.packageManager).toString(),
            )
        val refusals = mutableListOf<String>()
        for (candidate in screenIntents(screen, app)) {
            val refusal = tryToOpen(candidate)
            if (refusal == null) {
                if (refusals.isNotEmpty()) {
                    log(
                        "Opened another screen in place of $screen",
                        refusals,
                    )
                }
                return true
            }
            refusals += refusal
        }
        log("Could not open $screen, nor the app's own settings page", refusals)
        return false
    }

    /** Returns null if the screen opened, or why it did not. */
    private fun tryToOpen(candidate: ScreenIntent): String? = try {
        appContext.startActivity(candidate.toIntent())
        null
    } catch (missing: ActivityNotFoundException) {
        "$candidate: $missing"
    } catch (refused: SecurityException) {
        // The screen exists but is not open to other apps on this build.
        "$candidate: $refused"
    }

    // Lint offers String.toUri() from core-ktx, a library MilO does not declare (it is only on
    // the classpath through other libraries). One call does not justify adding it.
    @SuppressLint("UseKtx")
    private fun ScreenIntent.toIntent(): Intent {
        val spec = this
        return Intent().apply {
            spec.action?.let { action = it }
            spec.component?.let { (packageName, className) ->
                component = ComponentName(packageName, className)
            }
            spec.targetPackage?.let { setPackage(it) }
            spec.data?.let { data = Uri.parse(it) }
            spec.stringExtras.forEach { (key, value) -> putExtra(key, value) }
            spec.intExtras.forEach { (key, value) -> putExtra(key, value) }
            // Required to start a screen from anything that is not itself a screen.
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun log(what: String, refusals: List<String>) {
        scope.launch {
            eventLog.add(
                clock(),
                EventCategory.ERROR,
                what,
                refusals.joinToString(separator = "\n"),
            )
        }
    }
}

package com.shawnkowalchuk.milo.platform.trip

import android.app.ForegroundServiceStartNotAllowedException
import android.content.Context
import com.shawnkowalchuk.milo.platform.system.TripPreflight

/**
 * Starts the trip service the way ADR-002 sets out ("The service", steps 1 to 3): preflight
 * first, then `startForegroundService`, and on any failure the "could not start this trip"
 * notification. The caller ([TripController]) writes the outcome to the event log.
 *
 * It runs on the thread of whatever trigger called it, straight away. Nothing here is deferred:
 * the allowance Android gives a Bluetooth or boot broadcast for starting a foreground service
 * lasts about 20 seconds.
 */
class TripServiceStarter(
    private val context: Context,
    private val preflight: TripPreflight,
    private val notifications: TripNotifications,
) : RecordingStarter {
    override fun start(request: StartRequest): StartFailure? {
        val problems = preflight.problems()
        if (problems.isNotEmpty()) return failed(StartFailure(problems))
        val intent = tripServiceIntent(context, request.trigger, request.source, request.atMs)
        return try {
            context.startForegroundService(intent)
            null
        } catch (notAllowed: ForegroundServiceStartNotAllowedException) {
            // MilO is in the background and holds none of the exemptions that allow a start
            // from there (ADR-002, context point 2).
            failed(StartFailure(emptyList(), notAllowed.toString()))
        } catch (denied: SecurityException) {
            failed(StartFailure(emptyList(), denied.toString()))
        }
    }

    private fun failed(failure: StartFailure): StartFailure {
        val shown = notifications.showCouldNotStart()
        if (shown) return failure
        // The warning went unseen. Say so in the event log, which is where the caller sends
        // the detail.
        val unseen = "Notifications are off for MilO, so the warning was not shown."
        return failure.copy(detail = listOfNotNull(failure.detail, unseen).joinToString("\n"))
    }
}

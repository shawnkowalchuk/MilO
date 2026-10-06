package com.shawnkowalchuk.milo.platform.driving

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.DetectedActivity
import com.google.android.gms.tasks.Task
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/** How long Play services is given to answer a request. It normally answers at once. */
private const val ANSWER_LIMIT_MS = 10_000L

private const val MILLIS_PER_SECOND = 1000L

/**
 * The phone's driving detection, as far as the driving alert needs it. An interface so the alert
 * can be tested without Android; the real one is [PlayServicesDrivingDetection].
 */
interface DrivingDetection {
    /** Whether the Physical activity permission is granted right now. */
    fun permissionGranted(): Boolean

    /**
     * Asks to be told when the phone enters or leaves a vehicle. Asking again replaces the
     * earlier request, so it is safe to repeat.
     *
     * @return null if the phone agreed, otherwise what went wrong, in words for the event log.
     */
    suspend fun watch(): String?

    /** Asks not to be told any more. Returns as [watch] does. */
    suspend fun stopWatching(): String?
}

/**
 * Driving detection by Google Play services' Activity Recognition Transition API, which is part
 * of the location library MilO already uses for GPS fixes
 * (docs/research/2026-10-03-location-and-car.md, Part D).
 *
 * - **Only "in a vehicle" is asked for,** entering and leaving. The phone does not say which
 *   vehicle, which is why its reports never start a trip.
 * - **The reports arrive through a `PendingIntent`** aimed at [DrivingReceiver] by class name.
 *   That is the only way Play services delivers them, and it is what lets a report start MilO's
 *   process when it is not running. The intent has to be mutable, because Play services writes
 *   the report into it, and Android 14 accepts a mutable one only if it names its target.
 * - **A request does not outlive a reboot or an update of MilO** as far as Google documents it,
 *   so it is made again at every process start ([DrivingAlert.arm]).
 */
class PlayServicesDrivingDetection(context: Context) : DrivingDetection {
    private val appContext = context.applicationContext
    private val client = ActivityRecognition.getClient(appContext)

    override fun permissionGranted(): Boolean =
        appContext.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) ==
            PackageManager.PERMISSION_GRANTED

    override suspend fun watch(): String? = try {
        client.requestActivityTransitionUpdates(VEHICLE_TRANSITIONS, reportTo()).problem()
    } catch (denied: SecurityException) {
        // The permission was there a moment ago: it was taken away in between.
        denied.toString()
    }

    override suspend fun stopWatching(): String? = try {
        client.removeActivityTransitionUpdates(reportTo()).problem()
    } catch (denied: SecurityException) {
        denied.toString()
    }

    /**
     * Where the reports go. The same intent every time, so that a new request replaces the old
     * one and a request to stop names the one that was made.
     */
    private fun reportTo(): PendingIntent = PendingIntent.getBroadcast(
        appContext,
        0,
        Intent(appContext, DrivingReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
    )

    private companion object {
        val VEHICLE_TRANSITIONS =
            ActivityTransitionRequest(
                listOf(
                    vehicle(ActivityTransition.ACTIVITY_TRANSITION_ENTER),
                    vehicle(ActivityTransition.ACTIVITY_TRANSITION_EXIT),
                ),
            )

        fun vehicle(transition: Int): ActivityTransition = ActivityTransition
            .Builder()
            .setActivityType(DetectedActivity.IN_VEHICLE)
            .setActivityTransition(transition)
            .build()
    }
}

/**
 * Waits for Play services' answer.
 *
 * @return null if the request was carried out, otherwise why not. An answer that does not come
 * is a failure too: the caller holds a lock while it waits.
 */
private suspend fun Task<Void>.problem(): String? {
    val answered =
        withTimeoutOrNull(ANSWER_LIMIT_MS) {
            suspendCancellableCoroutine<Answer> { waiting ->
                // Play services calls exactly one of the three, on the main thread.
                addOnSuccessListener { if (waiting.isActive) waiting.resume(Answer(null)) }
                addOnFailureListener { if (waiting.isActive) waiting.resume(Answer("$it")) }
                addOnCanceledListener {
                    if (waiting.isActive) {
                        waiting.resume(
                            Answer("Play services dropped the request"),
                        )
                    }
                }
            }
        }
    return if (answered == null) {
        "Play services did not answer within ${ANSWER_LIMIT_MS / MILLIS_PER_SECOND} s"
    } else {
        answered.problem
    }
}

/** Wrapped, so that "carried out" (no problem) can be told from "no answer in time". */
private class Answer(val problem: String?)

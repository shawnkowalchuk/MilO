package com.shawnkowalchuk.milo.platform.trip

import android.content.Context
import android.content.Intent

// How a trigger rides in the intent that starts the trip service, and how the service reads it
// back once it is in the foreground (see TripController, "The service comes first").

private const val EXTRA_TRIGGER = "com.shawnkowalchuk.milo.extra.TRIGGER"
private const val EXTRA_SOURCE = "com.shawnkowalchuk.milo.extra.SOURCE"
private const val EXTRA_AT_MS = "com.shawnkowalchuk.milo.extra.AT_MS"

/**
 * The intent that starts [TripService] for [trigger].
 *
 * @param atMs when the trigger fired, or null to use the moment the intent arrives (for an
 * intent built in advance, such as a notification's).
 */
internal fun tripServiceIntent(
    context: Context,
    trigger: TripTrigger,
    source: String,
    atMs: Long?,
): Intent = Intent(context, TripService::class.java)
    .putExtra(EXTRA_TRIGGER, trigger.name)
    .putExtra(EXTRA_SOURCE, source)
    .apply { if (atMs != null) putExtra(EXTRA_AT_MS, atMs) }

/** Reads [tripServiceIntent] back. An intent MilO did not build is treated as a reconcile. */
internal fun startRequestFrom(intent: Intent): StartRequest {
    val trigger = TripTrigger.entries.firstOrNull {
        it.name == intent.getStringExtra(EXTRA_TRIGGER)
    }
    return StartRequest(
        trigger = trigger ?: TripTrigger.RECONCILE,
        source = intent.getStringExtra(EXTRA_SOURCE) ?: "an intent without a source",
        atMs = intent.getLongExtra(EXTRA_AT_MS, System.currentTimeMillis()),
    )
}

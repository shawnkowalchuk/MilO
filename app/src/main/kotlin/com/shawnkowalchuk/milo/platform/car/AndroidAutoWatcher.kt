package com.shawnkowalchuk.milo.platform.car

import android.content.Context
import androidx.car.app.connection.CarConnection
import androidx.lifecycle.Observer

/**
 * Watches whether Android Auto is connected, through the Car App Library's `CarConnection`.
 *
 * `CarConnection` asks the Android Auto app, and it only reports while something in this process
 * is observing it (docs/research/2026-10-03-location-and-car.md). So it can hold a trip open but
 * never start one: the trip service starts a watcher when recording begins and stops it when
 * the trip ends.
 *
 * A second watcher, started with the process and never stopped, belongs to [AndroidAutoLog]. It
 * writes Android Auto's changes to the event log while no trip is being recorded, and tells the
 * trip rules nothing.
 *
 * Only "projection" counts as connected: the phone is driving a car's screen. The "native" value
 * is for an app that runs on the car itself, which MilO never does. No answer, or an answer that
 * cannot be read, is treated as not connected.
 *
 * Every function here must be called on the main thread: the library requires it.
 *
 * @param onChange called on the main thread with whether Android Auto is connected and the raw
 * value it reported, for the event log. It is called for changes only, plus once after [start].
 */
class AndroidAutoWatcher(
    context: Context,
    private val onChange: (connected: Boolean, rawType: Int?) -> Unit,
) {
    private val connectionType = CarConnection(context.applicationContext).type
    private var lastReported: Int? = null
    private var reportedOnce = false
    private var watching = false

    private val observer =
        Observer<Int?> { type ->
            // The library re-delivers its last value to every new observer. Only a value that
            // differs from the last one handed on is news.
            if (!reportedOnce || type != lastReported) {
                reportedOnce = true
                lastReported = type
                onChange(type == CarConnection.CONNECTION_TYPE_PROJECTION, type)
            }
        }

    fun start() {
        if (watching) return
        watching = true
        connectionType.observeForever(observer)
    }

    fun stop() {
        if (!watching) return
        watching = false
        connectionType.removeObserver(observer)
        reportedOnce = false
        lastReported = null
    }

    /**
     * Makes the library ask the Android Auto app again. It asks only when it gains its first
     * observer, or when Android Auto announces a change; if that announcement is ever missed,
     * the value stays at "connected" and holds the trip open. Taking the observer away and
     * putting it back is the only way to force a fresh question. The trip service does this
     * about once a minute for as long as Android Auto is believed connected.
     */
    fun refresh() {
        if (!watching) return
        connectionType.removeObserver(observer)
        connectionType.observeForever(observer)
    }
}

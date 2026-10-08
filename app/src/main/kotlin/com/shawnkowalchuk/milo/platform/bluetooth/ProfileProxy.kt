package com.shawnkowalchuk.milo.platform.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothProfile
import android.content.Context
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/**
 * How long a reading waits for Android to hand a proxy over. It normally takes a fraction of a
 * second. The limit keeps one reading short enough for a trigger that started the process: such
 * a trigger has about 20 seconds in which the trip service may be started.
 */
private const val PROXY_WAIT_MS = 3_000L

/** What one Bluetooth profile says about the truck. */
internal enum class ProfileAnswer { TRUCK_CONNECTED, TRUCK_NOT_CONNECTED, NO_ANSWER }

/**
 * What one Bluetooth profile says about the paired vehicles.
 *
 * @param vehicle with [ProfileAnswer.TRUCK_CONNECTED], the address of the vehicle it lists:
 * the first of them in the order they were asked about, if it lists several.
 */
internal data class ProfileReply(val answer: ProfileAnswer, val vehicle: String? = null)

/**
 * One Bluetooth profile (hands-free or audio) and the question "is the truck connected on it?".
 *
 * The profile's list of connected devices can only be read through a proxy, which Android hands
 * over asynchronously, on the main thread. A new proxy is fetched for every reading and handed
 * back straight after it. That costs a bind to the Bluetooth app each time, which is cheap next
 * to what a kept proxy can do: after Bluetooth restarts, a proxy that has lost its service
 * answers with an empty list, not an error, and an empty list reads as "the truck is not
 * connected". During a trip, two such readings end the trip.
 */
internal class ProfileProxy(
    private val context: Context,
    private val adapter: BluetoothAdapter,
    private val profile: Int,
) {
    /** Whether one of the devices with [addresses] is among those connected on this profile. */
    suspend fun answerFor(addresses: List<String>): ProfileReply = try {
        val connected = connectedAddresses()
        val found =
            connected?.let { listed ->
                addresses.firstOrNull { a -> listed.any { sameAddress(it, a) } }
            }
        when {
            connected == null -> ProfileReply(answerWithoutProxy())
            found != null -> ProfileReply(ProfileAnswer.TRUCK_CONNECTED, found)
            else -> ProfileReply(ProfileAnswer.TRUCK_NOT_CONNECTED)
        }
    } catch (denied: SecurityException) {
        // The caller checked the Bluetooth permission a moment ago: it was taken away in
        // between. Without it there is no answer.
        ProfileReply(ProfileAnswer.NO_ANSWER)
    }

    /**
     * No proxy arrived. The adapter keeps a summary of each profile: if it says that no device
     * at all is connected on this one, the truck is not. Any other summary cannot say which
     * device is meant, so there is no answer.
     */
    private fun answerWithoutProxy(): ProfileAnswer = try {
        val summary = adapter.getProfileConnectionState(profile)
        if (summary == BluetoothAdapter.STATE_DISCONNECTED) {
            ProfileAnswer.TRUCK_NOT_CONNECTED
        } else {
            ProfileAnswer.NO_ANSWER
        }
    } catch (denied: SecurityException) {
        // As above: the permission went missing after the caller checked it.
        ProfileAnswer.NO_ANSWER
    }

    /** The addresses of the devices connected on this profile, or null if no proxy arrived. */
    private suspend fun connectedAddresses(): List<String>? {
        val arrived = CompletableDeferred<BluetoothProfile>()
        val listener =
            object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                    // False means the reading has given up waiting: nobody will use this proxy,
                    // so it is handed straight back.
                    if (!arrived.complete(proxy)) adapter.closeProfileProxy(profile, proxy)
                }

                // Nothing to do: the proxy is never kept beyond one reading.
                override fun onServiceDisconnected(profile: Int) = Unit
            }
        // False means this phone does not offer the profile at all.
        if (!adapter.getProfileProxy(context, listener, profile)) return null
        val proxy =
            withTimeoutOrNull(PROXY_WAIT_MS) { arrived.await() }
                ?: run {
                    // Given up. Cancelling makes the listener hand a late proxy back. If the
                    // proxy arrived in the instant before this line, the cancel does nothing
                    // and the proxy is here after all.
                    arrived.cancel()
                    if (arrived.isCancelled) return null
                    arrived.await()
                }
        return try {
            proxy.connectedDevices.map { it.address }
        } finally {
            adapter.closeProfileProxy(profile, proxy)
        }
    }
}

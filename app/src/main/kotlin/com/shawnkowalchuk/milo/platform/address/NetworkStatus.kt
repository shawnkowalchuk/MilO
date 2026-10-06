package com.shawnkowalchuk.milo.platform.address

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * Says whether the phone has a working internet connection right now.
 *
 * The geocoder needs one, and without this check a trip that ended in a place with no signal
 * would use up its lookup attempts on failures that say nothing about the place. MilO itself
 * never opens a connection: it has no INTERNET permission, and the lookup is carried out by the
 * phone's own geocoder service.
 */
class NetworkStatus(context: Context) {
    private val appContext = context.applicationContext

    /**
     * True only for a network that Android has checked and found to reach the internet. A Wi-Fi
     * network that is waiting behind a sign-in page does not count. Android also reports no
     * network while it is holding MilO's own background data back (Doze, a data saver), which
     * errs on the safe side: the lookup then waits for the next occasion.
     */
    fun isOnline(): Boolean {
        val connectivity =
            appContext.getSystemService(ConnectivityManager::class.java) ?: return false
        val capabilities =
            connectivity.getNetworkCapabilities(connectivity.activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}

package com.shawnkowalchuk.milo.platform.bluetooth

import android.app.Activity
import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.CompanionDeviceManager
import android.companion.ObservingDevicePresenceRequest
import android.content.Context
import android.content.IntentSender
import android.content.pm.PackageManager
import android.os.Build

private const val COMPANION_FEATURE = PackageManager.FEATURE_COMPANION_DEVICE_SETUP

/**
 * One companion device association MilO holds, as Android lists it.
 *
 * @param address the device's address in Android's own spelling, which is small letters on some
 * versions. Compare it with [sameAddress], and hand it back to Android only as Android gave it.
 * @param id the number Android gave the association.
 */
internal data class Association(val address: String, val id: Int)

/** What Android answers while an association is being made. */
internal sealed interface AssociationStep {
    /** Android wants Shawn's consent. An Activity has to launch this to show its dialog. */
    data class ConsentNeeded(val intentSender: IntentSender) : AssociationStep

    /** The association exists. */
    data object Created : AssociationStep

    data class Failed(val why: String) : AssociationStep
}

/**
 * Everything MilO asks of Android's `CompanionDeviceManager`, with the differences between
 * Android versions kept on the far side. An interface so that [TruckPairing] can be tested
 * without a phone.
 */
internal interface CompanionLink {
    /** False on a phone that has no companion device support at all. */
    val supported: Boolean

    /** The associations MilO holds right now. */
    fun associations(): List<Association>

    /**
     * Asks Android to associate the paired device with [address]. The steps are reported on the
     * main thread.
     *
     * @param activity the screen that asks. Android shows its consent dialog on top of it.
     */
    fun associate(activity: Activity, address: String, onStep: (AssociationStep) -> Unit)

    /**
     * Asks Android to bind MilO's companion service whenever the device connects. Asking again
     * while it is already observed changes nothing.
     */
    fun startObserving(association: Association)

    fun remove(association: Association)
}

/** [CompanionLink] on the phone. */
internal class SystemCompanionLink(context: Context) : CompanionLink {
    /**
     * Null without the feature. The Android documentation requires the feature to be checked
     * before the manager is used, and whether HyperOS reports it is not known until tried.
     */
    private val manager: CompanionDeviceManager? =
        if (context.packageManager.hasSystemFeature(COMPANION_FEATURE)) {
            context.getSystemService(CompanionDeviceManager::class.java)
        } else {
            null
        }

    override val supported: Boolean get() = manager != null

    override fun associations(): List<Association> {
        val manager = manager ?: return emptyList()
        return manager.myAssociations.mapNotNull(::associationOf)
    }

    /**
     * The request is ADR-002's: an address filter, single-device mode and no device profile.
     * That combination is the only one for which Android looks in its list of paired devices,
     * so the truck is found at once, without a scan and even while it is switched off.
     *
     * The request goes through the Activity's own manager, not the one this class keeps.
     * Android 12 could make this request from no other manager. Android 14 can, and the call
     * is left as it was: it is the one that paired the truck on the phone (2026-10-06).
     */
    override fun associate(activity: Activity, address: String, onStep: (AssociationStep) -> Unit) {
        check(supported) { "This phone has no companion device support" }
        val manager = activity.getSystemService(CompanionDeviceManager::class.java)
        val filter = BluetoothDeviceFilter.Builder().setAddress(address).build()
        val request =
            AssociationRequest.Builder().addDeviceFilter(filter).setSingleDevice(true).build()
        val callback =
            object : CompanionDeviceManager.Callback() {
                override fun onAssociationPending(intentSender: IntentSender) {
                    onStep(AssociationStep.ConsentNeeded(intentSender))
                }

                override fun onAssociationCreated(associationInfo: AssociationInfo) {
                    onStep(AssociationStep.Created)
                }

                // Android 16 added a second onFailure, with an error code. It calls both.
                override fun onFailure(error: CharSequence?) {
                    onStep(AssociationStep.Failed(error?.toString() ?: "Android gave no reason"))
                }
            }
        // A null handler: the callback runs on the main thread.
        manager.associate(request, callback, null)
    }

    override fun startObserving(association: Association) {
        val manager = checkNotNull(manager) { "This phone has no companion device support" }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            val request =
                ObservingDevicePresenceRequest.Builder().setAssociationId(association.id).build()
            manager.startObservingDevicePresence(request)
        } else {
            // Up to Android 15 presence is observed by address; the request form arrived with
            // Android 16. The argument is the address: given the id, the call fails.
            @Suppress("DEPRECATION")
            manager.startObservingDevicePresence(association.address)
        }
    }

    override fun remove(association: Association) {
        val manager = manager ?: return
        manager.disassociate(association.id)
    }

    private fun associationOf(info: AssociationInfo): Association? {
        // An association without an address is one an app manages itself; MilO makes none.
        val address = info.deviceMacAddress ?: return null
        return Association(address.toString(), info.id)
    }
}

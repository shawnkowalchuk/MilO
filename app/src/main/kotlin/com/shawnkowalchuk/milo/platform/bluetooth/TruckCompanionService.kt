package com.shawnkowalchuk.milo.platform.bluetooth

import android.companion.AssociationInfo
import android.companion.CompanionDeviceService
import android.companion.DevicePresenceEvent
import android.os.Build
import com.shawnkowalchuk.milo.app.MiloApplication
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.platform.trip.TripTrigger

/**
 * The service Android binds when the truck connects and unbinds after it has gone (ADR-002, the
 * companion service). Android starts MilO's process to bind it, so it is the trigger that
 * reaches a dead process on stock Android. Whether HyperOS lets it is for the phone to show.
 *
 * It is exported, because Android's own companion device manager binds it from outside MilO,
 * and the manifest lets nothing else bind it (`BIND_COMPANION_DEVICE_SERVICE`).
 *
 * Android has had three shapes of callback, and newer versions also send the older ones. Each
 * shape below acts only on the versions for which it is the newest, so one event reaches the
 * controller once:
 * - Android 12: an address.
 * - Android 13 to 15: the association.
 * - Android 16 and later: a presence event that says what was seen.
 *
 * A callback is a hint, like every trigger. Android queues these callbacks at the front of the
 * main thread's queue, so two that arrive together can run in reverse order.
 */
class TruckCompanionService : CompanionDeviceService() {
    private val container get() = (application as MiloApplication).container

    /**
     * After MilO's process dies with the truck connected, Android binds this service again
     * about ten seconds later and does not repeat "appeared". Being created is then the only
     * sign MilO gets, so it asks the controller to read the truck and to look at the trip the
     * last process left open.
     */
    override fun onCreate() {
        super.onCreate()
        container.tripController.onTrigger(TripTrigger.RECONCILE, "companion service created")
    }

    @Deprecated("Android 12's callback. Later versions call the ones below.")
    override fun onDeviceAppeared(address: String) {
        report(CompanionSignal.APPEARED, address, associationId = null)
    }

    @Deprecated("Android 12's callback. Later versions call the ones below.")
    override fun onDeviceDisappeared(address: String) {
        report(CompanionSignal.DISAPPEARED, address, associationId = null)
    }

    // The framework's own version of these two passes the address on to the callbacks above.
    // That is not wanted, so super is not called.

    @Deprecated("The callback of Android 13 to 15. Android 16 calls onDevicePresenceEvent too.")
    override fun onDeviceAppeared(associationInfo: AssociationInfo) {
        reportAssociation(CompanionSignal.APPEARED, associationInfo)
    }

    @Deprecated("The callback of Android 13 to 15. Android 16 calls onDevicePresenceEvent too.")
    override fun onDeviceDisappeared(associationInfo: AssociationInfo) {
        reportAssociation(CompanionSignal.DISAPPEARED, associationInfo)
    }

    override fun onDevicePresenceEvent(event: DevicePresenceEvent) {
        // Some Android 15 builds already send this event beside the older callback.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) return
        val signal = companionSignal(event.event)
        if (signal == null) {
            val what = "companion service: presence event ${event.event} is not a Bluetooth " +
                "connection. Ignored"
            container.tripController.note(EventCategory.TRIGGER, what)
            return
        }
        report(signal, address = null, associationId = event.associationId)
    }

    private fun reportAssociation(signal: CompanionSignal, association: AssociationInfo) {
        // Acted on only where this shape is the newest: Android 13 to 15.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA
        ) {
            return
        }
        report(signal, association.deviceMacAddress?.toString(), association.id)
    }

    private fun report(signal: CompanionSignal, address: String?, associationId: Int?) {
        val controller = container.tripController
        val lookup = container.pairedTruck.now()
        when (val decision = decideCompanion(signal, address, associationId, lookup)) {
            is SignalDecision.Ignore -> controller.note(EventCategory.TRIGGER, decision.why)
            is SignalDecision.Fire -> controller.onTrigger(decision.trigger, decision.source)
        }
    }
}

package com.shawnkowalchuk.milo.platform.bluetooth

import android.app.Activity
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.settings.SettingsStore
import com.shawnkowalchuk.milo.platform.trip.FakeEventLogDao
import com.shawnkowalchuk.milo.platform.trip.FakeSettingsFile
import kotlinx.coroutines.CoroutineScope

/** The truck's Bluetooth address in the tests of this package. */
internal const val TRUCK_ADDRESS = "AA:BB:CC:DD:EE:FF"
internal const val OLD_TRUCK_ADDRESS = "11:22:33:44:55:66"

internal val TRUCK_DEVICE = PairedDevice(TRUCK_ADDRESS, "Work truck")

/**
 * Everything pairing touches, held in memory: the settings, the event log, Android's companion
 * device manager and the phone's list of paired devices. The test plays Android's part.
 */
internal class PairingWorld {
    val settings = SettingsStore(FakeSettingsFile())
    val log = FakeEventLogDao()
    val link = FakeCompanionLink()

    /** What the phone's Bluetooth settings list. The truck is paired there, as it has to be. */
    var paired = PairedDeviceList(listOf(TRUCK_DEVICE), problem = null)

    /** How often the trip controller was asked to look at a newly stored truck. */
    var truckChanges = 0

    fun pairing(scope: CoroutineScope) = TruckPairing(
        settings = settings,
        link = link,
        listPaired = { paired },
        eventLog = EventLogRepository(log),
        onTruckChanged = { truckChanges++ },
        clock = { 0L },
        scope = scope,
    )

    fun logged(): List<String> =
        log.entries.filter { it.category == EventCategory.PAIRING }.map { it.message }
}

/** Android's companion device manager, held in memory. */
internal class FakeCompanionLink : CompanionLink {
    override var supported = true

    /** The associations Android holds for MilO. */
    val held = mutableListOf<Association>()

    /** Every call that asked Android to watch for a device. */
    val observed = mutableListOf<Association>()

    /** Set to make every request to Android fail, as it does without a permission. */
    var refuseWith: RuntimeException? = null

    var askedFor: String? = null
    private var onStep: ((AssociationStep) -> Unit)? = null

    override fun associations(): List<Association> = held.toList()

    override fun associate(activity: Activity, address: String, onStep: (AssociationStep) -> Unit) {
        refuseWith?.let { throw it }
        askedFor = address
        this.onStep = onStep
    }

    /** Shawn approved: Android makes the association and reports it through the callback. */
    fun create(address: String, id: Int) {
        held += Association(address, id)
        onStep?.invoke(AssociationStep.Created)
    }

    override fun startObserving(association: Association) {
        refuseWith?.let { throw it }
        observed += association
    }

    override fun remove(association: Association) {
        held -= association
    }
}

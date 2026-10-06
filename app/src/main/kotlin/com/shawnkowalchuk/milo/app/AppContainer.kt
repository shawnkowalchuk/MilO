package com.shawnkowalchuk.milo.app

import android.content.Context
import com.shawnkowalchuk.milo.data.buildMiloDatabase
import com.shawnkowalchuk.milo.data.buildPointsDatabase
import com.shawnkowalchuk.milo.data.crash.CrashFileStore
import com.shawnkowalchuk.milo.data.crash.buildCrashFileStore
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.point.RawPointRepository
import com.shawnkowalchuk.milo.data.settings.SettingsStore
import com.shawnkowalchuk.milo.data.settings.buildSettingsStore
import com.shawnkowalchuk.milo.data.trip.TripRepository
import com.shawnkowalchuk.milo.platform.bluetooth.BluetoothTruckConnection
import com.shawnkowalchuk.milo.platform.bluetooth.PairedTruck
import com.shawnkowalchuk.milo.platform.bluetooth.TruckConnectionSource
import com.shawnkowalchuk.milo.platform.bluetooth.TruckPairing
import com.shawnkowalchuk.milo.platform.bluetooth.buildTruckPairing
import com.shawnkowalchuk.milo.platform.diagnostics.ProcessExitReader
import com.shawnkowalchuk.milo.platform.diagnostics.StartupDiagnostics
import com.shawnkowalchuk.milo.platform.system.TripPreflight
import com.shawnkowalchuk.milo.platform.trip.TripController
import com.shawnkowalchuk.milo.platform.trip.TripNotifications
import com.shawnkowalchuk.milo.platform.trip.TripServiceStarter
import com.shawnkowalchuk.milo.platform.trip.TripTrigger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * The app's manual dependency injection: the one place where the long-lived objects are created
 * and handed to each other. [MiloApplication] creates it once per process.
 *
 * There is no Hilt (ADR-001). To see what a class depends on, read its constructor; to see what
 * it is given, read this file.
 *
 * Where each thing is stored is decided in `data/`, by the `build...` functions this file calls.
 *
 * The databases and the settings store are created lazily, on first use, so building the
 * container opens no file on the main thread at process start. The process will soon be started
 * by trip triggers that have only seconds to begin recording.
 */
class AppContainer(context: Context) {
    // The application context, never an activity: these objects outlive every screen.
    private val appContext: Context = context.applicationContext

    /**
     * For work that must outlive any screen. SupervisorJob, so one failed job does not cancel
     * the others.
     */
    val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val miloDatabase by lazy { buildMiloDatabase(appContext) }
    private val pointsDatabase by lazy { buildPointsDatabase(appContext) }

    val tripRepository: TripRepository by lazy { TripRepository(miloDatabase.tripDao()) }
    val rawPointRepository: RawPointRepository by lazy {
        RawPointRepository(pointsDatabase.rawPointDao())
    }
    val eventLogRepository: EventLogRepository by lazy {
        EventLogRepository(miloDatabase.eventLogDao())
    }

    val settingsStore: SettingsStore by lazy { buildSettingsStore(appContext) }

    /** Shared with the crash handler, which [MiloApplication] installs before anything else. */
    val crashFileStore: CrashFileStore = buildCrashFileStore(appContext)

    val startupDiagnostics: StartupDiagnostics by lazy {
        StartupDiagnostics(
            crashFileStore = crashFileStore,
            processExitsAfter = ProcessExitReader(appContext)::exitsAfter,
            eventLog = eventLogRepository,
            settings = settingsStore,
            clock = System::currentTimeMillis,
        )
    }

    /** Answers "is the truck connected right now?" from the phone's Bluetooth (ADR-002). */
    val truckConnection: TruckConnectionSource by lazy {
        BluetoothTruckConnection(appContext, settingsStore)
    }

    /** Tells the Bluetooth receiver and the companion service which device is the truck. */
    val pairedTruck: PairedTruck by lazy { PairedTruck(settingsStore) }

    /**
     * Pairing with the truck, and the check that Android still watches for it. A truck that
     * has just been paired may already be connected, and nothing reports that, so the trip
     * controller is asked to look.
     */
    val truckPairing: TruckPairing by lazy {
        buildTruckPairing(
            context = appContext,
            settings = settingsStore,
            eventLog = eventLogRepository,
            onTruckChanged = {
                tripController.onTrigger(TripTrigger.RECONCILE, "the truck was paired")
            },
            clock = System::currentTimeMillis,
            scope = applicationScope,
        )
    }

    /** Shared by the trip service and its starter, so the two notification channels exist once. */
    val tripNotifications: TripNotifications by lazy { TripNotifications(appContext) }

    /**
     * The one owner of trip recording (ADR-002). Every trigger, screen and service reaches it
     * here. Creating it opens no file: its worker does, on its own thread, when the first
     * trigger arrives.
     */
    val tripController: TripController by lazy {
        TripController(
            trips = tripRepository,
            points = rawPointRepository,
            eventLog = eventLogRepository,
            settings = settingsStore,
            truck = truckConnection,
            starter = TripServiceStarter(appContext, TripPreflight(appContext), tripNotifications),
            clock = System::currentTimeMillis,
            scope = applicationScope,
        )
    }
}

package com.shawnkowalchuk.milo.app

import android.content.Context
import com.shawnkowalchuk.milo.data.buildMiloDatabase
import com.shawnkowalchuk.milo.data.buildPointsDatabase
import com.shawnkowalchuk.milo.data.crash.CrashFileStore
import com.shawnkowalchuk.milo.data.crash.buildCrashFileStore
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogFiles
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.eventlog.buildEventLogFiles
import com.shawnkowalchuk.milo.data.point.RawPointRepository
import com.shawnkowalchuk.milo.data.report.SentReportRepository
import com.shawnkowalchuk.milo.data.settings.SettingsStore
import com.shawnkowalchuk.milo.data.settings.buildSettingsStore
import com.shawnkowalchuk.milo.data.sound.buildOwnSoundStore
import com.shawnkowalchuk.milo.data.trip.TripCategoryCatchUp
import com.shawnkowalchuk.milo.data.trip.TripRepository
import com.shawnkowalchuk.milo.platform.address.GeocoderAddressLookup
import com.shawnkowalchuk.milo.platform.address.NetworkStatus
import com.shawnkowalchuk.milo.platform.address.TripAddresses
import com.shawnkowalchuk.milo.platform.bluetooth.BluetoothTruckConnection
import com.shawnkowalchuk.milo.platform.bluetooth.PairedTruck
import com.shawnkowalchuk.milo.platform.bluetooth.TruckConnectionSource
import com.shawnkowalchuk.milo.platform.bluetooth.TruckPairing
import com.shawnkowalchuk.milo.platform.bluetooth.bluetoothSwitchChanges
import com.shawnkowalchuk.milo.platform.bluetooth.buildTruckPairing
import com.shawnkowalchuk.milo.platform.diagnostics.ProcessExitReader
import com.shawnkowalchuk.milo.platform.diagnostics.StartupDiagnostics
import com.shawnkowalchuk.milo.platform.driving.DrivingAlert
import com.shawnkowalchuk.milo.platform.driving.PlayServicesDrivingDetection
import com.shawnkowalchuk.milo.platform.system.SetupChecklist
import com.shawnkowalchuk.milo.platform.system.SetupReader
import com.shawnkowalchuk.milo.platform.system.SystemScreens
import com.shawnkowalchuk.milo.platform.system.TripPreflight
import com.shawnkowalchuk.milo.platform.trip.ContentPickedAudio
import com.shawnkowalchuk.milo.platform.trip.OwnTripSound
import com.shawnkowalchuk.milo.platform.trip.TripController
import com.shawnkowalchuk.milo.platform.trip.TripNotifications
import com.shawnkowalchuk.milo.platform.trip.TripServiceStarter
import com.shawnkowalchuk.milo.platform.trip.TripStartSound
import com.shawnkowalchuk.milo.platform.trip.TripTrigger
import com.shawnkowalchuk.milo.platform.trip.playbackProblem
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

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

    /** Writes the event log to a text file, for the Log screen's Share. */
    val eventLogFiles: EventLogFiles by lazy { buildEventLogFiles(appContext, eventLogRepository) }

    /** The reports Shawn has said he sent. A month is "submitted" because of a row in it. */
    val sentReportRepository: SentReportRepository by lazy {
        SentReportRepository(miloDatabase.sentReportDao())
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

    /**
     * Says when the phone's Bluetooth has finished switching on or off. The pairing screen then
     * reads the paired devices again. Nothing listens until the flow is collected.
     */
    val bluetoothSwitched: Flow<Unit> by lazy { bluetoothSwitchChanges(appContext) }

    /**
     * What stands in the way of recording, read from the phone. The trip service's starter asks
     * it before every start, and the setup checklist shows the same facts as rows.
     */
    val tripPreflight: TripPreflight by lazy { TripPreflight(appContext) }

    /**
     * The setup checklist, shared by the Setup screen (which shows its rows) and the home
     * screen (which warns while a required row is not in order).
     */
    val setupChecklist: SetupChecklist by lazy {
        SetupChecklist(
            readFacts = SetupReader(appContext, tripPreflight)::read,
            settings = settingsStore,
            pairing = truckPairing.status,
            eventLog = eventLogRepository,
            clock = System::currentTimeMillis,
            scope = applicationScope,
        )
    }

    /** Opens the screens of the phone's settings that the checklist's buttons lead to. */
    val systemScreens: SystemScreens by lazy {
        SystemScreens(
            context = appContext,
            eventLog = eventLogRepository,
            clock = System::currentTimeMillis,
            scope = applicationScope,
        )
    }

    /**
     * Shared by the trip service, its starter and the driving alert, so the notification
     * channels exist once.
     */
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
            starter = TripServiceStarter(appContext, tripPreflight, tripNotifications),
            clock = System::currentTimeMillis,
            zone = ZoneId::systemDefault,
            scope = applicationScope,
        )
    }

    /**
     * The driving alert: a notification when the phone reports driving during the work hours
     * with no trip being recorded and the paired truck not connected. It is handed two questions
     * about the stored trips ("is one open?" and "when did the last one end?"), what the trip
     * controller publishes and the controller's "tell me when you have caught up", and neither
     * the controller nor the trip storage itself: nothing in it can start a trip.
     */
    val drivingAlert: DrivingAlert by lazy {
        DrivingAlert(
            detection = PlayServicesDrivingDetection(appContext),
            showAlert = tripNotifications::showDrivingAlert,
            withdrawAlert = tripNotifications::cancelDrivingAlert,
            settings = settingsStore,
            truck = truckConnection,
            openTripStored = { tripRepository.findOpenTrip() != null },
            lastTripEndedAtMs = tripRepository::findNewestTripEndMs,
            tripActivity = tripController.activity,
            whenTripsCaughtUp = tripController::whenCaughtUp,
            eventLog = eventLogRepository,
            crashFileStore = crashFileStore,
            clock = System::currentTimeMillis,
            zone = ZoneId::systemDefault,
            scope = applicationScope,
        )
    }

    /**
     * Sorts the trips that were recorded before MilO had a work schedule into Business and
     * Personal. A trip recorded since is sorted by the trip controller when it closes, so after
     * its first pass this finds nothing to do.
     */
    val tripCategoryCatchUp: TripCategoryCatchUp by lazy {
        TripCategoryCatchUp(
            trips = tripRepository,
            settings = settingsStore,
            eventLog = eventLogRepository,
            crashFileStore = crashFileStore,
            zone = ZoneId::systemDefault,
            clock = System::currentTimeMillis,
            scope = applicationScope,
        )
    }

    /**
     * Finds the start and end address of each trip. It watches the trip controller and writes
     * only the address columns of finished trips, so nothing here can hold a trip up.
     */
    val tripAddresses: TripAddresses by lazy {
        TripAddresses(
            trips = tripRepository,
            eventLog = eventLogRepository,
            crashFileStore = crashFileStore,
            lookup = GeocoderAddressLookup(appContext),
            isOnline = NetworkStatus(appContext)::isOnline,
            tripActivity = tripController.activity,
            clock = System::currentTimeMillis,
            scope = applicationScope,
        )
    }

    // Three parts of this container, each in a class of its own because this file is at its
    // size limit: what makes a report for the accountant and hands it over, with the monthly
    // reminder to send it; Android's backup, with the export and import of all data; and the
    // watch on Android Auto outside trips, which only writes to the event log.
    val reports: ReportObjects by lazy { ReportObjects(appContext, this) }
    val transfer: TransferObjects by lazy {
        val main = miloDatabase.mainTransferDao()
        TransferObjects(transferParts(appContext, this, main, pointsDatabase.pointsTransferDao()))
    }
    val car: CarObjects by lazy { CarObjects(appContext, this) }

    /**
     * Makes an audio file Shawn picked the trip-start sound, by copying it into MilO's own
     * storage, and goes back to the bundled one. The trip service reads the result from the
     * settings at the next trip start.
     */
    val ownTripSound: OwnTripSound by lazy {
        OwnTripSound(
            picked = ContentPickedAudio(appContext),
            playbackProblem = ::playbackProblem,
            store = buildOwnSoundStore(appContext),
            settings = settingsStore,
            eventLog = eventLogRepository,
            clock = System::currentTimeMillis,
        )
    }

    /**
     * Plays the trip-start sound for the Settings screen's Play button, with the same player a
     * trip start uses, so what is heard there is what a trip start plays. The trip service has
     * a player of its own; this one marks its log lines as coming from Settings.
     */
    val soundPreview: TripStartSound by lazy {
        TripStartSound(appContext) { note ->
            applicationScope.launch {
                eventLogRepository.add(
                    System.currentTimeMillis(),
                    EventCategory.SERVICE,
                    "Settings screen, Play pressed. $note",
                )
            }
        }
    }
}

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
import com.shawnkowalchuk.milo.platform.diagnostics.ProcessExitReader
import com.shawnkowalchuk.milo.platform.diagnostics.StartupDiagnostics
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
}

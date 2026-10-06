package com.shawnkowalchuk.milo.app

import android.app.Application
import com.shawnkowalchuk.milo.platform.diagnostics.CrashHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * The process-wide entry point. Android creates it before any activity, service or receiver, so
 * it runs however the process was started: from the launcher today, and later from a Bluetooth
 * event or a reboot with no screen at all.
 *
 * It does two things only: it owns the [AppContainer], and it starts the crash and kill capture.
 */
class MiloApplication : Application() {
    /** Created in [onCreate]. Screens and services reach every shared object through it. */
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // Before any real work begins (building the container only wires objects together), so
        // a crash anywhere later in start-up is still captured.
        CrashHandler.install(container.crashFileStore)

        // Reading files and the database must not hold up the main thread, least of all when
        // the process was started by a trip trigger with seconds to begin recording.
        container.applicationScope.launch(Dispatchers.IO) {
            container.startupDiagnostics.record()
        }
    }
}

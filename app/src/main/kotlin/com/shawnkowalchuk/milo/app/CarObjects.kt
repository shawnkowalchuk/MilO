package com.shawnkowalchuk.milo.app

import android.content.Context
import com.shawnkowalchuk.milo.platform.car.AndroidAutoLog
import com.shawnkowalchuk.milo.platform.car.buildAndroidAutoLog

/**
 * The long-lived object around Android Auto: the watch that writes its connection changes to
 * the event log while no trip is being recorded. (The Android Auto screen has no object here:
 * Android creates its service, and each session builds itself.)
 *
 * Like [ReportObjects] and [TransferObjects], it is a part of the [AppContainer], which creates
 * it once and through which everything reaches it (`container.car`); it is a class of its own
 * only because the container is at its size limit, and it follows the container's rules.
 *
 * @param container where the storage this object writes to is made.
 */
class CarObjects(private val appContext: Context, private val container: AppContainer) {
    /**
     * Watches Android Auto for the life of the process and writes a line for each change
     * outside a trip. It is handed one question about the trip controller, "is a trip being
     * recorded?", and neither the controller nor the trip storage: it can only write to the
     * event log.
     */
    val connectionLog: AndroidAutoLog by lazy {
        buildAndroidAutoLog(
            context = appContext,
            tripBeingRecorded = { container.tripController.activity.value.trip != null },
            eventLog = container.eventLogRepository,
            crashFileStore = container.crashFileStore,
            clock = container.clock,
            scope = container.applicationScope,
        )
    }
}

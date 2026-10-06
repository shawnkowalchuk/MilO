package com.shawnkowalchuk.milo.app

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.shawnkowalchuk.milo.data.transfer.MainTransferDao
import com.shawnkowalchuk.milo.data.transfer.PointsTransferDao
import com.shawnkowalchuk.milo.feature.settings.DataViewModel
import com.shawnkowalchuk.milo.platform.transfer.BackupAftermath
import com.shawnkowalchuk.milo.platform.transfer.DataTransfer
import com.shawnkowalchuk.milo.platform.transfer.TransferParts
import com.shawnkowalchuk.milo.platform.transfer.buildBackupAftermath
import com.shawnkowalchuk.milo.platform.transfer.buildDataTransfer
import java.time.ZoneId
import kotlinx.coroutines.flow.map

/**
 * The long-lived objects around Android's backup and the manual export and import.
 *
 * Like [ReportObjects], it is a part of the [AppContainer], which creates it once and through
 * which everything reaches it (`container.transfer`); it is a class of its own only because the
 * container is at its size limit, and it follows the container's rules.
 *
 * @param parts what the two objects are handed from the rest of the app. The container makes
 * it, because it holds the two databases.
 */
class TransferObjects(private val parts: TransferParts) {
    /**
     * At a process start: writes what Android's backup did into the event log and, after a
     * restore, takes out of the settings what was only true of the installation they came from.
     */
    val aftermath: BackupAftermath by lazy { buildBackupAftermath(parts) }

    /** "Export all data" and "Import data" of the Settings screen, and where each stands. */
    val dataTransfer: DataTransfer by lazy { buildDataTransfer(parts) }
}

/**
 * What those two are handed, gathered from the [container]. Each shared object is reached
 * through a function, so that none of them is made before it is first used.
 *
 * @param main and [points] are the two databases' own ways of reading and replacing every row.
 */
internal fun transferParts(
    context: Context,
    container: AppContainer,
    main: MainTransferDao,
    points: PointsTransferDao,
): TransferParts = TransferParts(
    context = context,
    main = main,
    points = points,
    settings = container.settingsStore,
    eventLog = container.eventLogRepository,
    crashFileStore = container.crashFileStore,
    tripInProgress = { container.tripController.activity.value.trip != null },
    checkPairing = { occasion -> container.truckPairing.check(occasion) },
    afterImport = { occasion ->
        // The truck may have to be paired again, the imported trips may lack an address or
        // a category, and the settings that the alert and the reminder go by have changed.
        container.truckPairing.check(occasion)
        container.tripAddresses.catchUp(occasion)
        container.tripCategoryCatchUp.catchUp(occasion)
        container.drivingAlert.arm(occasion)
        container.reports.reminder.look(occasion)
    },
    clock = System::currentTimeMillis,
    zone = ZoneId::systemDefault,
    scope = container.applicationScope,
)

/**
 * How the Settings screen's card for backup, export and import gets its ViewModel. It stands
 * here because [MiloNavigation] is at its size limit (ENGINEERING_STANDARDS section 3).
 */
internal fun dataViewModelFactory(container: AppContainer): ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            DataViewModel(
                transfer = container.transfer.dataTransfer,
                settings = container.settingsStore.settings,
                tripInProgress = container.tripController.activity.map { it.trip != null },
                clock = System::currentTimeMillis,
                zone = ZoneId::systemDefault,
            )
        }
    }

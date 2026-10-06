package com.shawnkowalchuk.milo.platform.transfer

import android.content.Context
import android.content.pm.PackageManager
import com.shawnkowalchuk.milo.data.crash.CrashFileStore
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.settings.SettingsStore
import com.shawnkowalchuk.milo.data.transfer.DataExport
import com.shawnkowalchuk.milo.data.transfer.DataImport
import com.shawnkowalchuk.milo.data.transfer.MainTransferDao
import com.shawnkowalchuk.milo.data.transfer.PointsTransferDao
import com.shawnkowalchuk.milo.data.transfer.buildBackupNoteStore
import com.shawnkowalchuk.milo.data.transfer.buildIncomingImport
import com.shawnkowalchuk.milo.data.transfer.buildSafetyCopies
import com.shawnkowalchuk.milo.platform.bluetooth.SystemCompanionLink
import java.io.File
import java.net.URI
import java.net.URISyntaxException
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope

// How the two long-lived objects of this package are put together on the phone. Here, and not
// in the `AppContainer`, because what they are made of (Android's companion device manager, the
// content resolver, the folders of the files) is this package's own business.

/**
 * What an export, an import and the step after a restore are handed from the rest of the app.
 *
 * @param tripInProgress whether the trip controller shows a trip as being recorded.
 * @param checkPairing has the truck's pairing checked, with a word on why.
 * @param afterImport what has to look at imported data and settings; see `ImportRun`.
 * @param scope the application scope.
 */
class TransferParts(
    val context: Context,
    val main: MainTransferDao,
    val points: PointsTransferDao,
    val settings: SettingsStore,
    val eventLog: EventLogRepository,
    val crashFileStore: CrashFileStore,
    val tripInProgress: () -> Boolean,
    val checkPairing: (occasion: String) -> Unit,
    val afterImport: (occasion: String) -> Unit,
    val clock: () -> Long,
    val zone: () -> ZoneId,
    val scope: CoroutineScope,
)

/** Builds [DataTransfer] on the phone's own file picker, storage and companion device manager. */
fun buildDataTransfer(parts: TransferParts): DataTransfer {
    val appContext = parts.context.applicationContext
    val failures = TransferFailures(parts.eventLog, parts.crashFileStore, parts.clock)
    val export =
        DataExport(
            main = parts.main,
            points = parts.points,
            settings = parts.settings,
            appVersion = versionNameOf(appContext),
            clock = parts.clock,
            zone = parts.zone,
        )
    val import = DataImport(parts.main, parts.points)
    val incoming = buildIncomingImport(appContext)
    val safetyCopies = buildSafetyCopies(appContext)
    val importRun =
        ImportRun(
            export = export,
            import = import,
            incoming = incoming,
            safetyCopies = safetyCopies,
            settings = parts.settings,
            pairingHere = { pairingOn(appContext) },
            tripInProgress = parts.tripInProgress,
            afterImport = parts.afterImport,
            eventLog = parts.eventLog,
            failures = failures,
            clock = parts.clock,
        )
    val log = parts.eventLog
    return DataTransfer(
        export = export,
        import = import,
        incoming = incoming,
        safetyCopies = safetyCopies,
        documents = ContentPickedDocuments(appContext),
        settings = parts.settings,
        tripInProgress = parts.tripInProgress,
        importRun = importRun,
        importResume =
            ImportResume(importRun, import, incoming, safetyCopies, log, failures, parts.clock),
        eventLog = parts.eventLog,
        failures = failures,
        clock = parts.clock,
        scope = parts.scope,
    )
}

/** Builds [BackupAftermath] on the notes the backup agent leaves in the no-backup folder. */
fun buildBackupAftermath(parts: TransferParts): BackupAftermath {
    val appContext = parts.context.applicationContext
    return BackupAftermath(
        notes = buildBackupNoteStore(appContext),
        settings = parts.settings,
        pairingHere = { pairingOn(appContext) },
        ownSoundIsHere = ::isFileHere,
        checkPairing = parts.checkPairing,
        eventLog = parts.eventLog,
        failures = TransferFailures(parts.eventLog, parts.crashFileStore, parts.clock),
    )
}

/**
 * What Android on this phone says about MilO's companion associations, asked afresh each time.
 *
 * @throws RuntimeException if Android refuses: its companion manager does so with unchecked
 * exceptions of several kinds, and each caller is ready for them.
 */
private fun pairingOn(context: Context): PairingHere {
    val link = SystemCompanionLink(context)
    return PairingHere(link.supported, link.associations().map { it.address })
}

/** Whether the file a `file:` address names is there. An address that names no file is not. */
private fun isFileHere(uri: String): Boolean = try {
    File(URI(uri)).isFile
} catch (noAddress: URISyntaxException) {
    false
} catch (noFile: IllegalArgumentException) {
    false
}

/** MilO's version as Android names it, for the first lines of an export. */
private fun versionNameOf(context: Context): String = try {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
} catch (notInstalled: PackageManager.NameNotFoundException) {
    // Cannot happen for MilO's own package. The export is worth more than its version line.
    "unknown"
}

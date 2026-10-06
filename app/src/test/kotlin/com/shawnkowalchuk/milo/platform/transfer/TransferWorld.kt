package com.shawnkowalchuk.milo.platform.transfer

import com.shawnkowalchuk.milo.data.crash.CrashFileStore
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogDao
import com.shawnkowalchuk.milo.data.eventlog.EventLogEntry
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.point.RawPoint
import com.shawnkowalchuk.milo.data.settings.SettingsStore
import com.shawnkowalchuk.milo.data.transfer.CheckedExport
import com.shawnkowalchuk.milo.data.transfer.DataExport
import com.shawnkowalchuk.milo.data.transfer.DataImport
import com.shawnkowalchuk.milo.data.transfer.EDMONTON
import com.shawnkowalchuk.milo.data.transfer.ExportReading
import com.shawnkowalchuk.milo.data.transfer.FakeMainTransferDao
import com.shawnkowalchuk.milo.data.transfer.FakePointsTransferDao
import com.shawnkowalchuk.milo.data.transfer.IncomingImport
import com.shawnkowalchuk.milo.data.transfer.SafetyCopies
import com.shawnkowalchuk.milo.data.transfer.read
import com.shawnkowalchuk.milo.platform.trip.FakeEventLogDao
import com.shawnkowalchuk.milo.platform.trip.FakeSettingsFile
import com.shawnkowalchuk.milo.platform.trip.UnreadableSettingsFile
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CountDownLatch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/** The files of the file picker, held in memory: what was written to each, and what failed. */
class FakeDocuments : PickedDocuments {
    val files = mutableMapOf<String, ByteArray>()
    val deleted = mutableListOf<String>()

    /** Set to make a file break off after this many bytes have been written to it. */
    var failWritingAfterBytes: Int? = null

    /** Set to false for a file app that does not let a file be removed. */
    var canDelete = true

    /** Set to keep every file from being opened for writing until the latch is released. */
    var holdWritingUntil: CountDownLatch? = null

    override fun openForWriting(uri: String): OutputStream = object : ByteArrayOutputStream() {
        override fun write(buffer: ByteArray, offset: Int, length: Int) {
            super.write(buffer, offset, length)
            files[uri] = toByteArray()
            failWritingAfterBytes?.let { if (size() > it) throw IOException("the disk is full") }
        }
    }.also {
        holdWritingUntil?.await()
        files[uri] = ByteArray(0)
    }

    override fun openForReading(uri: String): InputStream =
        ByteArrayInputStream(files[uri] ?: throw IOException("no such file: $uri"))

    override fun delete(uri: String): Boolean {
        if (!canDelete) return false
        files.remove(uri)
        deleted += uri
        return true
    }

    fun textOf(uri: String): String = files.getValue(uri).toString(Charsets.UTF_8)
}

/**
 * Everything an export and an import touch, as stand-ins: the two databases, the settings
 * file, the event log, the file picker's files, and real folders for the files MilO keeps.
 *
 * @param root an empty folder of the test.
 * @param settingsUnreadable true for a settings file whose every read and write fails.
 * @param logUnwritable true for an event log that takes no line.
 */
class TransferWorld(
    root: File,
    settingsUnreadable: Boolean = false,
    logUnwritable: Boolean = false,
) {
    val main = FakeMainTransferDao()
    val points = FakePointsTransferDao()
    val settings =
        SettingsStore(if (settingsUnreadable) UnreadableSettingsFile else FakeSettingsFile())
    val log = FakeEventLogDao()
    val documents = FakeDocuments()
    val incoming = IncomingImport(File(root, "import"))
    val safetyFolder = File(root, "safety")
    val safetyCopies = SafetyCopies(safetyFolder)

    /** What the trip controller shows; the stored trips are asked apart from it. */
    var tripInProgress = false
    var pairing = PairingHere(companionSupported = true, associatedAddresses = emptyList())
    var nowMs = 1_791_400_000_000L

    /** Every time the rest of the app was told to look at imported data, and why. */
    val toldAfterImport = mutableListOf<String>()

    private val eventLog = EventLogRepository(if (logUnwritable) UnwritableLog(log) else log)
    private val failures =
        TransferFailures(eventLog, CrashFileStore(File(root, "crashes"))) { nowMs }
    private val export = DataExport(main, points, settings, "0.1.0", { nowMs }, { EDMONTON })
    private val import = DataImport(main, points)

    /**
     * What a process builds at its start, over the same storage each time: a second call is
     * the process that starts after the first was ended.
     */
    fun transfer(scope: CoroutineScope): DataTransfer {
        val importRun =
            ImportRun(
                export = export,
                import = import,
                incoming = incoming,
                safetyCopies = safetyCopies,
                settings = settings,
                pairingHere = { pairing },
                tripInProgress = { tripInProgress },
                afterImport = { toldAfterImport += it },
                eventLog = eventLog,
                failures = failures,
                clock = { nowMs },
            )
        return DataTransfer(
            export = export,
            import = import,
            incoming = incoming,
            safetyCopies = safetyCopies,
            documents = documents,
            settings = settings,
            tripInProgress = { tripInProgress },
            importRun = importRun,
            importResume =
                ImportResume(importRun, import, incoming, safetyCopies, eventLog, failures) {
                    nowMs
                },
            eventLog = eventLog,
            failures = failures,
            clock = { nowMs },
            scope = scope,
        )
    }

    fun lines(category: EventCategory): List<String> =
        log.entries.filter { it.category == category }.map { it.message }
}

/** An event log whose every write fails, as it does when the database itself is broken. */
private class UnwritableLog(readable: EventLogDao) : EventLogDao by readable {
    override suspend fun insert(entry: EventLogEntry): Long =
        throw IOException("the database is gone")
}

/** Waits for whatever was started to come to an end: an outcome, or a question. */
fun DataTransfer.settled(): TransferStatus = runBlocking {
    withTimeout(10_000) { status.first { it.working == null } }
}

/**
 * Waits until everything that was started in this scope has run to its end, however it ended:
 * also work that stopped where a stand-in ended the process, which never says that it is done.
 */
suspend fun CoroutineScope.finished() {
    coroutineContext[Job]?.children?.toList()?.forEach { it.join() }
}

/** What a file of the file picker, or a safety copy, holds, with its points. */
fun exportIn(text: String): Pair<CheckedExport, List<RawPoint>> {
    val points = mutableListOf<RawPoint>()
    val reading = read(text, points)
    check(reading is ExportReading.Good) { "Not a whole export: $reading" }
    return reading.export to points
}

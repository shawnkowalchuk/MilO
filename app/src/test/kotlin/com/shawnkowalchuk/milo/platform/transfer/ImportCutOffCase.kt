package com.shawnkowalchuk.milo.platform.transfer

import com.shawnkowalchuk.milo.data.point.RawPoint
import com.shawnkowalchuk.milo.data.report.SentReport
import com.shawnkowalchuk.milo.data.report.SentReportKind
import com.shawnkowalchuk.milo.data.transfer.fileOf
import com.shawnkowalchuk.milo.data.transfer.recordedTrip
import com.shawnkowalchuk.milo.data.transfer.sentReports
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.rules.TemporaryFolder

/**
 * What the tests of an import that is cut off share: the phone, the file, a process that is
 * ended in the middle of the import, and the process that starts after it.
 *
 * The phone starts with trips 40 and 50, a point of each and one sent report; the file holds
 * trips 1 to 9 with four points (`ExportFixtures.kt`). The process is ended where the
 * stand-ins say (`ProcessEnded`), and every call of `world.transfer` is a new process over the
 * same storage.
 */
abstract class ImportCutOffCase {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    protected val ownTrips =
        listOf(
            recordedTrip.copy(id = 40, distanceMetres = 1_111.0),
            recordedTrip.copy(id = 50, distanceMetres = 2_222.0),
        )
    protected val ownPoints =
        listOf(
            RawPoint(1, 40, 1_000, 10, 51.0, -114.0, 5f, 1f),
            RawPoint(2, 50, 2_000, 20, 51.1, -114.1, 5f, 1f),
        )
    protected val ownReports =
        listOf(SentReport(9, SentReportKind.RANGE, 20_000, 20_001, 77, 1, 1_100.0, 0))

    protected val world by lazy {
        TransferWorld(temporaryFolder.root).apply {
            main.trips = ownTrips
            main.sentReports = ownReports
            points.points = ownPoints
            runBlocking { settings.setReportName("Own Name") }
        }
    }

    protected val picked = "content://files/MilO-export-2026-10-06.json"

    /** Shawn's yes to the file, in a process that is ended wherever the stand-ins say. */
    protected suspend fun TestScope.importInAProcessThatIsEnded() {
        world.documents.files[picked] = fileOf().toByteArray()
        val transfer = world.transfer(backgroundScope)
        transfer.offerImport(picked)
        transfer.settled()
        transfer.confirmImport()
        backgroundScope.finished()
        // It never came to an end: the process that showed "Replacing the data…" is gone.
        assertEquals(TransferWork.IMPORTING, transfer.status.value.working)
    }

    /** The process that starts next, and what its card shows once it has looked. */
    protected suspend fun TestScope.nextStart(): TransferStatus {
        val transfer = world.transfer(backgroundScope)
        backgroundScope.launch { transfer.finishCutOffImport() }
        backgroundScope.finished()
        return transfer.status.value
    }

    protected fun nothingWasChanged() {
        assertEquals(ownTrips, world.main.trips)
        assertEquals(ownReports, world.main.sentReports)
        assertEquals(ownPoints, world.points.points)
        assertEquals("Own Name", runBlocking { world.settings.current() }.reportName)
    }

    protected fun storedPoints() = world.points.points.map { it.copy(id = 0) }
}

package com.shawnkowalchuk.milo.platform.transfer

import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.point.RawPoint
import com.shawnkowalchuk.milo.data.report.SentReport
import com.shawnkowalchuk.milo.data.report.SentReportKind
import com.shawnkowalchuk.milo.data.settings.ConfirmedStep
import com.shawnkowalchuk.milo.data.settings.TransferredSettings
import com.shawnkowalchuk.milo.data.transfer.changedSettings
import com.shawnkowalchuk.milo.data.transfer.everyKindOfTrip
import com.shawnkowalchuk.milo.data.transfer.fileOf
import com.shawnkowalchuk.milo.data.transfer.recordedTrip
import com.shawnkowalchuk.milo.data.transfer.sentReports
import com.shawnkowalchuk.milo.data.transfer.somePoints
import com.shawnkowalchuk.milo.data.transfer.sourceOf
import com.shawnkowalchuk.milo.data.transfer.withoutIds
import com.shawnkowalchuk.milo.data.trip.Trip
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * "Import data": nothing is touched before the whole file is checked and the question is
 * answered, a safety copy comes first, and a failure at any step leaves what that step was
 * about to replace as it was.
 *
 * The phone starts with two trips, a point of each and one sent report of its own, paired with
 * its truck; the file holds one trip of every kind (`ExportFixtures.kt`).
 */
class DataImportingTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val ownTrips =
        listOf(
            recordedTrip.copy(id = 40, distanceMetres = 1_111.0),
            recordedTrip.copy(id = 50, distanceMetres = 2_222.0),
        )
    private val ownPoints =
        listOf(
            RawPoint(1, 40, 1_000, 10, 51.0, -114.0, 5f, 1f),
            RawPoint(2, 50, 2_000, 20, 51.1, -114.1, 5f, 1f),
        )
    private val ownReports =
        listOf(SentReport(9, SentReportKind.RANGE, 20_000, 20_001, 77, 1, 1_100.0, 0))

    private val world by lazy {
        TransferWorld(temporaryFolder.root).apply {
            main.trips = ownTrips
            main.sentReports = ownReports
            points.points = ownPoints
            pairing = PairingHere(companionSupported = true, listOf("aa:bb:cc:dd:ee:01"))
            runBlocking {
                settings.setTruck("AA:BB:CC:DD:EE:01", "This phone's truck", 7)
                settings.setReportName("Own Name")
                settings.setConfirmedAtMs(ConfirmedStep.XIAOMI_AUTOSTART, 123)
            }
        }
    }

    private val picked = "content://files/MilO-export-2026-10-06.json"

    private fun TestScope.offered(text: String = fileOf()): DataTransfer {
        world.documents.files[picked] = text.toByteArray()
        return world.transfer(backgroundScope).also {
            it.offerImport(picked)
            it.settled()
        }
    }

    private fun DataTransfer.imported(): TransferStatus {
        confirmImport()
        return settled()
    }

    private fun nothingWasChanged() {
        assertEquals(ownTrips, world.main.trips)
        assertEquals(ownReports, world.main.sentReports)
        assertEquals(ownPoints, world.points.points)
        assertEquals("Own Name", runBlocking { world.settings.current() }.reportName)
    }

    @Test
    fun `a picked file is checked and asked about, and until the answer nothing is changed`() =
        runTest {
            val transfer = offered()

            val offer = transfer.status.value.offer
            assertEquals(
                ImportOffer(
                    exportedAtMs = 1_791_316_931_000,
                    trips = 8,
                    sentReports = 3,
                    points = 4,
                    hasSettings = true,
                    storedTrips = 2,
                    storedSentReports = 1,
                    safetyCopyAtMs = null,
                ),
                offer,
            )
            nothingWasChanged()
            assertTrue(world.safetyCopies.all().isEmpty())
            assertNotNull(world.incoming.waiting())
        }

    @Test
    fun `no to the question changes nothing and removes the copy of the file`() = runTest {
        val transfer = offered()

        transfer.declineImport()
        val status = transfer.settled()

        assertEquals(TransferStatus(), status)
        nothingWasChanged()
        while (world.incoming.waiting() != null) Thread.sleep(5)
        // And a yes that comes after the no does nothing either.
        transfer.confirmImport()
        nothingWasChanged()
    }

    @Test
    fun `yes replaces every trip, every sent report and every point with the file's`() = runTest {
        val status = offered().imported()

        assertEquals(everyKindOfTrip, world.main.trips)
        assertEquals(sentReports, world.main.sentReports)
        assertEquals(somePoints.withoutIds(), world.points.points.map { it.copy(id = 0) })
        val done = (status.outcome as TransferOutcome.Imported).done
        assertEquals(8, done.trips)
        assertEquals(3, done.sentReports)
        assertEquals(4L, done.points)
        assertEquals(PointsTaken.TAKEN, done.pointsTaken)
        assertEquals(SettingsTaken.TAKEN, done.settings)
        assertNull(world.incoming.waiting())
    }

    @Test
    fun `what the phone held is first kept as a safety copy that holds all of it`() = runTest {
        offered().imported()

        val copy = world.safetyCopies.all().single()
        val (kept, keptPoints) = exportIn(copy.file.readText())
        assertEquals(ownTrips, kept.trips)
        assertEquals(ownReports, kept.sentReports)
        assertEquals(ownPoints.withoutIds(), keptPoints)
        assertEquals("Own Name", kept.settings?.reportName)
        assertEquals(world.nowMs, copy.writtenAtMs)
    }

    @Test
    fun `the last import is undone by putting the safety copy back`() = runTest {
        val transfer = offered()
        transfer.imported()
        val before = world.nowMs
        world.nowMs += 60_000

        transfer.offerSafetyCopy(before)
        val offer = transfer.settled().offer
        val status = transfer.imported()

        assertEquals(before, offer?.safetyCopyAtMs)
        assertEquals(2, offer?.trips)
        assertEquals(8, offer?.storedTrips)
        assertEquals(ownTrips, world.main.trips)
        assertEquals(ownReports, world.main.sentReports)
        assertEquals(ownPoints.withoutIds(), world.points.points.map { it.copy(id = 0) })
        assertTrue(status.outcome is TransferOutcome.Imported)
        assertEquals("Own Name", world.settings.current().reportName)
    }

    @Test
    fun `the copy of a put back is of what the phone held then, and takes that copy's place`() =
        runTest {
            val transfer = offered()
            transfer.imported()
            val before = world.nowMs
            world.nowMs += 60_000

            transfer.offerSafetyCopy(before)
            transfer.settled()
            transfer.imported()

            val copy = world.safetyCopies.all().single()
            assertEquals(world.nowMs, copy.writtenAtMs)
            assertEquals(everyKindOfTrip, exportIn(copy.file.readText()).first.trips)
        }

    @Test
    fun `the settings are the file's, and what this phone knows about itself stays`() = runTest {
        offered().imported()

        val now = world.settings.current()
        assertEquals("Sam Driver", now.reportName)
        assertEquals(150, now.gracePeriodSeconds)
        assertEquals(changedSettings.schedule, now.schedule)
        assertEquals(mapOf(ConfirmedStep.XIAOMI_AUTOSTART to 123L), now.confirmedAtMs)
    }

    @Test
    fun `a phone that is paired with its truck stays paired with it, whatever the file names`() =
        runTest {
            val status = offered().imported()

            val now = world.settings.current()
            assertEquals("AA:BB:CC:DD:EE:01", now.truckAddress)
            assertEquals(7, now.truckAssociationId)
            assertFalse((status.outcome as TransferOutcome.Imported).done.truckNeedsPairing)
            val pairing = world.lines(EventCategory.PAIRING).single()
            assertTrue(pairing, pairing.contains("stays (AA:BB:CC:DD:EE:01)"))
            assertTrue(pairing, pairing.contains("AA:BB:CC:DD:EE:FF) was not taken"))
        }

    @Test
    fun `on a phone without a pairing the file's truck is stored and must be paired again`() =
        runTest {
            world.pairing = PairingHere(companionSupported = true, emptyList())
            world.settings.clearTruck()

            val status = offered().imported()

            val now = world.settings.current()
            assertEquals("AA:BB:CC:DD:EE:FF", now.truckAddress)
            assertEquals("Work truck", now.truckName)
            assertNull(
                "An import must never make the phone believe it is paired",
                now.truckAssociationId,
            )
            assertTrue((status.outcome as TransferOutcome.Imported).done.truckNeedsPairing)
            assertTrue(world.lines(EventCategory.PAIRING).single().contains("paired again"))
        }

    @Test
    fun `the rest of the app is told to look at the new data, once, after it is all in`() =
        runTest {
            offered().imported()

            assertEquals(listOf("data imported"), world.toldAfterImport)
            val line = world.lines(EventCategory.REPORT).single()
            assertTrue(
                line,
                line.contains("8 trips and 3 sent reports replaced the 2 trips and 1 sent"),
            )
            assertTrue(line, line.contains("4 raw GPS points were stored"))
            assertTrue(line, line.contains("MilO-before-import-${world.nowMs}.json"))
            assertTrue(world.lines(EventCategory.ERROR).isEmpty())
        }

    @Test
    fun `a file that is no export, is damaged, or is from a newer MilO is refused untouched`() =
        runTest {
            val whole = fileOf()
            val refused =
                mapOf(
                    "not an export" to TransferRefusal.NotAnExport,
                    whole.dropLast(40) to TransferRefusal.Damaged,
                    whole.replace("\"status\":\"DELETED\"", "\"status\":\"OPEN\"") to
                        TransferRefusal.Damaged,
                    whole.replace("\"formatVersion\": 2,", "\"formatVersion\": 3,") to
                        TransferRefusal.NewerVersion(3),
                )

            for ((text, why) in refused) {
                val transfer = offered(text)

                assertEquals(TransferOutcome.Refused(why), transfer.status.value.outcome)
                assertNull(transfer.status.value.offer)
                assertNull(world.incoming.waiting())
                transfer.confirmImport()
                nothingWasChanged()
            }
            val lines = world.lines(EventCategory.REPORT)
            assertEquals(4, lines.size)
            assertTrue(lines[2], lines[2].contains("Trip 4: its status is \"OPEN\""))
            assertTrue(lines[3], lines[3].contains("format version 3"))
        }

    @Test
    fun `a file that cannot be read at all is said, and written to the log`() = runTest {
        val transfer = world.transfer(backgroundScope)

        transfer.offerImport("content://files/gone.json")
        val status = transfer.settled()

        assertEquals(TransferOutcome.Refused(TransferRefusal.FileNotRead), status.outcome)
        assertEquals(1, world.lines(EventCategory.ERROR).size)
        nothingWasChanged()
    }

    @Test
    fun `no file is taken in while a trip is being recorded`() = runTest {
        world.tripInProgress = true

        val transfer = offered()

        val refusal = TransferOutcome.Refused(TransferRefusal.TripInProgress)
        assertEquals(refusal, transfer.status.value.outcome)
        assertNull(world.incoming.waiting())
    }

    @Test
    fun `a trip that starts while the question stands stops the import, with nothing changed`() =
        runTest {
            val transfer = offered()
            world.tripInProgress = true

            val status = transfer.imported()

            assertEquals(TransferOutcome.Refused(TransferRefusal.TripInProgress), status.outcome)
            nothingWasChanged()
            assertTrue(world.safetyCopies.all().isEmpty())
        }

    @Test
    fun `a trip that storage holds open stops it too, even if nothing shows it yet`() = runTest {
        val transfer = offered()
        val open = recordedTrip.copy(id = 60, status = TripStatus.OPEN, endedAtMs = null)
        world.main.trips += open

        val status = transfer.imported()

        assertEquals(TransferOutcome.Refused(TransferRefusal.TripInProgress), status.outcome)
        assertEquals(ownTrips + open, world.main.trips)
        assertEquals(ownPoints, world.points.points)
        assertTrue("No half-written safety copy is left", world.safetyCopies.all().isEmpty())
        assertTrue(world.safetyFolder.list().orEmpty().isEmpty())
    }

    @Test
    fun `a trip that starts in the instant before the trips are replaced stops it as well`() =
        runTest {
            val transfer = offered()
            val open = recordedTrip.copy(id = 60, status = TripStatus.OPEN, endedAtMs = null)
            world.main.tripStartsBeforeReplacing = open

            val status = transfer.imported()

            assertEquals(TransferOutcome.Refused(TransferRefusal.TripInProgress), status.outcome)
            assertEquals(ownTrips + open, world.main.trips)
            assertEquals(ownReports, world.main.sentReports)
            assertEquals(ownPoints, world.points.points)
        }

    @Test
    fun `without a safety copy nothing is replaced`() = runTest {
        val transfer = offered()
        // Where the folder of the safety copies would be made, a file stands in the way.
        world.safetyFolder.writeText("in the way")

        val status = transfer.imported()

        assertEquals(TransferOutcome.Refused(TransferRefusal.SafetyCopyNotWritten), status.outcome)
        nothingWasChanged()
        assertTrue(world.lines(EventCategory.ERROR).single().contains("Nothing was replaced"))
    }

    @Test
    fun `if the trips cannot be replaced, everything on the phone is as it was`() = runTest {
        val transfer = offered()
        world.main.failNextInsert = IllegalStateException("database or disk is full")

        val status = transfer.imported()

        assertEquals(TransferOutcome.Refused(TransferRefusal.NothingReplaced), status.outcome)
        nothingWasChanged()
        assertTrue(world.toldAfterImport.isEmpty())
        assertTrue(world.lines(EventCategory.ERROR).single().contains("replacing the trips"))
    }

    @Test
    fun `if the points cannot be stored, no imported trip is left with another trip's track`() =
        runTest {
            val transfer = offered()
            world.points.failInsertOnceStoredAtLeast = 3

            val status = transfer.imported()

            val done = (status.outcome as TransferOutcome.Imported).done
            assertEquals(PointsTaken.NOT_STORED, done.pointsTaken)
            assertEquals(0L, done.points)
            assertEquals(everyKindOfTrip, world.main.trips)
            assertTrue(
                "The points of the trips that are gone must go",
                world.points.points.isEmpty(),
            )
            assertTrue(world.lines(EventCategory.ERROR).single().contains("raw GPS points"))
            assertTrue(world.lines(EventCategory.REPORT).single().contains("could NOT be stored"))
        }

    @Test
    fun `a file without points leaves the imported trips with none`() = runTest {
        val status = offered(fileOf(sourceOf(points = null))).imported()

        assertEquals(everyKindOfTrip, world.main.trips)
        assertTrue(world.points.points.isEmpty())
        val done = (status.outcome as TransferOutcome.Imported).done
        assertEquals(PointsTaken.NONE_IN_FILE, done.pointsTaken)
    }

    @Test
    fun `the points of a trip that started after the trips were replaced are not touched`() =
        runTest {
            // The highest id a trip had or has is 50, so the truck's next trip is trip 51. Its
            // first fix arrives between the two transactions.
            val ofANewTrip = RawPoint(3, 51, 3_000, 30, 51.2, -114.2, 5f, 1f)
            world.points.arrivesBeforeReplacing = ofANewTrip

            offered().imported()

            assertTrue(ofANewTrip in world.points.points)
            assertEquals(somePoints.size + 1, world.points.points.size)
            assertTrue(world.points.points.none { it.tripId == 40L || it.tripId == 50L })
        }

    @Test
    fun `a file without settings leaves the phone's settings as they are`() = runTest {
        val status = offered(fileOf(sourceOf(settings = null))).imported()

        assertEquals("Own Name", world.settings.current().reportName)
        val done = (status.outcome as TransferOutcome.Imported).done
        assertEquals(SettingsTaken.NONE_IN_FILE, done.settings)
        assertTrue(world.lines(EventCategory.PAIRING).isEmpty())
    }

    @Test
    fun `settings that cannot be stored are said, and the trips are in all the same`() = runTest {
        val unwritable = TransferWorld(temporaryFolder.newFolder(), settingsUnreadable = true)
        unwritable.documents.files[picked] = fileOf().toByteArray()
        val transfer = unwritable.transfer(backgroundScope)
        transfer.offerImport(picked)
        transfer.settled()

        val status = transfer.imported()

        val done = (status.outcome as TransferOutcome.Imported).done
        assertEquals(SettingsTaken.NOT_STORED, done.settings)
        assertEquals(everyKindOfTrip, unwritable.main.trips)
        assertEquals(4, unwritable.points.points.size)
    }

    @Test
    fun `only the newest few safety copies are kept`() = runTest {
        val transfer = offered()
        repeat(5) {
            world.nowMs += 60_000
            world.documents.files[picked] = fileOf().toByteArray()
            if (it > 0) {
                transfer.offerImport(picked)
                transfer.settled()
            }
            transfer.imported()
        }

        assertEquals(3, world.safetyCopies.all().size)
        assertEquals(world.nowMs, world.safetyCopies.all().first().writtenAtMs)
    }

    @Test
    fun `with no safety copy there is nothing to put back`() = runTest {
        val transfer = world.transfer(backgroundScope)

        transfer.offerSafetyCopy(world.nowMs)

        val refusal = TransferOutcome.Refused(TransferRefusal.NoSafetyCopy)
        assertEquals(refusal, transfer.settled().outcome)
    }

    @Test
    fun `an import of a file made on this very phone changes nothing that can be seen`() = runTest {
        val transfer = world.transfer(backgroundScope)
        transfer.exportTo(picked, includePoints = true)
        transfer.settled()
        val before = Triple<List<Trip>, List<SentReport>, TransferredSettings?>(
            world.main.trips,
            world.main.sentReports,
            null,
        )

        transfer.offerImport(picked)
        transfer.settled()
        transfer.imported()

        assertEquals(before.first, world.main.trips)
        assertEquals(before.second, world.main.sentReports)
        assertEquals(ownPoints.withoutIds(), world.points.points.map { it.copy(id = 0) })
        assertEquals(7, world.settings.current().truckAssociationId)
    }

    @Test
    fun `a failure that the log cannot take either ends up in a crash file, not in the process`() =
        runTest {
            val folder = temporaryFolder.newFolder()
            val silent = TransferWorld(folder, logUnwritable = true)
            val transfer = silent.transfer(backgroundScope)

            transfer.offerImport("content://files/gone.json")
            val status = transfer.settled()

            assertEquals(TransferOutcome.Refused(TransferRefusal.FileNotRead), status.outcome)
            assertEquals(1, File(folder, "crashes").list()?.size)
        }
}

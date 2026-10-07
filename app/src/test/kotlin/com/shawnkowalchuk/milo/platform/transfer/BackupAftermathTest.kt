package com.shawnkowalchuk.milo.platform.transfer

import com.shawnkowalchuk.milo.data.crash.CrashFileStore
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.settings.ConfirmedStep
import com.shawnkowalchuk.milo.data.settings.MiloSettings
import com.shawnkowalchuk.milo.data.settings.SettingsStore
import com.shawnkowalchuk.milo.data.transfer.BackupNote
import com.shawnkowalchuk.milo.data.transfer.BackupNoteKind
import com.shawnkowalchuk.milo.data.transfer.BackupNoteStore
import com.shawnkowalchuk.milo.platform.trip.FakeEventLogDao
import com.shawnkowalchuk.milo.platform.trip.FakeSettingsFile
import com.shawnkowalchuk.milo.platform.trip.UnreadableSettingsFile
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * What the first ordinary start after a backup or a restore does with the notes the backup
 * agent left: lines in the event log, and after a restore the settings put right for this
 * phone.
 */
class BackupAftermathTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val log = FakeEventLogDao()
    private val settings = SettingsStore(FakeSettingsFile())
    private val notes by lazy { BackupNoteStore(File(temporaryFolder.root, "notes")) }
    private var pairing = PairingHere(companionSupported = true, associatedAddresses = emptyList())
    private var soundIsHere = false
    private val pairingChecks = mutableListOf<String>()

    private fun aftermath(readFrom: SettingsStore = settings) = BackupAftermath(
        notes = notes,
        settings = readFrom,
        pairingHere = { pairing },
        ownSoundIsHere = { soundIsHere },
        checkPairing = { pairingChecks += it },
        eventLog = EventLogRepository(log),
        failures =
            TransferFailures(
                EventLogRepository(log),
                CrashFileStore(File(temporaryFolder.root, "crashes")),
            ) { 9_000 },
    )

    private fun lines(category: EventCategory) =
        log.entries.filter { it.category == category }.map { it.message }

    /** The settings as Android restores them: those of the phone the backup was made on. */
    private suspend fun restoredFromAnotherPhone() {
        settings.setTruck("AA:BB:CC:DD:EE:FF", "Work truck", 7)
        settings.setReportName("Sam Driver")
        settings.setGracePeriodSeconds(150)
        settings.setCustomSound("file:/data/user/0/milo/no_backup/trip_sound/own_1", "r2d2.mp3")
        for (step in ConfirmedStep.entries) settings.setConfirmedAtMs(step, 1_000)
        notes.leave(BackupNote(BackupNoteKind.RESTORED, 5_000))
    }

    @Test
    fun `a start with no note does nothing at all`() = runTest {
        settings.setTruck("AA:BB:CC:DD:EE:FF", "Work truck", 7)
        val before = settings.current()

        aftermath().settle()

        assertTrue(log.entries.isEmpty())
        assertEquals(before, settings.current())
        assertTrue(pairingChecks.isEmpty())
    }

    @Test
    fun `after a restore on another phone the truck is known and not believed to be paired`() =
        runTest {
            restoredFromAnotherPhone()

            aftermath().settle()

            val now = settings.current()
            assertEquals("AA:BB:CC:DD:EE:FF", now.truckAddress)
            assertEquals("Work truck", now.truckName)
            assertNull("The other phone's association must not be believed", now.truckAssociationId)
            assertEquals(listOf("after a restore"), pairingChecks)
            val pairingLine = lines(EventCategory.PAIRING).single()
            assertTrue(pairingLine, pairingLine.startsWith("After the restore: the truck (AA:BB"))
            assertTrue(pairingLine, pairingLine.contains("has to be paired again"))
        }

    @Test
    fun `after a restore the confirmations of the setup checklist are gone`() = runTest {
        restoredFromAnotherPhone()

        aftermath().settle()

        assertTrue(settings.current().confirmedAtMs.isEmpty())
        assertTrue(lines(EventCategory.PROCESS).single().contains("Go through Setup again"))
    }

    @Test
    fun `after a restore a sound whose file is not here is forgotten, and the line says so`() =
        runTest {
            restoredFromAnotherPhone()

            aftermath().settle()

            assertNull(settings.current().customSoundUri)
            assertNull(settings.current().customSoundName)
            assertTrue(lines(EventCategory.PROCESS).single().contains("chosen trip-start sound"))
        }

    @Test
    fun `a sound whose file is here stays, and the line does not mention it`() = runTest {
        restoredFromAnotherPhone()
        soundIsHere = true

        aftermath().settle()

        assertEquals("r2d2.mp3", settings.current().customSoundName)
        assertFalse(lines(EventCategory.PROCESS).single().contains("trip-start sound"))
    }

    @Test
    fun `after a restore every other setting is as it was restored`() = runTest {
        restoredFromAnotherPhone()
        val restored = settings.current()

        aftermath().settle()

        val expected =
            restored.copy(
                truckAssociationId = null,
                customSoundUri = null,
                customSoundName = null,
                // Its copy is not here, so it goes off the list of his own sounds too.
                ownSounds = emptyList(),
                confirmedAtMs = emptyMap(),
            )
        assertEquals(expected, settings.current())
        assertEquals("Sam Driver", settings.current().reportName)
        assertEquals(150, settings.current().gracePeriodSeconds)
    }

    @Test
    fun `restored onto an installation that still holds the association, the truck stays paired`() =
        runTest {
            restoredFromAnotherPhone()
            pairing = PairingHere(companionSupported = true, listOf("aa:bb:cc:dd:ee:ff"))

            aftermath().settle()

            assertEquals(7, settings.current().truckAssociationId)
            assertTrue(lines(EventCategory.PAIRING).single().contains("already watches for"))
            // The confirmations go all the same: an uninstall resets what they were about.
            assertTrue(settings.current().confirmedAtMs.isEmpty())
        }

    @Test
    fun `a restore with no truck in it leaves no truck`() = runTest {
        notes.leave(BackupNote(BackupNoteKind.RESTORED, 5_000))

        aftermath().settle()

        assertEquals(MiloSettings(), settings.current())
        assertTrue(lines(EventCategory.PAIRING).single().contains("no truck"))
    }

    @Test
    fun `a restore is dealt with once, and its lines are dated when it happened`() = runTest {
        restoredFromAnotherPhone()

        aftermath().settle()
        aftermath().settle()

        assertEquals(1, lines(EventCategory.PROCESS).size)
        assertEquals(1, pairingChecks.size)
        assertTrue(log.entries.all { it.atMs == 5_000L })
        assertTrue(notes.waiting().isEmpty())
    }

    @Test
    fun `a backup that was refused for its size is an error line that says what to do`() = runTest {
        val sizes = "MilO's files are 26.1 MB and the limit is 25.0 MB"
        notes.leave(BackupNote(BackupNoteKind.QUOTA_EXCEEDED, 3_000, sizes))

        aftermath().settle()

        val line = lines(EventCategory.ERROR).single()
        assertTrue(line, line.startsWith("Android did not back MilO up: $sizes."))
        assertTrue(line, line.contains("Nothing at all is backed up"))
        assertTrue(line, line.contains("Export all data"))
        assertEquals(3_000L, log.entries.single().atMs)
        assertTrue(notes.waiting().isEmpty())
        assertTrue(pairingChecks.isEmpty())
    }

    @Test
    fun `a backup that was collected, and a database that was not settled, are each one line`() =
        runTest {
            notes.leave(BackupNote(BackupNoteKind.COLLECTED, 1_000, "for a cloud backup"))
            notes.leave(BackupNote(BackupNoteKind.NOT_SETTLED, 900, "milo.db: it stayed in use"))

            aftermath().settle()

            val collected = lines(EventCategory.PROCESS).single()
            assertTrue(collected, collected.startsWith("Android collected MilO's data for a cloud"))
            assertTrue(lines(EventCategory.ERROR).single().contains("milo.db: it stayed in use"))
            // Oldest first.
            assertEquals(listOf(900L, 1_000L), log.entries.map { it.atMs })
        }

    @Test
    fun `settings that cannot be read are said once in the log, and the restore is tried again`() =
        runTest {
            notes.leave(BackupNote(BackupNoteKind.COLLECTED, 1_000, "for a cloud backup"))
            notes.leave(BackupNote(BackupNoteKind.RESTORED, 5_000))

            aftermath(readFrom = SettingsStore(UnreadableSettingsFile)).settle()

            // The note before the one that failed was told and removed; the restore waits.
            assertEquals(1, lines(EventCategory.PROCESS).size)
            val error = lines(EventCategory.ERROR).single()
            assertTrue(error, error.contains("reading what Android's backup left behind"))
            assertEquals(listOf(BackupNoteKind.RESTORED), notes.waiting().map { it.second.kind })
            assertTrue(pairingChecks.isEmpty())

            aftermath().settle()

            assertTrue(notes.waiting().isEmpty())
            assertEquals(listOf("after a restore"), pairingChecks)
        }

    @Test
    fun `no line of the aftermath names a person or an address`() = runTest {
        restoredFromAnotherPhone()

        aftermath().settle()

        for (entry in log.entries) {
            assertFalse(entry.message, entry.message.contains("Sam Driver"))
            assertFalse(entry.message, entry.message.contains("Work truck"))
        }
    }
}

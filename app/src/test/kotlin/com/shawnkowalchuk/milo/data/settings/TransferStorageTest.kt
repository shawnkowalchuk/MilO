package com.shawnkowalchuk.milo.data.settings

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import com.shawnkowalchuk.milo.core.report.ReportPeriod
import com.shawnkowalchuk.milo.core.schedule.DEFAULT_WORK_SCHEDULE
import com.shawnkowalchuk.milo.core.util.DistanceUnit
import com.shawnkowalchuk.milo.platform.trip.FakeSettingsFile
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What an export, an import and a restore read from and write to the settings: the record of
 * the last export, the settings that travel, and what is taken out after a restore.
 */
class TransferStorageTest {
    private val file = FakeSettingsFile()
    private val store = SettingsStore(file)

    private val arrived =
        TransferredSettings(
            truck = TransferredTruck("11:22:33:44:55:66", "Other truck"),
            gracePeriodSeconds = 300,
            minimumTripDistanceMetres = 1_000,
            soundEnabled = false,
            schedule = DEFAULT_WORK_SCHEDULE.withTracked(DayOfWeek.SUNDAY, true),
            ignoreTripsOutsideSchedule = true,
            drivingAlertEnabled = false,
            reportName = "Sam Driver",
            reportCompany = null,
            reportVehicle = "Ford F-150",
            accountantEmail = "accounts@example.ca",
            reminderEnabled = false,
            reminderDay = 15,
        )

    /** A phone that has been set up and used: everything that is about this phone is stored. */
    private suspend fun aPhoneInUse() {
        store.setTruck("AA:BB:CC:DD:EE:FF", "Work truck", 7)
        store.setReportName("Old Name")
        store.setReportCompany("Old Company")
        store.setCustomSound("file:/data/sound", "r2d2.mp3")
        store.setConfirmedAtMs(ConfirmedStep.XIAOMI_AUTOSTART, 111)
        store.setConfirmedAtMs(ConfirmedStep.XIAOMI_RECENTS_LOCK, 222)
        store.setAutoStartHeldOffSinceMs(333)
        store.setLastDrivingAlertAtMs(444)
        store.setLastProcessExitImportedAtMs(555)
        store.setReminderShown(ReminderShown(YearMonth.of(2026, 9), LocalDate.of(2026, 10, 6)))
        val september = ReportPeriod.Month(YearMonth.of(2026, 9))
        store.setReportHandOver(ReportHandOver(september, 3, 120, 9, DistanceUnit.KILOMETRES))
        store.setLastExport(LastExport(666, withPoints = true))
    }

    @Test
    fun `no export has been made until one is recorded`() = runTest {
        assertNull(store.current().lastExport)

        store.setLastExport(LastExport(1_791_316_931_000, withPoints = false))

        assertEquals(LastExport(1_791_316_931_000, withPoints = false), store.current().lastExport)
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { store.setLastExport(LastExport(-1, true)) }
        }
    }

    @Test
    fun `the key names of the last export are the ones this version writes`() = runTest {
        // A renamed key silently resets the value on the phone, so the names are pinned here.
        store.setLastExport(LastExport(42, withPoints = true))

        val stored = file.data.first()
        assertEquals(42L, stored[longPreferencesKey("last_export_at_ms")])
        assertEquals(true, stored[booleanPreferencesKey("last_export_with_points")])
    }

    @Test
    fun `what travels is the settings that mean the same on another phone`() = runTest {
        aPhoneInUse()

        val travelling = store.current().transferred()

        assertEquals(TransferredTruck("AA:BB:CC:DD:EE:FF", "Work truck"), travelling.truck)
        assertEquals("Old Name", travelling.reportName)
        assertEquals(MiloSettings().gracePeriodSeconds, travelling.gracePeriodSeconds)
        assertEquals(DEFAULT_WORK_SCHEDULE, travelling.schedule)
        // The association, the sound's file, the confirmations and the rest have no place in
        // the type at all: what is not in it cannot be exported by accident.
        assertNull(MiloSettings().transferred().truck)
    }

    @Test
    fun `an import puts every arrived setting in force`() = runTest {
        aPhoneInUse()

        store.replaceTransferred(arrived, TruckChange.Keep)

        assertEquals(arrived.copy(truck = null), store.current().transferred().copy(truck = null))
        // A setting the file does not have is forgotten, like the company here.
        assertNull(store.current().reportCompany)
    }

    @Test
    fun `an import leaves what this phone knows about itself, and forgets the waiting report`() =
        runTest {
            aPhoneInUse()
            val before = store.current()

            store.replaceTransferred(arrived, TruckChange.Keep)

            val after = store.current()
            assertEquals(before.truck(), after.truck())
            assertEquals(before.customSoundUri, after.customSoundUri)
            assertEquals(before.customSoundName, after.customSoundName)
            assertEquals(before.confirmedAtMs, after.confirmedAtMs)
            assertEquals(before.autoStartHeldOffSinceMs, after.autoStartHeldOffSinceMs)
            assertEquals(before.lastDrivingAlertAtMs, after.lastDrivingAlertAtMs)
            assertEquals(before.lastProcessExitImportedAtMs, after.lastProcessExitImportedAtMs)
            assertEquals(before.reminderShown, after.reminderShown)
            assertEquals(before.lastExport, after.lastExport)
            // It was made of trips that have just been replaced.
            assertNull(after.reportHandOver)
        }

    private fun MiloSettings.truck() = Triple(truckAddress, truckName, truckAssociationId)

    @Test
    fun `a truck that an import stores is stored without an association`() = runTest {
        aPhoneInUse()

        val nameless = TransferredTruck("11:22:33:44:55:66", null)
        store.replaceTransferred(arrived, TruckChange.Store(nameless))

        val after = store.current()
        assertEquals("11:22:33:44:55:66", after.truckAddress)
        assertNull(after.truckName)
        assertNull("The other phone's association must not be believed", after.truckAssociationId)
    }

    @Test
    fun `settings that no setter would take are refused whole`() = runTest {
        aPhoneInUse()
        val before = store.current()
        val bad =
            listOf(
                arrived.copy(gracePeriodSeconds = -1),
                arrived.copy(minimumTripDistanceMetres = -1),
                arrived.copy(reminderDay = 0),
                arrived.copy(reportName = " padded "),
                arrived.copy(accountantEmail = "not an address"),
                arrived.copy(truck = TransferredTruck(" ", null)),
                arrived.copy(truck = TransferredTruck("AA:BB:CC:DD:EE:FF", "")),
            )

        for (settings in bad) {
            assertFalse(settings.isStorable())
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { store.replaceTransferred(settings, TruckChange.Keep) }
            }
        }

        assertTrue(arrived.isStorable())
        assertEquals(before, store.current())
    }

    @Test
    fun `after a restore the confirmations of the other installation are gone in any case`() =
        runTest {
            aPhoneInUse()
            val before = store.current()

            store.forgetOtherInstallation(dropAssociation = false, dropOwnSound = false)

            val after = store.current()
            assertTrue(after.confirmedAtMs.isEmpty())
            assertEquals(before.copy(confirmedAtMs = emptyMap()), after)
        }

    @Test
    fun `after a restore the association goes and the truck's address and name stay`() = runTest {
        aPhoneInUse()

        store.forgetOtherInstallation(dropAssociation = true, dropOwnSound = false)

        val after = store.current()
        assertEquals("AA:BB:CC:DD:EE:FF", after.truckAddress)
        assertEquals("Work truck", after.truckName)
        assertNull(after.truckAssociationId)
        assertEquals("file:/data/sound", after.customSoundUri)
    }

    @Test
    fun `after a restore a sound whose file is not here is forgotten, name and all`() = runTest {
        aPhoneInUse()

        store.forgetOtherInstallation(dropAssociation = false, dropOwnSound = true)

        val after = store.current()
        assertNull(after.customSoundUri)
        assertNull(after.customSoundName)
        assertEquals(7, after.truckAssociationId)
    }

    @Test
    fun `what a restore takes out can be taken out twice, and where there is none of it`() =
        runTest {
            store.forgetOtherInstallation(dropAssociation = true, dropOwnSound = true)
            assertEquals(MiloSettings(), store.current())

            aPhoneInUse()
            store.forgetOtherInstallation(dropAssociation = true, dropOwnSound = true)
            val once = store.current()
            store.forgetOtherInstallation(dropAssociation = true, dropOwnSound = true)

            assertEquals(once, store.current())
        }
}

package com.shawnkowalchuk.milo.platform.system

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.settings.ConfirmedStep
import com.shawnkowalchuk.milo.data.settings.SettingsStore
import com.shawnkowalchuk.milo.platform.bluetooth.PairingState
import com.shawnkowalchuk.milo.platform.bluetooth.PairingStatus
import com.shawnkowalchuk.milo.platform.trip.FakeEventLogDao
import com.shawnkowalchuk.milo.platform.trip.FakeSettingsFile
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The checklist the screens share, against a stand-in for the phone: that it reads the phone
 * when asked and not before, follows the settings and the pairing by itself, and stores
 * confirmations.
 */
// runCurrent() is how a test lets the checklist's coroutines run. The API is marked experimental
// by the coroutines library; there is no stable equivalent.
@OptIn(ExperimentalCoroutinesApi::class)
class SetupChecklistTest {
    /** What the stand-in phone reports. A test changes it and asks for a refresh. */
    private var phone =
        SetupFacts(
            preflight =
                PreflightFacts(
                    fineLocationGranted = true,
                    backgroundLocationGranted = true,
                    locationSwitchedOn = true,
                    backgroundRestricted = false,
                    bluetoothGranted = true,
                ),
            notificationsEnabled = true,
            notificationPermissionAskable = true,
            ignoringBatteryOptimizations = true,
            exemptFromUnusedAppPause = true,
            batterySaverOn = false,
            isXiaomi = true,
            autostart = AutostartReading.LOOKS_ON,
        )
    private var reads = 0
    private var nowMs = 1_791_028_800_000L
    private val log = FakeEventLogDao()
    private val pairing = MutableStateFlow<PairingStatus?>(null)

    /** A checklist with a screen watching its rows, as the Setup screen does. */
    private fun TestScope.watchedChecklist(
        settingsFile: DataStore<Preferences> = FakeSettingsFile(),
    ): SetupChecklist {
        val checklist =
            SetupChecklist(
                readFacts = {
                    reads++
                    phone
                },
                settings = SettingsStore(settingsFile),
                pairing = pairing,
                eventLog = EventLogRepository(log),
                clock = { nowMs },
                scope = backgroundScope,
            )
        backgroundScope.launch { checklist.rows.collect {} }
        runCurrent()
        return checklist
    }

    private fun SetupChecklist.row(item: SetupItem): SetupRow =
        checkNotNull(rows.value) { "The phone has not been read" }.single { it.item == item }

    @Test
    fun `there are no rows until the phone has been read`() = runTest {
        val checklist = watchedChecklist()

        assertNull(checklist.rows.value)
        assertEquals(0, reads)

        checklist.refresh()
        runCurrent()

        assertEquals(1, reads)
        assertEquals(SetupItem.entries, checklist.rows.value?.map { it.item })
    }

    @Test
    fun `every refresh reads the phone again and the rows follow`() = runTest {
        val checklist = watchedChecklist()
        checklist.refresh()
        runCurrent()
        assertEquals(SetupState.OK, checklist.row(SetupItem.LOCATION_SERVICES).state)

        // Shawn switches location off in the phone's settings and comes back.
        phone = phone.copy(preflight = phone.preflight.copy(locationSwitchedOn = false))
        runCurrent()
        // Nothing told MilO: the row is stale until the screen asks.
        assertEquals(SetupState.OK, checklist.row(SetupItem.LOCATION_SERVICES).state)

        checklist.refresh()
        runCurrent()

        assertEquals(2, reads)
        assertEquals(SetupState.PROBLEM, checklist.row(SetupItem.LOCATION_SERVICES).state)
    }

    @Test
    fun `the truck row follows the pairing check without a refresh`() = runTest {
        val checklist = watchedChecklist()
        checklist.refresh()
        runCurrent()
        assertEquals(SetupDetail.TRUCK_NOT_CHECKED_YET, checklist.row(SetupItem.TRUCK).detail)

        pairing.value = PairingStatus(PairingState.ARMED, "association 7 is observed")
        runCurrent()

        assertEquals(SetupState.OK, checklist.row(SetupItem.TRUCK).state)
        assertEquals(1, reads)
    }

    @Test
    fun `a confirmation is stored with the time it was given, and can be taken back`() = runTest {
        val checklist = watchedChecklist()
        checklist.refresh()
        runCurrent()
        assertEquals(
            SetupState.NEEDS_CONFIRMATION,
            checklist.row(SetupItem.XIAOMI_RECENTS_LOCK).state,
        )

        checklist.setConfirmed(ConfirmedStep.XIAOMI_RECENTS_LOCK, confirmed = true)
        runCurrent()

        val confirmed = checklist.row(SetupItem.XIAOMI_RECENTS_LOCK)
        assertEquals(SetupState.OK, confirmed.state)
        assertEquals(nowMs, confirmed.confirmedAtMs)
        // The other rows that wait for a confirmation are untouched.
        assertEquals(
            SetupState.NEEDS_CONFIRMATION,
            checklist.row(SetupItem.XIAOMI_BATTERY_SAVER).state,
        )

        checklist.setConfirmed(ConfirmedStep.XIAOMI_RECENTS_LOCK, confirmed = false)
        runCurrent()

        val takenBack = checklist.row(SetupItem.XIAOMI_RECENTS_LOCK)
        assertEquals(SetupState.NEEDS_CONFIRMATION, takenBack.state)
        assertNull(takenBack.confirmedAtMs)
    }

    @Test
    fun `a phone that refuses to be read is logged, and the rows stay as they were`() = runTest {
        var refuse = false
        val checklist =
            SetupChecklist(
                readFacts = { if (refuse) throw SecurityException("not for you") else phone },
                settings = SettingsStore(FakeSettingsFile()),
                pairing = pairing,
                eventLog = EventLogRepository(log),
                clock = { nowMs },
                scope = backgroundScope,
            )
        backgroundScope.launch { checklist.rows.collect {} }
        checklist.refresh()
        runCurrent()
        val before = checklist.rows.value

        refuse = true
        checklist.refresh()
        runCurrent()

        // The home screen asks for this reading too: it must never take the app down.
        assertEquals(before, checklist.rows.value)
        val error = log.entries.single { it.category == EventCategory.ERROR }
        assertTrue(error.message.contains("could not read the phone's settings"))
        assertTrue(error.detail.orEmpty().contains("SecurityException"))
    }

    @Test
    fun `settings that cannot be read are logged, and the rows ask for confirmation again`() =
        runTest {
            val checklist = watchedChecklist(settingsFile = UnreadableSettingsFile())
            checklist.refresh()
            runCurrent()

            // The rows are still there: an unreadable file must not blank the checklist.
            assertEquals(
                SetupState.NEEDS_CONFIRMATION,
                checklist.row(SetupItem.XIAOMI_BATTERY_SAVER).state,
            )
            val errors = log.entries.filter { it.category == EventCategory.ERROR }
            assertEquals(1, errors.size)
            assertTrue(errors.single().message.contains("could not read the settings"))
        }
}

/** A settings file as it is after it has been damaged: every read throws. */
private class UnreadableSettingsFile : DataStore<Preferences> {
    override val data: Flow<Preferences> = flow { throw IOException("the file is damaged") }

    override suspend fun updateData(
        transform: suspend (t: Preferences) -> Preferences,
    ): Preferences = throw IOException("the file is damaged")
}

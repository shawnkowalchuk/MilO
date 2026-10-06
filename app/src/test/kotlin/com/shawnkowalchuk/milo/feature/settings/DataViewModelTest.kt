package com.shawnkowalchuk.milo.feature.settings

import com.shawnkowalchuk.milo.data.transfer.everyKindOfTrip
import com.shawnkowalchuk.milo.platform.transfer.TransferWorld
import com.shawnkowalchuk.milo.platform.transfer.settled
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The first state of the card for backup, export and import. The card moves the screen to
 * whatever changes under its buttons, so what it is first drawn with has to be what really
 * stands there.
 */
// A ViewModel's coroutines run on the main dispatcher, which a plain JVM test has to supply
// (setMain), and runCurrent() lets them run. Both are marked experimental by the coroutines
// library; there is no stable equivalent.
@OptIn(ExperimentalCoroutinesApi::class)
class DataViewModelTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `Settings opened again gets what the last export came to as its first state`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val world = TransferWorld(temporaryFolder.root)
        world.main.trips = everyKindOfTrip
        val transfer = world.transfer(backgroundScope)
        transfer.exportTo("content://files/MilO-export.json", includePoints = false)
        transfer.settled()

        // The Settings screen is opened: a new ViewModel, as after Back and the cog again.
        val viewModel =
            DataViewModel(transfer, world.settings.settings, flowOf(false), { 0L }) {
                ZoneId.of("America/Edmonton")
            }
        val drawnWith = mutableListOf<DataCardState?>()
        backgroundScope.launch { viewModel.state.toList(drawnWith) }
        runCurrent()

        // Nothing made up comes first: a card drawn with no lines and then with this one
        // would take the line for news and move the screen to it.
        assertNull(drawnWith.first())
        val first = drawnWith.filterNotNull().first()
        assertEquals(listOf(OutcomeLine.Exported(8, 0, null)), first.lines)
    }
}

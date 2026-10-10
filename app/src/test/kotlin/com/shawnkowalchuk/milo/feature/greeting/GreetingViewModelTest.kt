package com.shawnkowalchuk.milo.feature.greeting

import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.platform.trip.FakeEventLogDao
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the Log is told when a picture of the mascot cannot be read. */
// A ViewModel's coroutines run on the main dispatcher, which a plain JVM test has to supply
// (setMain), and runCurrent() lets them run. Both are marked experimental by the coroutines
// library; there is no stable equivalent.
@OptIn(ExperimentalCoroutinesApi::class)
class GreetingViewModelTest {
    private val log = FakeEventLogDao()

    @After
    fun giveTheMainDispatcherBack() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a picture that cannot be read is one error line, with what went wrong`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel = GreetingViewModel(EventLogRepository(log), clock = { 42L })
        assertEquals(0, log.entries.size)

        viewModel.onUnreadable(IOException("mascot_walk is not an animated picture"))
        runCurrent()

        val line = log.entries.single()
        assertEquals(42L, line.atMs)
        assertEquals(EventCategory.ERROR, line.category)
        assertEquals("MilO did not greet: a picture of its mascot could not be read", line.message)
        assertTrue(line.detail.orEmpty().contains("mascot_walk is not an animated picture"))
    }
}

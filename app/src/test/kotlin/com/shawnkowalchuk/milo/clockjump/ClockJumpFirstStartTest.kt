package com.shawnkowalchuk.milo.clockjump

import com.shawnkowalchuk.milo.core.clock.ClockNews
import com.shawnkowalchuk.milo.core.clock.DAY_MS
import com.shawnkowalchuk.milo.core.schedule.TripCategory
import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.platform.trip.FakeWorld
import com.shawnkowalchuk.milo.platform.trip.TripTrigger
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val HOUR_SECONDS = 3_600

/**
 * The one start of MilO that has no anchor to go by: the first MilO process after a restart of
 * the phone (or after the update that brought the clock), **started in the seconds the date is
 * set ahead.** Android starts MilO for that very change of the clock, so it is no coincidence.
 *
 * Found by the verification of 2026-10-09, when such a MilO stayed a day ahead for hours, in
 * every later process too, dated the next drive tomorrow and stepped back a day ten minutes
 * into it. Its anchor is now on probation (ADR-006): it takes the phone's time as soon as the
 * phone's clock comes back. The real trip controller, rules and storage stand-ins run on MilO's
 * real clock ([ClockJumpScene]).
 *
 * The scene starts on Friday 2 October 2026 at 12:00 UTC, a work day inside the default hours.
 */
class ClockJumpFirstStartTest {
    private val world = FakeWorld()

    @Test
    fun `started inside the jump after a restart, MilO is right once the date is back`() = runTest {
        val scene = ClockJumpScene(this, world)
        // The phone is restarted and nothing starts MilO. Then the date trick, and the
        // change of the clock has Android start MilO's process.
        scene.goTo(100)
        scene.dateSetAhead()
        scene.phone.reboot()
        scene.processStarts()
        // For these seconds MilO goes by the date: the phone's clock is all it has.
        assertEquals(DAY_MS, scene.nowMs - scene.phone.trueNowMs)

        scene.goTo(109)
        scene.dateSetBack()

        // The next reading of the time by anything, here MilO being opened.
        scene.trigger(TripTrigger.RECONCILE, "app opened")
        assertEquals(scene.phone.trueNowMs, scene.nowMs)
        assertEquals(listOf<ClockNews>(ClockNews.StartedAhead(DAY_MS)), scene.phone.news)

        // Three more short-lived processes over the next hours: each has the right time.
        for (second in listOf(600, HOUR_SECONDS, 3 * HOUR_SECONDS)) {
            scene.goTo(second)
            scene.processStarts()
            assertEquals(scene.phone.trueNowMs, scene.nowMs)
        }

        // The afternoon's drive, 15:01 to 15:11: one trip, dated when it was driven,
        // Friday's and Business, and the clock does not step during it.
        val start = 3 * HOUR_SECONDS + 60
        scene.truckConnects(start)
        val end = scene.drive(fromSecond = start + 5, toSecond = start + 600)
        scene.stand(end, fromSecond = start + 605, toSecond = start + 1_205)

        val trip = world.trips.rows.single()
        assertEquals(TripStatus.FINISHED, trip.status)
        assertEquals(scene.realTimeOf(start), trip.startedAtMs)
        assertEquals(scene.realTimeOf(start + 600), trip.endedAtMs)
        assertEquals(end, trip.distanceMetres, 1.0)
        assertEquals(TripCategory.BUSINESS, trip.category)
        assertEquals(1, scene.phone.news.size)
        assertTrue(world.points.rows.all { it.wallClockMs <= scene.realTimeOf(start + 1_205) })
    }

    @Test
    fun `the process that took the date is gone before it is back, and the next one is right`() =
        runTest {
            val scene = ClockJumpScene(this, world)
            scene.goTo(100)
            scene.dateSetAhead()
            scene.phone.reboot()
            scene.processStarts()
            scene.goTo(109)
            scene.dateSetBack()

            // Nothing of MilO runs until the truck connects, three hours later. The new
            // process reads the stored anchor, a day ahead, and its first reading sees the
            // phone's clock behind it.
            val start = 3 * HOUR_SECONDS
            scene.goTo(start)
            scene.newProcess()
            scene.truckConnects(start)
            val end = scene.drive(fromSecond = start + 5, toSecond = start + 600)
            scene.stand(end, fromSecond = start + 605, toSecond = start + 1_205)

            val trip = world.trips.rows.single()
            assertEquals(scene.realTimeOf(start), trip.startedAtMs)
            assertEquals(scene.realTimeOf(start + 600), trip.endedAtMs)
            assertEquals(TripCategory.BUSINESS, trip.category)
            assertEquals(listOf<ClockNews>(ClockNews.StartedAhead(DAY_MS)), scene.phone.news)
        }
}

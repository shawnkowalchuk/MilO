package com.shawnkowalchuk.milo.clockjump

import com.shawnkowalchuk.milo.core.trip.TripStartCause
import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.platform.trip.FakeWorld
import com.shawnkowalchuk.milo.platform.trip.TripTrigger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A drive of two kilometres last moves here, seconds into the scene. */
private const val STOPPED_AT = 105

/** How far that drive goes. */
private const val DRIVEN_METRES = 2_000.0

private const val HOUR_SECONDS = 3_600
private const val GRACE_SECONDS = 120
private const val GRACE_MS = GRACE_SECONDS * 1000L

/**
 * The phone's date is set one day ahead by hand for a few seconds and then comes back (the
 * owner's habit, ADR-006), **while automatic start is held off after End, or while a grace
 * period is running after a disconnect.** The real trip controller, rules, ledger and storage
 * stand-ins run on MilO's real clock ([ClockJumpScene]), and each test asserts that the
 * hold-off and the grace period are untouched.
 *
 * They began as the investigation's proofs of what the jump did before MilO had a clock of its
 * own (2026-10-07): the 12 hours of the hold-off over at once, the grace period over at once, a
 * cut stored a day ahead. The emulator's runs 5 and 7 of that day are the first and the second
 * test. The scenes are the same; the outcomes are now the right ones.
 */
// See TripControllerTest for why runCurrent() needs the opt-in.
@OptIn(ExperimentalCoroutinesApi::class)
class ClockJumpHoldOffGraceTest {
    private val world = FakeWorld()

    // ---- The hold-off after End with the truck connected ----------------------------------------

    @Test
    fun `held off, a process started inside the jump starts no trip, and the 12 hours stand`() =
        runTest {
            val scene = ClockJumpScene(this, world)
            scene.truckConnects(second = 0)
            scene.drive(fromSecond = 5, toSecond = STOPPED_AT)
            scene.goTo(110)
            scene.trigger(TripTrigger.MANUAL_END, "End button")
            assertEquals(scene.realTimeOf(110), world.settings.current().autoStartHeldOffSinceMs)

            // 3 h 40 min later, as on 2026-10-06 (End at 18:03, the date set ahead at 21:43).
            // The daily alarm, due at once on the new date, starts MilO's process. Before the
            // fix that reading came "24 hours after End": the hold-off was released, and a trip
            // started with the sound beside a truck that had not moved, dated tomorrow.
            val jump = 110 + 13_200
            scene.goTo(jump)
            scene.dateSetAhead()
            scene.processStarts()
            scene.goTo(jump + 10)
            scene.dateSetBack()
            scene.trigger(TripTrigger.RECONCILE, "app opened")

            assertEquals(1, world.trips.rows.size)
            assertEquals(0, scene.service.tripStartsAnnounced)
            assertFalse(scene.service.recording)
            assertFalse(scene.service.watchingParked)
            assertEquals(scene.realTimeOf(110), world.settings.current().autoStartHeldOffSinceMs)
            assertTrue(world.logged(EventCategory.TRIP).none { it.endsWith(": TIME_LIMIT") })

            // The 12 hours end when they really have, and a reading then starts a trip.
            scene.goTo(110 + 12 * HOUR_SECONDS - 60)
            scene.trigger(TripTrigger.RECONCILE, "app opened")
            assertEquals(1, world.trips.rows.size)
            scene.goTo(110 + 12 * HOUR_SECONDS + 60)
            scene.trigger(TripTrigger.RECONCILE, "app opened")

            val started = world.trips.rows.last()
            assertEquals(2, world.trips.rows.size)
            assertEquals(TripStartCause.TRUCK, started.startedBy)
            assertEquals(scene.realTimeOf(110 + 12 * HOUR_SECONDS + 60), started.startedAtMs)
            assertNull(world.settings.current().autoStartHeldOffSinceMs)
        }

    // ---- The grace period after a disconnect ----------------------------------------------------

    @Test
    fun `a grace period runs on through the jump, and a reconnect is the same trip`() = runTest {
        val scene = ClockJumpScene(this, world)
        scene.truckConnects(second = 0)
        scene.drive(fromSecond = 5, toSecond = STOPPED_AT)
        scene.truckDisconnects(second = 110)
        scene.fix(DRIVEN_METRES, second = 115, speedMetresPerSecond = 0f)

        // Ten seconds into its two minutes. Before the fix the first fix of the jump ended
        // the grace period and the trip, and the truck coming back was a second trip.
        scene.dateSetAhead()
        scene.fix(DRIVEN_METRES, second = 120, speedMetresPerSecond = 0f)
        scene.minuteCheck(122)
        val open = world.trips.rows.single()
        assertEquals(TripStatus.OPEN, open.status)
        assertEquals(scene.realTimeOf(110) + GRACE_MS, open.graceDeadlineMs)

        scene.goTo(126)
        scene.dateSetBack()
        scene.truckConnects(second = 140)

        val trip = world.trips.rows.single()
        assertEquals(TripStatus.OPEN, trip.status)
        assertNull(trip.graceDeadlineMs)
        assertEquals(1, scene.service.tripStartsAnnounced)
    }

    @Test
    fun `a grace period that began before the jump still ends when its two minutes are up`() =
        runTest {
            val scene = ClockJumpScene(this, world)
            scene.truckConnects(second = 0)
            scene.drive(fromSecond = 5, toSecond = STOPPED_AT)
            scene.truckDisconnects(second = 110)
            scene.dateSetAhead()
            scene.fix(DRIVEN_METRES, second = 120, speedMetresPerSecond = 0f)
            scene.goTo(126)
            scene.dateSetBack()

            scene.stand(DRIVEN_METRES, fromSecond = 130, toSecond = 110 + GRACE_SECONDS - 5)
            assertEquals(TripStatus.OPEN, world.trips.rows.single().status)
            scene.stand(DRIVEN_METRES, fromSecond = 110 + GRACE_SECONDS, toSecond = 240)

            val trip = world.trips.rows.single()
            assertEquals(TripStatus.FINISHED, trip.status)
            assertEquals(scene.realTimeOf(STOPPED_AT), trip.endedAtMs)
            assertEquals(DRIVEN_METRES, trip.distanceMetres, 1.0)
        }

    @Test
    fun `a disconnect inside the jump - the walk away from the truck is not counted`() = runTest {
        val scene = ClockJumpScene(this, world)
        scene.truckConnects(second = 0)
        scene.drive(fromSecond = 5, toSecond = STOPPED_AT)

        scene.goTo(106)
        scene.dateSetAhead()
        scene.truckDisconnects(second = 107)
        val grace = world.trips.rows.single()
        assertEquals(scene.realTimeOf(107), grace.graceStartedAtMs)
        scene.goTo(112)
        scene.dateSetBack()

        // Two minutes of walking away with the phone, at 2 m/s.
        for (second in 115..230 step 5) {
            scene.fix(DRIVEN_METRES + (second - STOPPED_AT) * 2.0, second, 2f)
        }

        // The trip is cut where the truck was found gone. Before the fix that moment was
        // stored a day ahead of every fix that followed, nothing was cut, and the walk was
        // counted as driving.
        val trip = world.trips.rows.single()
        assertEquals(TripStatus.FINISHED, trip.status)
        assertTrue(checkNotNull(trip.endedAtMs) <= scene.realTimeOf(107))
        assertEquals(DRIVEN_METRES, trip.distanceMetres, 5.0)
    }

    @Test
    fun `a grace deadline stored inside the jump is the real one, and a new process keeps to it`() =
        runTest {
            val scene = ClockJumpScene(this, world)
            scene.truckConnects(second = 0)
            scene.drive(fromSecond = 5, toSecond = STOPPED_AT)
            scene.goTo(106)
            scene.dateSetAhead()
            scene.truckDisconnects(second = 107)
            assertEquals(
                scene.realTimeOf(107) + GRACE_MS,
                world.trips.rows.single().graceDeadlineMs,
            )
            scene.goTo(112)
            scene.dateSetBack()

            // MilO's process is killed, and Android brings the sticky trip service back.
            // Before the fix its timer was then 24 hours away, and half an hour of minute
            // checks that all read "not connected" left the trip open and recording.
            scene.newProcess()
            scene.controller.onServiceStarted(scene.service, request = null)
            runCurrent()
            assertNotNull(scene.timerRunsOutInMs)
            assertEquals(GRACE_MS - 5_000, scene.timerRunsOutInMs)

            scene.goTo(107 + GRACE_SECONDS + 1)

            val trip = world.trips.rows.single()
            assertEquals(TripStatus.FINISHED, trip.status)
            assertEquals(1, scene.service.stops)
        }
}

package com.shawnkowalchuk.milo.feature.home

import com.shawnkowalchuk.milo.core.trip.TripStartCause
import com.shawnkowalchuk.milo.platform.trip.CurrentTrip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The truck's arrival as Home shows it: when "Connecting…" is played, for how long, what
 * follows it, and everything that must not play it.
 */
class ShownArrivalTest {
    private val away = TruckState.NOT_CONNECTED
    private val connected = TruckState.CONNECTED_NOT_RECORDING
    private val recording = TruckState.CONNECTED_RECORDING

    /** Home has come on screen and has seen the truck away, at time 0. */
    private val watching = ShownArrival().saw(away, TruckSight.AWAY, atMs = 0)

    private fun ShownArrival.sees(state: TruckState, sight: TruckSight, atMs: Long) =
        saw(state, sight, atMs)

    /** The truck connects and its trip opens in the same look, one second in. */
    private val arrived = watching.sees(recording, TruckSight.RECORDING, atMs = 1_000)

    // ---- When it plays ------------------------------------------------------------------------

    @Test
    fun `a truck that connects in front of the owner is shown connecting for 2,6 seconds`() {
        val seen = watching.sees(connected, TruckSight.CONNECTED, atMs = 1_000)

        assertEquals(ArrivalStage.CONNECTING, seen.stage)
        assertEquals(ArrivalStage.CONNECTING, seen.at(3_599).stage)
        assertEquals(ArrivalStage.NONE, seen.at(3_600).stage)
        assertEquals(2_600L, seen.msUntilNextStage(1_000))
    }

    @Test
    fun `a connect that starts a trip goes on to Connected for 1,2 seconds, and then the trip`() {
        assertEquals(ArrivalStage.CONNECTING, arrived.at(3_599).stage)
        assertEquals(ArrivalStage.CONNECTED, arrived.at(3_600).stage)
        assertEquals(ArrivalStage.CONNECTED, arrived.at(4_799).stage)
        assertEquals(ArrivalStage.HANDED_OVER, arrived.at(4_800).stage)
        assertNull(arrived.at(4_800).msUntilNextStage(4_800))
    }

    @Test
    fun `the trip may open a moment after the connect, and the times are the connect's`() {
        val seen =
            watching
                .sees(connected, TruckSight.CONNECTED, atMs = 1_000)
                .sees(recording, TruckSight.RECORDING, atMs = 1_300)

        assertEquals(ArrivalStage.CONNECTING, seen.at(3_599).stage)
        assertEquals(ArrivalStage.CONNECTED, seen.at(3_600).stage)
        assertEquals(ArrivalStage.HANDED_OVER, seen.at(4_800).stage)
    }

    @Test
    fun `a trip the truck started before its link is reported plays the arrival too`() {
        val found = watching.sees(TruckState.RECORDING_WITHOUT_TRUCK, TruckSight.FOUND, 1_000)
        val confirmed = found.sees(recording, TruckSight.RECORDING, atMs = 1_400)

        assertEquals(ArrivalStage.CONNECTING, found.stage)
        assertEquals(ArrivalStage.CONNECTED, confirmed.at(3_600).stage)
        assertEquals(ArrivalStage.HANDED_OVER, confirmed.at(4_800).stage)
    }

    @Test
    fun `a trip whose truck is still not confirmed is shown as the trip, never as Connected`() {
        val found = watching.sees(TruckState.RECORDING_WITHOUT_TRUCK, TruckSight.FOUND, 1_000)

        assertEquals(ArrivalStage.HANDED_OVER, found.at(3_600).stage)
    }

    @Test
    fun `a look that comes late does not make the arrival longer`() {
        // Nothing looked at the clock for a while: both stages have run out.
        assertEquals(ArrivalStage.HANDED_OVER, arrived.at(60_000).stage)
        // The second stage is timed from the end of the first, not from the late look.
        val late = arrived.at(4_000)
        assertEquals(ArrivalStage.CONNECTED, late.stage)
        assertEquals(800L, late.msUntilNextStage(4_000))
    }

    @Test
    fun `a trip that opens after Connecting has run out is shown without a second wait`() {
        val seen = watching.sees(connected, TruckSight.CONNECTED, atMs = 1_000)
        val later = seen.sees(recording, TruckSight.RECORDING, atMs = 4_000)

        assertEquals(ArrivalStage.NONE, later.stage)
    }

    @Test
    fun `the Start tile is told what the truck's state was before it arrived`() {
        assertEquals(away, arrived.from)
        assertEquals(away, arrived.at(4_800).from)
        assertNull(watching.from)
        assertNull(watching.sees(connected, TruckSight.CONNECTED, 1_000).at(3_600).from)
    }

    // ---- When it does not play ------------------------------------------------------------------

    @Test
    fun `the first look never plays, whatever it shows`() {
        for (sight in TruckSight.entries) {
            assertEquals(
                sight.name,
                ArrivalStage.NONE,
                ShownArrival().sees(recording, sight, 0).stage,
            )
        }
    }

    @Test
    fun `a truck that was not read yet and then reads as connected is not an arrival`() {
        val opened = ShownArrival().sees(TruckState.CHECKING, TruckSight.UNKNOWN, atMs = 0)

        assertEquals(ArrivalStage.NONE, opened.sees(connected, TruckSight.CONNECTED, 200).stage)
        assertEquals(ArrivalStage.NONE, opened.sees(recording, TruckSight.RECORDING, 200).stage)
    }

    @Test
    fun `a trip started with Start is shown at once`() {
        val byHand = watching.sees(TruckState.RECORDING_WITHOUT_TRUCK, TruckSight.OTHER_TRIP, 1_000)

        assertEquals(ArrivalStage.NONE, byHand.stage)
    }

    @Test
    fun `a trip that starts beside a parked truck is shown at once`() {
        val parked = ShownArrival().sees(TruckState.PARKED_WAITING, TruckSight.CONNECTED, 0)

        assertEquals(ArrivalStage.NONE, parked.sees(recording, TruckSight.RECORDING, 1_000).stage)
    }

    @Test
    fun `the truck joining a trip that Start began is no arrival`() {
        val byHand = watching.sees(TruckState.RECORDING_WITHOUT_TRUCK, TruckSight.OTHER_TRIP, 1_000)

        assertEquals(ArrivalStage.NONE, byHand.sees(recording, TruckSight.OTHER_TRIP, 2_000).stage)
    }

    // ---- What ends it early ---------------------------------------------------------------------

    @Test
    fun `a truck that goes again ends the arrival at once`() {
        val gone = arrived.sees(away, TruckSight.AWAY, atMs = 2_000)

        assertEquals(ArrivalStage.NONE, gone.stage)
        assertNull(gone.msUntilNextStage(2_000))
        assertNull(gone.from)
    }

    @Test
    fun `a trip that waits for the truck, or that Start began, ends the arrival at once`() {
        val waiting = TruckState.WAITING_TO_RECONNECT

        assertEquals(ArrivalStage.NONE, arrived.sees(waiting, TruckSight.OTHER_TRIP, 2_000).stage)
        assertEquals(ArrivalStage.NONE, arrived.sees(waiting, TruckSight.OTHER_TRIP, 4_000).stage)
    }

    @Test
    fun `a press of Start ends whatever was showing, in every stage`() {
        assertEquals(ArrivalStage.NONE, arrived.at(2_000).startPressed().stage)
        assertEquals(ArrivalStage.NONE, arrived.at(4_000).startPressed().stage)
        assertEquals(ArrivalStage.NONE, arrived.at(5_000).startPressed().stage)
        // And what is seen afterwards does not bring it back.
        val pressed = arrived.at(2_000).startPressed()
        assertEquals(ArrivalStage.NONE, pressed.sees(recording, TruckSight.RECORDING, 2_100).stage)
    }

    @Test
    fun `a truck that the press of Start itself finds connected is no arrival`() {
        // The press reads the truck. For a moment Home sees it connected, before the trip that
        // Start began is open.
        val pressed = watching.startPressed()
        val found = pressed.sees(connected, TruckSight.CONNECTED, atMs = 1_000)

        assertEquals(ArrivalStage.NONE, found.stage)
        assertEquals(
            ArrivalStage.NONE,
            found.sees(recording, TruckSight.OTHER_TRIP, atMs = 1_050).stage,
        )
    }

    @Test
    fun `after a press that started nothing, the next arrival plays`() {
        val refused = watching.startPressed().sees(away, TruckSight.AWAY, atMs = 1_000)

        assertEquals(
            ArrivalStage.CONNECTING,
            refused.sees(recording, TruckSight.RECORDING, atMs = 9_000).stage,
        )
    }

    @Test
    fun `Connected is given up when the truck's link is no longer confirmed, for the trip`() {
        val unsure = arrived.sees(TruckState.RECORDING_WITHOUT_TRUCK, TruckSight.FOUND, 4_000)

        assertEquals(ArrivalStage.HANDED_OVER, unsure.stage)
        // A stage is only ever followed by a later one: the link coming back changes nothing.
        assertEquals(
            ArrivalStage.HANDED_OVER,
            unsure.sees(recording, TruckSight.RECORDING, 4_200).stage,
        )
    }

    @Test
    fun `after the trip has ended the next arrival plays again`() {
        val ended = arrived.at(60_000).sees(away, TruckSight.AWAY, atMs = 600_000)
        val again = ended.sees(recording, TruckSight.RECORDING, atMs = 900_000)

        assertEquals(ArrivalStage.NONE, ended.stage)
        assertEquals(ArrivalStage.CONNECTING, again.stage)
        assertEquals(2_600L, again.msUntilNextStage(900_000))
    }

    @Test
    fun `End pressed with the truck connected shows that at once, and is no arrival`() {
        val ended = arrived.at(
            60_000,
        ).sees(TruckState.CONNECTED_HELD_OFF, TruckSight.CONNECTED, 70_000)

        assertEquals(ArrivalStage.NONE, ended.stage)
    }

    // ---- What a look comes to -------------------------------------------------------------------

    private fun trip(by: TripStartCause, waiting: Boolean = false) = CurrentTrip(
        tripId = 7,
        startedAtMs = 1_000,
        startedBy = by,
        distanceMetres = 0.0,
        waitingForTruck = waiting,
    )

    @Test
    fun `with no trip a look is the truck's own state`() {
        assertEquals(TruckSight.UNKNOWN, truckSight(TruckState.CHECKING, null))
        assertEquals(TruckSight.AWAY, truckSight(TruckState.NOT_CONNECTED, null))
        assertEquals(TruckSight.AWAY, truckSight(TruckState.NOT_CONNECTED_SETUP_OPEN, null))
        assertEquals(TruckSight.AWAY, truckSight(TruckState.NO_TRUCK, null))
        assertEquals(TruckSight.CONNECTED, truckSight(TruckState.CONNECTED_NOT_RECORDING, null))
        assertEquals(TruckSight.CONNECTED, truckSight(TruckState.CONNECTED_HELD_OFF, null))
        assertEquals(TruckSight.CONNECTED, truckSight(TruckState.PARKED_WAITING, null))
        assertEquals(TruckSight.CONNECTED, truckSight(TruckState.PARKED_NOT_WATCHED, null))
    }

    @Test
    fun `a trip the truck started is one that an arrival leads to`() {
        val byTruck = trip(TripStartCause.TRUCK)

        assertEquals(TruckSight.RECORDING, truckSight(TruckState.CONNECTED_RECORDING, byTruck))
        assertEquals(TruckSight.FOUND, truckSight(TruckState.RECORDING_WITHOUT_TRUCK, byTruck))
    }

    @Test
    fun `a trip that Start began, or that waits for the truck, is shown at once`() {
        val byHand = trip(TripStartCause.MANUAL)
        val waiting = trip(TripStartCause.TRUCK, waiting = true)

        assertEquals(TruckSight.OTHER_TRIP, truckSight(TruckState.CONNECTED_RECORDING, byHand))
        assertEquals(TruckSight.OTHER_TRIP, truckSight(TruckState.RECORDING_WITHOUT_TRUCK, byHand))
        assertEquals(TruckSight.OTHER_TRIP, truckSight(TruckState.WAITING_TO_RECONNECT, waiting))
    }
}

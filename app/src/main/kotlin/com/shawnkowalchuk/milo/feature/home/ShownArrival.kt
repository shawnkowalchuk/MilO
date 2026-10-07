package com.shawnkowalchuk.milo.feature.home

import com.shawnkowalchuk.milo.core.trip.TripStartCause
import com.shawnkowalchuk.milo.platform.trip.CurrentTrip

// The truck's arrival, as Home shows it while Home is on screen: "Connecting…" for a moment,
// then "Connected", then, if the arrival started a trip, the trip. The owner's design plays it
// that way, and he asked for it to be built as drawn (FINDINGS_LOG, 2026-10-07).
//
// Android tells MilO that the truck is connected, never that it is about to be. So the
// "Connecting…" look is shown just after the truck was found, not before: it is how Home
// passes the news on, and it lasts a fixed time. Nothing here decides anything about a trip.
// The trip is recorded from its first second, whatever Home is showing.
//
// Plain values and pure functions: what was seen, what is seen now, and when. The screen
// supplies the time and draws the answer.

/** How long the tile says "Connecting…" after the truck was seen to arrive. The design's time. */
internal const val CONNECTING_MS = 2_600L

/**
 * How long the tile then says "Connected" and "Trip recording started", before Home changes to
 * the layout of a trip being recorded.
 */
internal const val CONNECTED_MS = 1_200L

/** What Home sees of the truck and of a trip at one look, as far as an arrival goes. */
internal enum class TruckSight {
    /** Not read yet. Nothing follows from it. */
    UNKNOWN,

    /** No trip, and the truck is not connected. The only sight an arrival can follow. */
    AWAY,

    /** No trip, and the truck is connected. */
    CONNECTED,

    /**
     * A trip the truck started, while the truck is not confirmed as connected. Seen for a
     * moment when the phone notices the truck before its Bluetooth link is reported.
     */
    FOUND,

    /** A trip the truck started, with the truck connected. */
    RECORDING,

    /**
     * A trip that Home shows at once: started with the Start button, or waiting for the truck
     * to reconnect.
     */
    OTHER_TRIP,
    ;

    /** The truck is there: connected, or found and recording. */
    val hasArrived: Boolean get() = this == CONNECTED || this == FOUND || this == RECORDING

    /** A trip the truck started is open. */
    val isTruckTrip: Boolean get() = this == FOUND || this == RECORDING
}

/** What a look at the truck's [state] and at the [trip] being recorded comes to. */
internal fun truckSight(state: TruckState, trip: CurrentTrip?): TruckSight = when {
    trip == null && state == TruckState.CHECKING -> TruckSight.UNKNOWN
    trip == null && state.connected -> TruckSight.CONNECTED
    trip == null -> TruckSight.AWAY
    trip.startedBy == TripStartCause.MANUAL || trip.waitingForTruck -> TruckSight.OTHER_TRIP
    state.connected -> TruckSight.RECORDING
    else -> TruckSight.FOUND
}

/** How much of an arrival Home is showing. A stage is only ever followed by a later one. */
internal enum class ArrivalStage {
    /** Nothing of an arrival: Home shows what is so. */
    NONE,

    /** The layout without a trip, whatever is recorded already, and the tile "Connecting…". */
    CONNECTING,

    /**
     * Still the layout without a trip, and the tile in its connected look with "Trip recording
     * started". Only when the arrival started a trip and the truck is connected.
     */
    CONNECTED,

    /** The layout of the trip being recorded, which fades in after the two stages before. */
    HANDED_OVER,
}

/**
 * What Home has seen of the truck since it came on screen, and how far it is in showing an
 * arrival. A new one is made each time Home comes on screen, so what happened while nobody
 * was looking is never played afterwards.
 *
 * @param seen the last look, or null before the first one.
 * @param from the truck's state just before it arrived, while an arrival is shown. The Start
 * tile keeps the line it had then, so that its words do not change under the animation.
 * @param stageEndsAtMs when [stage] runs out by itself, or null if it does not.
 */
internal data class ShownArrival(
    val seen: Look? = null,
    val stage: ArrivalStage = ArrivalStage.NONE,
    val from: TruckState? = null,
    val stageEndsAtMs: Long? = null,
) {
    /** One look at the truck: its state, and what that comes to. */
    data class Look(val state: TruckState, val sight: TruckSight)

    /**
     * Takes a look in. An arrival begins when a truck that was seen away is seen connected, or
     * seen to have started a trip. The first look never begins one: Home opened beside a
     * connected truck, or during a trip, shows that at once.
     *
     * An arrival that is being shown ends at once when the look contradicts it: the truck has
     * gone again, the trip is one that Start began, or the trip waits for the truck.
     */
    fun saw(state: TruckState, sight: TruckSight, atMs: Long): ShownArrival {
        val now = at(atMs)
        val look = Look(state, sight)
        val over = ShownArrival(seen = look)
        val before = now.seen
        return when (now.stage) {
            ArrivalStage.NONE ->
                if (before?.sight == TruckSight.AWAY && sight.hasArrived) {
                    ShownArrival(look, ArrivalStage.CONNECTING, before.state, atMs + CONNECTING_MS)
                } else {
                    over
                }

            ArrivalStage.CONNECTING -> if (sight.hasArrived) now.copy(seen = look) else over

            ArrivalStage.CONNECTED ->
                when (sight) {
                    TruckSight.RECORDING -> now.copy(seen = look)

                    // "Connected" is no longer true, and the trip is still there: show the trip.
                    TruckSight.FOUND -> now.handedOver(look)

                    else -> over
                }

            ArrivalStage.HANDED_OVER -> if (sight.isTruckTrip) now.copy(seen = look) else over
        }
    }

    /**
     * The same arrival at a later moment: moved on through every stage whose time has run.
     * "Connecting…" is followed by "Connected" only when a trip is recorded with the truck
     * connected; with a trip the truck is not confirmed in yet, by the trip; and with no trip,
     * by nothing: the tile then says what is so.
     */
    fun at(atMs: Long): ShownArrival {
        val endsAtMs = stageEndsAtMs ?: return this
        if (atMs < endsAtMs) return this
        val look = seen ?: return ShownArrival()
        return when (stage) {
            ArrivalStage.CONNECTING ->
                when (look.sight) {
                    // Timed from the end of the stage before, so that a late look at the
                    // clock does not make the whole arrival longer.
                    TruckSight.RECORDING ->
                        copy(
                            stage = ArrivalStage.CONNECTED,
                            stageEndsAtMs = endsAtMs + CONNECTED_MS,
                        ).at(atMs)

                    TruckSight.FOUND -> handedOver(look)

                    else -> ShownArrival(seen = look)
                }

            ArrivalStage.CONNECTED -> handedOver(look)

            ArrivalStage.NONE, ArrivalStage.HANDED_OVER -> copy(stageEndsAtMs = null)
        }
    }

    /**
     * Start was pressed. Whatever of an arrival was showing is over, so that Home shows the
     * trip at once: a press is never kept waiting behind an animation.
     *
     * What was seen is forgotten as well, so the look that follows the press is a first look.
     * The press reads the truck, and if that reading finds it connected, it is the press that
     * found it and not an arrival.
     */
    fun startPressed(): ShownArrival = ShownArrival()

    /** How long until [stage] runs out by itself, from [atMs], or null if it does not. */
    fun msUntilNextStage(atMs: Long): Long? = stageEndsAtMs?.let { (it - atMs).coerceAtLeast(1) }

    private fun handedOver(look: Look): ShownArrival =
        copy(seen = look, stage = ArrivalStage.HANDED_OVER, stageEndsAtMs = null)
}

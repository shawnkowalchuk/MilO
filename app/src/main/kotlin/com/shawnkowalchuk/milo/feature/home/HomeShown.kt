package com.shawnkowalchuk.milo.feature.home

import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.component.TruckLinkLook
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

/**
 * What Home draws at one moment: what is so ([ui]), and how much of the truck's arrival is
 * being shown on top of it. Made in one step, like [HomeUi], so that the layout, the truck's
 * tile and the Start tile of one frame always belong to the same stage.
 *
 * @param stage the stage of an arrival, or `NONE`: Home then shows [ui] as it is.
 * @param arrivedFrom the truck's state just before it arrived, while an arrival is shown.
 */
internal data class HomeShown(
    val ui: HomeUi,
    val stage: ArrivalStage = ArrivalStage.NONE,
    val arrivedFrom: TruckState? = null,
) {
    /**
     * Whether Home is laid out for a trip being recorded. While the truck's arrival is shown,
     * the layout without a trip stays, although the trip is being recorded already.
     */
    val recordingLayout: Boolean
        get() =
            ui.activity.trip != null &&
                stage != ArrivalStage.CONNECTING &&
                stage != ArrivalStage.CONNECTED

    /** Whether that layout was reached through an arrival: it then fades in. */
    val fadesToRecording: Boolean get() = stage == ArrivalStage.HANDED_OVER

    /** What the truck's large tile shows: the arrival's "Connecting…", or the state it is in. */
    val truckTile: TruckTileShown
        get() =
            if (stage == ArrivalStage.CONNECTING) {
                TruckTileShown.Connecting
            } else {
                with(ui.truck.state) { TruckTileShown(look, title, sentence) }
            }

    /**
     * The quieter line on the Start tile. During an arrival it stays what it was before the
     * truck arrived, as on the drawing.
     */
    val startLine: Int get() = startLineRes(arrivedFrom ?: ui.truck.state)
}

/** A look of the truck's large tile with the words that go with it. */
internal data class TruckTileShown(val look: TruckLinkLook, val title: Int, val sentence: Int?) {
    companion object {
        /** The design's words for the moment the truck has been found. */
        val Connecting =
            TruckTileShown(
                TruckLinkLook.CONNECTING,
                R.string.home_truck_connecting,
                R.string.home_truck_connecting_text,
            )
    }
}

/** The three things that change what Home shows of an arrival. */
private sealed interface Seen {
    data class Ui(val ui: HomeUi) : Seen

    data class OnScreen(val onScreen: Boolean) : Seen

    data object StartPressed : Seen
}

/**
 * Home as it is drawn, moment by moment: [ui] as it is, with the truck's arrival played over it
 * while Home is on screen (`ShownArrival.kt`).
 *
 * Only what happens in front of the owner is played. Each time Home leaves the screen, what
 * was seen is forgotten, and the first look after it comes back is where it starts from: a
 * truck that connected meanwhile is shown as connected, and its trip as a trip, at once.
 *
 * A value is sent on for every change of what is drawn: when [ui] changes, and each time a
 * stage of an arrival begins or runs out.
 *
 * @param onScreen whether Home is in front of the owner.
 * @param startPresses one value for each press of Start.
 * @param nowMs a clock that only moves forward, in milliseconds. Only differences are used.
 */
internal fun homeShown(
    ui: Flow<HomeUi>,
    onScreen: Flow<Boolean>,
    startPresses: Flow<Unit>,
    nowMs: () -> Long,
): Flow<HomeShown> = arrivalOver(ui, onScreen, startPresses, nowMs).distinctUntilChanged()

private fun arrivalOver(
    ui: Flow<HomeUi>,
    onScreen: Flow<Boolean>,
    startPresses: Flow<Unit>,
    nowMs: () -> Long,
): Flow<HomeShown> = channelFlow {
    var arrival = ShownArrival()
    var watching = false
    var latest: HomeUi? = null

    fun look(at: HomeUi): ShownArrival {
        val state = at.truck.state
        return arrival.saw(state, truckSight(state, at.activity.trip), nowMs())
    }

    val changes =
        merge(
            ui.map { Seen.Ui(it) },
            onScreen.distinctUntilChanged().map { Seen.OnScreen(it) },
            startPresses.map { Seen.StartPressed },
        )
    // The newest change is dealt with at once: a wait for a stage to run out is given up, and
    // taken up again from where the clock stands.
    changes.collectLatest { change ->
        when (change) {
            is Seen.Ui -> {
                latest = change.ui
                if (watching) arrival = look(change.ui)
            }

            is Seen.OnScreen -> {
                watching = change.onScreen
                // Whatever was seen before is forgotten, and what is so now is where the
                // watching starts from.
                arrival = ShownArrival()
                latest?.takeIf { watching }?.let { arrival = look(it) }
            }

            Seen.StartPressed -> arrival = arrival.startPressed()
        }
        val drawn = latest ?: return@collectLatest
        while (true) {
            val now = nowMs()
            arrival = arrival.at(now)
            send(HomeShown(drawn, arrival.stage, arrival.from))
            delay(arrival.msUntilNextStage(now) ?: break)
        }
    }
}

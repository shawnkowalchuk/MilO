package com.shawnkowalchuk.milo.core.designsystem.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState

/**
 * The dot fades to its faintest and comes back in this time: twice as slowly as the Bluetooth
 * mark of "Connecting…", which is over in seconds. This one is there for a whole drive.
 */
internal const val LIVE_PULSE_MS = 1_800

/** A pulse that has run out: the dot shows fully. */
private const val PULSE_OVER = 1f

/**
 * A small dot in the accent colour that pulses: what stands beside it is being counted right
 * now. Added on 2026-10-10 for the label of Home's odometer while a trip moves it.
 *
 * It pulses only while it is in view and its screen is in front, and with the phone's
 * animations switched off it shows fully and stands still. It says nothing to a screen reader:
 * the figure beside it does.
 */
@Composable
fun LiveDot(modifier: Modifier = Modifier) {
    val inFront = rememberInFront()
    val pulse = rememberPulse(inFront.shown)
    Dot(
        color = MaterialTheme.colorScheme.primary,
        modifier =
            modifier
                .then(inFront.modifier)
                .clearAndSetSemantics {}
                // Read while drawing, so a frame of the pulse composes nothing again.
                .graphicsLayer { alpha = markPulseAlpha(pulse.value) },
    )
}

/**
 * The pulse's turn, from 0 to 1 at an even pace, over and over. While [moving] is false there
 * is no animation at all. Compose stops the turn by itself when the phone's animations are
 * switched off, and holds it at its end, where the dot shows fully.
 */
@Composable
private fun rememberPulse(moving: Boolean): State<Float> {
    if (!moving) return AtRest
    return rememberInfiniteTransition(label = "live dot").animateFloat(
        initialValue = 0f,
        targetValue = PULSE_OVER,
        animationSpec = infiniteRepeatable(tween(LIVE_PULSE_MS, easing = LinearEasing)),
        label = "pulse",
    )
}

private val AtRest: State<Float> =
    object : State<Float> {
        override val value: Float = PULSE_OVER
    }

/**
 * Whether something is being looked at: at least partly inside the window, on a screen that is
 * resumed. What moves in the design system moves only then.
 *
 * @param modifier to be put on what is watched.
 * @param shown whether it is being looked at now.
 */
@Stable
internal class InFront(val modifier: Modifier, val shown: Boolean)

// TODO(debt): `TruckLink` keeps the same rule in lines of its own, written before this helper.
// It was left alone when the helper was added (2026-10-10), so that the truck's tile was not
// touched by a change to the odometer. The two are one rule and belong in this one place.
@Composable
internal fun rememberInFront(): InFront {
    var inView by remember { mutableStateOf(false) }
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    // What a scrolling screen cuts off is not in these bounds, so what has been scrolled out
    // of sight is not in view.
    val watch = remember { Modifier.onGloballyPositioned { inView = !it.boundsInWindow().isEmpty } }
    return InFront(watch, inView && lifecycle.isAtLeast(Lifecycle.State.RESUMED))
}

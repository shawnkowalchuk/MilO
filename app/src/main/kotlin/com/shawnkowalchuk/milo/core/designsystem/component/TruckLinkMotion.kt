package com.shawnkowalchuk.milo.core.designsystem.component

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.InfiniteTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.util.lerp

// What moves in the drawing of the truck's tile, and how. The times and the sizes are the
// design's: the "flow", "btFlash", "ping" and "glint" of the canvas's drawing of Home.
//
// Each movement is one number that runs from 0 to 1 at an even pace, over and over: a turn.
// What a turn means on the drawing (how far, how large, how faint) is worked out by the plain
// functions below, so that the design's values are held by unit tests and not by the eye alone.

/** Connecting: the dashes move on by one dash and one gap, toward the truck, in this time. */
internal const val DASH_FLOW_MS = 500

/** Connecting: the Bluetooth mark fades to its faintest and comes back in this time. */
internal const val MARK_PULSE_MS = 900

/** Connecting: a ring spreads from the mark in the middle, and is gone, in this time. */
internal const val MARK_PING_MS = 1_200

/** Connected: the light runs along the line once in this time. */
internal const val GLINT_MS = 1_800

/** Connected: a ring spreads from the truck, and is gone, in this time. */
internal const val TRUCK_PING_MS = 2_400

/** How wide the light is, as a share of the line. */
internal const val GLINT_WIDTH = 0.3f

private const val GLINT_FROM = -GLINT_WIDTH
private const val GLINT_TO = 1.1f
private const val PULSE_FAINTEST = 0.2f
private const val PING_SCALE_FROM = 0.85f
private const val PING_SCALE_TO = 1.9f
private const val PING_ALPHA_FROM = 0.8f

/** A turn that has run out. Everything that moves is drawn at its end while nothing may move. */
private const val TURN_OVER = 1f
private const val HALF_A_TURN = 0.5f

/**
 * How clearly the Bluetooth mark shows at [turn]: fully, down to a fifth at the half, and
 * fully again, slow at each end and quicker between.
 */
internal fun markPulseAlpha(turn: Float): Float {
    val there = if (turn <= HALF_A_TURN) turn / HALF_A_TURN else (TURN_OVER - turn) / HALF_A_TURN
    return lerp(1f, PULSE_FAINTEST, EaseInOut.transform(there))
}

/**
 * How large a spreading ring is at [turn], as a multiple of the round patch it spreads from.
 * It starts a little smaller than the patch, hidden under it, and slows down as it grows.
 */
internal fun pingScale(turn: Float): Float =
    lerp(PING_SCALE_FROM, PING_SCALE_TO, EaseOut.transform(turn))

/** How clearly that ring shows at [turn]: it fades as it grows, and is gone at the end. */
internal fun pingAlpha(turn: Float): Float = lerp(PING_ALPHA_FROM, 0f, EaseOut.transform(turn))

/**
 * Where the light begins at [turn], as a share of the line: from just before the line's start
 * to just past its end, slow at each end and quicker between.
 */
internal fun glintStart(turn: Float): Float = lerp(GLINT_FROM, GLINT_TO, EaseInOut.transform(turn))

/** The three turns of the connecting look. Each is read while drawing, and nowhere else. */
@Stable
internal class ConnectingMotion(
    val dashes: State<Float>,
    val pulse: State<Float>,
    val ping: State<Float>,
)

/** The two turns of the connected look. Each is read while drawing, and nowhere else. */
@Stable
internal class ConnectedMotion(val glint: State<Float>, val ping: State<Float>)

/** A turn that does not move. */
private class Still(override val value: Float) : State<Float>

private val AtItsEnd: State<Float> = Still(TURN_OVER)
private val ConnectingAtRest = ConnectingMotion(AtItsEnd, AtItsEnd, AtItsEnd)
private val ConnectedAtRest = ConnectedMotion(AtItsEnd, AtItsEnd)

/**
 * The connecting look's turns. While [moving] is false there is no animation at all, and the
 * look is drawn at rest: the dashes stand, the mark shows fully and no ring is seen.
 *
 * The turns belong to one `InfiniteTransition`. Compose stops it by itself when the phone's
 * animations are switched off (animator duration scale 0), and holds every turn at its end,
 * which is the same rest.
 */
@Composable
internal fun rememberConnectingMotion(moving: Boolean): ConnectingMotion {
    if (!moving) return ConnectingAtRest
    val turning = rememberInfiniteTransition(label = "truck connecting")
    val dashes = turning.turn(DASH_FLOW_MS, "dashes")
    val pulse = turning.turn(MARK_PULSE_MS, "mark pulse")
    val ping = turning.turn(MARK_PING_MS, "mark ping")
    return remember(turning) { ConnectingMotion(dashes, pulse, ping) }
}

/** The connected look's turns. At rest neither the light nor the ring is seen. */
@Composable
internal fun rememberConnectedMotion(moving: Boolean): ConnectedMotion {
    if (!moving) return ConnectedAtRest
    val turning = rememberInfiniteTransition(label = "truck connected")
    val glint = turning.turn(GLINT_MS, "glint")
    val ping = turning.turn(TRUCK_PING_MS, "truck ping")
    return remember(turning) { ConnectedMotion(glint, ping) }
}

/** A number that runs from 0 to 1 at an even pace in [durationMs], over and over. */
@Composable
private fun InfiniteTransition.turn(durationMs: Int, label: String): State<Float> = animateFloat(
    initialValue = 0f,
    targetValue = TURN_OVER,
    animationSpec = infiniteRepeatable(tween(durationMs, easing = LinearEasing)),
    label = label,
)

package com.shawnkowalchuk.milo.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/**
 * MilO's greeting (Shawn, 2026-10-09: "walked from the right side of the screen to the center
 * at the bottom of the screen. walking just above the bottom navigation than turned and
 * waved"). The mascot comes in from beyond the right edge and walks to the left along the top
 * of the bottom bar; in the middle of the screen it turns to the viewer, waves once, fades
 * away, and [onDone] is called.
 *
 * **It takes nothing from the screen under it.** It is a drawing and no more: no press, no
 * Back and no screen reader ever meets it, the screen is not dimmed, and every button under
 * the mascot works while it walks across.
 *
 * **[onDone] is called once, however the greeting ends:** after the fade; at once, with nothing
 * drawn, while the phone's animations are switched off or when a picture cannot be read
 * ([onUnreadable] is then told why); and when MilO is put away in the middle of it, which is
 * also how a turn of the phone ends it.
 *
 * Whether there is a greeting at all is not decided here (`feature/greeting`); which frame of
 * the walk is due at each moment, and where the mascot is in it, is worked out in
 * `MascotWalk.kt`.
 *
 * @param barHeight the room the bottom bar takes at the foot of the screen, with everything
 * under it: what a `Scaffold` reports as its content's bottom padding.
 */
@Composable
fun MascotGreeting(
    onDone: () -> Unit,
    barHeight: Dp,
    modifier: Modifier = Modifier,
    onUnreadable: (IOException) -> Unit = {},
) {
    var stage by remember { mutableStateOf(GreetingStage.READING) }
    // The greeting outlives a recomposition; these keep it calling the newest functions.
    val newestOnDone by rememberUpdatedState(onDone)
    val newestOnUnreadable by rememberUpdatedState(onUnreadable)
    val course =
        remember { GreetingCourse(onStage = { stage = it }, onDone = { newestOnDone() }) }

    // Put away in the middle of a greeting, MilO does not carry on with it when it comes back.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { course.on(GreetingEvent.CUT_SHORT) }

    // Leaving the composition is what stops the greeting (the effect in GreetingOnScreen).
    if (stage != GreetingStage.OVER) {
        BoxWithConstraints(modifier = modifier.fillMaxSize()) {
            GreetingOnScreen(course, barHeight, onUnreadable = { newestOnUnreadable(it) })
        }
    }
}

/** Plays one greeting through, telling [course] of each step, and draws the mascot. */
@Composable
private fun BoxWithConstraintsScope.GreetingOnScreen(
    course: GreetingCourse,
    barHeight: Dp,
    onUnreadable: (IOException) -> Unit,
) {
    val resources = LocalResources.current
    // Worked out once: a window that is resized under the greeting does not start it again.
    val way = remember { MascotWalk(screenWidth = maxWidth.value) }
    var pictures by remember { mutableStateOf<MascotPictures?>(null) }
    // The frame of the walk that is due. The mascot's step and its place are both read from
    // this one number, and both while drawing: they cannot come apart.
    val walkFrame = remember { mutableIntStateOf(0) }
    val fade = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        val read =
            if (coroutineContext[MotionDurationScale]?.scaleFactor == 0f) {
                null
            } else {
                try {
                    readMascotPictures(resources)
                } catch (unreadable: IOException) {
                    onUnreadable(unreadable)
                    null
                }
            }
        if (read == null) {
            course.on(GreetingEvent.CUT_SHORT)
            return@LaunchedEffect
        }
        try {
            pictures = read
            course.on(GreetingEvent.PICTURES_READ)
            val waved = CompletableDeferred<Unit>()
            walkIn(way) { frame ->
                walkFrame.intValue = frame
                // Still before the screen is drawn: the drawing that no longer shows the walk
                // is the one that shows the turn's first frame, and starts its clock.
                if (frame == way.frames) {
                    read.turnAndWave.playOnce { waved.complete(Unit) }
                    course.on(GreetingEvent.WALK_OVER)
                }
            }
            withTimeoutOrNull(MascotPicture.TURN_AND_WAVE_MS + PICTURE_PATIENCE_MS) {
                waved.await()
            }
            course.on(GreetingEvent.WAVE_OVER)
            fade.animateTo(0f, tween(LEAVE_MS))
            course.on(GreetingEvent.FADED)
        } finally {
            // Also when the greeting is cut short, or leaves the screen in the middle.
            read.close()
        }
    }

    // Nothing is drawn while the pictures are being read.
    val shown = pictures
    if (shown != null) {
        val shadow = MaterialTheme.colorScheme.scrim.copy(alpha = SHADOW_DARKEST)
        // The bar's tile begins rowGap below the top of the room the bar takes
        // (MiloNavigationBar); the walking mascot's soles are a small step above the tile.
        val spacing = MiloTheme.spacing
        val solesAboveFoot = barHeight - spacing.rowGap + spacing.extraSmall
        Box(
            modifier =
                Modifier
                    .align(AbsoluteAlignment.BottomLeft)
                    .offset(y = MascotPicture.SOLES.dp - solesAboveFoot)
                    // Read while drawing, so a step of the walk moves the mascot and nothing
                    // is composed or laid out again.
                    .graphicsLayer {
                        val middle = way.middleIn(walkFrame.intValue)
                        translationX = (middle - MascotPicture.WIDTH / 2).dp.toPx()
                        alpha = fade.value
                    }.drawBehind { groundShadow(shadow) },
        ) {
            Mascot(shown, walkFrame = { walkFrame.intValue.takeIf { it < way.frames } })
        }
    }
}

/**
 * Walks the mascot in. Before every drawing of the screen, [onFrame] is told the frame of the
 * walk that is due ([MascotWalk.frameAt]); the last it is told is [MascotWalk.frames], when the
 * walk is over. What [onFrame] does is drawn in that same drawing of the screen.
 *
 * The walk's clock is the screen's frame time, read as it is and not stretched by the phone's
 * "animator duration scale": the turn and wave that follows plays at its own pace whatever that
 * is set to.
 */
private suspend fun walkIn(way: MascotWalk, onFrame: (Int) -> Unit) {
    val began = withFrameNanos { it }
    do {
        val frame =
            withFrameNanos { now -> way.frameAt((now - began) / NANOS_IN_MILLI).also(onFrame) }
    } while (frame < way.frames)
}

/** A faint, soft patch of dark on the ground under the mascot's feet. */
private fun DrawScope.groundShadow(darkest: Color) {
    val radius = SHADOW_WIDTH.toPx() / 2
    val centre = Offset(size.width / 2, size.height - SHADOW_ABOVE_EDGE.toPx())
    val fading = Brush.radialGradient(listOf(darkest, Color.Transparent), centre, radius)
    scale(scaleX = 1f, scaleY = SHADOW_HEIGHT / SHADOW_WIDTH, pivot = centre) {
        drawCircle(fading, radius, centre)
    }
}

/** How long the mascot takes to fade away. */
private const val LEAVE_MS = 260

private const val NANOS_IN_MILLI = 1_000_000L

/** The shadow: about as wide as the mascot's two shoes, and flat. */
private val SHADOW_WIDTH = 92.dp
private val SHADOW_HEIGHT = 14.dp

/** The shadow's middle, above the picture's lower edge: between the nearer foot and the other. */
private val SHADOW_ABOVE_EDGE = 5.dp

/** How dark the shadow is in its middle. It fades to nothing at its rim. */
private const val SHADOW_DARKEST = 0.5f

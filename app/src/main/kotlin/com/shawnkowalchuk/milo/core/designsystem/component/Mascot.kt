package com.shawnkowalchuk.milo.core.designsystem.component

import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import java.nio.ByteBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The clips in the app's copy of the mascot's model. The copy is written by
 * `design/mascot/scripts/export_milo.py`, which leaves every other clip out, and
 * `MascotModelTest` holds the two lists together.
 *
 * @property named what the clip is called in the model.
 */
enum class MascotClip(internal val named: String) {
    /** Two seconds: the right arm goes up, the open hand waves, the arm comes down. */
    WAVE("Wave"),
}

/**
 * The mascot, playing one clip once from its start, on a see-through background. When the clip
 * is over, [onFinished] is called and the mascot is left standing in the clip's last pose.
 *
 * It is the app's only 3D drawing (ADR-005). The engine that draws it lives only while this
 * is on the screen: it is built when the mascot appears and given back when it leaves.
 *
 * **The clip begins when the mascot can be seen, not when it is asked for.** The phone's
 * graphics chip takes a moment to get ready the first time, longer on some phones than on
 * others, and the clip waits for it in its first pose. [onShown] says when that moment is over.
 *
 * **The mascot is an extra, and a screen never waits for it.** [onFinished] is called at once,
 * with nothing drawn and no [onShown], while the phone's animations are switched off and on a
 * phone that gives the engine no start; and after [GIVE_UP_NANOS], if by then no picture of it
 * has reached the screen.
 *
 * @param beforeStart awaited before anything of the engine is built: the place to note that
 * the drawing is about to begin, for a failure in native code that nothing can catch.
 */
@Composable
fun Mascot(
    clip: MascotClip,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    beforeStart: suspend () -> Unit = {},
    onShown: () -> Unit = {},
) {
    val context = LocalContext.current
    // A TextureView and not a SurfaceView: it is drawn with the screen's other content, so it
    // can lie over that content with a see-through background and fade with what holds it.
    val canvas = remember { TextureView(context).apply { isOpaque = false } }
    // The effect below outlives a recomposition; these keep it calling the newest functions.
    val newestOnFinished by rememberUpdatedState(onFinished)
    val newestBeforeStart by rememberUpdatedState(beforeStart)
    val newestOnShown by rememberUpdatedState(onShown)

    AndroidView(factory = { canvas }, modifier = modifier)

    LaunchedEffect(canvas, clip) {
        val stage =
            if (coroutineContext[MotionDurationScale]?.scaleFactor == 0f) {
                null
            } else {
                val model =
                    withContext(Dispatchers.IO) {
                        context.assets.open(MODEL).use { ByteBuffer.wrap(it.readBytes()) }
                    }
                newestBeforeStart()
                try {
                    MascotStage(canvas, model)
                } catch (_: IllegalStateException) {
                    null
                }
            }
        if (stage != null) {
            try {
                stage.play(clip.named, onShown = { newestOnShown() })
            } finally {
                // Also when the mascot leaves the screen in the middle of its clip.
                stage.close()
            }
        }
        newestOnFinished()
    }
}

/**
 * Draws the clip once, a picture for every frame of the screen, and returns when it is over.
 *
 * The clip's clock starts with the picture that is known to have been seen. Filament draws
 * behind the app by up to two pictures, and takes no new one while it is that far behind
 * ([MascotStage.draw] then says so); so once it has taken [SEEN_AFTER] pictures, the first of
 * them is on the screen. Until then every picture is the clip's first.
 */
private suspend fun MascotStage.play(clip: String, onShown: () -> Unit) {
    val length = lengthOf(clip) ?: return
    val asked = withFrameNanos { it }
    var taken = 0
    var start = NOT_STARTED
    var over = false
    while (!over) {
        withFrameNanos { now ->
            val seconds =
                if (start == NOT_STARTED) 0f else (now - start) / NANOS_IN_SECOND
            if (draw(clip, seconds.coerceAtMost(length), now)) taken++
            if (start == NOT_STARTED && taken >= SEEN_AFTER) {
                start = now
                onShown()
            }
            over = seconds >= length || (start == NOT_STARTED && now - asked > GIVE_UP_NANOS)
        }
    }
}

/** Where the app's copy of the model lies in the assets. */
internal const val MODEL = "mascot/milo_wave.glb"

private const val NANOS_IN_SECOND = 1_000_000_000f
private const val NOT_STARTED = -1L
private const val SEEN_AFTER = 3

/** Four seconds. A phone that has shown nothing by then is not going to greet anyone. */
private const val GIVE_UP_NANOS = 4_000_000_000L

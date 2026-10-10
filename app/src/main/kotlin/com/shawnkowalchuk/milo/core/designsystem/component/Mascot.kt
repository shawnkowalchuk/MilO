package com.shawnkowalchuk.milo.core.designsystem.component

import android.content.res.Resources
import android.graphics.ImageDecoder
import android.graphics.drawable.Animatable2
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import com.shawnkowalchuk.milo.R
import java.io.IOException
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The mascot's animated pictures, read and ready to play (ADR-007). The mascot is a 3D model in
 * Blender (`design/mascot/`); what the app carries is that model drawn there once, frame by
 * frame, with a see-through background. Android's own decoder plays them, so the app has no
 * library for it and no native code of its own.
 *
 * Both pictures are the same size and were drawn by the same camera, so one can take the
 * other's place without the mascot moving. What they hold is said in [MascotPicture].
 *
 * @property walk one cycle of the walk toward the left.
 * @property turnAndWave from the walk's first frame: a turn to the viewer, then the wave.
 */
internal class MascotPictures(
    val walk: AnimatedImageDrawable,
    val turnAndWave: AnimatedImageDrawable,
) {
    /**
     * Stops both pictures. They are not played again.
     *
     * **Their listeners are left where they are.** Android tells a picture's listeners of a
     * stop a moment later, from a list it reads only then; with the list cleared in between
     * it ends the app (seen on 2026-10-09: a NullPointerException in
     * `AnimatedImageDrawable.postOnAnimationEnd`). The listeners go when the pictures do.
     */
    fun close() {
        walk.stop()
        turnAndWave.stop()
    }
}

/**
 * Reads both pictures, off the main thread, with the first frame of each drawn and waiting.
 * Both at once and before anything is shown, so that the second is there the moment the first
 * has played.
 *
 * @throws IOException if a picture cannot be read, or is not an animated one.
 */
internal suspend fun readMascotPictures(resources: Resources): MascotPictures =
    withContext(Dispatchers.IO) {
        MascotPictures(
            walk = resources.animatedPicture(R.drawable.mascot_walk),
            turnAndWave = resources.animatedPicture(R.drawable.mascot_turn_and_wave),
        )
    }

private fun Resources.animatedPicture(@DrawableRes picture: Int): AnimatedImageDrawable {
    val read = ImageDecoder.decodeDrawable(ImageDecoder.createSource(this, picture))
    return read as? AnimatedImageDrawable
        ?: throw IOException("${getResourceEntryName(picture)} is not an animated picture")
}

/**
 * Plays the picture from its start, [times] times through, and leaves it on its last frame.
 *
 * @param onLastFrame called when the last frame of the last time through is on the screen. It
 * has then been shown for no time at all: Android says so when it draws that frame, not when
 * the frame's time is up. Called once more, and to no purpose, when the picture is stopped.
 */
internal fun AnimatedImageDrawable.play(times: Int, onLastFrame: () -> Unit) {
    // Android counts the times a picture starts again, not the times it plays.
    repeatCount = times - 1
    registerAnimationCallback(
        object : Animatable2.AnimationCallback() {
            override fun onAnimationEnd(drawable: Drawable) = onLastFrame()
        },
    )
    start()
}

/**
 * The mascot, as one of its animated pictures shows it, [MascotPicture.WIDTH] dp wide and
 * [MascotPicture.HEIGHT] high on every phone. It only draws: which picture, when it plays and
 * where it stands is its caller's business.
 *
 * **Nothing here redraws the picture for its next frame.** A screen of MilO is drawn by the
 * phone's graphics chip, and there Android moves an animated picture on by itself, between two
 * of the app's own drawings, for as long as some part of it is on the screen.
 *
 * It is the first thing in the app that is not drawn by Compose's own means: a picture file,
 * played by Android. A further use of the mascot goes through this component and adds its
 * picture to [MascotPictures].
 */
@Composable
internal fun Mascot(picture: AnimatedImageDrawable, modifier: Modifier = Modifier) {
    Spacer(
        modifier =
            modifier
                .size(MascotPicture.WIDTH.dp, MascotPicture.HEIGHT.dp)
                .drawBehind {
                    picture.setBounds(0, 0, size.width.roundToInt(), size.height.roundToInt())
                    drawIntoCanvas { picture.draw(it.nativeCanvas) }
                },
    )
}

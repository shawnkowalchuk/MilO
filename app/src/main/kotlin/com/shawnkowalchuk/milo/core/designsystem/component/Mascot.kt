package com.shawnkowalchuk.milo.core.designsystem.component

import android.content.res.Resources
import android.graphics.ImageDecoder
import android.graphics.drawable.Animatable2
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.shawnkowalchuk.milo.R
import java.io.IOException
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The mascot's pictures, read and ready (ADR-007). The mascot is a 3D model in Blender
 * (`design/mascot/`); what the app carries is that model drawn there once, frame by frame, with
 * a see-through background. Android's own decoder reads them, so the app has no library for it
 * and no native code of its own.
 *
 * Every frame of both is the same size and was drawn by the same camera, so one can take
 * another's place without the mascot moving. What they hold is said in [MascotPicture].
 *
 * @property walk one cycle of the walk toward the left: a still picture with its frames side by
 * side. The app draws the frame it wants, because the mascot's place has to change with its
 * step, and an animated picture does not say which frame it shows.
 * @property turnAndWave an animated picture, from the frame that follows the walk's last one: a
 * turn to the viewer, then the wave. The mascot stands where it is, so Android plays it.
 */
internal class MascotPictures(val walk: ImageBitmap, val turnAndWave: AnimatedImageDrawable) {
    /**
     * Stops the animated picture. It is not played again.
     *
     * **Its listener is left where it is.** Android tells a picture's listeners of a stop a
     * moment later, from a list it reads only then; with the list cleared in between it ends
     * the app (seen on 2026-10-09: a NullPointerException in
     * `AnimatedImageDrawable.postOnAnimationEnd`). The listener goes when the picture does.
     */
    fun close() {
        turnAndWave.stop()
    }
}

/**
 * Reads both pictures, off the main thread, with the first frame of the animated one drawn and
 * waiting. Both at once and before anything is shown, so that the second is there the moment
 * the walk is over.
 *
 * @throws IOException if a picture cannot be read, or the second is not an animated one.
 */
internal suspend fun readMascotPictures(resources: Resources): MascotPictures =
    withContext(Dispatchers.IO) {
        val walk = ImageDecoder.createSource(resources, R.drawable.mascot_walk)
        val turnAndWave = ImageDecoder.createSource(resources, R.drawable.mascot_turn_and_wave)
        MascotPictures(
            walk = ImageDecoder.decodeBitmap(walk).asImageBitmap(),
            turnAndWave =
                ImageDecoder.decodeDrawable(turnAndWave) as? AnimatedImageDrawable
                    ?: throw IOException("mascot_turn_and_wave is not an animated picture"),
        )
    }

/**
 * Plays the picture once from its start and leaves it on its last frame.
 *
 * @param onLastFrame called when the last frame is on the screen. It has then been shown for no
 * time at all: Android says so when it draws that frame, not when the frame's time is up.
 * Called once more, and to no purpose, when the picture is stopped.
 */
internal fun AnimatedImageDrawable.playOnce(onLastFrame: () -> Unit) {
    // Android counts the times a picture starts again, not the times it plays.
    repeatCount = 0
    registerAnimationCallback(
        object : Animatable2.AnimationCallback() {
            override fun onAnimationEnd(drawable: Drawable) = onLastFrame()
        },
    )
    start()
}

/**
 * The mascot, as one of its [pictures] shows it, [MascotPicture.WIDTH] dp wide and
 * [MascotPicture.HEIGHT] high on every phone. It only draws: which frame of the walk, when the
 * turn and wave plays and where the mascot stands is its caller's business.
 *
 * **Nothing here redraws the turn and wave for its next frame.** A screen of MilO is drawn by
 * the phone's graphics chip, and there Android moves an animated picture on by itself, between
 * two of the app's own drawings, for as long as some part of it is on the screen.
 *
 * It is the first thing in the app that is not drawn by Compose's own means: picture files,
 * read by Android. A further use of the mascot goes through this component and adds its picture
 * to [MascotPictures].
 *
 * @param walkFrame the frame of the walk to draw, counted on through its cycles, or null for
 * the turn and wave as it plays. Asked while drawing, so a new frame draws again and nothing
 * is composed or laid out for it.
 */
@Composable
internal fun Mascot(
    pictures: MascotPictures,
    walkFrame: () -> Int?,
    modifier: Modifier = Modifier,
) {
    Spacer(
        modifier =
            modifier
                .size(MascotPicture.WIDTH.dp, MascotPicture.HEIGHT.dp)
                .drawBehind {
                    val whole = IntSize(size.width.roundToInt(), size.height.roundToInt())
                    val frame = walkFrame()
                    if (frame != null) {
                        drawWalkFrame(pictures.walk, frame, whole)
                    } else {
                        pictures.turnAndWave.setBounds(0, 0, whole.width, whole.height)
                        drawIntoCanvas { pictures.turnAndWave.draw(it.nativeCanvas) }
                    }
                },
    )
}

/** Draws one frame of the walk's [sheet], [whole] in size: the sheet's frames lie row after row. */
private fun DrawScope.drawWalkFrame(sheet: ImageBitmap, frame: Int, whole: IntSize) {
    val one =
        IntSize(sheet.width / MascotPicture.WALK_COLUMNS, sheet.height / MascotPicture.WALK_ROWS)
    val inCycle = frame.mod(MascotPicture.WALK_FRAMES)
    val column = inCycle % MascotPicture.WALK_COLUMNS
    val row = inCycle / MascotPicture.WALK_COLUMNS
    drawImage(
        image = sheet,
        srcOffset = IntOffset(column * one.width, row * one.height),
        srcSize = one,
        dstSize = whole,
    )
}

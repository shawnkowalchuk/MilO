package com.shawnkowalchuk.milo.core.designsystem.component

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The mascot's two animated pictures are made by hand, by scripts outside the build
 * (`design/mascot/scripts/render_greeting.py` and `pack_greeting.py`), and committed. This
 * holds the files to what the code expects ([MascotPicture]): a picture that is made again
 * with another size, a frame more or another way of repeating fails here and not on the phone,
 * where the mascot would walk on the spot or turn in the middle of a step.
 *
 * An animated WebP file is a list of named chunks. "VP8X" says what the file holds and how
 * large its pictures are, "ANIM" how often it plays, and each "ANMF" is one frame with the
 * time it is shown for. Only those are read: no test off the phone can draw the frames.
 */
class MascotPicturesTest {
    private class Frame(val width: Int, val height: Int, val millis: Int, val seeThrough: Boolean)

    private class Picture(file: File) {
        val bytes: Long = file.length()
        var animated = false
        var seeThrough = false
        var width = 0
        var height = 0

        /** How often it plays, as the file says it: 0 is for ever. */
        var plays = -1
        val frames = mutableListOf<Frame>()

        init {
            val all = ByteBuffer.wrap(file.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
            assertEquals("${file.name} starts with 'RIFF'", RIFF, all.getInt(0))
            assertEquals("${file.name} is a WebP file", WEBP, all.getInt(8))
            var at = FIRST_CHUNK
            while (at + CHUNK_HEADER <= all.limit()) {
                val size = all.getInt(at + 4)
                val body = at + CHUNK_HEADER
                when (all.getInt(at)) {
                    VP8X -> {
                        animated = all.get(body).toInt() and ANIMATION_FLAG != 0
                        seeThrough = all.get(body).toInt() and ALPHA_FLAG != 0
                        width = all.threeBytes(body + 4) + 1
                        height = all.threeBytes(body + 7) + 1
                    }

                    ANIM -> plays = all.getShort(body + 4).toInt() and 0xFFFF

                    ANMF ->
                        frames +=
                            Frame(
                                width = all.threeBytes(body + 6) + 1,
                                height = all.threeBytes(body + 9) + 1,
                                millis = all.threeBytes(body + 12),
                                // The frame's own picture follows its 16 bytes: "ALPH" first,
                                // if it has a see-through channel of its own.
                                seeThrough = all.getInt(body + 16) == ALPH,
                            )
                }
                // A chunk is padded to an even length.
                at = body + size + (size and 1)
            }
        }

        private fun ByteBuffer.threeBytes(at: Int): Int = (get(at).toInt() and 0xFF) or
            ((get(at + 1).toInt() and 0xFF) shl 8) or
            ((get(at + 2).toInt() and 0xFF) shl 16)
    }

    private val walk = Picture(File("$PICTURES/mascot_walk.webp"))
    private val turnAndWave = Picture(File("$PICTURES/mascot_turn_and_wave.webp"))
    private val both = listOf("the walk" to walk, "the turn and wave" to turnAndWave)

    @Test
    fun `both are animated pictures with a see-through background`() {
        for ((name, picture) in both) {
            assertTrue("$name is animated", picture.animated)
            assertTrue("$name is see-through", picture.seeThrough)
            assertTrue("$name: every frame is", picture.frames.all { it.seeThrough })
        }
    }

    @Test
    fun `both are the size the code draws them at, and so lie exactly over each other`() {
        val width = (MascotPicture.WIDTH * MascotPicture.PIXELS_PER_DP).roundToInt()
        val height = (MascotPicture.HEIGHT * MascotPicture.PIXELS_PER_DP).roundToInt()
        assertEquals(600, width)
        assertEquals(420, height)
        for ((name, picture) in both) {
            assertEquals("$name, width", width, picture.width)
            assertEquals("$name, height", height, picture.height)
            // Every frame is the whole picture: none is a patch laid over the one before.
            assertTrue(name, picture.frames.all { it.width == width && it.height == height })
        }
    }

    @Test
    fun `the walk is one cycle of twelve frames, and starts again for ever`() {
        assertEquals(MascotPicture.WALK_FRAMES, walk.frames.size)
        assertEquals(12, walk.frames.size)
        assertEquals(0, walk.plays)
    }

    @Test
    fun `the turn and wave is sixty frames, and plays once`() {
        assertEquals(MascotPicture.TURN_AND_WAVE_FRAMES, turnAndWave.frames.size)
        assertEquals(60, turnAndWave.frames.size)
        assertEquals(1, turnAndWave.plays)
    }

    @Test
    fun `every frame of both is shown for 42 milliseconds`() {
        assertEquals(42, MascotPicture.FRAME_MS)
        for ((name, picture) in both) {
            assertTrue(name, picture.frames.all { it.millis == MascotPicture.FRAME_MS })
        }
        assertEquals(504, MascotPicture.WALK_CYCLE_MS)
        assertEquals(2_520, MascotPicture.TURN_AND_WAVE_MS)
    }

    @Test
    fun `the two stay under a megabyte together`() {
        assertTrue("the walk: ${walk.bytes} bytes", walk.bytes < MOST_WALK_BYTES)
        assertTrue(
            "the turn and wave: ${turnAndWave.bytes} bytes",
            turnAndWave.bytes < MOST_WAVE_BYTES,
        )
        assertTrue(MOST_WALK_BYTES + MOST_WAVE_BYTES <= 1_000_000L)
    }

    private companion object {
        const val PICTURES = "src/main/res/drawable-nodpi"

        /** The chunks' names, each as the four bytes of a little-endian number. */
        const val RIFF = 0x46464952
        const val WEBP = 0x50424557
        const val VP8X = 0x58385056
        const val ANIM = 0x4D494E41
        const val ANMF = 0x464D4E41
        const val ALPH = 0x48504C41

        /** "RIFF", the file's length and "WEBP" come first; a chunk is a name and a length. */
        const val FIRST_CHUNK = 12
        const val CHUNK_HEADER = 8

        const val ANIMATION_FLAG = 0x02
        const val ALPHA_FLAG = 0x10

        /** Measured on 2026-10-09: 100 KB and 773 KB. */
        const val MOST_WALK_BYTES = 150_000L
        const val MOST_WAVE_BYTES = 850_000L
    }
}

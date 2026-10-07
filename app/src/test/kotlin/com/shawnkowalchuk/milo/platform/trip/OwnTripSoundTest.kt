package com.shawnkowalchuk.milo.platform.trip

import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.settings.MiloSettings
import com.shawnkowalchuk.milo.data.settings.SettingsStore
import com.shawnkowalchuk.milo.data.sound.MAX_OWN_SOUND_BYTES
import com.shawnkowalchuk.milo.data.sound.OwnSoundStore
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.net.URI
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Choosing an audio file as the trip-start sound, on a real folder and stand-ins for the picked
 * file and for Android's player: a file that cannot be copied or played changes nothing, and a
 * new choice joins the list of his own sounds (since 2026-10-07).
 */
class OwnTripSoundTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val folder: File by lazy { File(temporaryFolder.root, "trip_sound") }
    private val store by lazy { OwnSoundStore(folder) }
    private val settings = SettingsStore(FakeSettingsFile())
    private val log = FakeEventLogDao()
    private var nowMs = 1_791_028_800_000L

    /** The files "on the phone", by the address the picker hands over. */
    private val files = mutableMapOf<String, Pair<String?, ByteArray>>()

    /** Set to make Android's player refuse every file, as it does for one that is not audio. */
    private var playerSays: String? = null

    /** Set to make the app that holds the files answer every open with an exception of its own. */
    private var holderThrows: RuntimeException? = null

    private val picked =
        object : PickedAudio {
            override fun open(uri: String): InputStream {
                holderThrows?.let { throw it }
                val (_, bytes) = files[uri] ?: throw FileNotFoundException("No file at $uri")
                return ByteArrayInputStream(bytes)
            }

            override fun nameOf(uri: String): String? = files[uri]?.first
        }

    private val sound by lazy {
        OwnTripSound(
            picked = picked,
            playbackProblem = { playerSays },
            store = store,
            settings = settings,
            eventLog = EventLogRepository(log),
            clock = { nowMs },
        )
    }

    private fun onThePhone(uri: String, name: String?, content: String) {
        files[uri] = name to content.toByteArray()
    }

    /** The file the settings name as the sound, read back the way the player would find it. */
    private fun soundInUse(now: MiloSettings): File = File(URI(now.customSoundUri.orEmpty()))

    /** Every copy MilO holds, read from the folder itself. */
    private fun copies(): List<File> = folder.listFiles().orEmpty().sortedBy { it.name }

    private fun logged(category: EventCategory): List<String> =
        log.entries.filter { it.category == category }.map { it.message }

    // ---- Choosing ---------------------------------------------------------------------------------

    @Test
    fun `a picked file is copied into MilO's own storage and played from there`() = runBlocking {
        onThePhone("content://music/1", "r2d2.mp3", "beep boop")

        assertNull(sound.choose("content://music/1"))

        val now = settings.current()
        assertEquals("r2d2.mp3", now.customSoundName)
        // The settings name MilO's copy, never the file that was picked.
        assertEquals(copies().single(), soundInUse(now))
        assertEquals("beep boop", soundInUse(now).readText())
        assertEquals(1, logged(EventCategory.SERVICE).size)
    }

    @Test
    fun `the copy still plays after the picked file is gone`() = runBlocking {
        onThePhone("content://music/1", "r2d2.mp3", "beep boop")
        sound.choose("content://music/1")

        files.clear()

        assertEquals("beep boop", soundInUse(settings.current()).readText())
    }

    @Test
    fun `a new sound joins the list, and the one before stays to be chosen again`() = runBlocking {
        onThePhone("content://music/1", "r2d2.mp3", "beep boop")
        onThePhone("content://music/2", "mario-1-up.mp3", "ding")
        sound.choose("content://music/1")
        nowMs += 60_000

        assertNull(sound.choose("content://music/2"))

        val now = settings.current()
        assertEquals("mario-1-up.mp3", now.customSoundName)
        assertEquals("ding", soundInUse(now).readText())
        // A list since 2026-10-07: both copies are kept, in the order they were added.
        assertEquals(listOf("r2d2.mp3", "mario-1-up.mp3"), now.ownSounds.map { it.name })
        assertEquals(2, copies().size)
    }

    @Test
    fun `a sound on the list can be chosen again`() = runBlocking {
        onThePhone("content://music/1", "r2d2.mp3", "beep boop")
        onThePhone("content://music/2", "mario-1-up.mp3", "ding")
        sound.choose("content://music/1")
        nowMs += 60_000
        sound.choose("content://music/2")
        val first = settings.current().ownSounds.first()

        sound.useOwn(first.uri)

        val now = settings.current()
        assertEquals("r2d2.mp3", now.customSoundName)
        assertEquals("beep boop", soundInUse(now).readText())
    }

    @Test
    fun `removing the sound in use takes its copy away, and the chirp plays`() = runBlocking {
        onThePhone("content://music/1", "r2d2.mp3", "beep boop")
        onThePhone("content://music/2", "mario-1-up.mp3", "ding")
        sound.choose("content://music/1")
        nowMs += 60_000
        sound.choose("content://music/2")
        val inUse = soundInUse(settings.current())

        sound.remove(settings.current().customSoundUri.orEmpty())

        val now = settings.current()
        assertNull(now.customSoundUri)
        assertEquals(listOf("r2d2.mp3"), now.ownSounds.map { it.name })
        assertTrue(inUse !in copies())
        assertEquals(1, copies().size)
    }

    @Test
    fun `removing another sound leaves the one in use alone`() = runBlocking {
        onThePhone("content://music/1", "r2d2.mp3", "beep boop")
        onThePhone("content://music/2", "mario-1-up.mp3", "ding")
        sound.choose("content://music/1")
        val other = settings.current().ownSounds.single().uri
        nowMs += 60_000
        sound.choose("content://music/2")

        sound.remove(other)

        val now = settings.current()
        assertEquals("mario-1-up.mp3", now.customSoundName)
        assertEquals(listOf(soundInUse(now)), copies())
    }

    @Test
    fun `a file whose name the phone does not give is still a usable sound`() = runBlocking {
        onThePhone("content://music/1", name = null, content = "beep")
        onThePhone("content://music/2", name = "  ", content = "boop")

        assertNull(sound.choose("content://music/1"))
        assertNull(settings.current().customSoundName)

        assertNull(sound.choose("content://music/2"))
        assertNull(settings.current().customSoundName)
    }

    // ---- Refusing ---------------------------------------------------------------------------------

    @Test
    fun `a file that cannot be read is refused, and the built-in sound stays`() = runBlocking {
        assertEquals(OwnSoundRefusal.COULD_NOT_COPY, sound.choose("content://music/gone"))

        assertEquals(MiloSettings(), settings.current())
        assertTrue(copies().isEmpty())
    }

    @Test
    fun `an exception of the other app's own refuses the file and does not end MilO`() =
        runBlocking {
            onThePhone("content://music/1", "r2d2.mp3", "beep boop")
            onThePhone("content://files/7", "horn.wav", "honk")
            sound.choose("content://music/1")
            val before = settings.current()
            // What the app that holds a file can answer an open with. Its code runs the
            // request, and whatever it throws reaches MilO unchanged.
            val answers =
                listOf(
                    SecurityException("Permission Denial: reading content://files/7"),
                    IllegalStateException("Unknown URI content://files/7"),
                    IllegalArgumentException("Missing file for content://files/7"),
                    UnsupportedOperationException("No external opens"),
                    NullPointerException(),
                )

            for (answer in answers) {
                holderThrows = answer

                assertEquals(
                    answer.toString(),
                    OwnSoundRefusal.COULD_NOT_COPY,
                    sound.choose("content://files/7"),
                )

                assertEquals(before, settings.current())
                assertEquals(listOf(soundInUse(before)), copies())
                val line = log.entries.last()
                assertEquals(EventCategory.ERROR, line.category)
                assertEquals(answer.toString(), line.detail)
            }
            assertEquals(answers.size, logged(EventCategory.ERROR).size)
        }

    @Test
    fun `a file that cannot be played is refused, and the sound chosen before stays`() =
        runBlocking {
            onThePhone("content://music/1", "r2d2.mp3", "beep boop")
            onThePhone("content://docs/9", "invoice.pdf", "not audio at all")
            sound.choose("content://music/1")
            val before = settings.current()
            playerSays = "java.io.IOException: Prepare failed.: status=0x1"

            assertEquals(OwnSoundRefusal.NOT_PLAYABLE, sound.choose("content://docs/9"))

            assertEquals(before, settings.current())
            assertEquals("beep boop", soundInUse(before).readText())
            // The copy that failed the check is not kept.
            assertEquals(listOf(soundInUse(before)), copies())
        }

    @Test
    fun `a file that is too large is refused, and the sound chosen before stays`() = runBlocking {
        onThePhone("content://music/1", "r2d2.mp3", "beep boop")
        files["content://music/album"] = "album.flac" to ByteArray(MAX_OWN_SOUND_BYTES.toInt() + 1)
        sound.choose("content://music/1")
        val before = settings.current()

        assertEquals(OwnSoundRefusal.TOO_LARGE, sound.choose("content://music/album"))

        assertEquals(before, settings.current())
        assertEquals(listOf(soundInUse(before)), copies())
    }

    @Test
    fun `a refusal is written to the log with what was said about the file`() = runBlocking {
        onThePhone("content://docs/9", "invoice.pdf", "not audio at all")
        playerSays = "java.io.IOException: Prepare failed.: status=0x1"

        sound.choose("content://docs/9")

        val line = log.entries.single()
        assertEquals(EventCategory.ERROR, line.category)
        assertEquals(
            "Trip-start sound: the chosen file was refused (NOT_PLAYABLE). The sound is unchanged",
            line.message,
        )
        assertEquals("java.io.IOException: Prepare failed.: status=0x1", line.detail)
    }

    // ---- Going back -------------------------------------------------------------------------------

    @Test
    fun `going back to the built-in sound keeps his own on the list`() = runBlocking {
        onThePhone("content://music/1", "r2d2.mp3", "beep boop")
        sound.choose("content://music/1")
        settings.setSoundEnabled(false)

        sound.useBuiltIn()

        val now = settings.current()
        assertNull(now.customSoundUri)
        // Whether the sound plays at all is another setting, and stays as it was.
        assertFalse(now.soundEnabled)
        assertEquals(listOf("r2d2.mp3"), now.ownSounds.map { it.name })
        assertEquals(1, copies().size)
    }

    @Test
    fun `going back when the built-in sound is already in use changes nothing`() = runBlocking {
        sound.useBuiltIn()

        assertEquals(MiloSettings(), settings.current())
        assertTrue(copies().isEmpty())
    }
}

package com.shawnkowalchuk.milo.platform.trip

import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.settings.SettingsStore
import com.shawnkowalchuk.milo.data.settings.addOwnSound
import com.shawnkowalchuk.milo.data.settings.chooseOwnSound
import com.shawnkowalchuk.milo.data.settings.removeOwnSound
import com.shawnkowalchuk.milo.data.sound.OwnSoundStore
import com.shawnkowalchuk.milo.data.sound.SoundTooLargeException
import java.io.File
import java.io.IOException
import java.net.URI
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Why a picked file did not become the trip-start sound. */
enum class OwnSoundRefusal {
    /** It could not be read, or the copy could not be written or stored. */
    COULD_NOT_COPY,

    /** It is larger than MilO copies. */
    TOO_LARGE,

    /** It was copied, but Android cannot play it: it is not audio, or it is damaged. */
    NOT_PLAYABLE,
}

/**
 * Changes which sound a trip start plays: one of Shawn's own audio files, or the bundled chirp.
 *
 * Since 2026-10-07 his own sounds are a list he chooses from (his choice: "A list of my own
 * sounds"): a picked file is added to it and plays from then on, and the sounds added before
 * stay on the list, to be chosen again or removed.
 *
 * A picked file is copied into MilO's own storage, checked to be playable, and only then put in
 * the settings, from where the trip service reads it at the next trip start. If any step fails
 * the choice is refused and nothing has changed: the settings and the copies are exactly as
 * before.
 *
 * @param picked reads the file Shawn picked.
 * @param playbackProblem says why Android cannot play a file, or null if it can. A plain
 * function handed in, so the whole class runs in unit tests without a phone.
 * @param clock wall-clock milliseconds.
 * @param io where the copying and the check run: both block.
 *
 * A change that has begun is finished, even if Shawn leaves the Settings screen meanwhile (the
 * work is not cancellable): stopped half-way, it could leave a copy that nothing uses, or two.
 */
class OwnTripSound(
    private val picked: PickedAudio,
    private val playbackProblem: (File) -> String?,
    private val store: OwnSoundStore,
    private val settings: SettingsStore,
    private val eventLog: EventLogRepository,
    private val clock: () -> Long,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    /** One change at a time: two picks in a row must not delete each other's copies. */
    private val oneAtATime = Mutex()

    /**
     * Adds the picked file to the list of his own sounds, and makes it the trip-start sound.
     *
     * @return null if it is now the sound, else why it was refused. The reason is also written
     * to the event log, with what Android or the file system said.
     */
    suspend fun choose(uri: String): OwnSoundRefusal? = withContext(io + NonCancellable) {
        oneAtATime.withLock {
            val copy =
                try {
                    picked.open(uri).use { store.writeCopy(it, stamp = clock()) }
                } catch (tooLarge: SoundTooLargeException) {
                    return@withLock refuse(OwnSoundRefusal.TOO_LARGE, tooLarge.toString())
                } catch (unreadable: IOException) {
                    return@withLock refuse(OwnSoundRefusal.COULD_NOT_COPY, unreadable.toString())
                } catch (fromTheOtherApp: RuntimeException) {
                    // The file is opened by code of the app that holds it, and whatever that
                    // code throws arrives here as it is: a SecurityException once Android no
                    // longer lets MilO read the file, but just as well an
                    // IllegalArgumentException or an IllegalStateException from an app that
                    // no longer knows the address it handed out. Left alone it would end the
                    // process, and the trip service runs in it.
                    val said = fromTheOtherApp.toString()
                    return@withLock refuse(OwnSoundRefusal.COULD_NOT_COPY, said)
                }
            val problem = playbackProblem(copy)
            if (problem != null) {
                tidy { store.discard(copy) }
                return@withLock refuse(OwnSoundRefusal.NOT_PLAYABLE, problem)
            }
            try {
                settings.addOwnSound(
                    uri = copy.toURI().toString(),
                    name = picked.nameOf(uri)?.takeIf { it.isNotBlank() },
                )
            } catch (notStored: IOException) {
                tidy { store.discard(copy) }
                return@withLock refuse(OwnSoundRefusal.COULD_NOT_COPY, notStored.toString())
            }
            tidy { store.keepOnly(listedCopies()) }
            note("Trip-start sound: a file added on the Settings screen is now used")
            null
        }
    }

    /**
     * Goes back to the bundled chirp. His own sounds stay on the list.
     *
     * @throws IOException if the settings cannot be written. Nothing has changed then.
     */
    suspend fun useBuiltIn() = withContext(io + NonCancellable) {
        oneAtATime.withLock {
            settings.clearCustomSound()
            note("Trip-start sound: the bundled chirp is used again")
        }
    }

    /**
     * Makes the sound of his own at [uri], one of the list, the trip-start sound.
     *
     * @throws IOException if the settings cannot be written. Nothing has changed then.
     */
    suspend fun useOwn(uri: String) = withContext(io + NonCancellable) {
        oneAtATime.withLock {
            if (settings.chooseOwnSound(uri)) {
                note("Trip-start sound: a sound added before is used again")
            }
        }
    }

    /**
     * Takes the sound at [uri] off the list and removes MilO's copy of it. If it was the one in
     * use, the bundled chirp plays from then on.
     *
     * @throws IOException if the settings cannot be written. Nothing has changed then.
     */
    suspend fun remove(uri: String) = withContext(io + NonCancellable) {
        oneAtATime.withLock {
            settings.removeOwnSound(uri)
            tidy { store.keepOnly(listedCopies()) }
            note("Trip-start sound: a sound was removed from the list")
        }
    }

    /** MilO's copies of the sounds on the list: every other copy can go. */
    private suspend fun listedCopies(): Set<File> = settings.current().ownSounds
        .mapNotNull { runCatching { File(URI(it.uri)) }.getOrNull() }
        .toSet()

    private suspend fun refuse(why: OwnSoundRefusal, detail: String): OwnSoundRefusal {
        eventLog.add(
            clock(),
            EventCategory.ERROR,
            "Trip-start sound: the chosen file was refused ($why). The sound is unchanged",
            detail,
        )
        return why
    }

    /**
     * Removes copies that are no longer wanted. A failure here changes nothing about which sound
     * plays, so it is logged and the change stands; the next change removes what was left.
     */
    private suspend fun tidy(remove: suspend () -> Unit) {
        try {
            remove()
        } catch (leftBehind: IOException) {
            val what = "Trip-start sound: an unused copy of a sound file could not be removed"
            eventLog.add(clock(), EventCategory.ERROR, what, leftBehind.toString())
        }
    }

    private suspend fun note(message: String) {
        eventLog.add(clock(), EventCategory.SERVICE, message)
    }
}

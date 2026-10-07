package com.shawnkowalchuk.milo.platform.trip

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.data.settings.MiloSettings
import com.shawnkowalchuk.milo.data.settings.SettingsStore
import java.io.File
import java.io.IOException
import java.util.concurrent.Executor

/**
 * Plays the trip-start sound once: Shawn's audible proof that a trip is being recorded.
 *
 * - **It behaves like a notification sound.** The audio attributes say "notification", so the
 *   phone plays it at the notification volume and mutes it on silent, on vibrate and in Do Not
 *   Disturb, with no check of the ringer mode here.
 * - **It asks for the phone's own speaker.** At the moment of a Bluetooth connect the truck's
 *   audio is not ready, and a stereo drops the first second or two of a new stream, which is
 *   most of this sound. Android treats the request as a preference and may route it elsewhere.
 * - **It is played by the service, not by a notification channel**, because MIUI is reported to
 *   switch channel sounds off (docs/research/2026-10-03-miui-dev-bluetooth-audio.md).
 *
 * The bundled sound is `res/raw/trip_start_chirp.wav`, an original synthesised chirp made by
 * `tools/make_trip_start_chirp.py`. If Shawn has chosen his own audio file, MilO's copy of it
 * is played (`OwnTripSound`), and the bundled one is the fallback whenever the copy cannot be:
 * it is gone, or it does not play after all.
 *
 * One sound at a time: a sound that is still playing is cut off by the next one, so pressing
 * Play on the Settings screen twice does not play two on top of each other. Call it on the
 * main thread, which is also where the player reports back.
 *
 * @param onNote a line for the event log.
 */
class TripStartSound(private val context: Context, private val onNote: (String) -> Unit) {
    /** The sound that is playing, if one is. */
    private var playing: MediaPlayer? = null

    /**
     * @param customSoundUri the audio file from the settings, or null for the bundled chirp.
     */
    // Lint offers String.toUri() from core-ktx, a library MilO does not declare (it is only on
    // the classpath through other libraries). One call does not justify adding it.
    @SuppressLint("UseKtx")
    fun play(customSoundUri: String?) {
        if (customSoundUri == null) {
            playBundled()
            return
        }
        val started =
            start(describe = "the chosen sound", onFailure = ::playBundled) { player ->
                player.setDataSource(context, Uri.parse(customSoundUri))
            }
        if (!started) playBundled()
    }

    private fun playBundled() {
        start(describe = "the bundled chirp", onFailure = {}) { player ->
            context.resources.openRawResourceFd(R.raw.trip_start_chirp).use { chirp ->
                player.setDataSource(chirp.fileDescriptor, chirp.startOffset, chirp.length)
            }
        }
    }

    /**
     * Prepares and plays one sound, and releases the player when it has finished or failed.
     *
     * @param onFailure called if the player fails after it was handed to Android (while
     * preparing or playing). A failure before that is reported by the return value.
     * @return false if the sound could not even be handed to Android.
     */
    private fun start(
        describe: String,
        onFailure: () -> Unit,
        setSource: (MediaPlayer) -> Unit,
    ): Boolean {
        playing?.release()
        val player = MediaPlayer()
        playing = player
        return try {
            player.setAudioAttributes(
                AudioAttributes
                    .Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            setSource(player)
            phoneSpeaker()?.let(player::setPreferredDevice)
            player.setOnPreparedListener(MediaPlayer::start)
            player.setOnCompletionListener(::finished)
            player.setOnErrorListener { failed, what, extra ->
                finished(failed)
                onNote("The trip-start sound failed while playing $describe (error $what/$extra)")
                onFailure()
                true
            }
            // Asynchronous: reading a file Shawn chose can be slow, and this is the main thread.
            player.prepareAsync()
            onNote("Trip-start sound: playing $describe")
            true
        } catch (unreadable: IOException) {
            abandon(player, describe, unreadable)
        } catch (denied: SecurityException) {
            // The permission to read a chosen file is lost when the file is moved or deleted.
            abandon(player, describe, denied)
        } catch (invalid: IllegalArgumentException) {
            abandon(player, describe, invalid)
        } catch (wrongState: IllegalStateException) {
            abandon(player, describe, wrongState)
        }
    }

    /** Hands the player back to Android, and forgets it unless a newer sound has replaced it. */
    private fun finished(player: MediaPlayer) {
        player.release()
        if (playing === player) playing = null
    }

    private fun abandon(player: MediaPlayer, describe: String, failure: Exception): Boolean {
        finished(player)
        onNote("The trip-start sound could not play $describe: $failure")
        return false
    }

    private fun phoneSpeaker(): AudioDeviceInfo? = context
        .getSystemService(AudioManager::class.java)
        .getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
}

/**
 * Whether Android can play [file], asked before it becomes the trip-start sound: null if it can,
 * else what the player said. A file the player can prepare is one it can play.
 *
 * It reads the start of the file and can take a moment, so it is not for the main thread.
 */
fun playbackProblem(file: File): String? {
    val player = MediaPlayer()
    return try {
        player.setDataSource(file.path)
        player.prepare()
        null
    } catch (unreadable: IOException) {
        // What the player throws for a file that is not audio, or is damaged.
        unreadable.toString()
    } catch (invalid: IllegalArgumentException) {
        invalid.toString()
    } catch (wrongState: IllegalStateException) {
        wrongState.toString()
    } catch (denied: SecurityException) {
        denied.toString()
    } finally {
        player.release()
    }
}

/**
 * Plays the trip-start sound as the settings have it: not at all if it is switched off, and
 * otherwise the file Shawn chose, or the bundled chirp.
 *
 * @param mainThread where the sound is played ([TripStartSound.play] must run there).
 * @param onUnreadable a line for the event log if the settings cannot be read. The controller
 * logs the unreadable file itself; the sound is on by default, so the bundled chirp plays.
 */
internal suspend fun TripStartSound.playAsSet(
    settings: SettingsStore,
    mainThread: Executor,
    onUnreadable: (String) -> Unit,
) {
    val now =
        try {
            settings.current()
        } catch (unreadable: IOException) {
            onUnreadable("Sound settings unreadable: $unreadable")
            MiloSettings()
        }
    if (now.soundEnabled) mainThread.execute { play(now.customSoundUri) }
}

package com.shawnkowalchuk.milo.platform.trip

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import com.shawnkowalchuk.milo.R
import java.io.IOException

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
 * `tools/make_trip_start_chirp.py`. If Shawn has chosen his own audio file, that is played, and
 * the bundled one is the fallback whenever his cannot be: the file moved, was deleted, or is
 * not audio.
 *
 * @param onNote a line for the event log.
 */
class TripStartSound(private val context: Context, private val onNote: (String) -> Unit) {
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
        val player = MediaPlayer()
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
            player.setOnCompletionListener(MediaPlayer::release)
            player.setOnErrorListener { failed, what, extra ->
                failed.release()
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

    private fun abandon(player: MediaPlayer, describe: String, failure: Exception): Boolean {
        player.release()
        onNote("The trip-start sound could not play $describe: $failure")
        return false
    }

    private fun phoneSpeaker(): AudioDeviceInfo? = context
        .getSystemService(AudioManager::class.java)
        .getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
}

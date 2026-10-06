package com.shawnkowalchuk.milo.data.sound

import android.content.Context
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files

/** The folder the copy is kept in, inside the app's never-backed-up files. */
private const val SOUND_FOLDER = "trip_sound"

/** Every copy's name starts with this; the rest is a number that tells two copies apart. */
private const val SOUND_FILE_PREFIX = "own_trip_start_sound_"

private const val COPY_BUFFER_BYTES = 64 * 1024

/**
 * The largest audio file that is copied. A trip-start sound is a few seconds long; this is far
 * more than that needs, and it stops a wrong pick (an hour of music) from filling the phone.
 */
const val MAX_OWN_SOUND_BYTES = 10L * 1024 * 1024

/** The file that was picked is larger than the limit. Nothing was kept. */
class SoundTooLargeException(val limitBytes: Long) :
    IOException("The audio file is larger than $limitBytes bytes")

/**
 * MilO's own copy of the audio file Shawn chose as the trip-start sound.
 *
 * The file he picks belongs to another app, and Android's permission to read it ends when it is
 * moved or deleted, so it is copied here once and played from here from then on.
 *
 * **A new copy never touches the one in use.** It is written under a name of its own
 * ([writeCopy]). Only when it is known to be playable, and the settings point at it, does the
 * caller remove the others ([keepOnly]). Whatever fails before that, the sound that was playing
 * until now is still there, and afterwards there is one copy and no more.
 *
 * @param folder where the copies live. Created on first use.
 */
class OwnSoundStore(private val folder: File) {
    /**
     * Copies [source] into a new file and returns it. On any failure nothing is left behind.
     *
     * @param stamp a number for the file's name, in practice the time. If a file of that name
     * exists, the next free number is used: the copy in use is never overwritten.
     * @throws SoundTooLargeException if the source is longer than [maxBytes].
     * @throws IOException if the source cannot be read or the copy cannot be written.
     */
    fun writeCopy(source: InputStream, stamp: Long, maxBytes: Long = MAX_OWN_SOUND_BYTES): File {
        if (!folder.isDirectory && !folder.mkdirs()) {
            throw IOException("The folder for the trip-start sound could not be created")
        }
        var number = stamp
        while (File(folder, SOUND_FILE_PREFIX + number).exists()) number++
        val copy = File(folder, SOUND_FILE_PREFIX + number)
        try {
            copy.outputStream().use { written ->
                val buffer = ByteArray(COPY_BUFFER_BYTES)
                var copied = 0L
                while (true) {
                    val read = source.read(buffer)
                    if (read < 0) break
                    copied += read
                    if (copied > maxBytes) throw SoundTooLargeException(maxBytes)
                    written.write(buffer, 0, read)
                }
                if (copied == 0L) throw IOException("The audio file is empty")
            }
        } catch (failure: IOException) {
            try {
                discard(copy)
            } catch (leftBehind: IOException) {
                // The failed copy is the news; that its remains could not be removed rides
                // along. They are removed by the next keepOnly.
                failure.addSuppressed(leftBehind)
            }
            throw failure
        }
        return copy
    }

    /** Throws one copy away, for example one that turned out not to be playable. */
    fun discard(copy: File) {
        Files.deleteIfExists(copy.toPath())
    }

    /**
     * Removes every copy except [inUse], so that copies are replaced and never pile up. Pass
     * null to remove them all, when Shawn goes back to the bundled sound.
     *
     * @return how many files were removed.
     */
    fun keepOnly(inUse: File?): Int {
        val others = folder.listFiles { file -> file != inUse }.orEmpty()
        others.forEach { Files.deleteIfExists(it.toPath()) }
        return others.size
    }
}

/**
 * Builds the store in the app's no-backup files, like the crash files. The copy can be several
 * megabytes, and Android's backup takes nothing at all once an app is over its 25 MB limit: the
 * sound must never be what pushes the trips out of the backup. After a restore on another phone
 * the sound therefore has to be chosen again.
 */
fun buildOwnSoundStore(context: Context): OwnSoundStore =
    OwnSoundStore(File(context.noBackupFilesDir, SOUND_FOLDER))

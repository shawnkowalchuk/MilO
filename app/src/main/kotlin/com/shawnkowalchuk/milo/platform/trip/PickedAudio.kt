package com.shawnkowalchuk.milo.platform.trip

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.IOException
import java.io.InputStream

/**
 * The file Shawn picked in the phone's file picker. It belongs to another app; MilO may read it
 * for a short while after the pick and no longer, which is why it is copied at once.
 */
interface PickedAudio {
    /**
     * @throws IOException if the file cannot be opened.
     * @throws SecurityException if Android no longer lets MilO read it.
     * @throws RuntimeException of any other kind, if the app that holds the file answers with
     * one. The request is carried out by that app's code, and what it throws is passed on
     * unchanged, so a caller must be ready for all of them.
     */
    fun open(uri: String): InputStream

    /** What the file is called, for display, or null if the phone does not say. */
    fun nameOf(uri: String): String?
}

/** Reads a picked file through Android's content resolver, which is how the picker hands it over. */
// Lint offers String.toUri() from core-ktx, a library MilO does not declare (it is only on the
// classpath through other libraries). Two calls do not justify adding it.
@SuppressLint("UseKtx")
class ContentPickedAudio(private val context: Context) : PickedAudio {
    override fun open(uri: String): InputStream =
        context.contentResolver.openInputStream(Uri.parse(uri))
            ?: throw IOException("The app that holds the file returned nothing")

    override fun nameOf(uri: String): String? {
        val columns = arrayOf(OpenableColumns.DISPLAY_NAME)
        return try {
            context.contentResolver.query(Uri.parse(uri), columns, null, null, null)?.use { row ->
                if (row.moveToFirst()) row.getString(0) else null
            }
        } catch (unanswered: RuntimeException) {
            // The answer comes from another app's code, which can throw anything. The name is
            // only shown on the Settings screen, so a file without one is still a usable sound.
            null
        }
    }
}

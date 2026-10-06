package com.shawnkowalchuk.milo.platform.transfer

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/** Opened for writing so that what the file held before is replaced, not written into. */
private const val WRITE_AND_TRUNCATE = "wt"

/**
 * A file Shawn picked or made with Android's file picker, for an export or an import. It
 * belongs to another app (Files, Drive); MilO may use that one file for a short while and
 * needs no permission for it.
 *
 * An interface, so that an export and an import are tested without a phone.
 */
interface PickedDocuments {
    /**
     * @throws IOException if the file cannot be opened.
     * @throws RuntimeException of any kind, if the app that holds the file answers with one:
     * the request is carried out by that app's code, and what it throws is passed on unchanged.
     */
    fun openForWriting(uri: String): OutputStream

    /** @throws IOException and [RuntimeException] as [openForWriting] does. */
    fun openForReading(uri: String): InputStream

    /**
     * Removes a file that an export made and could not finish, so that no half-written export
     * lies about looking like a whole one.
     *
     * @return false if it could not be removed. The file is then still there, incomplete.
     */
    fun delete(uri: String): Boolean
}

/** [PickedDocuments] through Android's content resolver, as the picker hands a file over. */
// Lint offers String.toUri() from core-ktx, a library MilO does not declare (it is only on the
// classpath through other libraries). The few calls here do not justify adding it.
@SuppressLint("UseKtx")
class ContentPickedDocuments(private val context: Context) : PickedDocuments {
    override fun openForWriting(uri: String): OutputStream =
        context.contentResolver.openOutputStream(Uri.parse(uri), WRITE_AND_TRUNCATE)
            ?: throw IOException("The app that holds the file returned nothing to write to")

    override fun openForReading(uri: String): InputStream =
        context.contentResolver.openInputStream(Uri.parse(uri))
            ?: throw IOException("The app that holds the file returned nothing to read")

    override fun delete(uri: String): Boolean = try {
        DocumentsContract.deleteDocument(context.contentResolver, Uri.parse(uri))
    } catch (gone: FileNotFoundException) {
        // Not there any more is as good as removed.
        true
    } catch (refused: RuntimeException) {
        // The other app's code can throw anything, and not every app lets a file it holds be
        // removed. The caller says that an incomplete file may be left.
        false
    }
}

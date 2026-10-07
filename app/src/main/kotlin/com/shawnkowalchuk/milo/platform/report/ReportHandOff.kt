package com.shawnkowalchuk.milo.platform.report

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/** Gmail's package name. The report is handed to Gmail if the phone has it. */
private const val GMAIL_PACKAGE = "com.google.android.gm"

/**
 * What the manifest's provider is registered under, after the app's own package name. The
 * provider hands out the files of one folder, the reports in the cache
 * (`res/xml/report_file_paths.xml`), and nothing else of MilO's storage.
 */
private const val REPORTS_AUTHORITY_SUFFIX = ".reports"

private const val PDF_TYPE = "application/pdf"
private const val CSV_TYPE = "text/csv"
private const val TEXT_TYPE = "text/plain"
private const val ANY_TYPE = "*/*"
private const val MAIL_SCHEME = "mailto"

/**
 * Builds the requests that hand a report file to another app: the email app, a PDF viewer, or
 * whatever Shawn picks from Android's share sheet. It only builds them. They are started from
 * the Report screen, on its activity, so that the other app opens on top of MilO and Back
 * leads back to it. The event log, shared from the Log screen as a text file, goes the same
 * way: it is written into the same folder and handed out by the same provider.
 *
 * **MilO sends nothing itself.** It has no INTERNET permission. The email is a draft in the
 * email app, with the address, the subject and the file filled in, and Shawn presses send.
 * Android tells an app nothing about what became of the draft, which is why MilO asks him.
 *
 * The other app reads the file through MilO's file provider, and only that one file: each
 * request carries a permission to read it, given explicitly (Android 18 stops giving it by
 * itself) and gone again when the other app's screen has closed.
 */
class ReportHandOff(context: Context) {
    private val appContext = context.applicationContext

    /**
     * An email to [address] with [pdf] attached, as the requests to try in order: Gmail first,
     * and then, for a phone without Gmail, any app that handles email. Each is the same email.
     *
     * **Which app opens is chosen by a selector, not by the request itself.** The request is
     * "send this file" (the only kind that carries an attachment), and many apps take a file.
     * The selector asks Android for an app that takes a "mailto:" address instead, which only
     * an email app does, and hands that app the request. Naming Gmail's package on the request
     * itself is not enough: the Gmail app also holds Google Chat, which takes files too, and on
     * an emulator Android then asked "Gmail or Chat?", with Chat first (2026-10-06). Asked for
     * the part of Gmail that takes a "mailto:" address, it opens the email draft.
     */
    fun toAccountant(pdf: File, address: String, subject: String, body: String): List<Intent> {
        val uri = uriOf(pdf)
        val anyEmailApp = Intent(Intent.ACTION_SENDTO, Uri.fromParts(MAIL_SCHEME, "", null))
        val gmail = Intent(anyEmailApp).setPackage(GMAIL_PACKAGE)
        return listOf(gmail, anyEmailApp).map { emailApp ->
            Intent(Intent.ACTION_SEND).apply {
                // No type is set on purpose. Android matches the selector with the type of
                // this request, and no email app takes "a mailto: address of type PDF": with a
                // type, neither request found Gmail (seen on an emulator, 2026-10-06). The
                // email app asks MilO's file provider what kind of file it is handed.
                // An array, not a single string: Gmail ignores the address otherwise.
                putExtra(Intent.EXTRA_EMAIL, arrayOf(address))
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, body)
                putExtra(Intent.EXTRA_STREAM, uri)
                readable(uri)
                selector = emailApp
            }
        }
    }

    /** Opens [pdf] in whatever app the phone shows a PDF with. */
    fun toView(pdf: File): Intent {
        val uri = uriOf(pdf)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, PDF_TYPE)
            readable(uri)
        }
    }

    /**
     * Offers [csv] to Android's share sheet: Drive, Files, an email app, whatever the phone
     * has. A CSV has no one place to go, so nothing is chosen for Shawn.
     *
     * @param title what the share sheet and the receiving app are told the file is.
     */
    fun toShare(csv: File, title: String): Intent = shareSheet(csv, CSV_TYPE, title)

    /**
     * Offers the report's two files together, [pdf] and [csv], to Android's share sheet, so
     * that both can be saved to one place in one go (Drive, Files) or sent with an app of
     * Shawn's own choosing. Nothing is chosen for him, and nothing is recorded as sent.
     *
     * @param title what the share sheet and the receiving app are told the files are.
     */
    fun toShareBoth(pdf: File, csv: File, title: String): Intent {
        val uris = arrayListOf(uriOf(pdf), uriOf(csv))
        val send =
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                // Two kinds of file, so the request names no single kind; the two it holds
                // are listed for an app that asks.
                type = ANY_TYPE
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(PDF_TYPE, CSV_TYPE))
                putExtra(Intent.EXTRA_SUBJECT, title)
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                // Both addresses go on the request as clip data, which is where Android looks
                // when it hands the permission to read them on.
                clipData =
                    ClipData.newRawUri("", uris.first()).apply {
                        uris.drop(1).forEach { addItem(ClipData.Item(it)) }
                    }
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        return Intent.createChooser(send, title)
    }

    /**
     * Offers the event log, written to [log] as plain text, to Android's share sheet. Where it
     * goes is Shawn's choice, as with the CSV.
     */
    fun toShareText(log: File, title: String): Intent = shareSheet(log, TEXT_TYPE, title)

    private fun shareSheet(file: File, fileType: String, title: String): Intent {
        val uri = uriOf(file)
        val send =
            Intent(Intent.ACTION_SEND).apply {
                type = fileType
                putExtra(Intent.EXTRA_SUBJECT, title)
                putExtra(Intent.EXTRA_STREAM, uri)
                readable(uri)
            }
        // The share sheet copies the permission and the file's address from the request it
        // wraps, so the app that is picked can read the file.
        return Intent.createChooser(send, title)
    }

    private fun uriOf(file: File): Uri {
        val authority = appContext.packageName + REPORTS_AUTHORITY_SUFFIX
        return FileProvider.getUriForFile(appContext, authority, file)
    }

    /**
     * Lets whoever receives the request read [uri], and nothing else. The address is put on
     * the request as clip data as well: that is where Android looks when it hands the
     * permission on.
     */
    private fun Intent.readable(uri: Uri) {
        clipData = ClipData.newRawUri("", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}

package com.shawnkowalchuk.milo.platform.transfer

import android.app.backup.BackupAgent
import android.app.backup.BackupDataInput
import android.app.backup.BackupDataOutput
import android.app.backup.FullBackupDataOutput
import android.os.ParcelFileDescriptor
import com.shawnkowalchuk.milo.data.crash.CrashRecord
import com.shawnkowalchuk.milo.data.crash.buildCrashFileStore
import com.shawnkowalchuk.milo.data.transfer.BackupNote
import com.shawnkowalchuk.milo.data.transfer.BackupNoteKind
import com.shawnkowalchuk.milo.data.transfer.buildBackupNoteStore
import com.shawnkowalchuk.milo.data.transfer.settleDatabasesForBackup
import java.io.IOException
import java.util.Locale

private const val BYTES_PER_MEGABYTE = 1024.0 * 1024.0

/**
 * What Android calls while it backs MilO up and when it has restored it.
 *
 * Which files are copied is not decided here: Android's own code does the copying
 * (`super.onFullBackup`) by `res/xml/data_extraction_rules.xml`. This class adds three things
 * around it, none of which Android offers another way to do:
 *
 * - **Before the files are collected, each database is brought into its one file**
 *   (`settleDatabasesForBackup`), so that a backup never holds a database whose newest trips
 *   are in a second file beside it.
 * - **A backup that Android refuses for its size is written down** ([onQuotaExceeded]). Android
 *   itself says nothing to anyone then, and goes on backing up nothing, night after night.
 * - **A restore is written down** ([onRestoreFinished]), so that the next start of MilO knows
 *   that its settings came from another installation and takes out what was only true there.
 *
 * Android runs this in a process of its own kind: the `Application` object is not MilO's, the
 * `AppContainer` does not exist, and nothing here may reach for either. What it has to tell is
 * left as a small file for the next ordinary start (`BackupNoteStore`, `BackupAftermath`).
 *
 * The manifest names this class and sets `fullBackupOnly`, which keeps Android to its
 * file-copying kind of backup; the two methods of the older key-and-value kind are never called.
 */
class MiloBackupAgent : BackupAgent() {
    override fun onBackup(
        oldState: ParcelFileDescriptor?,
        data: BackupDataOutput?,
        newState: ParcelFileDescriptor?,
    ) {
        // The key-and-value kind of backup, which `fullBackupOnly` in the manifest switches off.
    }

    override fun onRestore(
        data: BackupDataInput?,
        appVersionCode: Int,
        newState: ParcelFileDescriptor?,
    ) {
        // The restore of that kind of backup: never made, so never called.
    }

    override fun onFullBackup(data: FullBackupDataOutput) {
        val unsettled = settleDatabasesForBackup(this)
        if (unsettled.isNotEmpty()) note(BackupNoteKind.NOT_SETTLED, unsettled.joinToString("; "))
        super.onFullBackup(data)
        val toAnotherPhone = data.transportFlags and FLAG_DEVICE_TO_DEVICE_TRANSFER != 0
        note(
            BackupNoteKind.COLLECTED,
            if (toAnotherPhone) "for a transfer to another phone" else "for a cloud backup",
        )
    }

    override fun onQuotaExceeded(backupDataBytes: Long, quotaBytes: Long) {
        // Android measures the files with a call of onFullBackup before it refuses them, so
        // "collected" has been noted by now, and is not true of a backup that was refused.
        val collectedNoteStays =
            try {
                buildBackupNoteStore(this).withdrawCollected()
                ""
            } catch (stillThere: IOException) {
                // The refusal is the news. That the other note could not be taken back is
                // said with it, so that the line it becomes is not believed.
                " (the line that says this backup was collected is not true of it)"
            }
        note(
            BackupNoteKind.QUOTA_EXCEEDED,
            "MilO's files are ${megabytes(backupDataBytes)} and the limit is " +
                megabytes(quotaBytes) + collectedNoteStays,
        )
    }

    override fun onRestoreFinished() {
        note(BackupNoteKind.RESTORED)
    }

    /**
     * Leaves a note for the next start. If it cannot be written, the failure goes to a crash
     * file, the same way out the rest of MilO has when the event log cannot be written; if
     * that fails too, the exception is let through to Android, which logs it. A failure must
     * not vanish.
     */
    private fun note(kind: BackupNoteKind, detail: String = "") {
        val atMs = System.currentTimeMillis()
        try {
            buildBackupNoteStore(this).leave(BackupNote(kind, atMs, detail))
        } catch (unwritten: IOException) {
            val lost = IllegalStateException("The backup agent could not leave its $kind note")
            lost.addSuppressed(unwritten)
            val record = CrashRecord.from(atMs, Thread.currentThread().name, lost)
            buildCrashFileStore(this).write(record)
        }
    }

    // The same form in every language: the line goes to the event log, which is in English.
    private fun megabytes(bytes: Long): String =
        String.format(Locale.ROOT, "%.1f MB", bytes / BYTES_PER_MEGABYTE)
}

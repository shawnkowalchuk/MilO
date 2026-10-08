package com.shawnkowalchuk.milo.platform.transfer

import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.settings.SettingsStore
import com.shawnkowalchuk.milo.data.settings.TripSound
import com.shawnkowalchuk.milo.data.settings.forgetOtherInstallation
import com.shawnkowalchuk.milo.data.settings.sound
import com.shawnkowalchuk.milo.data.settings.transferred
import com.shawnkowalchuk.milo.data.transfer.BackupNote
import com.shawnkowalchuk.milo.data.transfer.BackupNoteKind
import com.shawnkowalchuk.milo.data.transfer.BackupNoteStore

/** What the event log calls the pairing check that follows a restore. */
private const val AFTER_RESTORE = "after a restore"

/**
 * At every process start, writes into the event log what Android's backup did with MilO since
 * the last one (the notes of [MiloBackupAgent]), and, after a restore, takes out of the
 * restored settings what was only true of the installation they came from.
 *
 * **After a restore the truck is not believed to be paired.** The settings Android restored
 * name the truck and carry the number of a companion device association, which Android on
 * another phone, or on this one after MilO was removed and installed again, does not hold.
 * `truckOnArrival` asks Android which associations it does hold: without one for the truck,
 * its address and name are kept and the association's number is removed, and the pairing check
 * that follows shows the truck as "paired before, pair it again" on Setup, on the pairing
 * screen and in Home's warning.
 *
 * Also taken out: Shawn's confirmations of the setup checklist, which were his word about
 * switches of the other installation, and the sounds of his own if the file of one in use is not
 * here (they are kept out of every backup). The trips, the sent reports, the event log and every
 * other setting are left as they were restored.
 *
 * Each note is written to the log first and removed only then, one at a time, so a process
 * that dies half-way reads that one note again: a line twice, never a restore unnoticed. What
 * a restore takes out is safe to take out twice.
 *
 * A failure in here is written to the log and goes no further: this runs in the process of the
 * trip service, at its start, where a trip trigger may be waiting.
 *
 * @param pairingHere asks Android which companion associations MilO holds on this phone.
 * @param ownSoundIsHere whether MilO's copy of a sound of Shawn's own, named as the settings
 * name it, is on this phone.
 * @param checkPairing has the truck's pairing checked, and so shown, with a word on why.
 */
class BackupAftermath internal constructor(
    private val notes: BackupNoteStore,
    private val settings: SettingsStore,
    private val pairingHere: () -> PairingHere,
    private val ownSoundIsHere: (uri: String) -> Boolean,
    private val checkPairing: (occasion: String) -> Unit,
    private val eventLog: EventLogRepository,
    private val failures: TransferFailures,
) {
    /** Deals with every note that waits. Never throws, except for being cancelled. */
    suspend fun settle() {
        // A note that fails stays where it is and is read again at the next start.
        failures.contained("reading what Android's backup left behind") {
            for ((file, note) in notes.waiting()) {
                tell(note)
                notes.remove(file)
            }
        }
    }

    private suspend fun tell(note: BackupNote) {
        when (note.kind) {
            BackupNoteKind.COLLECTED ->
                eventLog.add(note.atMs, EventCategory.PROCESS, collectedLine(note.detail))

            BackupNoteKind.QUOTA_EXCEEDED ->
                eventLog.add(note.atMs, EventCategory.ERROR, quotaExceededLine(note.detail))

            BackupNoteKind.NOT_SETTLED ->
                eventLog.add(note.atMs, EventCategory.ERROR, notSettledLine(note.detail))

            BackupNoteKind.RESTORED -> afterRestore(note.atMs)
        }
    }

    private suspend fun afterRestore(atMs: Long) {
        val restored = settings.current()
        // Nothing was stored on this phone "before": the settings file itself was replaced.
        val truck = truckOnArrival(restored.transferred().truck, here = null, pairingHere())
        val soundGone =
            TripSound.entries.mapNotNull { restored.sound(it).ownUri }.any { !ownSoundIsHere(it) }
        settings.forgetOtherInstallation(
            dropAssociation = truck !is TruckArrival.Paired,
            dropOwnSound = soundGone,
        )
        eventLog.add(atMs, EventCategory.PROCESS, restoredLine(soundGone))
        eventLog.add(atMs, EventCategory.PAIRING, "After the restore: ${truck.inWords()}")
        checkPairing(AFTER_RESTORE)
    }
}

// The lines are in English, like every line of the event log, and pure, so they are tested.

internal fun collectedLine(detail: String): String =
    "Android collected MilO's data $detail: the trips, the sent reports, the event log and " +
        "the settings. Whether the copy arrived is Android's to say (Settings, Google, Backup)"

internal fun quotaExceededLine(detail: String): String =
    "Android did not back MilO up: $detail. Nothing at all is backed up while MilO is over " +
        "the limit, and the last backup that worked stays as it was. \"Export all data\" in " +
        "Settings writes a copy that has no limit"

internal fun notSettledLine(detail: String): String =
    "Before a backup, a database could not be brought into its one file ($detail). It was " +
        "backed up together with its log file instead"

internal fun restoredLine(soundGone: Boolean): String =
    "MilO's data was put back by Android, from a backup or from another phone: the trips, " +
        "the sent reports, the event log and the settings are the restored ones. Raw GPS " +
        "points come with a transfer from another phone and not with a cloud backup. Taken " +
        "out of the settings, because it was only true of the installation they came from: " +
        "the confirmations of the setup checklist" +
        (if (soundGone) ", and the chosen sounds, whose files are in no backup" else "") +
        ". Go through Setup again on this phone"

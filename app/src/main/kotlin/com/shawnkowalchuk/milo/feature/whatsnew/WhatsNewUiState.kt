package com.shawnkowalchuk.milo.feature.whatsnew

import com.shawnkowalchuk.milo.data.changelog.Change
import com.shawnkowalchuk.milo.data.changelog.Changelog
import com.shawnkowalchuk.milo.platform.system.InstalledVersion
import java.time.LocalDate

/** What the What's new screen shows. */
sealed interface WhatsNewUiState {
    /** The list is being read: a moment, the first time the screen opens. */
    data object Reading : WhatsNewUiState

    /** The list built into this build could not be read. The event log says why. */
    data object Unreadable : WhatsNewUiState

    /** Every version, newest first. */
    data class Ready(val releases: List<ReleaseShown>) : WhatsNewUiState
}

/**
 * One version as the screen shows it.
 *
 * @param releasedOn the day it was released, or null while it is not released yet.
 * @param onThisPhone whether it is the version installed: its heading says so.
 */
data class ReleaseShown(
    val version: String,
    val releasedOn: LocalDate?,
    val onThisPhone: Boolean,
    val changes: List<Change>,
)

/** The screen's state from the list built into the app and the version on this phone. */
internal fun whatsNewState(changelog: Changelog, installed: InstalledVersion) =
    WhatsNewUiState.Ready(
        changelog.releases.map { release ->
            ReleaseShown(
                version = release.version,
                releasedOn = release.releasedOn,
                onThisPhone = release.version == installed.name,
                changes = release.changes,
            )
        },
    )

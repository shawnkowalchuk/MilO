package com.shawnkowalchuk.milo.data.changelog

import java.time.LocalDate
import java.time.format.DateTimeParseException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

// The list of versions and what changed in each (Shawn's request of 2026-10-08: "make it work
// same as gopherforms and seawingman. with including version and what has changed"). GopherForms
// and SeaWingman keep theirs in a database behind a server. MilO has neither, so the list is a
// file in the repository, `app/src/main/assets/changelog.json`, built into the app and turned
// into the website's page by `tools/changes_page.py`. It is edited in a pull request like the
// code: a change Shawn will notice adds a line, and a release dates the newest version
// (README, "Making a release").
//
// The file, newest version first:
//
//   { "releases": [
//       { "version": "0.2.0", "date": null,         "changes": [ ... ] },
//       { "version": "0.1.0", "date": "2026-10-09", "changes": [ ... ] } ] }
//
// A change is { "kind": "new" | "improved" | "fixed" | "security", "title": "...",
// "body": "..." }, the body optional. A version without a date is not released yet: only the
// newest can be. tools/changes_page.py checks the rest in CI (the order, the dates, and that the
// newest version is the build's versionName).

/** The whole list, newest version first. */
@Serializable
data class Changelog(val releases: List<Release>)

/**
 * One version and what changed in it.
 *
 * @param version the build's versionName for it ("0.1.0").
 * @param date the day it was released, as "2026-10-09", or null while it is not released yet.
 */
@Serializable
data class Release(val version: String, val date: String?, val changes: List<Change>) {
    /** [date] as a day, or null while the version is not released. */
    val releasedOn: LocalDate? get() = date?.let(LocalDate::parse)
}

/**
 * One thing that changed.
 *
 * @param body a sentence or two more, or null where the title says it all.
 */
@Serializable
data class Change(val kind: ChangeKind, val title: String, val body: String? = null)

/** What sort of change a line is: the four of GopherForms' list, with its words. */
@Serializable
enum class ChangeKind {
    @SerialName("new")
    NEW,

    @SerialName("improved")
    IMPROVED,

    @SerialName("fixed")
    FIXED,

    @SerialName("security")
    SECURITY,
}

/** Thrown when the bundled list cannot be read: a build with a broken file. */
class ChangelogUnreadableException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

/**
 * Strict, as the export file is: an unknown key is a typing mistake in the file, and is
 * refused rather than passed over.
 */
private val ChangelogJson: Json = Json

/**
 * Reads the list from the file's [text]. A date that is not a day is refused here too, so that
 * the screen never meets one.
 *
 * @throws ChangelogUnreadableException when the text is not such a list.
 */
fun parseChangelog(text: String): Changelog {
    val changelog =
        try {
            ChangelogJson.decodeFromString<Changelog>(text)
        } catch (broken: SerializationException) {
            // Also what a missing key, an unknown one and a wrong kind of value are.
            throw ChangelogUnreadableException("The list of changes is not valid", broken)
        }
    for (release in changelog.releases) {
        try {
            release.releasedOn
        } catch (notADay: DateTimeParseException) {
            throw ChangelogUnreadableException("${release.version} has no valid date", notADay)
        }
    }
    return changelog
}

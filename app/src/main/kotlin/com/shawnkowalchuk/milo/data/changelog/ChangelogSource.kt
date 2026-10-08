package com.shawnkowalchuk.milo.data.changelog

import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The name of the list of changes among the app's assets (`app/src/main/assets/`). */
const val CHANGELOG_ASSET = "changelog.json"

/**
 * The list of versions and their changes that is built into the app ([parseChangelog] says
 * what it holds). It is read each time it is asked for: it is a few kilobytes, and only the
 * What's new screen asks.
 *
 * @param open opens the file: the app's assets in the app, a string in a test.
 */
class ChangelogSource(
    private val open: () -> InputStream,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    /** @throws ChangelogUnreadableException when the file is missing or not such a list. */
    suspend fun read(): Changelog = withContext(io) {
        val text =
            try {
                open().bufferedReader().use { it.readText() }
            } catch (missing: IOException) {
                throw ChangelogUnreadableException(
                    "The list of changes could not be opened",
                    missing,
                )
            }
        parseChangelog(text)
    }
}

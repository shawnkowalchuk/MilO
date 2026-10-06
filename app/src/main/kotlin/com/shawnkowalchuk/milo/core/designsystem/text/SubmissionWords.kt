package com.shawnkowalchuk.milo.core.designsystem.text

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.util.formatDate
import java.time.ZoneId
import java.util.Locale

/**
 * Which sentence says whether a month's report has been sent: "Not submitted", "Submitted on
 * (date)", or the same with the newest revision and its date. Only the choice is made here, so
 * that it is tested without a phone; the words themselves are in strings.xml.
 *
 * In one place because the Trips screen's month card and the Report screen both say it, and a
 * month must read the same on both.
 *
 * @param submitted whether a report for the whole month is in the list of sent reports.
 * @param revisions the newest report's revision number: 0 for the month's original report.
 * @param sentAgain whether the list holds a later report for the month than the one that makes
 * it submitted. False with a revision number above 0 means that only a revision is left: the
 * original was removed from the list.
 */
fun submissionWordsRes(submitted: Boolean, revisions: Int, sentAgain: Boolean): Int = when {
    !submitted -> R.string.report_not_submitted
    sentAgain -> R.string.report_submitted_revised
    revisions > 0 -> R.string.report_submitted_by_revision
    else -> R.string.report_submitted_on
}

/**
 * The sentence itself, for a month that is submitted by the report sent at [firstSentAtMs]
 * (null if none is) and whose newest one, revision [revisions], was sent at [latestSentAtMs].
 */
@Composable
fun submissionWords(
    firstSentAtMs: Long?,
    revisions: Int,
    sentAgain: Boolean,
    latestSentAtMs: Long?,
    zone: ZoneId,
    locale: Locale,
): String {
    val first = firstSentAtMs?.let { formatDate(it, zone, locale) }
    val latest = latestSentAtMs?.let { formatDate(it, zone, locale) }
    return when (val words = submissionWordsRes(first != null, revisions, sentAgain)) {
        R.string.report_submitted_revised ->
            stringResource(words, first.orEmpty(), revisions, latest.orEmpty())

        R.string.report_submitted_by_revision -> stringResource(words, first.orEmpty(), revisions)

        R.string.report_submitted_on -> stringResource(words, first.orEmpty())

        else -> stringResource(words)
    }
}

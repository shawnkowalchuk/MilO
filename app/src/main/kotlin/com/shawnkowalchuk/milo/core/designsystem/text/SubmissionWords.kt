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
 * @param submitted whether a report for the whole month has been sent.
 * @param revisions how many times a report replaced the first one.
 */
fun submissionWordsRes(submitted: Boolean, revisions: Int): Int = when {
    !submitted -> R.string.report_not_submitted
    revisions > 0 -> R.string.report_submitted_revised
    else -> R.string.report_submitted_on
}

/**
 * The sentence itself, for a month whose first report was sent at [firstSentAtMs] (null if
 * none was) and whose newest one, revision [revisions], at [latestSentAtMs].
 */
@Composable
fun submissionWords(
    firstSentAtMs: Long?,
    revisions: Int,
    latestSentAtMs: Long?,
    zone: ZoneId,
    locale: Locale,
): String {
    val first = firstSentAtMs?.let { formatDate(it, zone, locale) }
    val latest = latestSentAtMs?.let { formatDate(it, zone, locale) }
    return when (val words = submissionWordsRes(submitted = first != null, revisions)) {
        R.string.report_submitted_revised ->
            stringResource(words, first.orEmpty(), revisions, latest.orEmpty())

        R.string.report_submitted_on -> stringResource(words, first.orEmpty())

        else -> stringResource(words)
    }
}

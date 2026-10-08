package com.shawnkowalchuk.milo.feature.report

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.component.ConfirmDialog
import com.shawnkowalchuk.milo.core.designsystem.text.distanceRes
import com.shawnkowalchuk.milo.core.report.ReportPeriod
import com.shawnkowalchuk.milo.core.report.periodInWords
import com.shawnkowalchuk.milo.core.util.formatDate
import com.shawnkowalchuk.milo.core.util.formatDay
import com.shawnkowalchuk.milo.core.util.formatDistance
import com.shawnkowalchuk.milo.core.util.formatTenths
import com.shawnkowalchuk.milo.data.report.RemovalEffect
import com.shawnkowalchuk.milo.data.report.SentEffect
import com.shawnkowalchuk.milo.data.settings.ReportHandOver
import java.time.ZoneId
import java.util.Locale

// The questions of the Report screen: before a month is sent that has not ended, before a
// period is sent a second time, when MilO is in front again after the email app, whether the
// email was sent, and before a report is removed from the list of sent reports.

/** What stands between two paragraphs of a question: an empty line. */
private const val PARAGRAPH_BREAK = "\n\n"

/** A period in the words of the screen, the PDF and the email's subject. */
@Composable
internal fun periodWords(period: ReportPeriod, locale: Locale): String =
    periodInWords(period, locale, stringResource(R.string.report_period_range_words))

/** One of the questions before a report is sent ([sendQuestions]), with its two answers. */
@Composable
internal fun BeforeSendQuestion(
    question: SendQuestion,
    state: ReportUiState.Ready,
    onSend: () -> Unit,
    onKeep: () -> Unit,
) {
    val resend = state.resend
    when {
        question == SendQuestion.MONTH_NOT_ENDED -> NotEndedQuestion(state, onSend, onKeep)

        resend != null -> ResendQuestion(resend, state.zone, onSend, onKeep)

        // Asked only for a period that was sent before, so this is never reached. If it ever
        // were, the question is closed and nothing is sent without one having been seen.
        else -> LaunchedEffect(Unit) { onKeep() }
    }
}

/**
 * Asked before a report is sent for a month that has not ended. The Report screen opens on the
 * month the Trips screen was showing, which is usually the current one, and a report sent on
 * the 28th leaves the trips of the last days on no report until the month is sent again.
 */
@Composable
private fun NotEndedQuestion(state: ReportUiState.Ready, onSend: () -> Unit, onKeep: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val period = state.choice.period
    ConfirmDialog(
        title = stringResource(R.string.report_not_ended_title),
        text =
            stringResource(
                R.string.report_not_ended_text,
                periodWords(period, locale),
                formatDay(period.lastDay, locale),
            ),
        confirmLabel = stringResource(R.string.report_not_ended_confirm),
        dismissLabel = stringResource(R.string.action_cancel),
        onConfirm = onSend,
        onDismiss = onKeep,
    )
}

/**
 * Asked before a report is removed from the list of sent reports: it says what removing it
 * does, which depends on what else the list holds ([RemovalEffect]). Removing takes away the
 * record that a report was sent, and there is no button that puts it back.
 */
@Composable
internal fun RemoveQuestion(
    report: SentLine,
    zone: ZoneId,
    onRemove: () -> Unit,
    onKeep: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val period = periodWords(report.period, locale)
    val sentOn = formatDate(report.sentAtMs, zone, locale)
    val text =
        when (val effect = report.removal) {
            RemovalEffect.UnmarksMonth ->
                stringResource(R.string.report_remove_text_month_unmarked, period, sentOn)

            is RemovalEffect.MonthStaysSubmitted ->
                stringResource(
                    R.string.report_remove_text_month_kept,
                    period,
                    sentOn,
                    formatDate(effect.submittedAtMs, zone, locale),
                )

            RemovalEffect.UnlistsRange ->
                stringResource(R.string.report_remove_text_range, period, sentOn)
        }
    ConfirmDialog(
        title = stringResource(R.string.report_remove_title),
        text = text,
        confirmLabel = stringResource(R.string.report_remove),
        dismissLabel = stringResource(R.string.action_cancel),
        onConfirm = onRemove,
        onDismiss = onKeep,
    )
}

/**
 * Asked before a report is sent for a period that was sent before. Sending again is allowed;
 * the question is there so that it is not done by a second tap, and so that he knows the new
 * report is labelled a revision.
 */
@Composable
private fun ResendQuestion(resend: Resend, zone: ZoneId, onSend: () -> Unit, onKeep: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val last = resend.last
    val trips = pluralStringResource(R.plurals.trips_count, last.tripCount, last.tripCount)
    // In the unit that report was printed in, as it stood on it.
    val km =
        stringResource(
            distanceRes(last.unit),
            formatDistance(last.distanceMetres, last.unit, locale),
        )
    ConfirmDialog(
        title = stringResource(R.string.report_resend_title),
        text =
            stringResource(
                R.string.report_resend_text,
                periodWords(last.period, locale),
                formatDate(last.sentAtMs, zone, locale),
                trips,
                km,
                resend.revision,
            ),
        confirmLabel = stringResource(R.string.report_resend_confirm),
        dismissLabel = stringResource(R.string.action_cancel),
        onConfirm = onSend,
        onDismiss = onKeep,
    )
}

/**
 * Asked before a report is marked as sent without the email app. It says what marking does,
 * which is what "I sent it" would do for the same period (`sentEffect`), with the figures that
 * are recorded, and for a month that has not ended what that costs. "Mark as sent" records it;
 * Cancel, Back and a tap beside the question record nothing.
 *
 * @param summary what the report holds at this moment: the figures that are recorded.
 */
@Composable
internal fun MarkQuestion(
    state: ReportUiState.Ready,
    summary: ReportSummary,
    onMark: () -> Unit,
    onKeep: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val period = periodWords(state.choice.period, locale)
    val trips = pluralStringResource(R.plurals.trips_count, summary.tripCount, summary.tripCount)
    val km = stringResource(distanceRes(summary.unit), formatTenths(summary.tenths, locale))
    val what =
        when (val effect = state.ifSent) {
            SentEffect.MarksMonth ->
                stringResource(R.string.report_mark_text_month, period, trips, km)

            is SentEffect.RevisesMonth ->
                stringResource(
                    R.string.report_mark_text_month_again,
                    period,
                    trips,
                    km,
                    effect.revision,
                    formatDate(effect.firstSentAtMs, state.zone, locale),
                )

            SentEffect.ListsRange ->
                stringResource(R.string.report_mark_text_range, period, trips, km)
        }
    val notEnded =
        if (SendQuestion.MONTH_NOT_ENDED in state.sendQuestions) {
            stringResource(R.string.report_mark_not_ended, period)
        } else {
            null
        }
    val paragraphs = listOfNotNull(what, notEnded, stringResource(R.string.report_mark_use))
    ConfirmDialog(
        title = stringResource(R.string.report_mark_title),
        text = paragraphs.joinToString(separator = PARAGRAPH_BREAK),
        confirmLabel = stringResource(R.string.report_mark_sent),
        dismissLabel = stringResource(R.string.action_cancel),
        onConfirm = onMark,
        onDismiss = onKeep,
    )
}

/**
 * "Did you send it?" Android gives an app no way to know, so the report is recorded as sent on
 * Shawn's word and on nothing else.
 *
 * "Not sent" is an answer too, and ends the question. A press beside it, or Back, is not an
 * answer: the question is put off and comes again the next time he is back on the screen, so
 * that a stray tap cannot leave a month that was sent looking as if it was not.
 *
 * @param effect what "I sent it" would do, which the question says before it is answered. A
 * month that was sent before is not promised a new date: it keeps the day of its first report.
 */
@Composable
internal fun SentQuestion(
    report: ReportHandOver,
    effect: SentEffect,
    zone: ZoneId,
    onAnswer: (sent: Boolean) -> Unit,
    onPutOff: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val period = periodWords(report.period, locale)
    val handedOver = formatDate(report.atMs, zone, locale)
    val text =
        when (effect) {
            SentEffect.MarksMonth ->
                stringResource(R.string.report_sent_question_month, period, handedOver)

            is SentEffect.RevisesMonth ->
                stringResource(
                    R.string.report_sent_question_month_again,
                    period,
                    handedOver,
                    effect.revision,
                    formatDate(effect.firstSentAtMs, zone, locale),
                )

            SentEffect.ListsRange ->
                stringResource(R.string.report_sent_question_range, period, handedOver)
        }
    ConfirmDialog(
        title = stringResource(R.string.report_sent_question_title),
        text = text,
        confirmLabel = stringResource(R.string.report_sent_yes),
        dismissLabel = stringResource(R.string.report_sent_no),
        onConfirm = { onAnswer(true) },
        onDismiss = { onAnswer(false) },
        onPutOff = onPutOff,
    )
}

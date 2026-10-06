package com.shawnkowalchuk.milo.feature.report

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.component.ConfirmDialog
import com.shawnkowalchuk.milo.core.report.ReportPeriod
import com.shawnkowalchuk.milo.core.report.periodInWords
import com.shawnkowalchuk.milo.core.util.formatDate
import com.shawnkowalchuk.milo.core.util.formatKilometres
import com.shawnkowalchuk.milo.data.report.SentEffect
import com.shawnkowalchuk.milo.data.settings.ReportHandOver
import java.time.ZoneId
import java.util.Locale

// The two questions of the Report screen: before a period is sent a second time, and, when
// MilO is in front again after the email app, whether the email was sent.

/** A period in the words of the screen, the PDF and the email's subject. */
@Composable
internal fun periodWords(period: ReportPeriod, locale: Locale): String =
    periodInWords(period, locale, stringResource(R.string.report_period_range_words))

/**
 * Asked before a report is sent for a period that was sent before. Sending again is allowed;
 * the question is there so that it is not done by a second tap, and so that he knows the new
 * report is labelled a revision.
 */
@Composable
internal fun ResendQuestion(resend: Resend, zone: ZoneId, onSend: () -> Unit, onKeep: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val last = resend.last
    val trips = pluralStringResource(R.plurals.trips_count, last.tripCount, last.tripCount)
    val km = stringResource(R.string.distance_km, formatKilometres(last.distanceMetres, locale))
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

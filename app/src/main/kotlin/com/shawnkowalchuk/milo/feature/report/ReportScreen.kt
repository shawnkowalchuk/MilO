package com.shawnkowalchuk.milo.feature.report

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.shawnkowalchuk.milo.core.designsystem.component.CameToFrontEffect
import java.time.LocalDate

/** What the tiles of the Report screen can ask for. */
internal class ReportActions(
    val onKind: (PeriodKind) -> Unit,
    val onPreviousMonth: () -> Unit,
    val onNextMonth: () -> Unit,
    val onRangeFirst: (LocalDate) -> Unit,
    val onRangeLast: (LocalDate) -> Unit,
    val onPreviewPdf: () -> Unit,
    val onSend: () -> Unit,
    val onExportCsv: () -> Unit,
    val onSaveBoth: () -> Unit,
    val onMarkSent: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onRemoveSent: (Long) -> Unit,
)

/**
 * The report for the accountant: the Business trips of a month, or of a date range, as a PDF
 * that is handed to the email app, and as a CSV file.
 *
 * MilO sends nothing itself. "Email the report" opens the email app with the PDF attached,
 * and Shawn presses send there. Android does not tell an app what became of an email, so when
 * he is back MilO asks, and only his "I sent it" records the report as sent. "Mark as sent"
 * records one without the email app, after a question, and a report that was recorded by
 * mistake can be removed from the list again, after a question too.
 *
 * @param onBack leaves the screen. Navigation belongs to the app, not the feature.
 * @param onOpenSettings opens Settings, where the name and the accountant's address are set.
 */
@Composable
fun ReportScreen(
    viewModel: ReportViewModel,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val ready = state as? ReportUiState.Ready

    // Which day is today can change while MilO sits in the background over midnight.
    CameToFrontEffect(viewModel::onCameToFront)

    // "Did you send it?" is asked about the report that was handed to the email app, which is
    // stored (ReportHandOver), so it is asked however MilO comes back: on this screen as it
    // was, on a screen that Android had to build again, or days later. It is held back only
    // while it would be in the way: from the moment this screen opens the email app until
    // Shawn is looking at MilO again, and after he has put the question off with a tap beside
    // it, until he next comes back to the screen.
    var heldBack by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { heldBack = false }
    // Which of the questions before sending is being asked (its place in the list the screen
    // was given), and which sent report is being asked about before it is removed. Saved, so
    // that turning the phone closes neither.
    var askingBeforeSend by rememberSaveable { mutableStateOf<Int?>(null) }
    var removingId by rememberSaveable { mutableStateOf<Long?>(null) }
    // Whether "Mark as sent" is asking its question. Saved for the same reason.
    var askingToMark by rememberSaveable { mutableStateOf(false) }

    // Another app is opened on MilO's own activity, so that Back from it leads here. What comes
    // back says nothing about an email ("Output: nothing", in Android's own words): it only
    // marks the moment the other app's screen has closed.
    val otherApp =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            heldBack = false
        }

    val launch = ready?.launch
    LaunchedEffect(launch?.id) {
        if (launch != null) {
            if (launch.handOver != null) heldBack = true
            // The requests are tried in order: Gmail, then any email app. A phone that has no
            // app for one of them says so by throwing.
            val opened =
                launch.intents.any { request ->
                    try {
                        otherApp.launch(request)
                        true
                    } catch (noSuchApp: ActivityNotFoundException) {
                        false
                    }
                }
            viewModel.onLaunched(launch, opened)
        }
    }

    val actions =
        ReportActions(
            onKind = viewModel::onKind,
            onPreviousMonth = viewModel::onPreviousMonth,
            onNextMonth = viewModel::onNextMonth,
            onRangeFirst = viewModel::onRangeFirst,
            onRangeLast = viewModel::onRangeLast,
            onPreviewPdf = viewModel::onPreviewPdf,
            onSend = {
                when {
                    // A report that was handed over earlier still waits for its answer, put
                    // off with a tap beside the question. It is asked again before another
                    // report is sent: one question at a time.
                    ready?.awaiting != null -> heldBack = false

                    // A month that has not ended, and a period that was sent before, ask first.
                    // A press that lacks a setting is not asked about: it is refused, and the
                    // screen says what is missing.
                    ready != null && ready.missing.isEmpty() && ready.sendQuestions.isNotEmpty() ->
                        askingBeforeSend = 0

                    else -> viewModel.onSend()
                }
            },
            onExportCsv = viewModel::onExportCsv,
            onSaveBoth = viewModel::onSaveBoth,
            onMarkSent = {
                // One question at a time, as for sending: a report that was handed to the
                // email app and still waits for its answer is asked about first.
                if (ready?.awaiting != null) heldBack = false else askingToMark = true
            },
            onOpenSettings = onOpenSettings,
            onRemoveSent = { id -> removingId = id },
        )
    ReportContent(state = state, actions = actions, onBack = onBack, modifier = modifier)

    // One question at a time, in the order the screen was given them. "Yes" to the last one
    // sends; "no" to any of them sends nothing.
    val asked = askingBeforeSend
    val question = asked?.let { ready?.sendQuestions?.getOrNull(it) }
    if (ready != null && asked != null && question != null) {
        BeforeSendQuestion(
            question = question,
            state = ready,
            onSend = {
                if (asked < ready.sendQuestions.lastIndex) {
                    askingBeforeSend = asked + 1
                } else {
                    askingBeforeSend = null
                    viewModel.onSend()
                }
            },
            onKeep = { askingBeforeSend = null },
        )
    }

    // Looked up again each time: once the report is gone from the list, by this press or
    // otherwise, there is nothing left to ask about.
    val removing = ready?.sent?.firstOrNull { it.id == removingId }
    if (ready != null && removing != null) {
        RemoveQuestion(
            report = removing,
            zone = ready.zone,
            onRemove = {
                removingId = null
                viewModel.onRemoveSent(removing.id)
            },
            onKeep = { removingId = null },
        )
    }

    val marking = ready?.summary
    if (ready != null && marking != null && askingToMark) {
        MarkQuestion(
            state = ready,
            summary = marking,
            onMark = {
                askingToMark = false
                viewModel.onMarkSent()
            },
            onKeep = { askingToMark = false },
        )
    }

    // Not while a file is being made or another app is about to be opened: the hand-over is
    // stored a moment before the email app covers the screen.
    val owed = ready?.awaiting
    val effect = ready?.awaitingEffect
    if (owed != null && effect != null && !heldBack && !ready.working && ready.launch == null) {
        SentQuestion(
            report = owed,
            effect = effect,
            zone = ready.zone,
            onAnswer = viewModel::onAnswer,
            onPutOff = { heldBack = true },
        )
    }
}

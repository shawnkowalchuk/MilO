package com.shawnkowalchuk.milo.feature.report

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.component.CameToFrontEffect
import com.shawnkowalchuk.milo.core.designsystem.component.RowStatus
import com.shawnkowalchuk.milo.core.designsystem.component.ScreenTitle
import com.shawnkowalchuk.milo.core.designsystem.component.StatusRow
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme
import java.time.LocalDate

/** What the cards of the Report screen can ask for. */
internal class ReportActions(
    val onKind: (PeriodKind) -> Unit,
    val onPreviousMonth: () -> Unit,
    val onNextMonth: () -> Unit,
    val onRangeFirst: (LocalDate) -> Unit,
    val onRangeLast: (LocalDate) -> Unit,
    val onCreatePdf: () -> Unit,
    val onOpenPdf: () -> Unit,
    val onSend: () -> Unit,
    val onExportCsv: () -> Unit,
    val onOpenSettings: () -> Unit,
)

/**
 * The report for the accountant: the Business trips of a month, or of a date range, as a PDF
 * that is handed to the email app, and as a CSV file.
 *
 * MilO sends nothing itself. "Send to accountant" opens the email app with the PDF attached,
 * and Shawn presses send there. Android does not tell an app what became of an email, so when
 * he is back MilO asks, and only his "I sent it" records the report as sent.
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
    var askingToResend by rememberSaveable { mutableStateOf(false) }

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
            onCreatePdf = viewModel::onCreatePdf,
            onOpenPdf = viewModel::onOpenPdf,
            onSend = {
                when {
                    // A report that was handed over earlier still waits for its answer, put
                    // off with a tap beside the question. It is asked again before another
                    // report is sent: one question at a time.
                    ready?.awaiting != null -> heldBack = false

                    // Sending a period again asks first. A press that lacks a setting is not
                    // asked about: it is refused, and the screen says what is missing.
                    ready?.resend != null && ready.missing.isEmpty() -> askingToResend = true

                    // TODO(debt): a month that has not ended is sent without a question, and a
                    //  month whose trips changed after it was sent still reads "Submitted" with
                    //  nothing said. See docs/FINDINGS_LOG.md (2026-10-06).
                    else -> viewModel.onSend()
                }
            },
            onExportCsv = viewModel::onExportCsv,
            onOpenSettings = onOpenSettings,
        )
    ReportContent(state = state, actions = actions, onBack = onBack, modifier = modifier)

    val resend = ready?.resend
    if (askingToResend && resend != null) {
        ResendQuestion(
            resend = resend,
            zone = ready.zone,
            onSend = {
                askingToResend = false
                viewModel.onSend()
            },
            onKeep = { askingToResend = false },
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

@Composable
internal fun ReportContent(
    state: ReportUiState,
    actions: ReportActions,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(MiloTheme.spacing.medium),
        verticalArrangement = Arrangement.spacedBy(MiloTheme.spacing.medium),
    ) {
        ScreenTitle(text = stringResource(R.string.report_title), onBack = onBack)
        when (state) {
            ReportUiState.Reading ->
                Text(
                    text = stringResource(R.string.report_reading),
                    style = MaterialTheme.typography.bodyLarge,
                )

            ReportUiState.Unreadable ->
                StatusRow(
                    label = stringResource(R.string.report_settings_unreadable),
                    status = RowStatus.PROBLEM,
                )

            is ReportUiState.Ready -> {
                PeriodCard(state, actions)
                SummaryCard(state)
                ActionsCard(state, actions)
                SentReportsCard(state)
            }
        }
    }
}

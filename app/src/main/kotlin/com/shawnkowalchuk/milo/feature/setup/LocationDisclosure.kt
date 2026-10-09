package com.shawnkowalchuk.milo.feature.setup

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.component.ConfirmDialog

/**
 * What MilO says before Android is asked for "Allow all the time" (2026-10-09, ADR-004): the
 * location it collects, why, that it does so even when the app is closed or not in use, and that
 * it stays on the phone. Google Play wants this in the app itself, shown before the request and
 * answered by a press; the request is made only after [onContinue].
 *
 * It is the design system's question dialog, so it looks like every other question MilO asks.
 * Pressing beside it, or Back, is "Not now": nothing is asked, and the row keeps its button.
 */
@Composable
internal fun LocationDisclosure(onContinue: () -> Unit, onNotNow: () -> Unit) {
    ConfirmDialog(
        title = stringResource(R.string.setup_disclosure_title),
        text = stringResource(R.string.setup_disclosure_text),
        confirmLabel = stringResource(R.string.setup_disclosure_continue),
        dismissLabel = stringResource(R.string.setup_disclosure_not_now),
        onConfirm = onContinue,
        onDismiss = onNotNow,
    )
}

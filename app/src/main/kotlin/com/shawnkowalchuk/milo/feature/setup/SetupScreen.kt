package com.shawnkowalchuk.milo.feature.setup

import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.component.AppHeader
import com.shawnkowalchuk.milo.core.designsystem.component.CameToFrontEffect
import com.shawnkowalchuk.milo.core.designsystem.component.PrimaryButton
import com.shawnkowalchuk.milo.core.designsystem.component.Tile
import com.shawnkowalchuk.milo.core.designsystem.component.TileColumn
import com.shawnkowalchuk.milo.core.designsystem.component.TilePress
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme
import com.shawnkowalchuk.milo.data.settings.ConfirmedStep
import com.shawnkowalchuk.milo.platform.system.SetupDetail
import com.shawnkowalchuk.milo.platform.system.SetupFix
import com.shawnkowalchuk.milo.platform.system.SetupItem
import com.shawnkowalchuk.milo.platform.system.SetupRow
import com.shawnkowalchuk.milo.platform.system.SetupState
import com.shawnkowalchuk.milo.platform.system.SystemScreen

/**
 * The permission checklist: one row for everything an automatic trip start depends on, each
 * with its state and a button that leads to the place to fix it. Above the rows, how many of
 * them are ready.
 *
 * Since 2026-10-07 it is not in the bottom bar: it is opened from the tile at the top of
 * Settings ([SetupLinkTile]) and from Home's warning, and its name at the top is the way back.
 *
 * @param onOpenPairing the truck row's button. Navigation belongs to the app, not the feature.
 * @param onBack leaves the screen.
 * @param onDone the button at the end, "Done, go to Settings", while the first start is being
 * gone through (2026-10-08); null otherwise, and then there is no such button. It can be pressed
 * with rows still to fix: Home's warning goes on saying so.
 */
@Composable
fun SetupScreen(
    viewModel: SetupViewModel,
    onOpenPairing: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onDone: (() -> Unit)? = null,
) {
    val rows by viewModel.rows.collectAsState()
    val activity = LocalActivity.current

    // Shawn changes a setting somewhere else and comes back: from the phone's settings, from
    // one of Android's own dialogs, or from the quick settings panel pulled down over MilO.
    CameToFrontEffect(viewModel::onCameToFront)

    // Android's question "should the app explain why it needs this?", which is how a dialog
    // that never appeared is told from one that was refused (see androidDidNotAsk).
    fun canExplain(permissions: Collection<String>): Boolean =
        permissions.any { activity?.shouldShowRequestPermissionRationale(it) == true }

    val permissionDialog =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) { answers ->
            viewModel.onPermissionAnswer(
                granted = answers.values.all { it },
                canExplainAfter = canExplain(answers.keys),
            )
        }

    val actions =
        SetupActions(
            onFix = { fix ->
                when (fix) {
                    is SetupFix.AskPermission -> {
                        viewModel.onAsking(fix, canExplain(fix.permissions))
                        permissionDialog.launch(fix.permissions.toTypedArray())
                    }

                    is SetupFix.Open -> viewModel.onOpenScreen(fix.screen)

                    SetupFix.OpenPairing -> onOpenPairing()
                }
            },
            onConfirm = viewModel::onConfirm,
            onTakeBack = viewModel::onTakeBack,
        )
    SetupContent(
        rows = rows,
        actions = actions,
        onBack = onBack,
        modifier = modifier,
        onDone = onDone,
    )
}

/**
 * The way into Setup from Settings (Shawn's decision of 2026-10-07: the bottom bar's Setup
 * button became Settings, and Setup is a tile at the top of Settings). It is the tile with the
 * count from the top of Setup, with the screen's name and an arrowhead over it, and the whole
 * tile opens the checklist.
 *
 * Settings shows it in a slot that the app fills, because a feature never imports another one.
 * It reads the rows through Setup's own view model, so the count cannot disagree with Setup's.
 *
 * @param onOpenSetup opens the checklist. Navigation belongs to the app, not the feature.
 */
@Composable
fun SetupLinkTile(viewModel: SetupViewModel, onOpenSetup: () -> Unit) {
    val rows by viewModel.rows.collectAsState()
    // As on Setup itself: the phone's settings change outside MilO without a word.
    CameToFrontEffect(viewModel::onCameToFront)
    SummaryTile(
        summary = rows?.let(::setupSummary),
        link = TilePress(stringResource(R.string.setup_open), onOpenSetup),
    )
}

/**
 * Setup as the owner's design draws it: the title, the tile with the count, then each group of
 * rows under its small label, the rows that are to be fixed first. While the phone is being
 * read for the first time, one tile says so in place of all of them.
 */
@Composable
private fun SetupContent(
    rows: List<SetupRow>?,
    actions: SetupActions,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onDone: (() -> Unit)? = null,
) {
    val spacing = MiloTheme.spacing
    TileColumn(
        modifier =
            modifier
                .fillMaxSize()
                // Large font settings or a small window must scroll rather than cut content off.
                .verticalScroll(rememberScrollState())
                .padding(vertical = spacing.small),
    ) {
        AppHeader(
            title = stringResource(R.string.setup_title),
            onBack = onBack,
            // As on Home: with the gap between two tiles, the design's 16 under the top line.
            modifier = Modifier.padding(bottom = spacing.extraSmall),
        )
        if (rows == null) {
            Tile(modifier = Modifier.fillMaxWidth()) { Note(R.string.setup_reading) }
            return@TileColumn
        }
        SummaryTile(setupSummary(rows))

        val (xiaomi, android) = rows.partition { it.item.xiaomiOnly }
        val androidLabel = stringResource(R.string.setup_section_android)
        ChecklistGroup(androidLabel, toFixFirst(android), actions)
        if (xiaomi.isNotEmpty()) {
            val xiaomiLabel = stringResource(R.string.setup_section_xiaomi)
            ChecklistGroup(xiaomiLabel, toFixFirst(xiaomi), actions)
            // Under the tile it explains, and in from the edge like the label above it.
            Note(
                R.string.setup_section_xiaomi_note,
                Modifier.padding(horizontal = spacing.extraSmall),
            )
        }
        if (onDone != null) {
            PrimaryButton(
                text = stringResource(R.string.setup_done),
                onClick = onDone,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** A quiet sentence: what the screen is doing, or what a group of rows is about. */
@Composable
private fun Note(@StringRes text: Int, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(text),
        modifier = modifier,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// Sample values are written inline because a preview is never shown to a user or shipped.
@Preview
@Composable
private fun SetupPreview() {
    val rows =
        listOf(
            SetupRow(SetupItem.PRECISE_LOCATION, SetupState.OK, SetupDetail.FINE),
            SetupRow(
                SetupItem.BATTERY_EXEMPTION,
                SetupState.PROBLEM,
                SetupDetail.NOT_SET,
                SetupFix.Open(SystemScreen.BATTERY_EXEMPTION),
            ),
            SetupRow(
                SetupItem.TRUCK,
                SetupState.OK,
                SetupDetail.FINE,
                SetupFix.OpenPairing,
                truckName = "Work truck",
            ),
            SetupRow(
                SetupItem.XIAOMI_AUTOSTART,
                SetupState.UNKNOWN,
                SetupDetail.AUTOSTART_UNREADABLE,
                SetupFix.Open(SystemScreen.XIAOMI_AUTOSTART),
                ConfirmedStep.XIAOMI_AUTOSTART,
            ),
            SetupRow(
                SetupItem.XIAOMI_RECENTS_LOCK,
                SetupState.OK,
                SetupDetail.CONFIRMED,
                confirmStep = ConfirmedStep.XIAOMI_RECENTS_LOCK,
                confirmedAtMs = 1_791_028_800_000,
            ),
        )
    MiloTheme {
        Surface {
            SetupContent(rows = rows, actions = SetupActions({}, {}, {}), onBack = {})
        }
    }
}

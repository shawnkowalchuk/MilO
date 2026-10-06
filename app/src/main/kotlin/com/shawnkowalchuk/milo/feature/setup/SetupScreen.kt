package com.shawnkowalchuk.milo.feature.setup

import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.component.CameToFrontEffect
import com.shawnkowalchuk.milo.core.designsystem.component.ScreenTitle
import com.shawnkowalchuk.milo.core.designsystem.component.SectionCard
import com.shawnkowalchuk.milo.core.designsystem.component.StatusRow
import com.shawnkowalchuk.milo.core.designsystem.component.StatusRowAction
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme
import com.shawnkowalchuk.milo.core.util.formatDate
import com.shawnkowalchuk.milo.data.settings.ConfirmedStep
import com.shawnkowalchuk.milo.platform.system.SetupDetail
import com.shawnkowalchuk.milo.platform.system.SetupFix
import com.shawnkowalchuk.milo.platform.system.SetupItem
import com.shawnkowalchuk.milo.platform.system.SetupRow
import com.shawnkowalchuk.milo.platform.system.SetupState
import com.shawnkowalchuk.milo.platform.system.SystemScreen
import com.shawnkowalchuk.milo.platform.system.needsAttention
import java.time.ZoneId

/** What the rows of the checklist can ask the screen to do. */
private class SetupActions(
    val onFix: (SetupFix) -> Unit,
    val onConfirm: (ConfirmedStep) -> Unit,
    val onTakeBack: (ConfirmedStep) -> Unit,
)

/**
 * The permission checklist: one row for everything an automatic trip start depends on, each
 * with its state and a button that leads to the place to fix it.
 *
 * @param onOpenPairing the truck row's button. Navigation belongs to the app, not the feature.
 */
@Composable
fun SetupScreen(
    viewModel: SetupViewModel,
    onOpenPairing: () -> Unit,
    modifier: Modifier = Modifier,
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
    SetupContent(rows = rows, actions = actions, modifier = modifier)
}

@Composable
private fun SetupContent(
    rows: List<SetupRow>?,
    actions: SetupActions,
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
        ScreenTitle(text = stringResource(R.string.setup_title))
        if (rows == null) {
            Text(
                text = stringResource(R.string.setup_reading),
                style = MaterialTheme.typography.bodyLarge,
            )
            return@Column
        }

        // The same rule as the home screen's warning, so the two always agree.
        val open = rows.count { it.needsAttention() }
        Text(
            text =
                if (open == 0) {
                    stringResource(R.string.setup_summary_all_set)
                } else {
                    pluralStringResource(R.plurals.setup_summary_open, open, open)
                },
            style = MaterialTheme.typography.bodyLarge,
        )

        val (xiaomi, android) = rows.partition { it.item.xiaomiOnly }
        SectionCard(title = stringResource(R.string.setup_section_android)) {
            for (row in android) ChecklistRow(row, actions)
        }
        if (xiaomi.isNotEmpty()) {
            SectionCard(title = stringResource(R.string.setup_section_xiaomi)) {
                Text(
                    text = stringResource(R.string.setup_section_xiaomi_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                for (row in xiaomi) ChecklistRow(row, actions)
            }
        }
    }
}

@Composable
private fun ChecklistRow(row: SetupRow, actions: SetupActions) {
    val fix = row.fix
    val fixButton =
        fix?.let { StatusRowAction(stringResource(row.fixLabelRes())) { actions.onFix(it) } }
    val step = row.confirmStep
    // A row Shawn can confirm has two buttons: the one that opens the setting, and the one that
    // says he set it (or takes that back). The button for the next thing to do comes last.
    val (main, second) =
        when {
            step == null -> fixButton to null

            row.confirmedAtMs == null ->
                StatusRowAction(stringResource(R.string.setup_action_confirm)) {
                    actions.onConfirm(step)
                } to fixButton

            else -> {
                val takeBack =
                    StatusRowAction(stringResource(R.string.setup_action_take_back)) {
                        actions.onTakeBack(step)
                    }
                if (fixButton == null) takeBack to null else fixButton to takeBack
            }
        }
    StatusRow(
        label = stringResource(row.item.labelRes()),
        status = row.state.asRowStatus(),
        supportingText = detailText(row),
        action = main,
        secondaryAction = second,
    )
}

/** The sentence under the row's name, with the truck's name or the confirmation date filled in. */
@Composable
private fun detailText(row: SetupRow): String {
    val confirmedAtMs = row.confirmedAtMs
    return when {
        row.detail == SetupDetail.CONFIRMED && confirmedAtMs != null -> {
            val locale = LocalConfiguration.current.locales[0]
            val day = formatDate(confirmedAtMs, ZoneId.systemDefault(), locale)
            stringResource(row.detailRes(), day)
        }

        row.item == SetupItem.TRUCK && row.detail in TRUCK_DETAILS_WITH_A_NAME ->
            stringResource(
                row.detailRes(),
                row.truckName ?: stringResource(R.string.truck_without_a_name),
            )

        else -> stringResource(row.detailRes())
    }
}

/** The truck row's sentences that name the truck. */
private val TRUCK_DETAILS_WITH_A_NAME =
    setOf(SetupDetail.FINE, SetupDetail.TRUCK_ASSOCIATION_MISSING, SetupDetail.TRUCK_NOT_WATCHED)

// Sample values are written inline because a preview is never shown to a user or shipped.
@PreviewLightDark
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
            SetupContent(rows = rows, actions = SetupActions({}, {}, {}))
        }
    }
}

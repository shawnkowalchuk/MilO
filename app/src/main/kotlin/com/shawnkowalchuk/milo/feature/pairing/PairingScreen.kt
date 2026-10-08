package com.shawnkowalchuk.milo.feature.pairing

import android.Manifest
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.component.AppHeader
import com.shawnkowalchuk.milo.core.designsystem.component.CameToFrontEffect
import com.shawnkowalchuk.milo.core.designsystem.component.ConfirmDialog
import com.shawnkowalchuk.milo.core.designsystem.component.Tile
import com.shawnkowalchuk.milo.core.designsystem.component.TileColumn
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme
import com.shawnkowalchuk.milo.platform.bluetooth.PairedDevice
import com.shawnkowalchuk.milo.platform.bluetooth.Truck
import com.shawnkowalchuk.milo.platform.system.SetupFix
import com.shawnkowalchuk.milo.platform.system.SystemScreen

/** The request for "Nearby devices", and where to send Shawn once Android stops asking. */
private val NEARBY_DEVICES =
    SetupFix.AskPermission(
        permissions = listOf(Manifest.permission.BLUETOOTH_CONNECT),
        ifNotAsked = SystemScreen.APP_DETAILS,
    )

/** What the parts of the screen can ask for. */
internal class PairingActions(
    val onPick: (PairedDevice) -> Unit,
    val onAllowNearbyDevices: () -> Unit,
    val onOpenScreen: (SystemScreen) -> Unit,
    val onRemove: (Truck) -> Unit = {},
)

/**
 * The truck pairing screen: Shawn picks the truck from the phone's paired Bluetooth devices,
 * Android asks for his consent in a dialog of its own, and the screen says how it went. Since
 * 2026-10-08 picking another device adds it beside the truck, and each paired vehicle can be
 * removed, after a question.
 *
 * @param onBack leaves the screen. Navigation belongs to the app, not the feature.
 */
@Composable
fun PairingScreen(viewModel: PairingViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsState()
    val activity = LocalActivity.current

    // Shawn leaves this screen to switch Bluetooth or location on, and comes back; or he
    // switches them in the quick settings panel, pulled down over this screen.
    CameToFrontEffect(viewModel::onCameToFront)

    val consentDialog =
        rememberLauncherForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult(),
        ) { result -> viewModel.onConsentResult(result.resultCode) }

    // Android hands over its consent dialog a moment after a device is picked. It is shown
    // once: the ViewModel remembers which one was shown, so a rotation does not repeat it.
    val consent = state?.consent
    LaunchedEffect(consent) {
        if (consent != null && viewModel.claimConsent(consent)) {
            consentDialog.launch(IntentSenderRequest.Builder(consent).build())
        }
    }

    fun canExplain(): Boolean = NEARBY_DEVICES.permissions.any {
        activity?.shouldShowRequestPermissionRationale(it) == true
    }

    val permissionDialog =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) { answers ->
            viewModel.onPermissionAnswer(
                granted = answers.values.all { it },
                canExplainAfter = canExplain(),
            )
        }

    val actions =
        PairingActions(
            // Without an Activity there is no screen for Android's dialog; it cannot happen
            // while this screen is showing.
            onPick = { device -> activity?.let { viewModel.onDevicePicked(device, it) } },
            onAllowNearbyDevices = {
                viewModel.onAsking(NEARBY_DEVICES, canExplain())
                permissionDialog.launch(NEARBY_DEVICES.permissions.toTypedArray())
            },
            onOpenScreen = viewModel::onOpenScreen,
            onRemove = viewModel::onRemove,
        )
    PairingContent(state = state, actions = actions, onBack = onBack, modifier = modifier)
}

/**
 * The pairing screen in the language of the owner's design, which has no drawing of it: the
 * title behind the square back button, a quiet sentence, and then each part as a small label
 * on the page over a tile. While the phone's devices are being read for the first time, one
 * tile says so in place of all of them.
 */
@Composable
internal fun PairingContent(
    state: PairingUiState?,
    actions: PairingActions,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
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
            title = stringResource(R.string.pairing_title),
            onBack = onBack,
            // As on Home: with the gap between two tiles, the design's 16 under the top line.
            modifier = Modifier.padding(bottom = spacing.extraSmall),
        )
        if (state == null) {
            Tile(modifier = Modifier.fillMaxWidth()) {
                Note(stringResource(R.string.pairing_reading))
            }
            return@TileColumn
        }
        // In from the edge like a label above a tile.
        Note(
            stringResource(R.string.pairing_intro),
            Modifier.padding(horizontal = spacing.extraSmall),
        )
        // The vehicle Remove was pressed on, until the question is answered. Its address, which
        // survives a rotation; the line is looked up again, and is gone once it is removed.
        var removing by rememberSaveable { mutableStateOf<String?>(null) }
        TruckGroup(
            vehicles = state.vehicles,
            attempt = state.attempt,
            onRemove = { removing = it.vehicle.address },
        )
        state.vehicles.firstOrNull { it.vehicle.address == removing }?.let { line ->
            val name = line.name ?: stringResource(R.string.truck_without_a_name)
            ConfirmDialog(
                title = stringResource(R.string.pairing_remove_title, name),
                text = stringResource(R.string.pairing_remove_text),
                confirmLabel = stringResource(R.string.pairing_action_remove),
                dismissLabel = stringResource(R.string.pairing_remove_keep),
                onConfirm = {
                    removing = null
                    actions.onRemove(line.vehicle)
                },
                onDismiss = { removing = null },
            )
        }
        state.blocker?.let { BlockerGroup(blocker = it, actions = actions) }
        if (state.devices.isNotEmpty()) {
            DevicesGroup(
                devices = state.devices,
                hasTruck = state.truck != null,
                canPick = state.canPick,
                onPick = actions.onPick,
            )
        }
    }
}

/** A quiet sentence: what the screen is doing, or what it is for. */
@Composable
private fun Note(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

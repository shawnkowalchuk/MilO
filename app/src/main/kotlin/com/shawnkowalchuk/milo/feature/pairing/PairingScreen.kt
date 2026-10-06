package com.shawnkowalchuk.milo.feature.pairing

import android.Manifest
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.component.CameToFrontEffect
import com.shawnkowalchuk.milo.core.designsystem.component.ScreenTitle
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme
import com.shawnkowalchuk.milo.platform.bluetooth.PairedDevice
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
)

/**
 * The truck pairing screen: Shawn picks the truck from the phone's paired Bluetooth devices,
 * Android asks for his consent in a dialog of its own, and the screen says how it went. Picking
 * another device later changes the truck.
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
        )
    PairingContent(state = state, actions = actions, onBack = onBack, modifier = modifier)
}

@Composable
private fun PairingContent(
    state: PairingUiState?,
    actions: PairingActions,
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
        ScreenTitle(text = stringResource(R.string.pairing_title), onBack = onBack)
        if (state == null) {
            Text(
                text = stringResource(R.string.pairing_reading),
                style = MaterialTheme.typography.bodyLarge,
            )
            return@Column
        }
        Text(
            text = stringResource(R.string.pairing_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TruckCard(truck = state.truck, attempt = state.attempt)
        state.blocker?.let { BlockerCard(blocker = it, actions = actions) }
        if (state.devices.isNotEmpty()) {
            DevicesCard(
                devices = state.devices,
                hasTruck = state.truck != null,
                canPick = state.canPick,
                onPick = actions.onPick,
            )
        }
    }
}

// Sample values are written inline because a preview is never shown to a user or shipped.
@PreviewLightDark
@Composable
private fun PairingPreview() {
    val state =
        PairingUiState(
            truck = TruckLine("Work truck", TruckWatch.WATCHED),
            devices =
                listOf(
                    DeviceLine(PairedDevice("AA:BB:CC:DD:EE:FF", "Work truck"), isTruck = true),
                    DeviceLine(PairedDevice("11:22:33:44:55:66", "Earbuds"), isTruck = false),
                    DeviceLine(PairedDevice("77:88:99:AA:BB:CC", null), isTruck = false),
                ),
            attempt = PairingAttempt.Paired("Work truck"),
        )
    MiloTheme {
        Surface {
            PairingContent(state = state, actions = PairingActions({}, {}, {}), onBack = {})
        }
    }
}

@PreviewLightDark
@Composable
private fun PairingBlockedPreview() {
    val state =
        PairingUiState(blocker = PairingBlocker.BLUETOOTH_OFF, attempt = PairingAttempt.Declined)
    MiloTheme {
        Surface {
            PairingContent(state = state, actions = PairingActions({}, {}, {}), onBack = {})
        }
    }
}

package com.shawnkowalchuk.milo.platform.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.shawnkowalchuk.milo.data.settings.SettingsStore
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * The reading that follows from what the two profiles said. One profile listing the truck is
 * enough: with Android Auto the truck keeps the hands-free profile and sends audio over the
 * cable. "Not connected" needs both to say so. A profile that gave no answer could be the one
 * the truck is on, so the reading is then "unknown", never "not connected".
 *
 * @param handsFreeVehicle and [audioVehicle] the vehicle each profile lists, if it lists one:
 * the reading says which vehicle it found (since 2026-10-08).
 */
internal fun readingFromProfiles(
    handsFree: ProfileAnswer,
    audio: ProfileAnswer,
    handsFreeVehicle: String? = null,
    audioVehicle: String? = null,
): TruckReading = when {
    handsFree == ProfileAnswer.TRUCK_CONNECTED ->
        TruckReading.connected("the hands-free profile lists the truck", handsFreeVehicle)

    audio == ProfileAnswer.TRUCK_CONNECTED ->
        TruckReading.connected("the audio profile lists the truck", audioVehicle)

    handsFree == ProfileAnswer.NO_ANSWER ->
        TruckReading.unknown("the hands-free profile gave no answer")

    audio == ProfileAnswer.NO_ANSWER ->
        TruckReading.unknown("the audio profile gave no answer")

    else -> TruckReading.notConnected("neither the hands-free nor the audio profile lists it")
}

/**
 * The real answer to "is the truck connected right now?" (ADR-002). Since 2026-10-08 it asks
 * about every paired vehicle at once, and says which one it found.
 *
 * How it is read depends on the Android version:
 * - **From Android 16 QPR2 (API 36.1):** `BluetoothDevice.isConnected` for the classic
 *   transport. One call, and it is the link itself.
 * - **Before that (the POCO X5 is here):** Android has no public "is this device connected".
 *   The check asks the hands-free and the audio profile which devices they are connected to,
 *   through a proxy for each ([ProfileProxy]).
 *
 * **It fails closed.** Without the Bluetooth permission ("Nearby devices") every one of these
 * calls throws, so the answer is "unknown", with the reason. The same goes for a settings file
 * that cannot be read and for a profile that does not answer. Unknown is never "connected".
 *
 * It may be called from any thread and never blocks the caller: the Bluetooth calls run on a
 * background thread, and the wait for a proxy is a suspension with a time limit. Do not wrap it
 * in `runBlocking` on the main thread: the proxies are delivered there and could not arrive.
 */
class BluetoothTruckConnection(context: Context, private val settings: SettingsStore) :
    TruckConnectionSource {
    private val appContext = context.applicationContext

    /** Null on a device with no Bluetooth at all, such as some emulator images. */
    private val adapter: BluetoothAdapter? =
        appContext.getSystemService(BluetoothManager::class.java)?.adapter

    private val handsFree = adapter?.let { ProfileProxy(appContext, it, BluetoothProfile.HEADSET) }
    private val audio = adapter?.let { ProfileProxy(appContext, it, BluetoothProfile.A2DP) }

    override suspend fun read(): TruckReading = withContext(Dispatchers.Default) {
        val permission = appContext.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
        if (permission != PackageManager.PERMISSION_GRANTED) {
            return@withContext TruckReading.unknown("MilO is not allowed to use Bluetooth")
        }
        val addresses =
            try {
                settings.current().trucks().map { it.address }
            } catch (unreadable: IOException) {
                // The trip controller logs the unreadable file itself, with its stack trace.
                return@withContext TruckReading.unknown("the settings cannot be read")
            }
        when {
            addresses.isEmpty() -> TruckReading.notConnected("no truck is paired")
            adapter == null -> TruckReading.notConnected("this phone has no Bluetooth")
            else -> readBluetooth(adapter, addresses)
        }
    }

    private suspend fun readBluetooth(
        adapter: BluetoothAdapter,
        addresses: List<String>,
    ): TruckReading = try {
        when {
            !adapter.isEnabled -> TruckReading.notConnected("Bluetooth is switched off")

            // `BluetoothDevice.isConnected` arrived in a minor release (API 36.1).
            // `SDK_INT_FULL`, the number that counts minor releases, is itself only there
            // from API 36, so the plain level is checked first.
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA &&
                Build.VERSION.SDK_INT_FULL >= Build.VERSION_CODES_FULL.BAKLAVA_1 -> {
                // A plain loop, not a lambda: Lint follows the version check into it.
                var found: String? = null
                for (address in addresses) {
                    val device = adapter.getRemoteDevice(address)
                    if (device.isConnected(BluetoothDevice.TRANSPORT_BREDR)) {
                        found = address
                        break
                    }
                }
                linkReading(found)
            }

            else -> readProfiles(addresses)
        }
    } catch (denied: SecurityException) {
        // The permission was there a moment ago: it was taken away in between.
        TruckReading.unknown("Android refused the Bluetooth call ($denied)")
    } catch (malformed: IllegalArgumentException) {
        // Android rejects an address that is not six pairs of capital hex digits.
        TruckReading.unknown("the stored truck address is not a Bluetooth address ($malformed)")
    }

    /** @param found the vehicle whose classic link is up, or null if none is. */
    private fun linkReading(found: String?): TruckReading = if (found != null) {
        TruckReading.connected("its classic Bluetooth link is up", found)
    } else {
        TruckReading.notConnected("its classic Bluetooth link is down")
    }

    /** Asks both profiles at the same time, so a slow proxy is waited for once, not twice. */
    private suspend fun readProfiles(addresses: List<String>): TruckReading = coroutineScope {
        val noAnswer = ProfileReply(ProfileAnswer.NO_ANSWER)
        val handsFreeReply = async { handsFree?.answerFor(addresses) ?: noAnswer }
        val audioReply = async { audio?.answerFor(addresses) ?: noAnswer }
        val handsFreeAnswer = handsFreeReply.await()
        val audioAnswer = audioReply.await()
        readingFromProfiles(
            handsFreeAnswer.answer,
            audioAnswer.answer,
            handsFreeAnswer.vehicle,
            audioAnswer.vehicle,
        )
    }
}
